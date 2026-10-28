# UNGRAVITY 1.1.24-boarding.2

[Установочный JAR](drmd-6dof-1.1.24-boarding.2-fabric-1.21.1.jar) · [Установка](INSTALL_TLAUNCHER.txt) · [SHA-256](SHA256SUMS).

Промежуточный этап ТЗ: геометрические contact/sweep-запросы к локальному BVH
BlockBody и контакт двух D6-тел с взаимной реакцией и ограниченным трением.
Новый решатель применяется к опоре BlockBody на землю. Устранено повторение
старого угла неподвижного корпуса при сбросе partial tick. Ходьба игрока/мобов на
летящем корпусе, DynamicBodyAttachment, joints, ControlCore и полноценный3D AI
остаются открытыми. Все12 критериев Boarding и9 UFO по-прежнему OPEN.

Проверенный source SHA: `a1b045cde046d2a9f3be932eae960ed28bd4e083`. [Точный CI-прогон](https://github.com/terminaldetector/ReforkDescnt/actions/runs/40683605278).
Unit: 598; серверные GameTests: 147.
Полная регрессия настоящего клиента, оружие, инвентарь, зеркала/Fabulous и LOD.
[CI-отчёт](CI_REPORT.md) · [Доказательства](ci-evidence.zip) · [Детали этапа](../../../docs/v5/boarding/CONTACT_STAGE_REPORT.md).

Программная графика Linux. Дополнительно этот же JAR прошёл2 native BCC-клиента
(GL3.2 и Sodium),10 сцен и120 кадров остановившегося угла.
[Native-отчёт и кадры](NATIVE_REPORT.md) · [Сохранённые ошибки](preserved-failures.zip).
Windows/RTX этими прогонами не подтверждены. Предыдущий snapshot с native-проверками сохранён отдельно.
Упаковочные коммиты отличаются от проверенной исходной ревизии.
