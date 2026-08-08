# Аудит мира и биомов (отдельно от арсенала)

## Доктрина

**Три слоя в итоге образуют параллелепипед** (Core · Surface · Sky/Orbit/End) в масштабе tall Overworld (−512…1024).

Отображение — **через хуки и максимальное использование движка**, не постройкой трёх кубов:

| Хук | Что даёт |
|-----|----------|
| `LayerBridge` | Тонкая **зона телепорта** на шве Y |
| `BoundarySeamRenderer` | «Занавес» блоков на границе |
| `LevelSky` / `OrbitalBeltSkyRenderer` | Небо/пояс с **анимацией как у Oblivion** (дрейф по времени мира) |
| `MantleStream` | Мантия рядом с игроком, не fill всей колонны |
| `SeamWarmup` | Фон: стрим колонки + chunk tickets реального Nether/End по **прогнозу на 3 с** вперёд, в обе стороны |
| `OrbitalBeltSkyRenderer` | **Spark-кольцо**: планета + тёмная полоса + зелёный ореол (skybox) |
| `KlondikeIslandGenerator` | Реальные блочные острова в sky-band |
| `EndIslandGenerator` | Тот же остров в End-камне — архипелаг End-полосы (900…1000) |

Immersive Portals — опциональный soft-dep для настоящего see-through.

**Voxel LLOD удалён целиком** (`world/llod`, `client/llod`, payload'ы). Дальний вид — Distant Horizons.
Orbit junk parked. Связка слоёв = LayerBridge + SeamWarmup + BoundarySeam + Spark ring + End-полоса.

| Шов Y | Слои |
|------:|------|
| −240 | Core ↔ Dungeon |
| 40 | Dungeon ↔ Surface |
| 320 | Surface ↔ Orbit |
| 880 | Orbit ↔ Oblivion |

---

## 6DoF + креатив

Vanilla creative `abilities.flying` (двойной пробел) ломает корпус: Y×0.6 и flySpeed вне `travel()`.  
Пока Descent включён: клиент (`ClientPlayerEntityMixin` HEAD+RETURN) и сервер держат `flying=false` **и** `allowFlying=false` — двойной пробел не может перевключить fly. H выключает 6DoF и возвращает `allowFlying` в креативе. Серверный `ServerPlayerFlightTravelMixin` отменяет vanilla `travel` пока armed.

`LayerBridge.tick` всегда (announce + display); teleport-hop только при 6DoF. `BoundarySeamRenderer` рисует все грани параллелепипеда (−240/40/320/880) в радиусе 120.

### SeamWarmup (бесшовный Nether / End)

- Решение принимается по **спрогнозированной** позиции: где корабль будет через **3 с**
  (`LOOKAHEAD_SECONDS`), по скорости лётной модели. Окно то же — **72** блока до лица шва,
  к **10** блокам радиус стрима/тикетов усиливается — но открывается заранее и тянется
  за прогнозом по XZ, а не за текущей клеткой.
- Пересечение шва внутри окна прогноза ловится отдельно: на форсаже корабль может быть
  вне окна и до, и после, и всё равно пройти шов между ними.
- Шов −240 (`NETHER_CEILING`): `LevelBuilder.streamAround` + MantleStream + ticket в `World.NETHER` (1:8).
- Шов 880 (`ORBITAL_TOP`): `LevelBuilder.streamEndBand` (только острова, без мантии) + ticket
  в `World.END` (XZ прогноза + арена 0,0) + ticket на арену в Overworld; ранний wake реактора.
- Внутри End-полосы стрим продолжается: чанки, уже загруженные до набора высоты, CHUNK_LOAD не покроет.
- **Обратный путь**: в End/Nether раз в секунду держится ticket на последней наземной точке пилота
  (`DescentPlayerData.lastOverworld*`) — возвращение домой тоже без подгрузки.
- Прогулка футпринта — раз в 5 тиков (`STREAM_INTERVAL`): постановка в очередь идемпотентна.
- Без fill кубов — только очередь LevelBuilder и движковые chunk tickets.

---

## Dig path

```
 SURFACE → plasma granite → mantle → CORE
```

*Мир аудируется отдельно от оружия.*

---

## Seams removed (bands are Y ranges, not rooms)

Three things were drawing a boundary the world does not have. All are gone.

**The curtain.** `BoundarySeamRenderer` painted a checkerboard of the two neighbouring layers'
HUD colours — 2-block cells with a gap, 64×64 around the player — on every face within 120 blocks.
At the Surface face (Y 40) that put a blue-green grid (`DUNGEON 0x445566` × `SURFACE 0x558844`) over
the ground at ordinary play altitude. Deleted.

**The hop.** `LayerBridge.seamTeleport` teleported the pilot a few blocks past each edge while 6DoF
was armed — inside the *same* dimension, since the bands are Y ranges of one column. It kept XZ,
scaled velocity to 0.4, forced a fixed ±2 vertical nudge and dropped creative flight. That was the
whole of what a boundary felt like. Deleted; the bands are streamed on approach by `SeamWarmup`
regardless, so nothing depended on the hop having run.

**The announcement.** Every crossing threw a full-screen title plus a chat line naming the seam Y.
A climb through the column flashed four of them. Now one grey action-bar line with the band name.

`SEAM_HALF`, `inSeamZone`, `nearestSeamY` and `parallelepipedFaces` existed only for the curtain and
went with it.

## Nether band relief

The band was a floor slab at −420, a ceiling slab at −240 and 180 blocks of nothing between them
with up to two basalt pillars per chunk — a very tall empty room, which from a cockpit has no scale
in it and nothing to fly around.

`NetherRelief` supplies both surfaces as a pure function of world position: two octaves of value
noise, floor rising up to 22 above its slab, ceiling hanging up to 16 below its own, lava sea five
blocks up so the low ground floods and the rest becomes coast. Glowstone is clustered by a lattice
cell rather than rolled per block, because scattered singles are invisible at flying range.

Purity is the point — the band is written chunk by chunk by a background stream, so two neighbours
built seconds apart on different ticks must agree about the column they share. `NetherReliefTest`
pins that: no step across a chunk edge, no neighbouring step over 3 blocks, band never pinches shut,
sea leaves both dry ground and flooded ground.

The fill is split into its own phases (1 floor, 2 ceiling) because relief costs several times what
two flat slabs did, and one chunk overrunning the tick budget by that much is a visible hitch.


## Fixed: chunk-seed leak into the relief field (the vertical-stripe bug)

Shipped in the relief commit above and caught from a screenshot the same session: the Nether band
rendered as a dense grid of vertical multicoloured stripes from altitude — a cliff at every chunk
border, packed close enough over a wide flyover to read as stripes rather than as individual walls.

The cause was one wrong variable. `LevelBuilder.step()` derives
`seed = world.getSeed() ^ (chunkX·A) ^ (chunkZ·B)` for its per-chunk `Random` — correct there, block
variety is supposed to differ chunk to chunk — and that same local was also being passed into
`buildNetherFloor` / `buildNetherCeiling`, which forwarded it into `NetherRelief.floorTop` /
`ceilingBottom`. Those are meant to be pure functions of world position so that two chunks built on
different ticks agree on the height they share at their border. Feeding them a seed that itself
depends on which chunk is asking defeats that on every single border: the world x=15 column (chunk 0)
and the world x=16 column (chunk 1) are neighbours, but computed their relief under two unrelated
seeds, so the two heights were as good as random relative to each other.

`NetherReliefTest` did not catch it, because it tested `NetherRelief`'s math in isolation with one
shared constant `SEED` on both sides of a simulated chunk border — which correctly proved the pure
function is continuous, but never exercised the call site that broke that guarantee. The fix is
entirely at the call site: `step()` now passes `job.world.getSeed()` into both builders instead of
the chunk-mixed local, and the two functions rename the parameter to `worldSeed` so the requirement
is visible at the signature. `NetherReliefTest.chunkMixedSeedIsUnsafeForContinuity` mirrors the mixing
formula and asserts it reliably breaks continuity, so the failure mode is on record even though the
math it protects was never wrong.

---

## Height budget: 1536 → 2672 blocks

Measured against the engine's own limits for a `dimension_type` (`height` ≤ 4064, `min_y` ≥ −2032,
`min_y + height` ≤ 2032): the shipped column used 1536 of a possible 4064 — under 38%. Stretched to
`min_y=−784, height=2672` (`min_y+height=1888`, still 144 short of the 2032 ceiling — headroom kept
deliberately rather than pushed to the edge).

**Not every band grew.** Vanilla's own terrain generator is untouched here (no `noise_settings`
override), and it always shapes ground between Y −64 and 320 regardless of how tall the dimension is
declared — that is where sea level, villages and biomes actually sit, no matter what `WorldLevels`
says. `ABYSS_TOP`(−64) and `SURFACE_TOP`(320) are exactly those two numbers for that reason, and
stayed exactly those two numbers: moving them would not move one block of real terrain, only mislay
the `LevelSky` untouched-plateau and the industrial/surface flavour split relative to ground that
never budged. `INDUSTRIAL_TOP`(40) sits between them by choice, not requirement, and was left alone
too. The other five bands are entirely this mod's own generated content with nothing external pinning
them, and very nearly doubled:

| Band | Was | Now |
|---|---|---|
| NETHER (open) | 180 | 360 |
| ABYSS | 176 | 260 |
| INDUSTRIAL | 104 | 104 *(pinned)* |
| SURFACE | 280 | 280 *(pinned)* |
| SKY | 320 | 700 |
| ORBITAL | 240 | 560 |
| END | 144 | 308 |

`NetherRelief.FLOOR_RELIEF`/`CEILING_DROP` grew too (22→35, 16→26) — by less than the band did, so
the extra height reads mostly as open flight room, with terrain relief scaled up rather than diluted.

### Two absolute-Y literals this broke

Neither was caught by compiling — both are exactly the "still a valid `int`, silently the wrong
place" class of bug the chunk-seed fix above already was.

**`EndReactorSession.END_BAND_ARENA_Y`** was the bare literal `940`, chosen when END spanned 880…1024
(60 above its own floor). Left as a literal, the arena would have stayed at 940 while its band moved
to 1580…1888 — landing inside the *Orbital* band, five hundred blocks under the islands it is dug
into. Now `WorldLevels.ORBITAL_TOP + 60`.

**`WorldLevels.Level.ORBITAL.travelY()`** was the bare literal `720` (80 above the old
`SKY_TOP`=640). `/d6 level orbital` would have arrived inside the new Sky band instead of Orbital.
Every `travelY()` case is now an offset from a band edge — `WorldLevelsTest.travelAltitudesAreInsideTheirBand`
already existed and catches this whole class by construction; it was this file that hadn't been
carrying a bare literal into being wrong, not the test that was missing.

Swept every other file touching a `WorldLevels` boundary (30 of them) for the same pattern; the rest
already derive from the constants and moved for free.

## Fixed: the streaming budget didn't grow when the bands did

Reported as the Nether band still not loading in, after the chunk-seed fix above — same visual family
of symptom, different cause. The seed fix stops neighbouring chunks disagreeing about height; this is
about a chunk not finishing in time at all.

`LevelBuilder.BUDGET_PER_TICK` (block writes the background drain may spend per tick, shared across
the whole queue) stayed at its pre-rescale value, `2800`, while the height-budget rescale
(`WorldLevels`, two commits back) grew every row count it pays for: mantle span 176→260, floor relief
22→35, ceiling drop 16→26 — a combined ×1.5 in total rows written per chunk. The budget still finishes
every chunk eventually; it just now takes 1.5× as many ticks to do it, and a pilot moving at the speed
that used to safely outrun the stream now doesn't. What that looks like from the cockpit is terrain
that failed to load, because from the player's side "still mid-build" and "never going to finish" are
indistinguishable — both are missing terrain where terrain should be.

Scaled `BUDGET_PER_TICK` to `4200`, the same ×1.5 the row count grew by, so a chunk takes the same real
time to finish now as it did before the rescale — reasoned from the ratio, not benchmarked against a
live server (nothing here can be). `GenerationBudgetTest` ties the two together going forward: it
fails if `WorldLevels`/`NetherRelief` grow the bands again without a matching budget change, which is
exactly the class of bug this was.

### If the Nether still looks wrong after this

Two independent things have to both be true for a rebuilt jar to actually contain either fix: the
source has to have the fix (it does, as of this session), and the jar being run has to be built from
it. `dist/`'s auto-refresh only fires on a genuine GitHub `push` event, and pushes from this session
have not been reliably registering as one — every fix this session needed a manual
`workflow_dispatch` to even get CI to run, and that path does not update `dist/`. If the same striped
pattern is still showing up, check which jar is actually running before looking for a third bug.

## Fixed: generation "locking onto a small area" — the drain queue's scheduling, not its budget

Reported again after the budget fix above, this time as generation staying confined to a small area
around wherever the pilot started rather than extending as they fly. Different mechanism from either
Nether fix above: not a disagreement between chunks, not a budget too small to finish a chunk — a
scheduling order that let one chunk hog the entire queue.

`LevelBuilder.enqueue` appends new work to the *tail* of `QUEUE` — for a moving pilot, that is
whatever chunk they have just approached. But `drain`'s loop re-queued an unfinished job with
`QUEUE.addFirst`, putting it straight back at the *head*, where the very next `poll()` in the same
tick picks it up again. That job then wins every poll after that too, tick after tick, for as long as
it takes to run mantle fill → floor relief → ceiling relief → shaft/End finish to completion — a
single chunk's full build is on the order of the mantle span alone (260 rows × 256 blocks), tens of
thousands of writes against a 4200-per-tick budget, so on the order of twenty ticks before the queue
ever looks at anything behind it. Multiply that by however many chunks were already queued and a
pilot's newest, closest-ahead chunk could sit behind minutes of backlog before its first block ever
gets placed. What finishes is whatever was queued earliest — the start point — and it looks exactly
like generation refusing to follow the pilot anywhere past it.

Changed the re-queue to `QUEUE.add` (tail), so an unfinished job goes to the back of the line instead
of monopolizing the front. Every in-flight job now gets a slice of each tick's budget in round-robin
rather than one job running to completion before the next is even touched — same total throughput
(the budget didn't change, so the whole backlog still takes exactly as long to fully drain), but a
chunk added under load starts making progress on the tick it is queued instead of after everything
ahead of it in the queue finishes. `LevelBuilderFairnessTest` mirrors the scheduling in isolation
(the real `drain`/`step`/`Job` are private and need a live `ServerWorld`) and pins the gap: in a
seed-backlog-of-five-plus-one-late-job scenario, the old policy leaves the late job untouched for
most of the backlog's total drain time, the new one touches it within two ticks.

### A second, smaller finding from the same sweep: crashed UFOs rebuilding themselves

Not the Nether/mantle stream at all — the separate macro-worldgen landmark system
(`world/gen2/*`, `world/base/DescentSession`) that seeds megacities, rifts, lunar bases, crashed UFOs.
No hard radius cap exists anywhere in it — placement is unbounded, keyed off whichever chunk a player
happens to load — so it is not itself a confinement bug. But `CrashedUfoGenerator.resolveCrashSite`
clamped the terrain-surface Y it resamples differently (`[≤120, floor sub-50 to 70]`) than
`ModWorldgen2.skyY`'s `CRASHED_UFO` case does (`[55,110]`) for the identical `getTopY` sample at the
identical X/Z — the two only ever agreed inside the overlap. Outside it, the LODESTONE "already built"
marker landed away from the Y every future chunk-load pre-check looks for it at, so any crash site on
low or tall terrain fully rebuilt itself — saucer, trap lattice, 24-entity garrison — on every reload,
which is ordinary play in a dogfighting mod: circling back over ground already cleared. Matched the
clamp to `[55,110]` in both places; `CrashedUfoMarkerTest` pins the two formulas agreeing for every
terrain height from bedrock to the surface band's top, not just the range that happened to overlap
before.

## Fixed: "OBLIVION SEAM" ghosting through the flight HUD in the End band

Same report, second half — described as part of the client "falling apart," with the settings screen
visible but unresponsive. A screenshot from inside the End band showed the answer to the HUD half of
that: the DRMD flight HUD's THRUST/ENERGY instrument cluster with a second, fainter line of text
bleeding through it — `§d◈ OBLIVION SEAM §7— End band streaming · dim warm`.

`SeamWarmup.tickColumn`'s End branch gated that action-bar announce on `crit`, the same flag used for
streaming radius. `crit` is `atSeamFace || y >= END_SEAM_Y` — deliberately true for a pilot's *entire*
time above the seam, because islands keep streaming in as they roam the band, not just while crossing
into it. Correct for streaming; wrong for a one-shot notification, which then re-announced every 80
ticks for as long as the pilot stayed in the band — not the brief crossing flash it reads as for the
Nether seam (whose `crit` has no such "inside forever" clause). `InGameHudMixin` draws the DRMD HUD
after vanilla's own render pass, and the instrument cluster's panel fill is translucent
(`PANEL = 0xA6000C10`, roughly 65% opacity) rather than solid, so an action-bar message that never
stops repeating shows through it continuously instead of fading out between crossings — read from
outside as the HUD glitching, not as a status message just doing its job too enthusiastically.

Split the two: `crit` keeps its wide definition for streaming radius; a new, narrower `atSeamFace`
(seam-face proximity only, no "already inside" clause) gates the announce. The heads-up still fires on
an actual crossing or a fast approach; it stops once the pilot is settled deep in the band with
nothing left to cross. `SeamAnnounceTest` mirrors the seam-proximity check (`SeamWarmup.approaching`
is private and the real check needs a `ServerPlayerEntity` tick) and pins both halves: the old
gate never stops firing while loitering in the band, the new one does, and an actual crossing still
announces either way.

The settings-screen-unresponsive half of the same report is still open — read through
`MouseMixin`/`GameMenuScreenMixin`/`DescentSettingsScreen`/`DescentKeybinds` twice without finding a
structural defect in any of them; worth noting the player's own log shows TLauncher with its bundled
skin/cape mod (`tlskincape`) loaded, and `GameMenuScreenMixin`'s own comment already documents that
launcher's skin overlays as a known source of pause-menu interference on this exact screen. Whether
that is the actual cause here or a red herring needs either a repro via the `,` keybind directly
(bypassing the pause menu entirely) or a log from the moment it happens — nothing in the uploaded
launcher log points at it (no exception anywhere, every session exits cleanly with code 0).

## Fixed: Core-band chunks visibly crawling in, and vanilla's sun/moon still showing underground

Reported as slow chunk loading near the Core, seen first-hand ("чанки буквально росли друг над
другом" — chunks literally growing on top of each other) while loitering there rather than passing
through, plus the Overworld's sun and moon still visible from the Core band and from the End band
alike — read together as "the transitions aren't really set up."

**The crawl.** Different mechanism from either Nether-band fix above — not a chunk disagreeing with
its neighbour, not a budget too small to finish a chunk ever, and not one job starving the rest of the
queue (that was the addFirst→addLast fix). `MantleStream.STREAM_CHUNKS = 6` around one digger is a
13×13, 169-chunk neighbourhood, refreshed by `LevelBuilder.drain`'s digger loop every tick a pilot
spends near the Core. The `addLast` fairness fix means every one of those 169 gets a turn — but a turn
each, round-robin, off one shared `BUDGET_PER_TICK`, is a completely different thing from finishing
quickly: with the budget divided across everything still mid-build, a chunk directly under a loitering
pilot advances by `MANTLE_ROWS_PER_STEP` rows every few ticks instead of every tick, and the whole
mantle fill is visibly, slowly assembling in front of them rather than already being there when they
arrive. Fairness stopped starvation; it didn't stop dilution.

Split the digger radius into two tiers: `MantleStream.STREAM_CHUNKS_NEAR` (2 — a 5×5, 25-chunk ring)
drains on `LevelBuilder.QUEUE` with the full per-tick budget, same as before; the remaining ring out to
`STREAM_CHUNKS` (144 chunks) drains on a new `PRESTREAM_QUEUE` that only gets whatever budget the near
ring didn't spend that tick — the same "gets what is left" relationship `END_QUEUE` already had to the
column queue, applied one level up. Total throughput is unchanged (same budget, same total row count,
same number of ticks to fully drain everything queued); what changes is which 25 of the 169 finish
first, and those 25 are the ones the pilot can actually see. A chunk that starts in the prefetch ring
and never gets promoted if the pilot later reaches it early behaves exactly as it did before this
split — never worse, which is what makes the change safe to ship without a way to load-test it live.
`LevelBuilderPriorityStreamTest` mirrors the two-queue scheduling and pins the three load-bearing
claims: the near ring finishes in the same tick count whether or not 144 far jobs are also queued, the
same near jobs take more than 3× as long in one undivided queue, and the split changes who goes first
without changing the total ticks to drain everything.

**The sky.** `ClientWorldMixin.drmd$levelSky` (see `LevelSky` above) tints `getSkyColor` — the flat
background colour, which the fog also reads from — but the sun and moon are separate textured quads
vanilla draws regardless of that colour, on their own draw calls this mod has never touched. Tinting
the sky red-black at the Core or violet in Oblivion while the ordinary sun still crosses it is exactly
"presence of the regular world's skybox" the report named — the colour changed, but the two objects
that make a sky read as *the Overworld's* specifically never went anywhere.

There is no per-position hook to cancel just those two quads without a raw Mixin into
`WorldRenderer`'s internals, and this project has no decompiled Minecraft source and no live client to
get that exact target right — a wrong Mixin target fails to apply at startup and takes the whole game
down with it, not a cosmetic miss CI would catch either way. Occluded them instead, with geometry
through the same `WorldRenderEvents` Fabric API this file already uses safely elsewhere
(`OrbitalBeltSkyRenderer`, shipped and unchanged): a large sky-coloured box enclosing the camera in
every direction (6DoF has no fixed "up" to skip one face of), coloured from `world.getSkyColor` itself
so it is invisible as a shape and reads only as "no sun or moon here." Unlike this file's other draws,
it keeps depth testing **on** — a shape meant to fill the whole sky has to lose to anything nearer
(terrain, a cavern wall, an island) or it would paint over real geometry the same way a depth-ignoring
skybox always would once it's this large.

Split across two owners rather than one new class covering the whole column: `CoreSkyDome` (new file)
handles the lower reaches, where nothing else draws custom sky content and there's nothing to conflict
with. The Oblivion/End side is handled inside `OrbitalBeltSkyRenderer` itself
(`paintOblivionEnvelope`) instead of a second independent class, because that file already owns a
competing set of visuals up there (the belt, the distant "Oblivion object" landmark) whose own alpha
stays saturated from the Sky band up through Oblivion with no natural gap to hand off through — one
method deciding both the landmark and the envelope is what keeps them from fighting over the same
pixels; two renderers gated on separate altitude curves would each need to know the other's alpha to
avoid it. `SkyOcclusionRampTest` mirrors both ramps (`CoreSkyDome.lowerAlpha`,
`OrbitalBeltSkyRenderer.envelopeAlpha`) and pins the same properties `LevelSkyTest` already established
for the colour tint: exact 0/1 endpoints, held flat rather than extrapolated beyond them, and no step
anywhere a pilot flying straight through would actually see one.

Neither fix touches the Sky/Orbital band itself — `OrbitalBeltSkyRenderer`'s existing belt vista owns
that altitude range untouched, and the report didn't name it; only the Core and Oblivion ends, which
it did.

## Cross-reference: `LayerBridge`/`SeamWarmup` are no longer unconditional

Both `tick` methods documented above as running "always" now start with a
`WorldLevels.isAdvancedColumn(...)` early return, added alongside three other gates
(`EnderDragonFightMixin`, `EndReactorSession`, `PortalComplexity`) for a Vanilla world-modification
level — a genuinely vanilla-height Overworld with real, unreplaced Nether/End. Full writeup, including
why the gate re-derives from the loaded world's actual height instead of a locked per-world flag like
`psychedelic`/`infiniteMegacity` use, is in `docs/WORLDGEN_MENU.md`. Nothing about the seam/streaming
mechanics themselves changed — the gate only decides whether they run at all for a given world.
