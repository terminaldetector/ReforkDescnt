# Ungravity Stage B

Рабочая ветка `codex/ungravity-stage-b` создана от `main` на `586fd2ab441a3055d7764a58b2bf6ddd2e8a6444`. Клиент и сервер используют один Fabric JAR.

## Объединение веток

| Ветка | Что входит в общую сборку |
| --- | --- |
| `feature/ufo-body-after-1.1.34`, `52d729f` | UFO, абордаж, физический корпус и его боевые отверстия уже входят в историю `main`. |
| `feature/body-joint-runtime-after-1.1.33`, `2a410af` | Игровой код перенесён в `ec1a539`, `233914e` и `fcedf66`. На этих точках совпадают изменения урона по корпусу, переноса пассажира на отделённую часть и сохранения физических соединений. |
| `codex/d6-spatial-physics-integration`, `963375b` | Пространственный индекс, контакты D6 и рой уже входят в историю `main`. |
| `claude/descent-secondary-weapons-veo7m2`, `c100887` | Вторичное оружие уже входит в историю `main`. |
| `codex/ungravity-cloud-recovery-20261024`, `95f38f0` | Четыре отдельных коммита содержат исторические пакеты и свидетельства проверки 1.1.34. |

Сохранённый профиль мира задаёт генератор и параметры загрузки XYZ-кубов. Генерация, Hybrid и дальние силуэты используют одни координаты, а форма построенных блоков определяется сохранённым материалом. Боевые попадания проходят через `WeaponCore` и `ProjectileEntity`. Контакты с корпусом, обломки и ragdoll используют существующие BlockBody, индекс тел и решатель D6.

## Запуск

1. Установите Minecraft Java 1.21.1, Java 21, Fabric Loader 0.19.5 и Fabric API 0.116.15+1.21.1. В `mods` нужна одна версия JAR с идентификатором `drmd`.
2. Для режима Ungravity добавьте Better Cubic Chunks Fabric 0.1.1+mc1.21.1. Sodium 0.8.13+mc1.21.1 подходит для проверочного профиля с OpenGL 3.3. [Better Cubic Chunks](https://www.curseforge.com/minecraft/mc-mods/better-cubic-chunks/files/9091780)
3. В меню нового мира откройте настройки Ungravity. Выберите **Ungravity**, **Infinity Psychedelic**, стандартный кубический масштаб и старт на площадке глайдера. Для воспроизводимой сцены введите seed `271026`. Масштаб `HUGE` появляется при соответствующем режиме координат Better Cubic Chunks.
4. В полёте откройте карту сочетанием **Tab+H**. **Page Up** и **Page Down** смещают область сканирования. После запуска **Guided** клавиша **V** передаёт мышь ракете и освобождает управление при повторном нажатии. Текущий стартовый глайдер использует игровой корпус Pyro GX.

Для сборки исходников выполните `./gradlew remapJar` на Java 21. Установочный JAR появится в `build/libs`.

## Кандидаты для Connectivity

Все три релиза доступны для Fabric 1.21.1. Совместимость их одновременного запуска с кубическим миром предстоит проверить.

- **Lithostitched 1.8.1**, MIT. Пулы шаблонов подходят как источник модулей для каталога структур. Размещение в кубах остаётся задачей генератора Ungravity. [Релиз](https://modrinth.com/mod/lithostitched/version/GZFp8lnS) · [Пулы шаблонов](https://github.com/Apollounknowndev/lithostitched/wiki/Adding-Entries-to-Template-Pools)
- **Tech Reborn 5.11.19**, MIT. Транзакционный `EnergyStorage.SIDED` подходит для подачи энергии в загруженный порт. Расход на ремонт и оборону контролирует Ungravity. [Релиз](https://modrinth.com/mod/techreborn/version/CNwZZ4FD) · [Energy API](https://github.com/TechReborn/Energy#documentation)
- **CC: Tweaked 1.122.0**, CCPL. Через Fabric `PeripheralLookup` можно читать состояние реактора и передавать команды тому же серверному обработчику, который обслуживает действия игрока. [Релиз](https://modrinth.com/mod/cc-tweaked/version/KN2WFTly) · [Peripheral API](https://tweaked.cc/mc-1.21.x/javadoc/dan200/computercraft/api/peripheral/package-summary.html#fabric)
