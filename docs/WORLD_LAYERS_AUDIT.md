# Аудит мира и биомов (отдельно от арсенала)

*Срез: tall Overworld + **зоны телепорта на швах** + хук отображения блоков. Не параллелепипеды.*

## Доктрина

Движок **не ломаем** — `min_y=-512`, `height=1536` даёт место для полёта/копания.

Слои — это **не** «три куба, которые надо построить». На каждой границе Y:

1. **Зона телепорта** (`LayerBridge`, ±`SEAM_HALF`) — пересёк шов → arrive в соседний бэнд  
2. **Хук отображения блоков** (`BoundarySeamRenderer`) — клиент рисует «занавес» клеток на плоскости шва  

Без portal-load. Immersive Portals — опциональный soft-dep для настоящего see-through.

| Шов Y | Слои |
|------:|------|
| −240 | Core ↔ Dungeon |
| 40 | Dungeon ↔ Surface |
| 320 | Surface ↔ Orbit |
| 880 | Orbit ↔ Oblivion |

---

## Вердикт

| Тема | Состояние |
|------|-----------|
| Tall Overworld −512…1024 | Да (комната для 6DoF) |
| Параллелепипеды / три куба | **Нет** — убраны из доктрины |
| Seam teleport | `LayerBridge.seamTeleport` |
| Boundary block display | `BoundarySeamRenderer` |
| Бедрок как граница | **Нет** — `plasma_granite` |
| Мантия / Core stream | `MantleStream` у игроков |
| Vanilla Nether/End portals | Catalysts |
| ImmPtl | Soft-dep |

---

## Dig path

```
 SURFACE / INDUSTRIAL
        ↓ dig
 plasma granite crust
        ↓
 mantle mix → continuous nether
        ↓
 CORE cavern
```

Полный мантийный столбец не пишется во все чанки — только рядом с игроками.

---

## Приоритеты

1. ImmPtl optional pack  
2. Богаче визуал шва (типы блоков по слою)  
3. Hostile populate в Core  

*Мир аудируется отдельно от оружия.*
