# Actual ORB / ROCKET / JET128 swarm and world restart

Native observer sourced1d92e9e278e7ebe59f3850974b17280ac0211e4, production game sourceb042d9f66cd72c875efb2070560e3af94d139c24. This extends the earlier generic mixed-role proof with actual independent43 ORB,43 ROCKET and42 JET classes, their authored roles and rotating meshes. The existing swarm/index/combat/physics are used; no new entity engine or impostor substitution. An8×4×4 fixture uses3m spacing to admit the conservative real hull bounds without starting them interpenetrating. This is a test spawn layout, not a gameplay formation override.

All targets are acquired normally,20m above the initial cloud at Y207 and20m below at Y2156. No manual actor ticks, per-frame velocities or target assignments. The server requires128 live/active/registered/indexed actors,>=96 actually moving and targeting, bounded<=96 examined candidates, and participation/movement/targets/shots from every kind. Client128 exact kind identities and render calls from all three registered models are required. ROCKET shots must be actual missile projectiles. Flock/physics/collisions, explosions and smoke remain active.

| Renderer / height | Active / moving / natural targets | ORB / ROCKET / JET shots | Shooters | Max examined neighbours |
| --- | --- | --- | ---: | ---: |
| Mesa207 |128 /128 /128|see raw kind JSON; total2871|128|72|
| Mesa2156 |128 /128 /128|see raw kind JSON; total2849|128|96|
| Sodium207 |128 /128 /128|see raw kind JSON; total2811|128|70|
| Sodium2156 |128 /128 /128|see raw kind JSON; total2924|128|84|

After the high scene, real world serialization saves all128 UUIDs and type IDs, damaged hulls, bank, phase/timer, motion and weapon state. A second JVM opens the exact same closed world. The actual NBT read callback compares components and exact registered class/type before the first entity tick; no replacement NBT/entities are injected. It then requires genuine ticking/movement/natural targets and>=96 shooters across all three damaging roles. Client identities must match all128 original saved types, with all three models rendered.

| Reader | Exact classes / active / moving | Natural targets | Shooters | Assault / Artillery / Interceptor shots | Accumulated rendered identities by kind |
| --- | --- | ---: | ---: | --- | --- |
| Mesa |128 /128 /128|127|125|710 /58 /1201|ORB2 /ROCKET43 /JET42|
| Sodium |128 /128 /128|128|128|714 /55 /1373|ORB1 /ROCKET43 /JET42|

Counters are frozen in the accepted server snapshot; shot-role sums match the standard total. Rendered IDs accumulate real renderer calls during the view window, not a per-pixel assertion that every actor is unobscured in the final photograph. Dense missile smoke occludes some bodies. Writer frames include125–128 distinct rendered actors; reader frames satisfy the existing>=32 threshold and all three kinds. Close individual model/icon proof remains in FLIGHT_ENEMY_IDENTITIES_2026-11-05.md and JET_MOTOR_2026-11-05.md.

Game JAR SHA256fe3d500a7a63c48576bd074b082d720c14f287828f972203de4ad53db9f8c8ea. Native observer JAR SHA2569402fdf30d55a6779f9a46e59b671815842b3f9d9ce00b88e23175df375b72d8. EVIDENCE/typed-swarm-d1d92e9.zip SHA256cba4c9d0beed1e88adaf90371a5d34d467ea2e97950c7870f2d5f8c36264def3 contains six fresh frames, full logs/inputs/launches, raw actor NBT/types, verified closed first-process world snapshots and retained failed diagnostics. All four final client exits are0 and both input source SHAs/clean tracked diffs match. Current main has701 unit/269 GameTests; the fixture-only load correction318d535 was verified locally and in Actions. Native-only changes after that do not alter the game JAR.

## Retained failures and limits

The first d717ba8 functional proof passed but placed the camera inside overlapping smoke. It is retained as an unaccepted visual diagnostic.3557cee moved the observer outside the live blast volume; its reader then asserted all three kinds before the camera's updated view had rendered them. d1d92e9 includes that condition in the existing bounded frame-readiness wait; the required counts and15s/8s deadlines remain intact.

That fatal viewer assertion also exposed a shutdown livelock in Better Cubic Chunks0.1.1: the Server thread consumed19539 CPU seconds in recursive CubicServerLevel.advance while the Render thread joined shutdown. The stack dump and source/JAR metadata are preserved. Binary inspection found a depth6 recursion guard and outer scheduling deadline checks; the upstream source URL returns404. No upstream source is copied or patched. Test launchers now use timeout --kill-after=15 so a failed JVM cannot ignore TERM indefinitely. This bounds diagnostic cleanup; it is NOT a production scheduler fix. Shutdown/streaming recovery remains OPEN.

Final writer MSPT p95: Mesa81.65/78.66ms; Sodium100.61/87.14ms. Software Mesa/shared cloud CPUs, Minecraft1.21.1, BCC0.1.1, Sodium0.8.13, Java21.0.12.1, seed271026. These are functional passes with performance OPEN. FPS percentiles, instantaneous loaded-cube counts and peak working memory are UNKNOWN. Raw frame interval samples are not FPS percentiles. Full avoidance/shared passages/dynamic hulls, matching wreck rigs,256+ scaling/LOD, Survival director and the complete v8 remain open; no new accepted release.

Actions42024972632/42026526199 passed their executed game/client checks and stopped only at the deliberate 'Development build is not an accepted distribution' guard. That guard remains. Current observer source artifact13439823552/run42079452394 is saved/notexpired, digestsha256:c1c5d04ad2bfe66d4a0a173106653ade60e960b5e6dcc4e29def7db4e2aa3966. The latest workflow is not claimed fully green.
