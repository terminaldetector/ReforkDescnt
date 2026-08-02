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
