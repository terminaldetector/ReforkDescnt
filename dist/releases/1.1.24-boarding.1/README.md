# UNGRAVITY 1.1.24-boarding.1

[Установочный JAR](drmd-6dof-1.1.24-boarding.1-fabric-1.21.1.jar) · [Установка](INSTALL_TLAUNCHER.txt) · [SHA-256](SHA256SUMS).

Промежуточный этап ТЗ: геометрические contact/sweep-запросы к локальному BVH
BlockBody и контакт двух D6-тел с взаимной реакцией и ограниченным трением.
Новый решатель применяется к опоре BlockBody на землю. Ходьба игрока/мобов на
летящем корпусе, DynamicBodyAttachment, joints, ControlCore и полноценный3D AI
остаются открытыми. Все12 критериев Boarding и9 UFO по-прежнему OPEN.

Проверенный source SHA: `0b2226709261cbbeeec4197edb26abed297e3587`. [Точный CI-прогон](https://github.com/terminaldetector/ReforkDescnt/actions/runs/40664920873).
Unit: 592; серверные GameTests: 147.
Полная регрессия настоящего клиента, оружие, инвентарь, зеркала/Fabulous и LOD.
[CI-отчёт](CI_REPORT.md) · [Доказательства](ci-evidence.zip) · [Детали этапа](../../../docs/v5/boarding/CONTACT_STAGE_REPORT.md).

Программная графика Linux; новая native BCC-сессия и Windows/RTX этим CI не
подтверждены. Предыдущий snapshot с native-проверками сохранён отдельно.
Упаковочные коммиты отличаются от проверенной исходной ревизии.
