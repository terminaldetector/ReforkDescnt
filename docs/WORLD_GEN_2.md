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

Генерация: chunk-load (~1/18) + команда `/d6 worldgen2 <kind>`.

Философия: мир выглядит «неправильным», но остаётся полностью проходимым полётом.

## Mega Creatures (`world/mega`)

Не боссы — **элементы ландшафта**:

| Entity | ID | Роль |
|--------|-----|------|
| Sky Worm | `drmd:mega_worm` | Многосегментный червь; полёт вдоль тела |
| Drone Swarm | `drmd:drone_swarm` | Якорь колоссального роя штурмовых дронов |
| Reactor Keeper | `drmd:reactor_keeper` | Древний страж реакторных ядер |

Спавн: редко при chunk-load + `/d6 mega worm|swarm|keeper`.

Каждый регистрируется в `MacroWorld` для LLOD-силуэтов.

## LLOD — Voxel Long Level of Detail

```
LLOD0  силуэт (~тысячи кубов)
  ↓
LLOD1  крупные формы
  ↓
LLOD2  регион
  ↓
CHUNK  обычные блоки Minecraft
```

Спека и бюджеты: [`docs/VOXEL_LLOD.md`](VOXEL_LLOD.md).

Сервер шлёт компактный каталог макрообъектов; клиент разворачивает воксельные меши. `/d6 llod` — sync + счётчики по бэндам.

## Индустриальные биомы

См. `world/gen` — комплексы с реакторами, тоннелями, ангарами. WG 2.0 связывает их с вертикальным континуумом и LLOD.

Практические biome labels (`WorldRules.practicalLayer` / `biomeLabel`) отображаются в PC HUD. Stock seed (`DescentSession.seedLayerBiomes`) гарантирует ориентир в каждом слое у спавна. Mega-structures помечаются `LODESTONE` для идемпотентности при chunk reload.

## Команды

```
/d6 worldgen2 arch|ring|rift|canyon|continent|spiral|inverted|…
/d6 mega worm|swarm|keeper
/d6 llod
/d6 worldgen industrial [STYLE]
```

## Пакеты

- `com.terminaldetector.drmd.world.gen2` — MacroEntry, MacroWorld, MegaStructureGenerator, ModWorldgen2
- `com.terminaldetector.drmd.world.mega` — mega fauna
- `com.terminaldetector.drmd.world.llod` — LlodLevel, LlodRegistry
- `com.terminaldetector.drmd.client.llod` — клиентские силуэты
