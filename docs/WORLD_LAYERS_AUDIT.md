# Аудит мира и биомов (отдельно от арсенала)

*Срез: Terraria-слои + HL2 fragment load. `NETHER_BAND=true` (streamed). `SURFACE_DISTRICTS=true`.*

---

## Вердикт

| Тема | Состояние |
|------|-----------|
| Колонна Overworld −512…1024 | Готова |
| Бедрок как граница | **Нет** — rewrite → `plasma_granite` (плазмоустойчивый гранит) |
| Мантия / dig-down Core | **On** — granite → nether mix → continuous nether |
| HL2 streaming | `MantleStream` — полный fill только у diggers / shaft grid |
| Vanilla Nether/End portals | Усложнены (`PortalComplexity` + catalysts) |
| Immersive Portals | Soft-dep, см. [`IMMPTL_STACK.md`](IMMPTL_STACK.md) |
| DimensionSync | Smoke / reactor / falls — выход на поверхность читаем |

---

## Dig path (Terraria / HL2)

```
 SURFACE / INDUSTRIAL
        ↓ dig
 plasma-resistant granite crust   (бывший −64 bedrock plug)
        ↓
 mixed granite + netherrack       (mantle)
        ↓
 continuous nether blocks
        ↓ seamless
 CORE cavern (−420…−240)          basalt / lava / pillars
```

Полный мантийный столбец **не** пишется во все чанки сразу — только рядом с игроками (`MantleStream.STREAM_CHUNKS`). Shaft grid больше не форсится при нуле игроков (это вешало Preparing spawn 100%). Bedrock→plasma — только тонкие Y-полосы (пол колонны + шов −64), не весь −512…−56. Crust plug (−64…−70) с early-out если уже solid.

---

## Порталы

| Путь | Как |
|------|-----|
| Seamless | Копать колонну / shafts / 6DoF |
| Vanilla Nether frame | Нужен `nether_gate_catalyst` |
| End portal eye | Нужен `end_gate_catalyst` |
| ImmPtl stack | Опционально; catalysts всё равно |

Крафт: stabilizer → nether/end catalysts (дорогие компоненты).

---

## Концепт слоёв

```
OBLIVION   880…1024 / vanilla End boss
ORBIT      320…880
SURFACE     40…320
DUNGEON   −240…40   (+ mantle dig into Core)
CORE      −512…−240
```

---

## Приоритеты

1. ImmPtl configs как user-facing optional pack (`IMMPTL_STACK.md`)  
2. Sparse Orbit / Oblivion nodes без полного WG2  
3. Hostile 6DoF populate в Core  

*Мир аудируется отдельно от оружия.*
