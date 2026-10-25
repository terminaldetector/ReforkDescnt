# M — Строительные инструменты / Adaptive 6DoF Construction

**Проблема:** инструменты строительства и установки объектов должны корректно работать с произвольной локальной гравитацией/плоскостью и быть пригодны для использования в реальной игре. **P1.** Не смешивать с буровой геометрией (L).

## Кодовые зацепки
- `world/build/BuildToolItem.java`: смена `LOOK/SURFACE/PLANE`, цикл осей `PITCH/YAW/ROLL`, placement по выбранному material и local normal.
- `world/build/AdaptivePlacement.java`: `placeAlongLook` через ship-forward при 6DoF, `placeOnSurface` и consume offhand при `leftHandOnly`.
- `world/build/ConstructionMode.java`: активация адаптивного режима после посадки/высадки Pyro.
- `world/engineer/EngineerTools.java`: `ConstructionLaserItem` использует `AdaptivePlacement` и блок в левой руке.

## ТЗ
1. Проверить режимы `LOOK`, `SURFACE`, `PLANE` на полу/стене/потолке при 0/90/180° roll, наклонной локальной гравитации и 6DoF ON/OFF. `LocalOrientation` должен быть локальным состоянием пользователя, не глобальным side effect для другого игрока.
2. Визуальный preview перед установкой: точка, нормаль, оси локального вращения, доступный material, действующий режим, footprint; зелёное/красное подтверждение допустимости.
3. Атомарная серверная проверка: корректное target raycast, replaceable/neighbor updates, protected/bedrock blocks, world height, world border, unloaded chunks, collision с игроками/entity, survival item consumption и creative exceptions.
4. **Потенциальный дефект:** в просмотренном `BuildToolItem.useOnBlock` `world.setBlockState(...)` не сопровождается очевидным `decrement`; проверить, не создаёт ли инструмент бесконечные бесплатные блоки в survival. В `AdaptivePlacement` отдельное списание реализовано не во всех путях. Не утверждать эксплойт без runtime-теста.
5. Положение нового блока не должно расходиться между preview-клиентом и серверным resolver при inverted camera/ship-forward.
6. Undo/redo, batch build, ограничение скорости можно добавлять **только после** исправления существующих действий, с hard cap блоков за tick и корректной сериализацией для undo; не запускать массовую стройку как обход правил survival.
7. Строительные лазеры, турели, ворота, гравиблоки, портальные устройства и поверхностные структуры проверять через один матричный тест. Существующие ID сохранять.

## Приёмка
- Установка одного блока отрабатывает точно, не дублируется и не расходует дважды; survival учитывает расход, creative не расходует, прочность/ресурс при наличии списывается корректно.
- При 6DoF любой крен позволяет строить от локальной поверхности, а не по жёсткой мировой вертикали; поведение детерминировано после unload/rejoin.
- Исключены регрессии `ConstructionMode` после гибели/выхода из корабля (см. UG-001).
