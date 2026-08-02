# World Generation 2.0 — Experimental Multi-Scale Universe

Цель: превратить Minecraft из игры про поверхность в **многомасштабную трёхмерную воксельную вселенную** с непрерывным 6DoF-полётом.

## Вертикальный континуум (спека)

| Y (цель) | Слой |
|----------|------|
| −50 000 | Глубинные реакторные комплексы / Nether-аналог |
| 0 | Classic Overworld |
| 5 000–20 000 | Небесные архипелаги |
| 20 000–60 000 | Орбитальные мегаструктуры |
| 100 000 | End-space без отдельного портала |

Прототип мапит целевые высоты в практический Overworld (`WorldRules.practicalY`): industrial −56…40, sky 180…300.

## Многомасштабные генераторы (`world/gen2`)

| Kind | Описание |
|------|----------|
| `RIFT` | Огромные разломы |
| `CANYON` | Вертикальные каньоны |
| `ARCH` | Небесные арки |
| `RING` | Природные кольца / торы |
| `FLOATING_CONTINENT` | Летающие материки |
| `SPIRAL_RANGE` | Спиральные горные системы |
| `INVERTED_ISLAND` | Перевёрнутые острова |
| `LUNAR_BASE` | Заброшенная лунная база Descent 1 — щиты, ловушки, микрореактор + Keeper |
| `CRASHED_UFO` | Упавшая XCOM-тарелка — плотные ловушки/гарнизон (после Pyro GX) |
| `UFO` | Летающая тарелка (entity) — LLOD с неба, выжигает постройки, рой |

Генерация: chunk-load (~1/18, lunar/crashed в пуле) + `/d6 worldgen2 <kind>`.

Философия: мир выглядит «неправильным», но остаётся полностью проходимым полётом.

## Mega Creatures (`world/mega`)

| Entity | ID | Роль |
|--------|-----|------|
| Sky Worm | `drmd:mega_worm` | Многосегментный червь |
| Drone Swarm | `drmd:drone_swarm` | Якорь роя штурмовых дронов |
| Reactor Keeper | `drmd:reactor_keeper` | Страж реакторов / lunar boss |
| Sky UFO | `drmd:sky_ufo` | XCOM saucer: cruise, burn, drones, LLOD |

Спавн: chunk-load + `/d6 mega worm|swarm|keeper|ufo`.

## Descent Lunar Base

`LunarBaseGenerator` — серый диск на sky band, спицы с hermetic/laser, shield crystals вокруг `UNSTABLE_REACTOR`, `ReactorKeeper` на якоре. Stock seed у спавна.

## XCOM UFO pair

1. **Sky UFO** — **enterable flying hull** (oxidized copper saucer): bay door underside/side, deck cavity, `UNSTABLE_REACTOR` core. Cruises on a grid (carries occupants); LLOD at distance. Kill: fly in → **reactor dump** (`weapon_d6_reactor`) / bomb the core / break the reactor → hull shatters, fly out.
2. **Crashed UFO** — crater + copper saucer, турели/мины/14 дронов. Tip: чистить на Pyro GX.

## LLOD — Voxel Long Level of Detail

```
LLOD0 → LLOD1 → LLOD2 → CHUNK
```

Спека: [`docs/VOXEL_LLOD.md`](VOXEL_LLOD.md). `/d6 llod` — sync + счётчики.

## Команды

```
/d6 worldgen2 arch|ring|rift|canyon|continent|spiral|inverted|lunar|crashed|ufo
/d6 mega worm|swarm|keeper|ufo
/d6 llod
/d6 worldgen industrial [STYLE]
```

## Пакеты

- `world/gen2` — MacroEntry, MegaStructureGenerator, LunarBaseGenerator, CrashedUfoGenerator, ModWorldgen2
- `world/mega` — worm / swarm / keeper / sky UFO
- `world/llod` — LlodLevel, LlodRegistry, VoxelLodMesh

---

## Cyberpunk megacity

`MacroEntry.Kind.MEGACITY` — `/d6 mega city`, and one is seeded 320 blocks west / 280 south of
spawn on world creation. Far enough that spawn keeps open sky, close enough to be the obvious first
destination once you have a ship.

A 5×5 grid of 16-block city blocks with 8-block street canyons between them — 112 blocks across.
Built on a grid rather than scattered because the canyons are the point: they are what makes the
district flyable space instead of scenery.

| Part | Detail |
|--|--|
| Towers | 24, heights 22–58, shells with floor plates every 6 — enterable, not solid prisms |
| Glazing | cyan / purple / tinted bands between the storey lines |
| Crowns | lit rim per tower, one in three carries an aerial mast |
| Streets | decked and cleared 4 high, sea-lantern grid on an 8-block pitch |
| Sewers | full-span tunnels on both axes, 14 down, water channel with walkable flanks |
| Manholes | laddered shaft at all 16 street intersections |
| Pyramid | 11 steps, hollow, oxidised-copper seams, south entrance |
| Reactor | `unstable_reactor` core in a crying-obsidian shell, mounted mid-height on a copper column |

Every street line is mirrored underground and the two are stitched at the intersections, so the
city is one continuous volume from sewer floor to tower roof — enter at any manhole, come out
anywhere. A sewer you cannot get out of is a pit, not a level.

### Cost

Roughly 250,000 block writes if every one were issued. Two thirds of that is air over air — tower
interiors and street clearing — so `set` skips a write when the target already holds the wanted
state. The read is far cheaper than the block update and lighting recalculation it avoids.

| Part | Writes if unguarded |
|--|--|
| Streets + clearing | 76,614 |
| Towers | 141,696 |
| Sewer tunnels | 27,120 |
| Manhole shafts | 1,200 |
| Reactor pyramid | 4,017 |
