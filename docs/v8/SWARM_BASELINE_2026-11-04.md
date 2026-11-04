# Pre-refactor generic drone baseline

Native sourcef3b7cac6f59ccd877b9d71eccd72aeb268600ac0; unchanged game JAR4609134fc38f4d856507e4cba47743cdc84674a7674988a49571e547b33a1962; test JAR72c83931dccc11e0799b25a1e20322099ab555e0748344bfea068bbf5f74262d.

The older AuditSwarmProfile128/96 fixture is retained unchanged. The new independent native stand repeats its mixed SUPPORT/ASSAULT/MG/INTERCEPTOR roles, layout, forced invulnerable/invisible cow target, NORMAL difficulty and35-tick warmup/60-tick measurement window, then repeats at cubic Y2156. Seed271026; backgrounds/weapon view/cockpit disabled for a consistent open-air scene. Drones receive no manual ticks and keep actual AI. Server world lifecycle drives them.

All four cases (Mesa GL3.2 and Sodium GL3.3 at Y207/Y2156) passed:128 alive,128 with advanced real tick age,128 moving,128 in server lookup,128 indexed,128 retaining the target,128 synchronized and128 observed by their actual renderer. Real projectiles spawned:811/815(Mesa),731/782(Sodium), from40/40 and40/41 agents. Maximum examined boid candidates59–60, within64. Fixture actor removal reported0remaining. The invulnerable forced target intentionally isolates the inherited load profile; it does not prove natural targeting, damage balance, two separately measured damaging roles, persistence, obstacle navigation or the full ORB/ROCKET/JET bestiary.

Measured server p50/p95/p99 milliseconds:

| Renderer | Height | p50 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: |
| Mesa |207|25.7360|74.1263|77.9768|
| Mesa |2156|14.4370|25.0212|50.4459|
| Sodium |207|26.7372|65.4535|69.1599|
| Sodium |2156|15.7589|50.9098|66.1452|

These short cold/warm windows run on software rendering with a shared3-vCPU environment, not the user's Intel GPU. Performance acceptance remains OPEN: several p95 values exceed50ms. Observed tick throughput can exceed20 during catch-up; it is not a new configured tick rate or proof of sustained performance. The CSV's frame count is interval samples; no FPS percentiles were measured, so BENCHMARKS.csv leaves them UNKNOWN. Do not compare these timings to a different warmup, profile or machine without qualification.

Four raw frames, complete CSV/logs/input hashes and first failure are in EVIDENCE/swarm-baseline-f3b7cac.zip (SHA2560e16671109ad789ca2a312b31b8c5960d97bb7817fe0ce57873bc3fecc53d435). First stand attempt passed server128/128 and real fire but failed client visibility (113 synchronized,0rendered). Resetting fixture flight attitude before positioning and keeping actors within tracking range resolved the harness issue. That failed run is retained, never counted as a full pass. The seed's static cube/chest markers are test fixtures.

Lineage status: inherited bounded swarm snapshot and generic mixed-role moving128 fixture PRESERVED on this exact JAR. Existing192-agent snapshot/8-agent coincident escape GameTests passed within the250-test suite; these are distinct from the new multi-tick native run. Full bestiary, survival threat, persistence and volumetric obstacle navigation remain UNVERIFIED/OPEN. AI production code was not refactored to obtain this baseline.

GHA source artifact13369246538 for run41924857387 verified saved/notexpired, digestsha256:0c0dd9a152ed0aa16deaf8731cd438f35b7b32c2063a66db5b50d0ed63152d62. Full workflow conclusion remains separate from local native evidence. No accepted1.1.35 release or complete G2/G4/v8 claim.
