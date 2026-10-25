# I — Модели инвентаря / Item-model integrity

**Проблема пользователя:** некорректные и одинаковые модели оружия и инструментов в инвентаре. **P1/P2.** Не смешивать с невидимыми пушками в мире (H).

## Выявлено в репозитории
- `assets/drmd/models/item/weapon_d6_laser.json` и `weapon_d6_mega_laser.json` используют фактически идентичные наборы `elements`, как и проверенный `weapon_d6_gravy_railgun.json`, изменяется прежде всего texture path. Причина визуальной одинаковости требует проверки ассетов/клиентского resource reload.
- Часть моделей (`tunnel_laser.json`, `bomb_cluster.json`) — generated/handheld sprites, другие — объёмные `elements` с `display.gui`; без единого стандарта размеров и углов.
- В `assets/drmd/models/item/construction_laser.json` одновременно присутствуют `parent: minecraft:item/handheld` и собственные `elements`. Это допустимо как JSON, но неоднородный pipeline требует тестов; один лишь parent не доказывает ошибки.

## ТЗ
1. Сопоставить item registry IDs, **путь JSON**, путь texture PNG, парсинг resource loader, локализацию tooltip и display transforms (`gui`, `ground`, `fixed`, `firstperson_*`, `thirdperson_*`). Для каждой записи обозначить `valid`, `duplicate by design`, `placeholder`, `missing`, `broken`.
2. Привести геометрию и масштабы к читаемым силуэтам, не заменять все 3D-пушки одним и тем же длинным параллелепипедом. Для специализированных вариантов выбирать осознанно GUI icon и in-hand model, а не один шаблон без проверки.
3. Проверить модель в creative inventory, обычном inventory, hotbar, item frame/ground, обеих руках, на тёмном/светлом фоне, GUI scale 2/3/4 и 720p/1080p.
4. Проблемы `missing texture` диагностировать по `latest.log` и resource reload. Не изменять legacy ID, иначе старые сохранения потеряют предмет.
5. Добавить в CI проверку наличия `models/item/*.json` для всех item IDs, всех texture references, валидных `display` полей и неявных copy-paste duplicates как предупреждение.

## Приёмка
- Все зарегистрированные предметы имеют адекватную GUI-модель, иконки семейств различимы, 3D-геометрия не вылезает за слот или экран.
- Сняты до/после матрицы скриншотов по всем категориям. Не объявлять исправлением только существование JSON в resources.
