# DRMD 6DOF — аудит к сильному релизу в Minecraft

*Черновая работа закончена. Ниже — инвентарь того, что есть, и что нужно довести, чтобы релиз в MC-среде ощущался цельным, а не демо-хабом.*

**Дата среза:** ветка с ордонансом / роевым ИИ / тоннельными бурами  
**Цель релиза:** «более-менее сильный» продукт в экосистеме Minecraft (Fabric 1.21.1), а не полный порт Descent.

---

## Вердикт

| Слой | Готовность | Комментарий |
|------|------------|-------------|
| Полёт 6DoF + камера + HUD | **Сильный** | Инерция, крен, бочки, миникарта, радар |
| Оружие (количество / ассеты) | **Сильный по объёму** | 29 стволов, рецепты, модели, текстуры |
| Оружие (поведение / баланс) | **Средний** | Мины-заглушки, ракеты с energy=0, мёртвый `ProjectileFramework` |
| Мобы / ИИ | **Сильный у дронов и CyberMobs** | Рой + враждебность к среде + бомбёжка деревень |
| Контент мира (survival loop) | **Слабый** | `MACRO_WORLDGEN=false` — после хаба пусто |
| Мега-сущности | **Слабый** | Командные игрушки; worm без боя |
| Мультиплеер | **Средний / честный gap** | Smoke/fire без полного S2C |
| Survival-прогрессия | **Слабая** | Крафт есть, кампании/гейтов нет |

**Рекомендуемый ярлык сейчас:** `0.9 Beta — Creative & Sandbox Descent`  
**До `1.0 Survival-lite`:** гарантированный encounter-loop без `/d6` + честный README + мины/энергия ракет.

---

## 1. Оружие (`weapon_d6_*`) — полный список

Источник: `ModItems` / `WeaponDef`. Splash: `splashDamage @ splashRadius` (Source units → `su()` если >20).

| # | id | Имя | Кат. | Dmg | Splash | CD (s) | Energy | Behavior | Режим огня | Статус |
|---|-----|-----|------|-----|--------|--------|--------|----------|------------|--------|
| 1 | mg | Пулемёт | primary | 12 | — | 0.06 | 1 | mg | снаряд | OK |
| 2 | plasma | Плазма | primary | 45 | 25@120 | 0.45 | 8 | plasma | ×2 орб | OK |
| 3 | heavy | Тяжёлый | primary | 80 | 60@220 | 1.1 | 18 | heavy | снаряд+blast | OK |
| 4 | laser | Лазер | primary | 40–150* | растёт* | 0.4 | 4–22* | laser | hitscan charge | OK, def игнор |
| 5 | rockets | Ракеты | primary | ≥100* | ≥90@320* | 0.8 | 20 | rockets | 1/3/6/atomic | OK |
| 6 | gravy_railgun | Грави-Рельса | primary | — | — | 0.3 | gravy pool | gravy | grab/fling | Lite, не Havok |
| 7 | vulcan | Вулкан | secondary | 15 | — | 0.065 | 2 | vulcan | все дула | OK |
| 8 | flak | Флак | secondary | 12 | 16@90 | 0.7 | 14 | flak | ×12 спред | OK |
| 9 | quad_laser | Quad-лазер | secondary | 45 | 55@160 | 0.3 | 12 | quad_laser | hitscan×4 | OK |
| 10 | frag | Фраг | secondary | 60 | 50@180 | 1.5 | 24 | frag | +8 осколков | OK |
| 11 | homing | ГСН-ракета | heavy | 110 | 75@260 | 3.5 | **0** | homing | самонаведение | ⚠ free |
| 12 | concussion | КС-ракета | heavy | 150 | 140@420 | 2.0 | **0** | **basic** | dumbfire | ⚠ не seeker, free |
| 13 | smart_missile | Умная-ракета | heavy | 130 | 100@320 | 4.0 | **0** | homing | самонаведение | ⚠ free |
| 14 | mega_missile | Мега-ракета | heavy | 420 | 320@720 | 8.0 | **0** | **basic** | dumbfire | ⚠ не seeker, free |
| 15 | mega_laser | Мега-лазер | heavy | 420 | 280@520 | 1.4 | 55 | mega_laser | hitscan+AoE | OK |
| 16 | railmk2 | Рельса МК2 | heavy | 120 | — | 0.6 | 18 | rail | pierce×5 | OK |
| 17 | bfg | BFG | heavy | 300 | 150@600 | 10 | 80 | bfg | орб+chain | OK |
| 18 | fusion | Фьюжен | heavy | 45–195* | 90@260* | hold | 25–70* | fusion | заряд | OK |
| 19 | darklance | Копьё тьмы | heavy | 250 | — | 5.0 | 70 | darklance | multi-hitscan | ⚠ пустой FX |
| 20 | overdrive | Овердрайв | utility | 35 | 22@110 | 0.08 | 3 | beam | hitscan −shield | OK |
| 21 | shockwave | Энерговолна | utility | — | 120@900 | 3.0 | 40 | shockwave | self AoE | OK |
| 22 | darkfield | Поле тьмы | utility | 18 | 0@400 | 4.0 | 45 | deploy | lob | ❌ не поле |
| 23 | energytrap | Энерго-капкан | utility | 6 | 0@150 | 1.2 | 22 | deploy | lob | ❌ не капкан |
| 24 | gravmine | Грави-мина | utility | 90 | 0@300 | 1.5 | 30 | deploy | lob | ❌ не мина |
| 25 | plasmamine | Плазма-мина | utility | 120 | 100@260 | 1.0 | 25 | deploy | lob | ❌ impact, не prox |
| 26 | reactor | Сброс реактора | utility | dump* | dump* | 15 | all | reactor | self nuke | OK + UFO dump |
| 27 | warp | Боевой варп | utility | 100 | @200 | 2.5 | 30 | warp | TP+splash | OK |
| 28 | telefrag | Телефраг | utility | 1000 | @140 | 5.0 | 50 | telefrag | TP на цель | OK |
| 29 | whiplash | Хлыст | utility | 80 | @160 | 3.0 | 20 | whiplash | zip/TP | OK |

\*Runtime перебивает `WeaponDef`.  
Ассеты: рецепты / модели / текстуры = **29/29**.  
`ru_ru.json` ключи оружия: почти все строки живут в `en_us` (RU-текст).

### Бомбоотсек

| id | Тип | blast | Особенность |
|----|-----|------:|-------------|
| bomb_tnt | TNT_BOMB | 1.45 | HE + огонь |
| bomb_cluster | CLUSTER | 0.95 | 7 суббоеприпасов |
| bomb_heavy_cluster | HEAVY_CLUSTER | 1.75 | 14 сферических |
| bomb_rocket | ROCKET | 2.25 | реактивный полёт |
| bomb_incendiary | INCENDIARY | 1.15 | поджог |
| bomb_guided | LASER_GUIDED | 1.65 | по целеуказателю |
| laser_designator | — | — | метка |

### Инженерка

| id | Роль |
|----|------|
| mining_laser | широкая проходка |
| tunnel_laser | цилиндрический тоннель |
| construction_laser | постройка по взгляду |
| repair_laser | ремонт / heal / shield+ (слабо на non-cracked) |
| build_tool | 6DoF build |
| gravity_scanner | диагностика |
| drill_rig | вертикальная 3×3 шахта (redstone) |
| tunnel_drill_rig | горизонтальный бур r2 |

---

## 2. Мобы и сущности

### Дроны (`AiRole` × `DroneEntity`)

| Роль | AI style | Бой | Рой | Бомбит деревни | Спавн без WG |
|------|----------|-----|-----|----------------|--------------|
| assault | pressure | сильный | да | нет | хаб, яйца, swarm, UFO |
| interceptor | flank | сильный | да | нет | то же |
| artillery | standoff | сильный | да | **да** | хаб/яйца/cmd |
| support | support | heal+snipe | да | нет | хаб/яйца/cmd |
| heavy_elite | anchor | танк+deathboom | нет | нет | хаб/яйца/cmd |
| mg | pressure | сильный | да | нет | яйца/swarm/UFO |
| laser | flank | сильный | да | нет | то же |
| rpg | standoff | сильный | да | **да** | хаб/яйца/cmd |
| heavy | anchor | танк | нет | нет | хаб/яйца/cmd |
| seeker | flank | homing | да | **да** | хаб/яйца/UFO |
| grav | pressure | как assault | нет | нет | **только `/6dof_spawn`** |

Цели: игрок → жители/големы → животные; свои DRMD не бьют.

### CyberMobs (лучшие «новые»)

| Моб | ИИ | Спавн в мире | Заметка |
|-----|-----|--------------|---------|
| Tripod | высокий | **только яйцо** | ланц + стomp |
| Scanner | высокий | ~25% swarm | laser→3 ракеты |
| Spider Turret | высокий | **только яйцо** | MG/laser head |

### Прочее

| Сущность | Бой | Спавн | К релизу |
|----------|-----|-------|----------|
| AirMine | prox splash | только cmd | яйцо или убрать из публичного API |
| Pyro GX | пилот | крафт/креатив/хаб | OK как транспорт |
| SkyUfo | дроны+burn | `/d6 mega` | sandbox / later |
| DroneSwarm | якорь+18 юнитов | `/d6 mega` | sandbox |
| MegaWorm | **нет атаки** | `/d6 mega` | доделать или «ландшафт» |
| ReactorKeeper | pulse | End / cmd | later |
| EndReactorBoss | фазы+кристаллы | **ванильный End** | OK как endgame при честном тексте |

---

## 3. Механики (карта систем)

| Система | Флаг / состояние | Для релиза |
|---------|------------------|------------|
| 6DoF flight | ON | must keep |
| Gravity torch / FootGravity / fields | ON | must keep — визитка |
| Energy / shields / presets | ON | must keep |
| HUD + dynamic minimap + radar | ON | must keep |
| Smoke / Fire | ON, **SP-biased** | честно писать про MP |
| Bombardment | ON | keep |
| Workshop (M) | ON | keep |
| Atmosphere bands | ON (правила) | keep |
| Engineer tools / drill | ON | keep |
| LLOD | ON, пустой без макро | зависит от worldgen |
| `MACRO_WORLDGEN` | **false** | **главная дыра survival** |
| `NETHER_BAND` | false | later / perf |
| `END_BAND` | false | OK (сток-небо), End boss в End dim |
| Natural biome spawns | нет | later |

---

## 4. Что ломает ощущение «сильного релиза» в MC

1. **Мир пустой после хаба** — README обещает комплексы/арки, флаги выключены.  
2. **Лучшие мобы (Tripod/Turret) не встречаются** без креатива.  
3. **Deploy-оружие врёт названием** — мины не мины.  
4. **Ракеты без энергии** — ломают экономику.  
5. **Баланс vs ваниль** — телефраг/мега-ракеты уничтожают обычный MC без Descent-контекста.  
6. **Мега-червь без боя** — нельзя продавать как контент.  
7. **MP smoke** — нельзя обещать полную атмосферу на выделенном сервере.

---

## 5. План до сильного релиза (приоритеты)

### Must-have (иначе не 1.0, а честная beta)

| # | Работа | Зачем |
|---|--------|-------|
| M1 | **Честный scope в README / first-join tip** *или* минимальный unpark worldgen | Игрок понимает, во что сел |
| M2 | **Encounter loop без опс-команд**: респавн хаб-дронов / лёгкий garrison / патруль | После первого зачищения есть игра |
| M3 | Впустить **Tripod + Scanner + SpiderTurret** в этот loop | Новые мобы перестают быть «яичными» |
| M4 | **Починить deploy**: proximity fuse / `AirMineEntity` / или переименовать | Нет фейковых «мин» |
| M5 | **Energy cost на ракеты** (homing/concussion/smart/mega) | Энергия снова ресурс |
| M6 | MP-дисклеймер: smoke/fire best on integrated SP | Нет обманутых серверщиков |

### Should-have (перед «Survival-lite 1.0»)

| # | Работа |
|---|--------|
| S1 | Concussion / mega_missile → реальный homing или честные имена |
| S2 | AirMine + Grav eggs (или скрыть Grav) |
| S3 | MegaWorm: укус/луч **или** ребранд «ландшафт» |
| S4 | Баланс-пасс: soft weapons для ванили / hard для 6DoF, или явный «overdrive vs machines» |
| S5 | Синхронизация docs с `WorldFeatures` |
| S6 | Подключить живой `ProjectileFramework` к deploy/flak (убрать dead code) |
| S7 | Repair laser: реальный ремонт блоков, не no-op |

### Later (после 1.0)

- Unpark sparse industrial + 1–2 landmarks  
- Nether/End bands с дешёвой генерацией  
- Havok-lite gravy (roadmap)  
- Blockbench FP стволы  
- Natural spawns / datapacks  
- Smoke/fire S2C  

---

## 6. Предлагаемые конфигурации релиза

### A. «Сильный sandbox» (быстрее)

- Оставить WG parked  
- M1 + M2 (патруль от хаба) + M3–M6  
- Маркетинг: *Descent sandbox в Minecraft, мир-контент через `/d6` и креатив*  
- Ярлык: **0.9 / 1.0-Sandbox**

### B. «Survival-lite» (сильнее в MC-среде)

- Всё из A  
- Минимальный `MACRO_WORLDGEN`: редкий industrial + garrison CyberMobs + 1 landmark у спавна  
- End boss как конец пути (ванильный End)  
- Ярлык: **1.0 Survival-lite**

### C. «Full README» (дорого)

- Unpark WG2 + bands + мега-сидинг + MP smoke  
- Это уже следующий крупный цикл, не «дожать релиз»

**Рекомендация:** идти в **B**, но shipить **A** через неделю работы, если нужен быстрый публичный билд — при условии переписанного README.

---

## 7. Метрики «достаточно сильный для MC»

Релиз считается сильным, если за первый час без команд игрok:

1. Скрафтил / взял Pyro → 6DoF ощущается лучше элитр.  
2. Получил бой с **роем** и хотя бы одним **CyberMob**.  
3. Увидел гравифакел / локальный UP пешком.  
4. Потратил энергию на ракеты/лазеры (нет бесконечных heavy missiles).  
5. Понял, куда идти дальше (хаб / End / редкий комплекс) — без чтения исходников.

---

*Документ для планирования. Не заменяет changelog; срез по состоянию кода на момент аудита.*
