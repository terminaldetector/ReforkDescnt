# ReforkDescnt / Ungravity — Bugs and shit: пакет v3 для Codex

Аудит записи **19:29**, 1280×720; Google Drive ID `1AxwBhyTncn08eH5xlaZZdjWQoBdCbgwT`. Дополнения от 25.10.2026.

- [Главный аудит: 31 пункт UG-001…UG-031](UNGRAVITY_VIDEO_AUDIT.md)
- [Зеркала — F](MIRRORS_TZ.md)
- [Порталы — G](PORTALS_TZ.md)
- [Оружие в первом лице — H](WEAPON_VISIBILITY_TZ.md)
- [Модели инвентаря — I](INVENTORY_ITEM_MODELS_TZ.md)
- [Зачистка legacy — J](LEGACY_DEAD_CODE_AUDIT_TZ.md)
- [Кассетные боеприпасы — K](CLUSTER_MUNITIONS_TZ.md)
- [Буры / плавление / NoCubes — L](DRILL_MELT_NOCUBES_TZ.md)
- [Строительные инструменты — M](CONSTRUCTION_TOOLS_TZ.md)
- [Гибридный рендер ON по умолчанию — N](HYBRID_RENDER_DEFAULT_TZ.md)
- [Expanded Physics по умолчанию — O](EXPANDED_PHYSICS_DEFAULT_TZ.md)
- `frames/`: стоп-кадры, `evidence_early.jpg`, `evidence_late.jpg` — обзорные листы.

## Первое действие Codex
1. Привязать фактическую работающую JAR из видео к точной ветке/commit: UI в записи **не полностью соответствует** найденному `DescentSettingsScreen`, поэтому не считать source findings root cause без проверки.
2. Сначала P0: восстановление управления/entity, вертикальные границы мира. Затем исправления LOD, видимости оружия, кассет/бурения и настроек; предметные модели и legacy cleanup без риска удаления живой логики.
3. Для каждой подсистемы: воспроизведение → патч → компиляция и tests → игровой rendercheck/приёмка → отдельный commit и краткий результат с видео/screenshots.
4. Сохранить разделение Mirrors F и Portals G, не смешивать их с Hybrid N.
5. Продуктовые defaults: **HYBRID ON**, **EXPANDED PHYSICS**. Не сбрасывать явное пользовательское `OFF` или `STANDARD` после перезапуска.

Только документация и анализ; в этом пакете нет заявлений о внесённых исправлениях в исходники.
