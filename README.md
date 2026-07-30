# DRMD 6DOF — Minecraft Fabric Mod

![Fabric](https://img.shields.io/badge/Fabric-1.21.1-brightgreen)
![Java](https://img.shields.io/badge/Java-21-orange)
![Status](https://img.shields.io/badge/status-Port-blue)

**DRMD 6DOF** (Descent-Inspired Resource Management Dynamics) — полный порт боевого аддона Garry's Mod на **Minecraft Fabric 1.21.1**.

Сохранены исходные черты: 6DOF-полёт, энергия с пресетами, щиты, 27 оружий, AI-дроны с ролями, карточные сущности, кокпит-HUD и **Оружейная Мастерская**.

Исходный GMod-код лежит в [`legacy/`](legacy/) для сверки поведения.

---

## Системы

| Система | Источник (GMod) | Реализация |
|---------|-----------------|------------|
| 6DOF полёт + инерция + dash + крюк | `d6_core.lua` | `flight/FlightSystem` |
| Энергия + пресеты | `d6_energy.lua` | `energy/EnergySystem` |
| Щиты | `d6_shield.lua` | `shield/ShieldSystem` |
| Снаряды / урон / отдача | `d6_weapon_core.lua` | `weapon/core/WeaponCore` |
| 27 оружий | `weapon_d6_*.lua` | `weapon/items/*` |
| AI роли | `d6_ai_roles.lua` | `ai/AiRole` + `DroneEntity` |
| Мастерская | SCK schema/UI | `workshop/*` |
| Карта | FGD entities | `checkpoint` / `dock` / … |
| HUD | `d6_cockpit.lua` | `client/hud/DescentHud` |

### Физика (Source → Minecraft)

Скорости и ускорения из Source переводятся через `UNIT_SCALE = 1/80`, чтобы сохранить характер Descent (быстрый инерционный полёт) без неиграбельных значений.

### Энергетические пресеты

- **balanced** — 34 / 33 / 33 (W/S/E)
- **assault** — 55 / 25 / 20
- **interceptor** — 25 / 20 / 55
- **siege** — 45 / 45 / 10

Реген: `8 * (0.5 + weaponsAlloc)` ед/сек — как в оригинале.

### Оружие

Пулемёт, Плазма, Тяжёлый, Лазер, Ракеты (4 сабрежима), Грави-рельса, Вулкан, Флак, ГСН/КС/Умная/Мега ракеты, Quad-лазер, Рельса МК2, BFG, Фраг, Овердрайв, Энерговолна, Копьё/Поле тьмы, капканы, мины, Сброс реактора, Варп, Телефраг, Хлыст.

Классы урона: `kinetic` / `energy` / `explosive` / `exotic`.

### Враги

Роли: assault, interceptor, artillery, support, heavy_elite + legacy mg/laser/rpg/heavy/seeker/grav + air mines.

```
/6dof_spawn assault
/6dof_spawn_squad 5
/6dof_spawn_mine
/d6_kill_npcs
```

---

## Управление

| Клавиша | Действие |
|---------|----------|
| **H** | Вкл/выкл 6DOF |
| **WASD** | Тяга |
| **Space / Ctrl** | Вверх / вниз |
| **Q / E** | Крен |
| **Shift** | Рывок (15 энергии) |
| **R** | Форсаж (Always-Run) |
| **F** | Flight Assist |
| **Z** | Крюк |
| **G** | Сабрежим ракет |
| **T** | Радар |
| **M** | Оружейная Мастерская |
| **X** | Сброс крена |

---

## Команды

```
/6dof toggle|dash|alwaysrun|flightassist
/d6 energy preset <balanced|assault|interceptor|siege>
/d6 energy set <w> <s> <e>
/d6 set <gravity|accel|drag|maxSpeed> <value>   # admin
/d6 weapons list
/d6 weapons give_all                            # admin
```

---

## Сборка

Требования: **JDK 21**, интернет для зависимостей Fabric.

```bash
./gradlew build
```

Готовый jar: `build/libs/drmd-6dof-1.0.0.jar`  
Установка: положить в `mods/` вместе с Fabric API для 1.21.1.

Запуск клиента разработки:

```bash
./gradlew runClient
```

---

## Структура

```
src/main/java/com/terminaldetector/drmd/
├── DescentMod.java          # входная точка
├── DescentPlayerData.java   # состояние игрока
├── flight/                  # 6DOF
├── energy/                  # пул + пресеты
├── shield/                  # поглощение + реген
├── weapon/                  # ядро + 27 оружий
├── ai/                      # роли и цели
├── entity/                  # снаряды, дроны, блоки карты
├── workshop/                # 4-вкладочный редактор
├── client/                  # HUD, бинды, рендер
├── command/                 # /6dof /d6
└── network/                 # sync + input

legacy/                      # оригинальный GMod аддон
```

---

## Оружейная Мастерская

Клавиша **M** — вкладки **Stats / Projectile / Flak / Guidance / Clusters**.

### Clusters (строительство)
Порт SCK WeaponClusters + `D6_SCKBridge`:
- Зоны: **Center → Upper → SideLeft → SideRight → Lower**
- Модули: `barrel` / `nosegun` / `strider` / `gravy` с позицией, scale, muzzle idx
- Дефолтные компоновки всех 27 оружий из `d6_wepview.lua`
- **Load Held** — загрузить construction текущего оружия
- **Apply Build** — применить маззлы/вид к оружию (сервер + клиенты)
- **Reset Layout** — вернуть Doom-стиль по умолчанию
- Экспорт Java включает блок `WeaponClusters` + `ConstructionRegistry.setOverride`

Точки выстрела из construction кормят `WeaponCore.muzzleFor` (плазма/quad/vulcan стреляют из нескольких стволов).

---

MIT License. Inspired by Descent. Original GMod systems preserved in spirit and numbers.
