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
| `SeamWarmup` | Фон: стрим Nether-колонки + chunk tickets реального Nether/End **до 10 блоков** до шва (−240 / 880) |
| `OrbitalBeltSkyRenderer` | **Spark-кольцо**: планета + тёмная полоса + зелёный ореол (skybox) |
| `KlondikeIslandGenerator` | Реальные воксельные острова в sky-band — вместо macro LLOD |

Immersive Portals — опциональный soft-dep для настоящего see-through.

**LLOD силуэты parked** (`WorldFeatures.MACRO_LLOD=false`). Orbit junk parked. Связка слоёв = LayerBridge + SeamWarmup + BoundarySeam + Spark ring.

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

- Старт фона: **72** блока до лица шва; к **10** блокам — усиленный радиус стрима/тикетов.
- Шов −240 (`NETHER_CEILING`): `LevelBuilder.streamAround` + MantleStream + ticket в `World.NETHER` (1:8).
- Шов 880 (`ORBITAL_TOP`): ticket в `World.END` (XZ пилота + арена 0,0); ранний wake реактора.
- Без fill кубов — только очередь LevelBuilder и движковые chunk tickets.

---

## Dig path

```
 SURFACE → plasma granite → mantle → CORE
```

*Мир аудируется отдельно от оружия.*
