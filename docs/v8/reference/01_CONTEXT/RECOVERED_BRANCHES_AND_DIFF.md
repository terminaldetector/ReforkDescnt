# КОНТЕКСТ СОСЕДНИХ ВЕТОК / ОБНОВЛЕНИЯ v5→v8
**Назначение:** предотвратить потерю принятых решений и смешение готового/запланированного. Это не «полный transcript» всех веток, а восстановленные решения из проектной истории и ранее сформированного v5.

| Период | Источник/ветка | Сохранённое решение | Статус |
| --- | --- | --- | --- |
| Сентябрь–октябрь | ReforkDescnt/DRMD | 6DoF инерционный бой, четыре разных типа тяжёлых взаимодействий, flight as core gameplay | цель + частичная историческая реализация |
| 02–10.10 | PR #9 | Cubic snapshots, ragdoll, field torches, gravity guns, 128 swarm, hybrid organic mesher | git claims; доказательство последнего JAR отсутствует |
| 22–26.10 | Bugs and shit + local play | зависшая entity/лава, LOD void, inventory, mirror, torch, sky junk, viewmodel | observed / logs / reported |
| 25–26.10 | Новый дизайн инфраструктуры | полная Cubic migration, hybrid_render=ON, expanded_physics=ON | approved; source runtime unverified |
| 27.10 | UFO/BlockBody | автономный модульный летающий корпус, реактор и абордаж, минимум UI | approved gameplay; integration status uncertain |
| 27.10 | Total conversion | System Shock + Strife + Minecraft + Teardown / Red Faction и 6DoF-боёвка | approved |
| 27.10 | ORB/ROCKET/JET | независимые дрон-мобы, не смешивать с UFO | approved |
| 27.10 | Shapes | переосмысление эндерменов как вооружённой пехоты; teleport перенести другому классу | planned after core |
| 27.10 | Survival | фракционные государства/5 ступеней, rogue-lite зоны, оружие/ресурсы, оборона от ракетчиков | planned after core; уровни не определены |
| 28.10 | NoCubes morphology | не «обмылки»: гладкие деревья, полигональные пустоты, круглые тоннели; крупные поверхности | approved concept; implementation unverified |
| 29.10 | PS1 Hybrid | основной визуальный профиль: 640×360, snapping/dither/affine UV/fog и runtime toggle | approved; not verified in game |
| 29.10 | v7 Agent directive | 18 этапов/99 задач, handoff `STATE/TASKS/EVIDENCE`, автономный Codex | prior agent plan; not proof of execution |
| 29.10 | Ветвь Asset concept | planetary core → laboratories/tunnels → surface → sky/asteroids/broken ring/Citadel | approved universe vision; worldgen deferred |
| 29–30.10 | UFO phase | Sol заканчивает предыдущую задачу, ассеты накапливаются отдельно | user-reported process, current state unverified |
| 30.10 | Agent continuity | между агентами передавать не только описание, но checkpoint состояния и отрицательные тесты | approved workflow |
| 04.11 | v8 | единый master документ, разграничение production, experimental и design backlog | THIS SPEC |

## Что НЕ переносить из других проектов
- `Digital Iris / PS1` — проект нативной оригинальной PS1, bare-metal GTE/SPU; его задачи и показатели FPS не переносятся в Minecraft. Для Ungravity принят **стиль PS1 Hybrid**, а не цель запуска на приставке.
- Термин `Another` относится к другим рабочим потокам, их исходники не смешивать с Ungravity.
- Эстетика пользователя в других проектах может быть референсом low-poly, но для Ungravity утверждён научно-фантастический, компактный и читаемый визуальный язык.

## Вопросы с пометкой UNVERIFIED, которые нельзя «закрыть памятью»
1. Какая конкретно текущая версия UFO/BlockBody и branch после работ Sol на 29–30.10?
2. Какие локальные изменения были выполнены до начала переписки 26.10 и не попали в GitHub?
3. Был ли успешно интегрирован реальный Cubic backend и его save format (не обнаружено подтверждения)?
4. Работает ли PS1 Hybrid в Minecraft-клиенте или пока существует ТЗ/частичный renderer?
5. Прошёл ли последний GameTest + rendercheck с точным JAR, не только CI?
6. Каким образом применятся пять технологических стадий фракций? Они требуют отдельного проектного решения.

**Процедура проверки:** найти файлы/коммиты/логи, сопоставить build SHA/JAR и поставить `CONFIRMED_CURRENT`/`REGRESSED`/`UNVERIFIED`/`DEFERRED`.
