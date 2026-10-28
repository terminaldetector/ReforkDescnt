# Текущий проверенный этап ТЗ — 1.1.26-boarding.1

[Скачать JAR](releases/1.1.26-boarding.1/drmd-6dof-1.1.26-boarding.1-fabric-1.21.1.jar) · [Установка](releases/1.1.26-boarding.1/INSTALL_TLAUNCHER.txt)
· [Проверки и ограничения](releases/1.1.26-boarding.1/README.md) · [SHA-256](releases/1.1.26-boarding.1/SHA256SUMS).

Корабельная гравитация и ориентированное движение игрока по BlockBody.
Большой UFO, абордаж с механизмами/мобами и полный3D AI ещё не готовы.
Проверка CI использует обычный мир и программную графику Linux.

Ниже сохранён предыдущий snapshot и его отдельные native BCC-проверки.

---

# Текущий проверенный этап ТЗ — 1.1.25-boarding.1

[Скачать JAR](releases/1.1.25-boarding.1/drmd-6dof-1.1.25-boarding.1-fabric-1.21.1.jar) · [Установка](releases/1.1.25-boarding.1/INSTALL_TLAUNCHER.txt)
· [Проверки и ограничения](releases/1.1.25-boarding.1/README.md) · [SHA-256](releases/1.1.25-boarding.1/SHA256SUMS).

Первая серверная посадка/ходьба/прыжок по BlockBody с world-UP.
Полный абордаж с локальной гравитацией и3D AI ещё не готовы.
CI проверяет обычный мир; тот же установочный JAR также прошёл два native BCC-клиента.
[Отчёт, 15 кадров и ограничения](releases/1.1.25-boarding.1/NATIVE_REPORT.md).
Программная графика Linux/llvmpipe.

Ниже сохранён предыдущий snapshot и его отдельные native BCC-проверки.

---

# Текущий проверенный этап ТЗ — 1.1.24-boarding.2

[Скачать JAR](releases/1.1.24-boarding.2/drmd-6dof-1.1.24-boarding.2-fabric-1.21.1.jar) · [Установка](releases/1.1.24-boarding.2/INSTALL_TLAUNCHER.txt)
· [Проверки и ограничения](releases/1.1.24-boarding.2/README.md) · [SHA-256](releases/1.1.24-boarding.2/SHA256SUMS).

Это основа контактов BlockBody; полноценный абордаж и3D AI ещё не готовы.
CI проверяет обычный мир; дополнительно выполнены2 native BCC-прогона с тем же JAR.
[Отчёт, кадры и пределы проверки](releases/1.1.24-boarding.2/NATIVE_REPORT.md).
Графика Linux/llvmpipe; полноценный абордаж ещё не подключён.

Ниже сохранён предыдущий snapshot и его отдельные native BCC-проверки.

---

# Текущий проверенный этап ТЗ — 1.1.24-boarding.1

[Скачать JAR](releases/1.1.24-boarding.1/drmd-6dof-1.1.24-boarding.1-fabric-1.21.1.jar) · [Установка](releases/1.1.24-boarding.1/INSTALL_TLAUNCHER.txt)
· [Проверки и ограничения](releases/1.1.24-boarding.1/README.md) · [SHA-256](releases/1.1.24-boarding.1/SHA256SUMS).

Это основа контактов BlockBody; полноценный абордаж и3D AI ещё не готовы.
Проверка CI использует обычный мир и программную графику Linux.

Ниже сохранён предыдущий snapshot и его отдельные native BCC-проверки.

---

# Готовые сборки UNGRAVITY / DRMD

Текущая проверенная сборка: **1.1.23-snapshot.1**, Minecraft **1.21.1**, Fabric, Java **21**.

- **[Скачать готовый JAR](https://github.com/terminaldetector/ReforkDescnt/raw/refs/heads/codex/ungravity-cloud-recovery-20261024/dist/releases/1.1.23-snapshot.1/drmd-6dof-1.1.23-snapshot.1-fabric-1.21.1.jar)**
- [Установка](releases/1.1.23-snapshot.1/INSTALL_TLAUNCHER.txt)
- [SHA-256](releases/1.1.23-snapshot.1/SHA256SUMS)
- [Исходная ревизия и результаты проверок](releases/1.1.23-snapshot.1/README.md)
- [Подробный локальный отчёт](releases/1.1.23-snapshot.1/NATIVE_REPORT.md)

JAR хранится непосредственно в Git. Скачивать его из Actions или собирать проект для установки
не требуется. Старые файлы в корне `dist/` сохранены как исторические версии; актуальная сборка
находится в указанном каталоге `releases/`.

Портальная пушка: ЛКМ — синий, ПКМ — оранжевый, Shift+ПКМ — удалить пару.
Ровная опора5×5 и свободный воздух перед ней. Immersive Portals не требуется.
[Управление, рецепт и ограничения](../docs/v5/PORTAL_GUN.md).

N — отдельный инвентарь дрона. ЛКМ / ПКМ — два выбранных оружия, B — вспомогательное.
Старые Advanced-генераторы отключены. [Пределы snapshot](releases/1.1.23-snapshot.1/README.md).

Все 11 ролей дронов проверены на полный запас здоровья и корректное сохранение повреждений.
Новые адаптеры дронов и наземного бестиария включены по умолчанию. Полное Master ТЗ v5 ещё
не выполнено: интеграция коллайдеров в UFO, полноценный Dynamic Boarding и маршрутизация AI
в 3D остаются в работе. Исходники snapshot прошли [GitHub Actions](https://github.com/terminaldetector/ReforkDescnt/actions/runs/40657904862).
Тот же JAR прошёл12 [локальных кубических сценариев](releases/1.1.23-snapshot.1/NATIVE_REPORT.md).
