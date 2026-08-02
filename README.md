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
| Survival | Скрафтить **Pyro GX** (плиты + энергоячейки + ядро наведения + печь + железо) → ПКМ поставить → сесть |
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
- **Полный обзор 360°** — как в оригинальном Descent: нос проходит через зенит и надир
  без гимбал-лока, бочка крутится бесконечно в любую сторону
- Энергия + пресеты balanced / assault / interceptor / siege
- Щиты с регеном
- 27 оружий Descent/Doom-стиля
- Кластеры стволов + Workshop

### Обзор 360° (как это работает)
Ориентация корабля хранится как ортонормированный базис (forward + up), а не как углы
Эйлера. Мышь вращает базис **вокруг локальных осей**, поэтому в тракте ввода нет ни одной
ссылки на мировой «верх» — гимбал-лока не существует в принципе.

Камера Minecraft умеет только yaw/pitch, поэтому крен подаётся отдельно, экранным
поворотом. Ключевое: опорный кадр для крена считается **из yaw/pitch самой камеры**
(`ShipAttitude.levelRight/levelUp`), а не через `forward × worldUp` — последнее вырождается
на полюсах. Тройка (yaw, pitch, bank) восстанавливает базис точно, поэтому крен нельзя
сглаживать: у полюсов yaw «прокручивается», и только точный крен это компенсирует.
Сглаженное значение (`DescentCamera.hudRoll`) идёт исключительно на приборы HUD.

### Мир 6DoF (stock)
- Descent-сессия **нативная**: spawn-хаб + 6DoF при входе
- **Industrial Underground** ~1/12 чанков + гарнизон дронов
- **WG 2.0** мегаструктуры ~1/18 чанков + сид вокруг спавна
- **LLOD** — Voxel LLOD0→1→2→Chunk (`docs/VOXEL_LLOD.md`)
- Спеки: `docs/WORLD_DESIGN.md`, `docs/WORLD_GEN_2.md`

### Кокпит-HUD
Изумрудный тактический оверлей поверх ванильного хотбара/сердец:

| Зона | Панель |
|------|--------|
| Слева сверху | `6DOF MODE / THRUST / DAMPENERS / GRAVITY / SPEED` |
| Слева | `COORDS: X / Y / Z` |
| Слева | `TARGET INFO` — силуэт, тип, HP-полоса, дистанция, скорость |
| Слева снизу | `[SYS]` лог · миникарта (ship-up, рельеф + контакты) · `BIOME / TIME / LIGHT` |
| Сверху по центру | Полоса здоровья цели |
| Справа сверху | 3D-радар `±X / ±Y` — проекция **в базис корабля**, верна в петле и бочке |
| Справа | `WEAPONS` (боезапас из инвентаря) · `SHIELD` / `ARMOR` + каркас корпуса с креном |
| Справа снизу | Карточка боеприпаса из руки · `PROJECTION / ATM / ALT` |
| Снизу по центру | Приборы: `THRUST + BOOST` · авиагоризонт + `LOCK` · `ENERGY + DAMP.` |

Панели раскладываются по фактическому scaled-разрешению и отбрасываются по приоритету,
если места нет, — при GUI scale 4 на 1080p кокпит остаётся читаемым.

### Pyro GX
- Item `drmd:pyro_gx` — ПКМ / по блоку → спавн корабля
- Survival рецепт: 2× alloy plate, targeting core, 2× energy cell, furnace, 3× iron block
- Creative: вкладка DRMD + автовыдача при первом входе

### Крафты (survival)
Всё в моде крафтится. Дерево держится на трёх промежуточных предметах:

| Предмет | Рецепт |
|---------|--------|
| `drmd:alloy_plate` ×2 | 2× iron ingot + 2× copper ingot (2×2 в шахматку) |
| `drmd:energy_cell` ×2 | 4× copper ingot + 4× redstone + amethyst shard |
| `drmd:targeting_core` | ender pearl + 2× alloy plate + energy cell + gold ingot |

Дальше — единые «рамы», где меняются только ствол и силовое ядро:

| Семейство | Рама | Что решает |
|-----------|------|------------|
| 27 оружий | `AAB / CRB / A··` | `B` — ствол, `C` — ядро (iron → tnt → energy cell → targeting core → nether star) |
| 5 инженерных инструментов | `·B· / AEA / ·A·` | `B` — рабочая головка |
| 4 бомбы + целеуказатель | `·A· / ATA / ·X·` | `X` — взрыватель |
| 4 турели | `·W· / AEA / ADA` | `W` — готовое оружие DRMD на диспенсере |
| 5 маркеров сессии | `XEX / AAA` | `X` — сигнальный цвет |

Отдельные рецепты: 6D-грунт, гермоворота, лазерный барьер, магнитная аномалия,
нестабильный реактор, генератор гравитации, гравифакел. Всего **57 рецептов** — покрыты
все предметы и блоки, кроме creative-only spawn eggs. Рецепты открываются в книге при
входе в мир.

### Мир — одна колонна, а не три измерения
Оверворлд расширен до **−512 … 1024**. Нижний мир и Энд — не измерения, а **уровни**:
полосы высоты, куда просто долетаешь, без портала и загрузочного экрана.

```
 1024 ┐ END        880…1024   осколки эндстоуна, обсидиановые шпили
  880 ┤ ORBITAL    640… 880   мегаструктуры, вакуум
  640 ┤ SKY        320… 640   небесный архипелаг
  320 ┤ SURFACE     40… 320   ванильный рельеф
   40 ┤ INDUSTRIAL −64…  40   ванильный камень, комплексы DRMD
  −64 ┤ ABYSS     −240… −64   провал между уровнями (пробиты шахты)
 −240 ┤ NETHER    −420…−240   базальтовые каверны, лавовые моря
 −512 ┘
```

Безшовность: каждый 8-й чанк по обеим осям получает шахту радиусом 3, пробитую сквозь
бедрок и камень над ним (с ободом из sea lantern) плюс дыру в потолке нижнего уровня.
`/d6 level` — где ты сейчас, `/d6 level <name>` — лифт на уровень.

### Роты врагов (гибридный киберпанк)
10 ролей дронов + три новые машины на общем шасси `CyberMobEntity`
(щит → броня → корпус, резисты по классу урона, энергия гейтит спуск курка):

| | Треножник | Летающий сканер | Паук-турель |
|--|-----------|-----------------|-------------|
| Ход | шагает на трёх ногах | висит, без гравитации | ходит, врастает при линии огня |
| Оружие | заряжаемое плазменное копьё + топот | лазер → 3 ракеты по очереди → перезарядка |  пулемёт очередями ≤18 м, дальше лазер |
| Фишка | приседает на зарядке — это тэлл | ракеты уходят с разных пилонов вращающегося кольца | башня наводится в 5× быстрее шасси |

Рой: члены дальше 46 блоков от якоря подтягиваются обратно, четверть роя — сканеры,
поэтому рой давит вблизи и одновременно кидает ракеты со standoff.

Все повороты летающих — через `ai/FlightAttitude` по тем же правилам, что и корабль
игрока: крен считается от полюсобезопасного кадра, углы идут кратчайшей дугой.

### Проджектайлы и фьюзы
Фьюз отделён от боевой части: `IMPACT`, `DELAYED_IMPACT` (ракеты взводятся в 0.12 с от
пусковой), `TIMED` (флак рвётся в конус шрапнели), `PROXIMITY` (мины ложатся и ждут).
**Боевой лазер поджигает** — точка попадания сеет очаг в общий `FireSystem`, так что
распространение и дым идут через существующую симуляцию.

Спека: [`docs/ENEMIES_AND_LEVELS.md`](docs/ENEMIES_AND_LEVELS.md)

### 10 ролей дронов (6DoF attitude)
Assault, Interceptor, Artillery, Support, Heavy Elite, MG, Laser, RPG, Heavy, Seeker — общий CombatGoal с yaw/pitch/roll по скорости. Spawn eggs в креативе.

### Модели и текстуры (Descent-like)
| Объект | ID | Описание |
|--------|-----|----------|
| Pyro ship | `drmd:pyro_ship` | Транспорт — корпус + крылья + сопло |
| Drone | `drmd:drone` | Враг-дрон с 4 спарсами |
| Reactor | `drmd:reactor_display` | Вращающееся ядро реактора |
| Air mine | `drmd:air_mine` | Воздушная мина |
| Mega worm | `drmd:mega_worm` | Небесный червь (24 сегмента) |
| Drone swarm | `drmd:drone_swarm` | Якорь колоссального роя |
| Reactor keeper | `drmd:reactor_keeper` | Страж реакторных комплексов |

Меши — процедурные (готовы к замене Blockbench-ассетами), а вот текстуры собственные:
51 иконка предметов 16×16, 19 блочных текстур 16×16 и 3 атласа сущностей 64×64,
разложенные ровно под UV кубоидов из `entity/model/*.java`. Всё генерируется из кода —
`python3 scripts/gen_textures.py` (нужен Pillow); при правке UV модели правьте и генератор.

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

## Evening test (PC + MCPE Master)

```bash
./scripts/package_all.sh
# → dist/drmd-6dof-1.0.0.jar
# → dist/drmd-6dof-mcpe-master-1.0.3.mcaddon
# → dist/drmd-6dof-mcpe-master-1.0.3.zip   # ручная установка в games/com.mojang
```

CI Artifacts (Actions → **Build DRMD 6DOF**):
- `drmd-6dof-pc` — Fabric jar
- `drmd-6dof-mcpe` — Master `.mcaddon` + `.zip` + packs
- `drmd-evening-test` — both + README

MCPE Master: [`mcpe/README.md`](mcpe/README.md) · [`mcpe/INSTALL_MCPE_MASTER.txt`](mcpe/INSTALL_MCPE_MASTER.txt)

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
