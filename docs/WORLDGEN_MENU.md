# World generation menu + Infinite Megacity

Two requests, handled together because the second is one of the choices the first exposes:
surface the mod's world/biome generation options in the menu before a world is created, covering
the variety that already existed; add an endlessly-tiling megacity for testing drone-swarm AI and
bot combat behaviour on open ground.

## What existed before this

Nothing was GUI-editable. `WorldFeatures` (`NETHER_BAND`, `END_BAND`, `KLONDIKE_ISLANDS`,
`ORBIT_JUNK`, `MACRO_WORLDGEN`, `SURFACE_DISTRICTS`) was seven `static final` booleans — compile-time
constants with no loader at all; changing one meant editing the source and rebuilding the jar. The one
setting that *was* runtime-configurable, `psychedelicWorlds`, only reached a hand-edited
`config/drmd-server.properties`, read once at mod init, with no in-game screen anywhere that pointed at
it. Vanilla's Create World screen was completely untouched — no mixin, no injected button, nothing.

## The settings screen

`DrmdWorldGenScreen`, opened from a "DRMD World Generation…" button `CreateWorldScreenMixin` injects
into the vanilla Create World screen (same `addDrawableChild`-at-`init`-`RETURN` technique
`GameMenuScreenMixin` already uses for the in-game pause menu, for the same reason: it survives
third-party launcher overlays rebuilding the screen, which is why that technique was chosen there in
the first place).

It exposes:

- **World kind** — a three-way choice: Stock Descent, Psychedelic Void, Infinite Megacity.
- **Six sub-toggles** — the `WorldFeatures` flags that used to require a rebuild:
  Nether/Core band, End band, Klondike islands, Orbit junk, Macro structures, Surface districts.

Every click writes `config/drmd-server.properties` immediately (`DrmdServerConfig.save`) and applies
to the running process's `WorldFeatures` fields live — the six fields changed from `static final` to
plain `static` for exactly this, so every existing read site (chunk-load listeners, `DescentSession`,
the biome-source mixin) picks up a change without itself needing to change.

**This is global config, not per-world.** Making every `WorldFeatures` read site world-aware would have
meant threading a `ServerWorld`/`DescentWorldState` lookup through ten-odd hot paths — chunk-load
handlers and a biome-source mixin among them — for a payoff (a config screen) that does not need it.
Instead, the screen edits the same global default `psychedelicWorlds` already was, and
`DescentSession.seedWorld` locks the *resolved* world kind into that world's own `DescentWorldState`
the moment it is first seeded — exactly the rule the pre-existing `psychedelic` flag already followed.
Changing this screen after a world exists changes what the *next* world becomes; it was never able to
reach back into one already created, and still can't.

## Infinite Megacity

`DrmdServerConfig.WorldKind.INFINITE_MEGACITY` is a third alternative to Stock/Psychedelic in
`DescentSession.seedWorld` — it skips spawn-hub and landmark seeding entirely (nothing to seed
up front) and locks `DescentWorldState.infiniteMegacity`, which the four existing biome-plate
listeners (`MegacityBiomeWorldgen`, `TechnogenicSeaBiomeWorldgen`, `ScorchedLandsBiomeWorldgen`,
`IronGuildBiomeWorldgen`) and `SurfaceEventWorldgen` now check and skip on, the same way they already
skip for `isPsychedelic()` — a focused mode should not also be growing an unrelated sparse plate in
the middle of the city.

### Why placement wasn't the hard part

An audit of the existing sparse megacity system (`MegacityRegions`) found its plate *placement* is
already unbounded — `anchorInCell` hashes any integer cell coordinate with no upper bound, so it
already works infinitely far from spawn. What makes it feel small is *density* (roughly 1 plate in
every 6 cells of a 3072-block grid — mostly empty terrain between plates) and that
`MegacityGenerator.generate` is a single synchronous stamp over a fixed ~112-block footprint, not
chunk-tiled.

`InfiniteMegacityRegions` reuses `MegacityGenerator` completely unmodified — no rewrite of the city
generator itself — and replaces the *placement* strategy: a plain regular grid, `PITCH = 160` blocks
between plate centres, every cell built, no hash, no roll, no spawn exclusion.

`PITCH` is not a round number picked by feel. `MegacityGenerator`'s widest off-centre feature is the
artifact hangar, reaching `half + 20 = 76` blocks from a plate's origin (every other feature, including
the plate rim, stays inside `half + 10 = 66`). Packing plates `160` apart keeps each one's full `±80`
half-cell wider than that 76-block worst case in every direction — not just the one the hangar happens
to use — so no two adjacent plates can ever draw into each other regardless of which feature is doing
the reaching. The 8 blocks left over (`160 − 2×76`) is deliberately tight: the point of this mode is
contiguous city, not city with parks between the districts. `InfiniteMegacityGridTest` derives this
from `MegacityGenerator`'s own layout constants rather than asserting the two numbers agree by
coincidence — it fails if either file's geometry changes without the other being revisited.

### Its own queue, not the shared landmark one

Cells are discovered on chunk load, same as the sparse plates, but building is deferred to its own
queue (`InfiniteMegacityWorldgen`) rather than `DescentSession`'s shared `enqueueLandmark` queue. That
shared queue drains 1–3 jobs a tick across every landmark source a stock world has — a couple dozen
distinct callers, sized for how rarely any one of them actually fires. A mode whose entire purpose is
a city in *every* cell would flood that budget within seconds of a player taking off, starving
whatever else shares it. It gets a separately accounted budget instead (1 plate/tick, 2 once the
backlog passes 8) — sized against a megacity plate specifically, which was already one of the heaviest
single jobs the shared queue accepted.

Building itself waits for a player to actually be within 256 blocks of a plate's anchor (mirroring
`DescentSession.SEED_RADIUS`, tuned there for the identical reason, explained in that class's own doc
comment): a ~150-block plate footprint reaches chunks well past whichever single chunk first crossed
into a fresh cell, and writing into a chunk that is not loaded yet is either a silent no-op or an
unwanted forced load. By the time a player is 256 blocks out, the plate's footprint is already inside
their own view distance and loading regardless.

Building goes through `MegaStructureGenerator.generate(..., MacroEntry.Kind.MEGACITY, ...)`, not
`MegacityGenerator.generate` directly, even though the latter would have worked the first time. The
wrapper's LODESTONE-at-origin check is the *only* re-entry guard a plate gets — `MegacityGenerator`
itself has none. `DescentWorldState.clearLandmarkSeedMarks` wipes this mode's own "already queued"
cell tracking on every server restart, by design (the queue is in-memory only, same as every other
landmark queue in this mod), so a previously-built cell a player flies back over gets rediscovered and
re-enqueued after any restart. Calling the unguarded generator directly there would have rebuilt the
entire plate on top of itself — caught in review before it shipped, not from a report.

## Advanced vs Vanilla — how much of the world this mod is allowed to change

A second control on the same screen, `WorldModLevel` (`DrmdServerConfig.worldModLevel`): **Advanced**
(today's full tall column, unchanged — every band, every layer) or **Vanilla** (the goal: a genuinely
vanilla-height Overworld, real unmodified Nether/End with normal portals, DRMD flight/weapons/HUD
layered on top with minimal world changes).

Resolving `VANILLA` — at config load, and again at every `SERVER_STARTED`, against whichever world
actually loaded — forces all six `WorldFeatures` flags off regardless of what was individually saved
for each (`DrmdServerConfig.forceFeaturesFor`, shared by `applyFrom`, `save`, and the `SERVER_STARTED`
reconciliation so there's exactly one place this rule lives), and gates five further systems that had
no `WorldFeatures` gate of their own before this:

- `LayerBridge.tick` / `SeamWarmup.tick` — no band-crossing announcements or Nether/End warm-loading;
  there is nothing to warm in a column with no bands.
- `EnderDragonFightMixin` (`tick` and `respawnDragon`) — vanilla's own dragon fight and respawn run
  untouched instead of being replaced by the reactor-base fight.
- `EndReactorSession` (`onServerTick`'s dragon suppression and `arenaWorlds`) — the real End is left as
  an ordinary dragon-fight dimension: no suppressed dragons, no reactor arena raised in it.
- `PortalComplexity`'s `UseBlockCallback` — Nether/End portals ignite and activate normally, no
  catalyst item required.

Each of those five is a one-line early return on `WorldLevels.isAdvancedColumn(...)` — no other logic
in any of them changed.

### Ground truth, not a locked flag — and why that means two different timings

`psychedelic`/`infiniteMegacity` lock their resolved value into `DescentWorldState` the moment a world
is first seeded, because they're pure bookkeeping — nothing else in the save says which one a world is.
Column height doesn't need that: the dimension registry is already the authoritative record, fixed
permanently at world creation, so `isAdvancedColumn` re-reads the loaded Overworld's own
`getBottomY()`/`getHeight()` every call instead of trusting a stored flag that could go stale — and
self-corrects a case config-time forcing alone can't: opening a second, differently-configured world in
the same running game.

That split is deliberate and produces two different timings on this one screen, which is why the six
feature toggles disappear the instant Vanilla is picked but the row itself carries a restart note
instead of matching the rest of the screen's instant-apply language:

- The six `WorldFeatures` flags are, and always were (see "What existed before this" above), live
  global config with no per-world memory — flipping to Vanilla force-clears them immediately, the same
  global/live-apply tradeoff unchecking any one of them by hand already had.
- The five gated systems above read `isAdvancedColumn` — the *actual* loaded Overworld's height — so
  they keep behaving exactly as Advanced for any world that is still physically the tall column,
  regardless of what this screen says, until that world's height itself changes.

### What's not wired up yet

Nothing here can make an existing or new Overworld actually shorter — `overworld.json` is still merged
into the mod's base data unconditionally, so `isAdvancedColumn` reports `true` for every world today no
matter what this screen says. That's the next, riskier step: moving the dimension-type override into an
optional built-in resource pack (`DrmdBuiltinPacks`, via
`ResourceManagerHelper.registerBuiltinResourcePack`) whose activation type is chosen from this same
config — tracked separately from this scaffolding pass. Until it lands, picking Vanilla here clears the
six feature flags and previews the screen layout, but does not yet produce a shorter world.

**Load-bearing limitation once that pack does exist, worth restating here because it will not be
obvious from playing the game:** a resource pack's registered activation type is fixed once, at mod
init, from whatever `worldModLevel` the config held at *that* moment — flipping this screen's row mid-
session still writes the config immediately like everything else here, but the pack itself only picks
up the new value on the *next game launch*. Reaching into the live `CreateWorldScreen`/`WorldCreator`'s
own datapack-selection state to make it same-session-immediate was investigated and set aside: the real
Yarn field/method names for that surface cannot be confirmed without decompiled source unavailable in
this sandbox, so it's a follow-up for once the primary mechanism has round-tripped through a live client,
not part of this pass.

## Tests

- `InfiniteMegacityGridTest` — derives `PITCH`'s safety margin from `MegacityGenerator`'s own
  constants; checks cell/anchor assignment is consistent and gap-free.
- `DrmdServerConfigKindTest` — pins `WorldKind` resolution against every existing
  `config/drmd-server.properties` shape: no `worldKind` line at all (old file, either value of the
  legacy `psychedelicWorlds` boolean), an explicit line in either case, and a malformed value falling
  back safely instead of failing config load.
- `WorldModLevelConfigTest` — same shape for `WorldModLevel` (default `ADVANCED`, case-insensitive,
  malformed value falls back safely), plus pinning that resolving `VANILLA` forces every
  `WorldFeatures` output false regardless of what was individually saved for each one.
- `AdvancedColumnGateTest` — pins `isAdvancedColumn`'s two concrete numbers: this mod's tall column
  (`-784`, height `2672`) reads as Advanced; vanilla's real height (`-64`, height `384`) and an
  arbitrary third height both read as not-Advanced.
