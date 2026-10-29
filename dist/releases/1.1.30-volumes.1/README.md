# UNGRAVITY 1.1.30-volumes.1

[Установочный JAR](drmd-6dof-1.1.30-volumes.1-fabric-1.21.1.jar) · [Установка](INSTALL_TLAUNCHER.txt) · [SHA-256](SHA256SUMS).

Полный объём движущегося BlockBody удерживается обычными server tickets и native
Cubic Chunks XYZ views. Игрок на дальнем краю больше не оставляет противоположные
секции недоступными. Старые leases освобождаются при перемещении, уходе игрока,
удалении корпуса и закрытии мира.32768 блоков, сборка128³, секционные BVH,
компактные snapshots и bounded journals сохранены.

Проверенный source `45b11218e6189bdc65e43b3de34d525f3afe5819`.638 unit,186 GameTests,49 Python,
[полный CI](https://github.com/terminaldetector/ReforkDescnt/actions/runs/40872232972) и5
установленных native-прогонов с тем же JAR;35 свежих кадров. Большой полигон
прошёл300 сетевых правок и удаление с освобождением всех колонок.

[Подробный отчёт и ограничения](NATIVE_REPORT.md) · [CI](CI_REPORT.md)
· [native-доказательства](native-evidence.zip) · [первые сбои](first-failures.zip).
Полное ТЗ, боевой UFO/3D AI, фрагменты, механизмы и multiplayer ещё в работе.
Предыдущие версии сохранены. Упаковочные/evidence-коммиты отличаются от source.
