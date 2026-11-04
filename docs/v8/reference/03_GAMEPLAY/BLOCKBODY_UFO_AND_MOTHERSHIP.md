# BLOCKBODY / UFO / MOTHERSHIP — ИГРОВОЙ КОНТУР И ФИЗИЧЕСКИЕ КРИТЕРИИ

## Разграничение
UFO — модульный физический корпус, **не ORB/ROCKET/JET**. Дроны — самостоятельные дешёвые AI units. UFO может быть геймплейным тестом для крупного корабля-матки позднее. Проект не конкурирует с Create и не требует автоматических производственных линий.

## Минимальный словарь функциональных блоков (предлагаемый интерфейс, не утверждённый набор ресурсов)
`Hull`, `Core/Reactor`, `Thruster`, `Steering/Control`, `WeaponMount`, `LaserTurret`, `RocketPod`, `Door/Hatch`, `Joint/Actuator`, `Access/Override`, `EnergyLink` (логический, не отдельные сотни проводов), `Explosive/Charge`. Некоторые функции могут быть одним блоком/пресетом, если gameplay не требует физического объекта.

## Состояния машины
`Dormant → Patrolling → Alert → Hostile → Disabled → Boarded/Hijacked → ReactorCritical → Fragmented/Destroyed`. Параллельные состояния: pilot owner, crew factions, turret activity, joint state, gravity frame, collision target, camera anchor. Безопасное переключение пилота между игроком и AI, no re-mount trap.

## Пять целевых боевых эпизодов
1. Player fast approach in 6DoF: подлетает к дрейфующему UFO, корректная относительная скорость и посадка.
2. Boarding: привязка local character position and gravity к корабельному телу. Повороты корпуса не ломают контроль и коллизию.
3. Sabotage: отключение engine/weapon power, ручной доступ к reactor, обратимый захват управления.
4. Reactor destruction: временное предупреждение, blast, фрагментация корпуса в несколько ConnectedComponents, bounded debris, damage to other entities.
5. Mothership later: те же интерфейсы с более крупным физическим бюджетом и несколькими зонами, а не отдельный incompatible API.

## Engine swap experiment
Evaluate internal BlockBody vs VS2/Eureka unofficial 1.21.1 (independent test). Preserve the same gameplay contract. Actual Oct26 GPU was Intel HD Graphics 4400 OpenGL 3.2 while official VS hardware compatibility states GL3.3+; do not set VS required/default until GL compatibility confirmed, especially under Sodium and Cubic backend.

## Acceptance
- Thrusters create translational thrust and torque; orientation quaternion; rigid contact and inertia; missiles/lasers use attachments moving with hull.
- Boarding and exit survive upside-down roll, ship death, player death, unload/reload, portal if enabled.
- Entity/BlockBody authority server-consistent, no duplicate hull, no duplicated weapons after restore.
- Physical destruction within tight budget; one UFO and a swarm do not freeze integrated server.
- No hundreds of engineering UI objects. The gameplay must demonstrate aboard/steal/destroy, not only rotate an arbitrary block volume.
