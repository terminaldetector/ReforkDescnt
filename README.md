# DRMD 6DOF — Descent-like in Minecraft

![Fabric](https://img.shields.io/badge/Fabric-1.21.1-brightgreen)
![Java](https://img.shields.io/badge/Java-21-orange)
![Build](https://img.shields.io/badge/build-Gradle-blue)

**DRMD 6DOF** — это не «Minecraft с полётом». Это **готовый режим Minecraft** в духе Descent: инерционный 6DoF, Pyro GX, индустриальные комплексы, небесные мегаструктуры, 10 ролей дронов, энергия, щиты и оружейная мастерская.

Descent-сессия **встроена в мир** — при загрузке Overworld появляется spawn-хаб, 6DoF включается при входе, мегаструктуры генерируются стоково.

---

## Быстрый старт

### 1. Собрать jar (CLI)

```bash
# JDK 21
./gradlew build --no-daemon
```

Готовый файл: `build/libs/drmd-6dof-1.0.0.jar`  
CI: **Actions → Build DRMD 6DOF → Artifacts → drmd-6dof** (`workflow_dispatch` поддерживается).

### 2. Установить

1. [Fabric Loader](https://fabricmc.net/use/) **1.21.1**
2. В `mods/`: `drmd-6dof-1.0.0.jar` + [Fabric API 1.21.1](https://modrinth.com/mod/fabric-api)
3. Новый мир / сервер — Descent уже часть мира

### 3. Играть

| Режим | Как |
|-------|-----|
| Survival | Скрафтить **Pyro GX** (железо + редстоун + печь + медь) → ПКМ поставить → сесть |
| Creative | Вкладка **DRMD 6DOF** → **Pyro GX** (ПКМ) или spawn egg ×10 ролей |
| Исследование | Летать (`H`) к индустриальным комплексам / небесным аркам / кольцам |
| Опционально | `/d6 start` — мгновенная реакторная комната + кит |

| Действие | Как |
|----------|-----|
| Сесть в корабль | ПКМ по Pyro GX |
| Свободный полёт | `H` |
| Рывок | `Shift` |
| Мастерская | `M` |
| Дроны | spawn eggs / `/6dof_spawn assault` |

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

### Мир 6DoF (stock)
- Descent-сессия **нативная**: spawn-хаб + 6DoF при входе
- **Industrial Underground** ~1/12 чанков + гарнизон дронов
- **WG 2.0** мегаструктуры ~1/18 чанков + сид вокруг спавна
- **LLOD** — Voxel LLOD0→1→2→Chunk (`docs/VOXEL_LLOD.md`)
- Спеки: `docs/WORLD_DESIGN.md`, `docs/WORLD_GEN_2.md`

### Pyro GX
- Item `drmd:pyro_gx` — ПКМ / по блоку → спавн корабля
- Survival рецепт: 4× iron block, 3× redstone block, furnace, copper block
- Creative: вкладка DRMD + автовыдача при первом входе

### 10 ролей дронов (6DoF attitude)
Assault, Interceptor, Artillery, Support, Heavy Elite, MG, Laser, RPG, Heavy, Seeker — общий CombatGoal с yaw/pitch/roll по скорости. Spawn eggs в креативе.

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
/d6 start                         # опционально: реакторная база + кит
/d6 ship                          # заспавнить Pyro (или используй item Pyro GX)
/d6 kit                           # блоки мира / build tool
/d6 worldgen industrial [STYLE]   # комплекс
/d6 worldgen2 <kind>              # WG2.0 mega-structure
/d6 mega worm|swarm|keeper
/d6 llod
/d6 atmosphere                   # atmospheric band + smoke/fire counts
/d6 bomb [tnt|cluster|incendiary|guided]
/d6 laser
/d6 orient reset
/6dof toggle|dash|alwaysrun
/6dof_spawn <role>                # 10 ролей
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
│   ├── atmosphere/                   # altitude physics bands
│   ├── bombardment/                  # aerial TNT / cluster / guided
│   ├── smoke/ fire/                  # dynamic smoke + 3D fire
│   ├── llod/                         # Long Level of Detail
│   └── base/ReactorRoomStarter.java  # /d6 start
└── client/                           # HUD, модели, рендер, LLOD silhouettes, smoke

docs/WORLD_DESIGN.md                  # спецификация мира
docs/WORLD_GEN_2.md                   # World Generation 2.0
docs/ATMOSPHERE_COMBAT.md             # Sector A: atmosphere / bombs / smoke / fire
legacy/                               # исходный GMod-аддон
```

---

## Рекомендуемый первый час

1. Зайти в мир — 6DoF уже включён, рядом spawn-хаб с дронами и Pyro  
2. **Survival:** скрафтить Pyro GX → поставить → сесть  
3. **Creative:** взять Pyro GX / spawn eggs из вкладки DRMD  
4. Облететь индустриальные комплексы и небесные арки  
5. `M` — Workshop · Build Tool для 6D-строительства  

---

## Evening test (PC + MCPE)

```bash
./scripts/package_all.sh
# → dist/drmd-6dof-1.0.0.jar              (Fabric PC)
# → dist/drmd-6dof-fast-test-1.0.0.mcaddon (Bedrock/MCPE)
```

CI Artifacts (Actions → **Build DRMD 6DOF**):
- `drmd-6dof-pc` — Fabric jar
- `drmd-6dof-mcpe` — `.mcaddon` + packs
- `drmd-evening-test` — both + README

MCPE sandbox notes: [`mcpe/README.md`](mcpe/README.md)

## Phase 3 — 6DoF Framework

- Adaptive Construction после посадки Pyro GX (`/d6 construct`)
- Engineer tools: Construction / Repair / Mining Laser + Gravity Scanner
- Projectile Framework (7 kinds, shared hits)
- Gravity Generator + Gravity Torch + multi-zone stations
- Spec: [`docs/PHASE3_FRAMEWORK.md`](docs/PHASE3_FRAMEWORK.md) · MCPE sandbox: [`docs/MCPE_FAST_TEST.md`](docs/MCPE_FAST_TEST.md)

## AI Sector A — Atmosphere & vertical combat

- Atmospheric bands (classic → thin → near-space + deep pressure)
- Aerial bomb bay (TNT / cluster / incendiary / laser-guided) + designator
- Dynamic volumetric smoke + 3D fire with LLOD
- Spec: [`docs/ATMOSPHERE_COMBAT.md`](docs/ATMOSPHERE_COMBAT.md)

## Блог / концепт

- Блог / концепт: `docs/BLOG_PRESENT.md` · Roadmap: `docs/ROADMAP.md` · Voxel LLOD: `docs/VOXEL_LLOD.md`

---

## Nexus / дистрибуция

Готовый продукт для выгрузки (Modrinth / CurseForge / Nexus-совместимый jar):

| | |
|--|--|
| Loader | Fabric 1.21.1 |
| API | Fabric API `0.116.15+1.21.1` (см. `gradle.properties`) |
| Java | 21 |
| License | MIT |
| Artifact | `drmd-6dof-<version>.jar` (без `-sources`) |
| Build | `./gradlew build` или GitHub Actions |

---

MIT. Inspired by **Descent**. GMod-оригинал — в `legacy/`.
