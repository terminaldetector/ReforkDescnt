# Как воспроизвести третий цикл Ungravity

Основные источники требований содержатся в [MASTER v8](reference/00_MASTER_SPEC.md), [карте Dynamic Boarding](TWO_SPEC_COVERAGE_2026-11-17.md), [встроенном PS1 и Hybrid](reference/02_ENGINE/RENDER_NOCUBES_PS1.md). Точные SHA игровых запусков, статус каждой попытки и хеши JAR записаны в [квитанции](EVIDENCE/round3-20261119/receipt.json).

## Сборка и обычные серверные сценарии

На Java 21 откройте отдельный checkout исходного SHA из квитанции. Для точного diff этого цикла используйте:

```bash
git diff --binary bd4c5cdb5acbda413c46ead960d6259dd50543a2 "$tested_source" -- src
git diff --stat bd4c5cdb5acbda413c46ead960d6259dd50543a2 "$tested_source"
```

После первичного получения зависимостей локальной сборки сохраните старый `build/gametest/world` отдельно и запустите новый отдельный мир. Архив проверки содержит точные `selector.txt` и `units.txt` для каждого SHA. Из соответствующей распакованной папки `run` выполните:

```bash
selectors=$(cat "$run/selector.txt")
mapfile -t units < "$run/units.txt"
./gradlew --offline --no-daemon remapJar remapCubiccheckJar
./gradlew --offline --no-daemon -I scripts/v8/focused_gametest.init.gradle \
  "-PdrmdGameTests=$selectors" runGametest
filters=()
for unit in "${units[@]}"; do filters+=(--tests "*$unit"); done
./gradlew --offline --no-daemon test "${filters[@]}"
sha256sum build/libs/drmd-6dof-1.1.35-ufo-dev.1{,-cubiccheck}.jar
```

Проверьте `reports/gametest-results.xml` под каталогом запуска, JUnit XML в `build/test-results/test` и фактический код выхода процесса. У запуска без XML бывают ошибки компиляции и остановка в тике сущности, сохранённые в `gradle.log` и crash report.

## Нативный клиент Minecraft 1.21.1

Возьмите чистую копию стенда или `gate-v5-cubic-fixed` с seed `271026`. Используйте `scripts/v5/prepare_cubic_client.py --help`. Передайте Java 21, неизменённые BCC `0.1.1`, Fabric API `0.116.15` и Sodium `0.8.13`, а также пару игровых JAR из того же исходного SHA. Клиентская проверка выполняется в отдельном каталоге с именем `cubic-client-automation`. В архиве приведены точные `inputs.json`, `launch.json` и фактические `native.exit` каждой попытки. Скопируйте соответствующие пути под свой стенд и запустите в этом каталоге команду из `launch.json`. Проверочное дополнение `-cubiccheck.jar` относится только к тестовому экземпляру.

- `-Ddrmd.gate.connectedPortal=true` проверяет один реальный проход собранного корпуса с игроком, физической сцепкой и подтверждениями клиента.
- `-Ddrmd.gate.gravityInputRoute=true` проводит шесть сценариев ввода, три гравипушки в обычном режиме и 6DoF.
- `-Ddrmd.gate.ps1Atmosphere=true` измеряет фактически выполненные пары дальних GPU-кадров без дымки и с дымкой, а также восстановление Native.
- `-Ddrmd.gate.verticalFlight=true` проверяет целевой подъём через −64, 1024, 1640 и 2150 на отдельном стенде. Текущий исход фиксирует проход −64 и отказ наблюдателя на расхождении клиентского и серверного тактов. Исправленный наблюдатель сохранён в архиве как исходник для следующей проверки. Холодный readback выполняется отдельным запуском.

JVM exit `0` подтверждает, что процесс остановился штатно. Игровой исход берётся из JSON самого сценария и сверяется с логом, кадрами и JAR. Для A/B профиля меняйте только `simulationDistance` на `8` или `24` в стартовых параметрах копии и добавляйте `-XX:StartFlightRecording=filename=simulation.jfr,settings=profile,dumponexit=true,maxsize=20m`. `jfr print --json --events minecraft.ServerTickTime simulation.jfr` выдаёт скользящую среднюю Minecraft. В логе integrated server видна фактически принятая дистанция. Этот короткий одинаковый проход измерен с клиентским render distance `4` и относится к выбранной нагрузке.

## Привязка оставшихся работ к игре

- Кубы и рельеф требуют подтверждённых отрицательных и высотных переходов на новой генерации, холодного lighting/BlockEntity и совпадения формы, навигации и жидкости. Отдельный сценарий исходного провала и маршрута из видео остаётся открыт.
- Для орбиты следующий игровой шаг проходит через фактические посадку, ангар, шахту, галерею и кольцо. За блоковым полигоном следуют перезагрузка, стабильные near/mid/far ступени с настоящими астероидами и станциями и проверка поворота камеры на скорости. [Игровой полигон](proposals/ORBITAL_PILOT_2026-11-19.md) и [карта мира](WORLD_TWO_SPEC_REMAINING_CLAUSES_2026-11-18.md).
- Для Dynamic Boarding остаются холодная передача всех модулей, перемещение между корпусами и отдельная сеть. Для драки остаются независимые ориентации C1–C7, агрегация/уборка 128 смертей и профиль часто активных 256–1024 участников.
- Отдельно остаются полная демонстрация падения деревьев и разрушения базы ракетами и бомбами, баланс двух ракетчиков против жилой базы и длительное сохранение/восстановление разрушенного города. Полная карта статусов находится в [TASKS.yaml](TASKS.yaml).
