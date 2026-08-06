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
