# UNGRAVITY / ReforkDescnt / DRMD — MASTER ENGINEERING SPECIFICATION v8.0
**Дата редакции:** 04.11.2026. **Исполнитель:** Codex и независимые агенты по изолированным worktree.  
**Target runtime:** Minecraft Java 1.21.1, Fabric, Java 21. **Основная цель:** локальная/offline одиночная игра; мультиплеер и сетевые функции не являются предметом данной очереди.  
**Исходная база:** DRMD/ReforkDescnt (6DoF), наблюдаемая старая сборка `drmd 1.1.14-audit.4` в журналах от 26.10; **не принимать её за актуальную рабочую ветку 04.11 без проверки**.  
**Репозиторий:** `terminaldetector/ReforkDescnt`; SHA источников, исполняемой JAR и текущего локального дерева на 04.11 ещё НЕ сверены.  
**Преемственность:** v5 (26.10) → v7 (29.10, 18 этапов/99 задач) → v8 (04.11, сводный договор, архитектура и evidence gating).

## 0. ГЛАВНАЯ ДИРЕКТИВА
Продолжить существующий проект, не создавать новую игру под тем же именем и не переписывать работающее ради эстетики архитектуры. Сохранить работоспособные механики и подтверждённые алгоритмы, провести source-level ревизию и реальные Minecraft-тесты. Ключевое противоречие предшествующих веток: красивая демонстрация 6DoF/орудий/роя уже возможна, но world substrate, камера, модели, физика и серверный lifecycle дают системные ошибки. Решение — заменять только доказанно неисправные инфраструктурные механизмы через адаптеры и проверяемые gates.

**Продукт:** не сборка модов и не «ещё один Create», а total conversion / immersive shooter в блочном изменяемом мире: **Minecraft × System Shock × Strife × Teardown/Red Faction × Descent/Terminal Velocity**. Художественная тема — научная фантастика, техногенные фракции, многоярусная планета, гравитационные аномалии, бой в 6DoF, абордаж, саботаж, крупномасштабные рои.

**Необходимая честность статусов:** `[USER-APPROVED]` (принятая цель), `[OBSERVED]` (видно в видео/скрине), `[LOGGED]` (строка лога), `[GIT-CLAIM]` (заявлено коммитом/PR), `[VERIFIED-RUNTIME]` (проверено на сопоставленной JAR), `[EXPERIMENTAL]`, `[BLOCKED]`, `[DEFERRED]`. Наличие файла или тестового маркера не доказывает выполнение задачи.

## 1. PRODUCT VISION / ГРАНИЦЫ
- Minecraft выбран из-за уже существующих процедурных карт, мобов, блочного редактирования и инструментов моддинга. Карты не нужно создавать с нуля, сценарии могут быть компактны и эпизодичны как у Doom/Strife.
- Полёт и gunplay обязаны быть интересны независимо от количества локаций. Нельзя заменять игровые решения демонстрацией километров пустого космоса, нагромождением UI или десятками служебных блоков.
- База survival сохраняет ценность постройки, но игроку противостоят ракеты, лазеры и охотничьи группы. Нужен отдельный слой угроз, обороны, ресурсов и прогрессии. Нельзя просто дать мобам ракетницы и оставить ванильный баланс.
- UFO/BlockBody — летучая блочная конструкция с установленными двигателями, ракетными и лазерными узлами, шарнирами, реактором. Основной loop: **подойти → проникнуть/сесть на борт → захватить управление/провести диверсию → уничтожить реактор/покинуть корабль**. Допустим mothership. Сборка не должна требовать сотен специализированных блоков.
- Независимые ORB, ROCKET, JET — не типы UFO и не части корабля: это самостоятельные AI/дроны, для которых нужны разные роли, траектории, навигация и ограничения поведения.
- Гравитация — игровая системная механика для игрока, всех мобов, ragdoll, BlockBody и оружия; не декоративное вращение модели.

**Исключения:** не возвращать исключённый Pyro 3D model; не делать Create clone; не превращать собственные кубические снапшоты в параллельный мир; не подменять исправление выключением sky/LOD/ground render; не сливать экспериментальные порты в production без CI + реального клиента; не переносить в Ungravity физический код/проект Digital Iris PS1 (PS1 Hybrid здесь исключительно клиентская графика).

## 2. СХЕМА ОТВЕТСТВЕННОСТИ ДВИЖКОВ
```text
Minecraft 1.21.1 / Fabric / Java 21
  └─ CubicWorldBackend [authoritative blocks, loading, height, persistence, fluids, light]
      ├─ SpatialWorldAccess  [cube/section queries, events, worldgen, lifecycle]
      │   ├─ Generation modules [surface / mantle / core / orbit / ring / Citadel]
      │   ├─ HybridNearMesher [marked smooth terrain, trees, poly cavities, sharp buildings]
      │   ├─ 3D LOD + atmospheric horizon [distant landscape, satellites, ring]
      │   └─ CollisionGeometry [mesh-aware, server-authoritative where necessary]
      ├─ UniversalGravityFieldRegistry [dimension-aware, indexed spatially]
      ├─ Rigid6DoF / MotionState [player, entities, ships, debris; quaternion]
      │   ├─ Gravity-aware navigation + AI / swarm coordinator
      │   ├─ Articulated ragdoll solver
      │   ├─ BlockBody components [UFO, turrets, reactor, joints]
      │   └─ Weapon and destructible terrain events
      ├─ Client RenderPipeline
      │   ├─ Gameplay native hybrid render [DEFAULT ON]
      │   ├─ PS1 Hybrid presentation profile [approved primary aesthetic]
      │   ├─ Mirror pass [independent]
      │   └─ Portal pass [independent]
      └─ Save/restore + tests + profiling + evidence manifests
```
**Cubic Chunks ≠ NoCubes ≠ PS1 Hybrid ≠ BlockBody ≠ Ragdoll.** Первый управляет миром и кубами, второй строит видимую геометрию, третий меняет клиентский raster/postprocess, четвёртый отвечает за движущиеся блочные конструкции, пятый — за сочленённые тела. Объединение API должно быть явным, жизненный цикл — согласованным.

## 3. CUBIC WORLD — МИГРАЦИЯ ВМЕСТО САМОДЕЛКИ [P0]
3.1. Провести независимый upstream GATE для **Better Cubic Chunks 0.1.1 Beta (Fabric 1.21.1)** и альтернатив. На 04.11.2026 его страница заявляет 16×16×16 кубы, 32768 высотных блоков по умолчанию и 3D LOD через Better Distant Horizons, но **стабильность в Ungravity не подтверждена**. Лицензия All Rights Reserved: как внешняя зависимость, без копирования source. Для альтернатив проверить лицензию, Fabric 1.21.1, Java 21, фактический renderer и world migration path.
3.2. Зафиксировать точный loaded mod list, JS/Java runtime, GPU, версию Sodium, `git SHA`, `SHA256(JAR)`, active datapack/world preset, server dimension height и отдельные vertical distances. Старые логи НЕ доказывают кубическую генерацию: `cubic body prototype` — это не native Cubic World.
3.3. Воспроизвести в **новом мире** генерацию/stream/save/load/fluid/light/Block Entity на координатах ниже нуля и выше +1024/+1640/+2150; при полёте 6DoF не должно возникать жёстких обрезанных плоскостей, phantom columns, неверной границы пустоты. Координаты вне выбранного preset проверяются на корректное отклонение.
3.4. `WorldAccessAdapter` должен полностью развязать gameplay от старых hard-coded altitude bands, vanilla-only chunk assumptions и 2D-only horizontal neighborhoods. Снять/реализовать необходимую интеграцию Cubic lifecycle, streaming, dimension traversal, entity ticking, structure placement.
3.5. Миграция мира осуществляется **не на исходных save-файлах**. По результатам parity удалить самодельные вертикальные shadow maps, fake boundaries и surrogate render. Не удалять художественные планетарные слои и биомы. Порталы остаются отдельной механикой перемещений, а не заменой доступа к высотам.
3.6. Поскольку кубическая вертикаль не даёт бесконечные координаты автоматически, задокументировать реальные пределы X/Y/Z и сделать safe bounds.

## 4. HYBRID/NOCUBES/PS1 RENDER [P1]
- `hybrid_render=true` в **новом профиле по умолчанию**, override сохраняется в настройках; `expanded_physics=true` как default со своим независимым переключателем. Существующий пользовательский выбор не сбрасывать.
- Hybrid terrain: естественный рельеф, стволы деревьев и полости могут иметь полигонально сглаженный силуэт; рукотворные/построенные блоки сохраняют чёткую геометрию. NoCubes — референс, не готовый универсальный physics backend. Нельзя допускать mismatch render/collision на проходах и склонах.
- Слабый бур разрушает дискретные кубические/ступенчатые объёмы; сильный нагревает/плавит материал и образует гладкую, близкую к идеально круглой трубу. На каждом изменении синхронизируются material state, mesh invalidation, коллизии, освещение и fluid updates.
- PS1 Hybrid **главный художественный профиль**: 640×360 internal target (настраиваемый), nearest-neighbor upscale, vertex snapping, affine-like UV distortion, low color precision, ordered dithering, fog/far fade, low-poly предметы. Must be optional selectable runtime mode: `PS1_HYBRID`, `HYBRID_NATIVE`, `DEBUG`, без перезапуска и без влияния на физический simulation state. Сетку блоков не искажать в world data, GUI/текст оставлять читаемыми; исключать сильную дисторсию, ломающее прицел.
- PS1 профиль не равен «всё сделать кубическим»: крупные читаемые low-poly массы, грубая художественная геометрия, управляемая степень зернистости, никакого мусора из микрополигонов. Рука/оружие проверяются при yaw/pitch/roll = 0/90/180/270/360.
- High-altitude view: мягкий переход земли в облака, астероиды, станции, кольцо и орбитальные силуэты; 3D LOD без отключения поверхности. Mirror/portal passes независимы. OpenGL 3.2 Intel HD 4400 в октябрьском стенде является реальным нижним ограничением, нужен graceful fallback / compatibility matrix.

## 5. ENTITY GRAVITY / UNIVERSAL 6DOF / RAGDOLL [P0/P1]
- Непрерывный `MotionState(position, velocity, quaternion, angularVelocity, localGravity, localBasis)` для игрока, всех мобов и поддерживаемых BlockBody; преобразования авторитетны на сервере. Конвертация vanilla yaw/pitch — на интерфейсе, не основной источник ориентации.
- Гравитационные факелы создают независимые пространственные поля, привязанные к dimension. Индекс 3D 16³ или более крупные physics cells **не тождественен обязательному тиканию кубов**. Любая сущность под полем получает новый local UP; AI, AABB broadphase/narrowphase, camera eye и raycast должны согласовываться.
- Классы мобов: ground, climber, flying, aquatic, mechanical, boss/multipart, anchored. Для любого моба — универсальный fallback с предсказуемой ориентацией, а не hard-crash/потеря AI. Кластеризация в spatial group обеспечивает bounded-neighbor / interest tick, а не запрет 6DoF рядовым животным.
- Ragdoll: joints, mass, impulse, contacts, penetration prevention, sleep/wake, despawn and block collision. Смерть дрона не должна производить два тела. Удар ракетой в деревню/город: debris/ragdoll budget, deterministic cleanup, сохраняемое состояние мира.
- Критический P0 из видео: аварийное уничтожение промежуточной entity лавой для выхода из 6DoF. Требуется штатный exit/dismount/reload/death recovery, no orphan or re-mount loop.

## 6. BLOCKBODY / UFO / SHIPS [P1; ЭПИЗОД УЖЕ В РАБОТЕ]
- **Не конфликтовать с текущей работой Sol/Codex над UFO и физическим ядром.** Сначала branch reconciliation: какой вариант уже принят, какой runtime test прошёл, где последняя JAR. Статус «почти завершён» относится к предыдущему пользовательскому описанию от 29.10; новый commit/JAR на 04.11 не подтверждён.
- BlockBody — мобильный rigid block composition, с отдельными турелями, двигателями, шарнирами, реактором. Свободное 6DoF, инерция и разрушение суставов; первоочередной эталон — **UFO демо**, а не универсальный редактор в стиле Create.
- Нужны устойчивая система control ownership, simple placement, boarding/hijack, damage zones, reactor detonation, emergency eject, physical/debris cleanup. Мотершип — более поздний сценарный масштаб.
- Valkyrien Skies (неофициальный порт 1.21.1) / Eureka — **сравнительный experimental backend**, не обязательный runtime. Официальный VS требует OpenGL 3.3+ по совместимости, что выше GL3.2 октябрьского Intel-стенда; нужен реальный совместимый hardware test. Не импортировать систему тысяч технических блоков.

## 7. WEAPONS, DRILLS, BUILDING [P1]
- Оружие видимо и от первого лица (6DoF и обычная камера), и в инвентаре. Проверить `WeaponViewRenderer` fallback, GUI item JSON, transform, missing textures и дублирующиеся иконки; не маскировать недочёты замещающим placeholder в release.
- Кассетное оружие: в первых кадрах cluster shot — carrier → раскрытие → пространственное веерное отделение submunitions, включая downward/forward perspective. Не просто рандомный россыпь. Физика, timing, arm/disarm, impact, cleanup, friend-or-foe, protection, hit trace, save/load. Историческая реализация выпускала 7/14 суббоеприпасов, но распределение и работоспособность проверять.
- Гравитационные метатели: роевой захват/метание нескольких блоков, тяжёлый rail throw, shifter локальной гравитации. После подтверждения ragdoll и контактов. Не подменять разные роли одной общей пушкой.
- Буры и строительные инструменты: server-authoritative изменения блоков, smooth melt, bounded jobs, permission/survival resource cost, chunk/cube invalidation; **не** внедрять автоматические производственные цепи Create.
- Очистка legacy: красные отметки на инвентарном скриншоте от 26.10 означают шесть кандидатов. До удаления установить registry IDs, ссылки команд/рецептов, сохранить backward save mapping. Рисованные круги — не уникальные ID.

## 8. AUTONOMOUS AI / ROCKET THREAT / FACTIONS [P1→P2]
- Классы врагов: ORB (компактная манёвренная сфера), ROCKET (ракетный дрон), JET (быстрый реактивный), наземные/летучие ракетоносцы, Shapes на основе эндерменов (киберпехота без стандартного teleport), отдельный телепортирующий летающий противник, тяжёлый оператор лазера с наплечными ракетами.
- AI иерархия: faction commander/raid director → squad tasks → spatial partition/boids → local steering / physics / aim / avoidance. Не делать полного O(N²) перебора всех юнитов. Дроны атакуют, уклоняются, сохраняют separation и назначают роли; поражают реальные цели, а не проводят декоративную анимацию.
- Survival: наблюдение/обнаружение, предупреждающие сигналы, поисковая охота, преследование, отступление/дозаправка, raid pressure, укрытия, ремонт и противоракетная оборона. Пара ракетчиков может разбить необорудованную базу, но нельзя давать неотвратимый instant softlock новичку.
- Бенчмарки 128 / 256 / 512 / 1024 затем 5000/10000 как исследовательские цели с формальной reduced/dormant симуляцией. Исторический ориентир: 128 active / >=96 реально движущихся. FPS/TPS/MSPT, update budget, draw calls, network-like local sync and particle budget; без цифр от профайлера «супримка» — лишь удачная демонстрация.
- Пять уровней технологического развития фракций пока **не детализированы**: агент не вправе объявлять их утверждённым каноном. Нужно создать схемы-кандидаты в отдельных дизайн-документах, не вносить в баланс без утверждения.

## 9. ПЛАНЕТАРНЫЙ МИР / LEVEL DESIGN [P2; AFTER CORE]
- **Нижний объём:** ядро планеты, геотермальные/лавовые области, шахты, тоннели, подземные комплексы и лаборатории с эстетическими мотивами Xenogears; мир физически проходим, а не набор teleport-only декораций.
- **Поверхность:** технологические биомы, города, руины, фракционные форпосты, следы боёв и выживание; рукотворные конструкции местами кубические, природный рельеф гладкий. Карта большого города — отдельный damage/ragdoll полигон. У сторонних карт проверять лицензию, не клонировать чужой защищённый level wholesale.
- **Верхний объём:** постепенное атмосферное переходное пространство, floating asteroids, abandoned stations, satellites, damaged mega-ring around the planet and central Citadel/faction hub. Геометрические ориентиры должны существовать в volumetric navigation and render tiers. Среднее расстояние — simplified mesh, сверхдальнее — impostors; динамическая физика только ближних активных объектов.
- Вдохновение Strife — технологические государства, социальный порядок, гарнизоны, фракции и конфликт, не буквальное копирование контента. 5 уровней развития — будущий редакторский модуль; roguelite zone rules — позже.
- Порталы нужны для специальных переходов/пространственных эффектов, зеркала — независимый render pass. Никакой необходимости «склеивать вертикальную колонну порталами» больше нет.

## 10. ПРОБЛЕМЫ, СВЯЗАННЫЕ С ВИДЕО И СКРИНАМИ [P0/P1]
**Bugs and shit**, MKV 19:29, Google Drive `1AxwBhyTncn08eH5xlaZZdjWQoBdCbgwT`: отсутствие штатного выхода из 6DoF (лава как workaround), повреждённая высотная геометрия, `Y≈2150` и странный `OBLIVION` resolver, падение в void 10:26, спам Local gravity, проблемный обзор, оружие, кассеты, бурение и стройинструменты.

**Дополнения пользователя к шести кадрам (26.10):**
1. Зеркальная плита не отображается.
2. Некорректный render рельефа/кубических секций.
3. Геометрические столбы/нарушения мира — уже обрабатывались отдельным агентом; не дублировать работу.
4. Гравитационные факелы создают ошибочную видимость/позицию камеры.
5. Убрать мусорные объекты в небе, выяснив их origin; не удалить плановые будущие астероиды/орбитальную архитектуру.
6. Модель руки пересекает экран при 6DoF вращении; отдельная camera/viewmodel матрица.

## 11. ЖУРНАЛЫ: ПОДТВЕРЖДЁННЫЕ МЕТРИКИ [LOGGED]
`latest.log` (15:06–15:52 26.10): 55 `Can't keep up!`, максимум 152216 ms/3044 ticks; 33 DUMMY Block Entity, все Y=-51..-2.  
`latest(1).log` (16:09–16:55): 41 `Can't keep up!`, максимум **193148 ms / 3862 ticks**; 100 DUMMY Block Entity, Y=-48..-1; реальная ошибка `Failed to create block entity minecraft:mob_spawner` на `air` @ (334,-21,496). Mirror pass: видимые mirrors временами пропускаются как `off-screen or no render target` (16:53–16:55).  
Архивы `2026-10-26-1.log.gz` и `(1).gz` побайтно дублируются; `2026-10-26-2.log.gz` соответствует содержимому `latest.log`. **Не суммировать повторные файлы как отдельные сессии**. Старый `2026-10-26-1.log.gz` фиксирует GLFW NPE при остановке и `Invalid characters in username`, не доказанный кубический краш.

В обоих длительных логах: Fabric 1.21.1, Java 21, Sodium 0.8.13, Intel HD 4400 OpenGL 3.2, driver workarounds; Distant Horizons и Immersive Portals отсутствовали, `worldKind=STOCK, worldModLevel=ADVANCED`, simulation distance доходил до 24. Эти факты могут объяснять совместимость/нагрузку, но конкретный cause seaming без профайлера неизвестен.

## 12. АУДИТ ЧУЖИХ ПРАВОК И ПРЕЕМСТВЕННОСТЬ [REQUIRED]
- **Historical PR #9** `codex/d6-spatial-physics-integration` SHA `963375bf` (10.10): cubic snapshots, hybrid meshes, gravity torches, collapse, ragdoll, swarm test 128. Описание PR прямо сообщает, что **vanilla управляет chunk lifecycle/lighting/render; полной Cubic интеграции НЕТ**. «Тест написал» ≠ «всё работает в актуальной JAR».
- **Recovery branch 24.10** `codex/ungravity-cloud-recovery-20261024` HEAD `93baf2a`: оружие, range shots, Workshop preview, models, NBT; все изменения требуют регрессионного сравнения. Default branch `claude/trusting-dijkstra-xuin48` может быть устаревшей относительно этих веток.
- По требованиям от 26.10, **обязателен аудит исправлений, присланных до общения в тот день**, включая локальные uncommitted, tar/zip/JAR, history of agent runs, если доступны. Не приписывать пропущенные файлы конкретному агенту без evidence.
- По решениям 29–30.10: продолжать **эпизод UFO и ранее запущенную цепочку Sol/Codex**, параллельно готовить ассеты; новый master документ — не разрешение второму агенту одновременно править его незавершённые классы. Состояние Sol/UFO на 04.11 — **UNVERIFIED**.
- При переносе между агентами обязательны `STATE.md`, `TASKS.yaml`, `DECISIONS.md`, `REGRESSIONS.md`, `EVIDENCE/index.json`, exact SHA/JAR, причины отказов. По user constraint **Loom разрешён только Codex**; не приписывать другим агентам доступ.

## 13. ИСПОЛНИТЕЛЬНЫЕ ГРАНИЦЫ
| Workstream | Ownership | Ветка | Запрет |
| --- | --- | --- | --- |
| Core recovery / UFO | агент, заканчивающий текущий эпизод, координация Codex | existing branch, reconcile | не перехватывать state или overwrite |
| Cubic backend migration | отдельный агент | `experiment/cubic-world` | не трогать оружие и GUI |
| Hybrid/NoCubes + PS1 render | независимый клиентский worktree | `experiment/render-hybrid-ps1` | не менять world storage или физические coordinate systems |
| Ragdoll / universal 6DoF | Codex (Loom при необходимости) | `feature/entity-6dof-physics` | не мутировать backend Cubic без адаптера |
| Combat / swarm / survival | AI workstream | `feature/combat-director` | не менять wave generation и глобальные настройки мира |
| Legacy asset cleanup | отдельная ветка | `cleanup/legacy-items` | не удалять используемые registry IDs без mapping |
| Planet/Strife worldbuilding | проектирование/прототипы | `design/planet-world` | не попадать в основную сборку до gates |

## 14. ПОЭТАПНЫЙ ПЛАН
Сохранить модель **18 этапов и 99 атомарных задач** из v7, обновив содержание новыми архитектурными решениями. Детальный маршрут в `05_EXECUTION/TASKS.yaml`, зависимости и приоритеты в `05_EXECUTION/AGENTS_AND_GATES.md`. Порядок: lineage audit → baseline → независимый Cubic GATE → Near mesher/PS1 GATE → 6DoF/ragdoll/UFO parity → weapon/AI bench → survival/faction/world → integrative regressions.

**Definition of Done:** пользователь получает конкретный JAR с source SHA + SHA256, доказательный rendercheck с датой/версиями, валидные save/load на новых мирах, журналы без предъявленных P0, проверку 6DoF/torch/mirror/portal/weapon/AI/physics, профайлер MSPT/FPS и реестр нерешённых проблем. Никаких CLAIMED PASS по старому файлу `*-ok.txt` или скриншоту IDE. Не выходить в production, если benchmark или upstream beta не проходят GATE.

## 15. ВХОДНЫЕ МАТЕРИАЛЫ
- `07_REFERENCES/UPSTREAM.md`: версии/лицензии/проверенные страницы upstream на 04.11.
- `04_BUGS/ISSUE_REGISTER.md`: все критичные пользовательские симптомы с source/evidence.
- `08_EVIDENCE/`: исходные логи и скриншоты, JSON-аналитика.
- `09_LEGACY_V5/`: полный аудит v5 и предшествующие ТЗ, без изменения содержимого.
- Полное исходное видео по ссылке: https://drive.google.com/file/d/1AxwBhyTncn08eH5xlaZZdjWQoBdCbgwT/view?usp=drivesdk . MKV > 360 MiB не дублируется в архиве.
