# Аудит мира и биомов (отдельно от арсенала)

*Срез концепции безшовного мира DRMD 6DOF. Флаги: `MACRO_WORLDGEN` / bands parked; `SURFACE_DISTRICTS` on.*

---

## Вердикт

| Тема | Состояние |
|------|-----------|
| Колонна Overworld −512…1024 | Готова (`overworld.json`, `WorldLevels`) |
| Контент слоёв (MACRO / NETHER_BAND / END_BAND) | **Выключен** — полный WG2 spam off |
| HL2 surface districts | **`SURFACE_DISTRICTS=true`** — lunar hub + megacity + sparse landmarks |
| Безшовность «одной колонной» | Частично: shaft grid + LevelSky; потолок движка ~2032 |
| Immersive Portals / Dimension Stack | Рекомендуемый внешний путь к true seamless |
| HL2-переходы без модов | `LayerBridge` — fade + soft arrive (districts/macro + afterburner) |
| Биомы ванили vs DRMD | Поверхность = ваниль + districts; остальные — LevelBuilder/gen2 |

---

## Концепт слоёв (narrative → Y / dim)

```
OBLIVION (End upper)     880…1024 / vanilla End
  Oblivion-атмосфера, гигантская враждебная база, реактор
  периодически жжёт лазер до слоя Core/Nether, финальный босс.
  Эндермены дружественны к DRMD-фракции.

ORBIT                    320…880
  Средняя враждебность: аномалии, деревни кверх ногами,
  парящие острова и станции.

SURFACE                  40…320 (+ industrial  −64…40 как «руины»)
  Руины цивилизации, неизведанные города, мирные базы, лаборатории.
  Поверхность периодически выжигается НЛО и враждебными сканерами.

DUNGEON                 −240…40 (abyss + industrial)
  Подземный филиал Энда: hi-tech комплексы рядом с заброшенными шахтами.
  Средняя враждебность.

CORE (Nether depth)     −512…−240
  Ядро планеты. Верх/середина незера — более агрессивные базы,
  запутанные шахты; тот же сеттинг.
```

Код: `com.terminaldetector.drmd.world.layer.WorldLayer`.

---

## Безшовность — два пути

### 1) С модом (предпочтительно для «настоящего» seamless)

| Мод | Зачем |
|-----|--------|
| **Immersive Portals** (Fabric 1.21.1) | See-through portals, **Dimension Stack**: пол Overworld ↔ потолок Nether / пол End ↔ орбита без loading screen |
| Forgiving World / Y-teleport rules | Проще, без see-through |

Ограничение движка: одна колонна выше ~2k блоков нестабильна (chunk sections, lighting, heightmap). Стек измерений обходит это.

Рекомендуемый стек ImmPtl:

```
End (Oblivion)  ↑
Orbit-dim/sky   ↑   ← опционально отдельный dim или верх OW
Overworld       ↑
Nether (Core)   ↑
```

### 2) Без модов — HL2-стиль (реализовано каркасом)

- Максимальная безопасная колонна: **−512…1024** (уже в моде).
- Переходы: `LayerBridge` — короткий title fade при смене `WorldLayer`, без hard load.
- При `MACRO_WORLDGEN=true` + afterburner на шве — soft reposition к midY слоя (как HL2 level stitch).
- Плавность визуала: `LevelSky` интерполяция, shaft grid каждые 8 чанков.
- Vanilla End/Nether остаются запасными «ячейками» для босса / ядра, пока bands parked.

Не обещать километровую Euclidean-башню без ImmPtl.

---

## Биомы / генерация (аудит gaps)

| Слой | Сейчас | Нужно для релиза |
|------|--------|------------------|
| Surface | Ваниль + хаб | Руины/лаборатории sparse, UFO/scanner scorches |
| Orbit | Пусто (band off) | Острова, станции, inverted villages, anomalies |
| Oblivion | Босс в vanilla End | База + reactor laser, friendly Endermen policy |
| Dungeon | Shafts only | Hi-tech + abandoned mines adjacency |
| Core | `buildNetherLevel` gated | Агрессивные базы, denser mines |

Флаги: `MACRO_WORLDGEN`, `NETHER_BAND`, `END_BAND` — `false`. `SURFACE_DISTRICTS` — `true` (см. `MEGACITY_COMPLEX.md`).

### Surface megacity (активно)

- Хаб: Lunar Base Descent 1 на spawn; маяки к городу.
- Город-данж NW: plate, highways, sky arena, artifact hangar, atriums, sewers, pyramid/mako, garrison.
- Под городом: tech ruins + rift; над городом: orbit ring + arch.
- 6DoF: улицы → maglev → arena; shaft под пирамидой → industrial.

---

## Фракции / враждебность (заметка)

- Endermen + DRMD CyberMobs/drones = одна «холодная» фракция в Oblivion (не агрят друг друга) — отдельный AI-пасс.
- Surface: НЛО / сканеры жгут ландшафт периодически (уже есть SkyUfo hooks; нужен schedule).
- Orbit: средняя; Core: высокая; Dungeon: средняя.

---

## Приоритеты до сильного world-релиза

1. Честный README: колонна + HL2 seams **или** ImmPtl stack.  
2. Расширить districts: Orbit / Dungeon sparse nodes (не полный WG2).  
3. Oblivion reactor laser как scripted event (End dim OK).  
4. Endermen faction ally.  
5. Опциональная soft-dep документация Immersive Portals Dimension Stack.

*Мир аудируется отдельно от оружия (`ARSENAL.md` / `RELEASE_AUDIT.md`).*
