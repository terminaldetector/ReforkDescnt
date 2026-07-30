# DRMD 6DOF — Descent-like in Minecraft

![Fabric](https://img.shields.io/badge/Fabric-1.21.1-brightgreen)
![Java](https://img.shields.io/badge/Java-21-orange)
![Build](https://img.shields.io/badge/build-Gradle-blue)

**DRMD 6DOF** — это не «Minecraft с полётом». Это режим Minecraft, переосмысленный как **трёхмерная воксельная песочница в духе Descent**: инерционный 6DoF-полёт, реакторные комплексы, дроны-враги, транспорт Pyro, энергия, щиты и оружейная мастерская.

---

## Быстрый старт (активация мода)

### 1. Собрать jar

```bash
# Нужен JDK 21
./gradlew build
```

Готовый файл:

```
build/libs/drmd-6dof-1.0.0.jar
```

Или скачать артефакт из GitHub Actions: **Actions → Build DRMD 6DOF → Artifacts → drmd-6dof**.

### 2. Установить в Minecraft

1. Установите [Fabric Loader](https://fabricmc.net/use/) для **Minecraft 1.21.1**
2. Положите в папку `mods/`:
   - `drmd-6dof-1.0.0.jar`
   - [Fabric API](https://modrinth.com/mod/fabric-api) для 1.21.1
3. Запустите клиент / сервер

### 3. Активировать Descent-сессию в мире

В чате (читов достаточно для `/d6 start` в одиночке):

```
/d6 start
```

Это:

- вырезает **реакторную комнату** (сфера + ядро + кольца + шахты);
- ставит **вращающийся реактор**, док, чекпоинт, магнитную аномалию;
- спавнит **Pyro-транспорт** и **5 вражеских дронов**;
- выдаёт стартовый арсенал (MG, Plasma, Laser, Rockets, Gravy, Build Tool);
- включает 6DoF.

Дальше:

| Действие | Как |
|----------|-----|
| Сесть в корабль | ПКМ по Pyro |
| Свободный полёт | `H` (toggle 6DoF) |
| Рывок | `Shift` |
| Мастерская оружия | `M` |
| Ещё дроны | `/6dof_spawn assault` |
| Новый корабль | `/d6 ship` |

---

## Управление

| Клавиша | Действие |
|---------|----------|
| **H** | Вкл/выкл 6DoF |
| **WASD** | Тяга |
| **Space / Ctrl** | Вверх / вниз (локально) |
| **Q / E** | Крен |
| **Shift** | Dash (15 энергии) |
| **R** | Форсаж |
| **F** | Flight Assist |
| **Z** | Крюк |
| **G** | Сабрежим ракет |
| **M** | Оружейная Мастерская |
| **X** | Сброс крена |

---

## Что внутри

### Полёт и бой
- Инерционный 6DoF (spool, drag, idle-гравитация по **локальному UP**)
- Энергия + пресеты balanced / assault / interceptor / siege
- Щиты с регеном
- 27 оружий Descent/Doom-стиля
- Кластеры стволов + Workshop

### Мир 6DoF
- Нет абсолютного верха (`LocalOrientation`)
- **6D Soil** — рост на шести гранях наружу
- **Build Tool** — Look / Surface / Plane + вращение по 3 осям
- **Industrial Underground** — технокомплексы в генерации
- **World Generation 2.0** — небесные мегаструктуры, mega fauna, LLOD
- Ловушки навигации: hermetic / laser / turret / magnetic / unstable

### World Generation 2.0
- Многомасштабные формы: разломы, каньоны, арки, кольца, летающие материки, спирали, перевёрнутые острова
- Mega creatures как элементы мира: sky worm, drone swarm, reactor keeper
- **LLOD** — далёкие объекты как воксельные силуэты за километры
- Стриминг: MACROWORLD → REGION → CHUNK → LOCAL
- Спека: [`docs/WORLD_GEN_2.md`](docs/WORLD_GEN_2.md)

### Placeholder-модели (Descent-like)
| Объект | ID | Описание |
|--------|-----|----------|
| Pyro ship | `drmd:pyro_ship` | Транспорт — корпус + крылья + сопло |
| Drone | `drmd:drone` | Враг-дрон с 4 спарсами |
| Reactor | `drmd:reactor_display` | Вращающееся ядро реактора |
| Air mine | `drmd:air_mine` | Воздушная мина |
| Mega worm | `drmd:mega_worm` | Небесный червь (24 сегмента) |
| Drone swarm | `drmd:drone_swarm` | Якорь колоссального роя |
| Reactor keeper | `drmd:reactor_keeper` | Страж реакторных комплексов |

Модели — procedural placeholder (готовы к замене Blockbench-ассетами).

---

## Команды

```
/d6 start                         # реакторная база + кит + дроны + корабль
/d6 ship                          # заспавнить Pyro
/d6 kit                           # блоки мира / build tool
/d6 worldgen industrial [STYLE]   # комплекс (CRYSTAL_REACTOR, SMELTERY, …)
/d6 worldgen2 <kind>              # WG2.0: arch, ring, rift, canyon, continent, …
/d6 mega worm|swarm|keeper        # mega creatures
/d6 llod                          # sync LLOD silhouettes
/d6 orient reset                  # сброс локального UP
/6dof toggle|dash|alwaysrun
/6dof_spawn <role>                # assault, interceptor, mg, rpg, …
/6dof_spawn_squad 5
/d6 weapons give_all
```

Стили комплексов: `ABANDONED_RESEARCH`, `ANCIENT_POWER`, `AUTO_FACTORY`, `SMELTERY`, `CRYSTAL_REACTOR`, `TECH_RUINS`.

---

## Dev / CI

```bash
./gradlew runClient    # клиент разработки
./gradlew build        # jar
```

GitHub Actions: `.github/workflows/build.yml` — сборка на push/PR + upload jar.

---

## Структура

```
src/main/java/com/terminaldetector/drmd/
├── flight/ energy/ shield/ weapon/   # бой и полёт
├── ai/ entity/                       # дроны, Pyro, реактор
├── workshop/                         # оружейная мастерская + clusters
├── world/                            # 6DoF world rules, soil, traps, industrial
│   ├── gen2/                         # WG 2.0 megastructures + MacroWorld
│   ├── mega/                         # mega worm / swarm / keeper
│   ├── llod/                         # Long Level of Detail
│   └── base/ReactorRoomStarter.java  # /d6 start
└── client/                           # HUD, модели, рендер, LLOD silhouettes

docs/WORLD_DESIGN.md                  # спецификация мира
docs/WORLD_GEN_2.md                   # World Generation 2.0
legacy/                               # исходный GMod-аддон
```

---

## Рекомендуемый первый час

1. `/d6 start` — оказаться в реакторной камере  
2. ПКМ по **Pyro** — облететь ядро  
3. Отстрелять дронов (Plasma / Rockets)  
4. `M` — подкрутить оружие в Workshop → Apply Build  
5. Build Tool: Shift+ПКМ по стене → Local UP = эта грань → строить «пол» где угодно  
6. `/d6 worldgen industrial CRYSTAL_REACTOR` — новый комплекс рядом  
7. `/d6 worldgen2 arch` + `/d6 mega worm` — небесные мегаструктуры и фауна  
8. Улететь далеко и `/d6 llod` — увидеть силуэты LLOD на горизонте  

---

MIT. Inspired by **Descent**. GMod-оригинал — в `legacy/`.
