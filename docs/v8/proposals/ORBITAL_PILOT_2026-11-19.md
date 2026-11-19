# Original orbital pilot — S13-T04 / S13-T06

This is a small original editable blockout, not a planetary-scale ring or a new
generator. It reuses the existing manual city authoring storage,64-cell CAS,
durable WAL, owner/context/permission checks, sterile-state limits and cancellation.
No extra engine, dimensions, actors, block entities, startup writes or legacy
Advanced generator. Near Minecraft blocks are used; volumetric mid/far station
LOD in S13-T03 remains separate.

For a connected Creative permission2 author, choose an already loaded sterile
site within the actual world/border/halo envelope:

```text
/ungravitycity planorbital <seed> <x> <y> <z>
/ungravitycity inspect
/ungravitycity apply
/ungravitycity route
```

Planning is inert. Apply advances only through the original loaded-only jobs;
unavailable cubes, protected/foreign states and intersecting actors still pause
before writes. Pause/resume/cancel remain the same commands and fixed save slot.
An existing city job cannot be overwritten to create this pilot. No automatic
placement is added to ordinary worlds.

The original shape has eight damaged octagonal ring sections, a separate service
hangar and a three-level central Citadel service hub with a clear vertical shaft.
Seed selects the broken arc; all passages and nine ordered route gates are
deterministic. It fits the existing256×256×72authoring envelope and unchanged
65536solid/65536cut/zeroactor/zeroBE limits. Every module remains under the existing
2048-solid building limit. No external map/model layout or copyrighted asset is
included. The pilot is authored at the chosen real coordinates, not at a fake
hardcoded orbital altitude.

Manifest schema2 explicitly records CITY or ORBITAL_PILOT. Schema1 remains CITY
with its original seed/anchor/geometry/hash and no automatic migration. A schema1
manifest can still be read and written byte-exact; unknown kind/schema, changed
context or mismatched geometry refuses. New template metadata cannot silently
regenerate an orbital plan as city or alter old CITY geometry.

Only after actual authoring completes, `route` reads the current operator's real
server position. The next gate must have loaded clear air, be within1.5blocks and
be observed on a later actual world tick. It reports ordered gate count and
elapsed ticks through hangar, hub, vertical shaft, upper gallery, eastern ring
and return. It never moves the player, spawns enemies or grants a reward. Progress
is an explicitly session-only authoring/playtest measurement, not a saved mission
receipt or proof that every intervening movement was collision-valid. Gameplay
Survival quest integration and full cross-layer/cold pacing remain open.

Seven new pure tests cover bounded original geometry/clear gate cells,
determinism/unchanged CITY factory, byte-exact legacy schema1, distinct orbital
manifest restoration/type rejection, ordered later-tick gates, unavailable gate
refusal and terminal measured completion. One added case in the already
registered PlanetaryZoneContextGameTests uses a real connected player, normal
operator authority and the actual dispatcher to check unprivileged refusal,
inert plan, route refusal before placement and explicit cancellation. That case
does not claim a completely placed or physically traversed pilot.

The focused ordinary run on `b6dbee75` placed all 7,331 plan cells. It read back 4,412 solids and 2,919 air cuts, then verified the same journal, geometry hash and cells after a warm I/O reopen. The stock fixture kept its own temporary chunk leases. The full run completed at test tick 554 and retained the existing 64-cell batch limit. Flight across all nine gates, a cold save reload and native/Sodium orbital footage remain open. [Exact execution and first failures](../EVIDENCE/round3-20261119/orbital-authoring.zip).
