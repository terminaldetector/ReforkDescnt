# Default combat roles recover a firing lane through cover

Production source `99eafb1f967195e1b667cc080c28a262ad51dad8` makes a local confirmed obstruction take precedence over the ordinary combat orbit/range ring. This extends the existing DroneNavigation and FlightRouteSpace. It does not add a second AI or physics engine. Support and low-health escape retain their separate behavior. When a firing lane becomes clear, normal combat steering resumes.

The local visibility probe checks loaded cubes before tracing terrain, covers at most 32 blocks and checks at most 32 indexed physical bodies. It normally caches four ticks, invalidating earlier after significant actor/target movement. An unknown or distant volume does not initiate this cover search and is not represented as proven clear. No chunk tickets or generation requests are added by navigation.

The route scheduler retains its limits: 16 admitted searches, 48 queue pops per world tick, quantum 8 and 512 stored cells per search. Combat recovery uses a weighted heuristic of 2 to find a feasible lane earlier within that cap; it does not guarantee a shortest path. Other route callers keep heuristic 1. A nearest grid point inside a wall is replaced by the closest swept-clear point among at most 27 local candidates. Grid alignment, actual actor clearance, terrain/body collision checks and failure on unavailable geometry remain in force.

## Reproduction and server verification

Source c1560b7 first failed all three new real default-role cases; the preceding 276 GameTests passed. ORB could eventually fire after moving around cover but did not use the offset opening. ROCKET and JET did not obtain the required lane. Targets are assigned once to isolate navigation; these tests do not establish natural target acquisition.

The first unweighted implementation made ORB/JET pass but left ROCKET at the wall. That one-failure run and its exact source patch are retained. Weighted search then passed 712 unit and 279 GameTests. The final additional case starts ROCKET already touching the wall. With swept-clear grid admission, all 712 unit and 280 GameTests pass. No test threshold or timeout was relaxed.

The two new pure path tests cover three actual hull clearances under the unchanged memory/queue/query bounds and reject invalid heuristic settings. Four real GameTests use ordinary goal/entity ticks, an intact bedrock wall with an offset aperture, a clear ray through that aperture and actual owned projectiles beyond the cover near the target. No path, velocity or follow teleport is supplied to the drones.

## Native cubic clients

Observer/source `b59884b476f51c566183917e557866dab4f8d671` passes four cases each on Mesa GL3.2 and Sodium GL3.3 at Y=2156. Independent native clients use the actual cubic backend and real registered ORB, ROCKET and JET entities with their default combat roles. The fourth case is ROCKET already against the wall. Actual AI obtains an unobstructed ray through the aperture and actual projectiles reach the target side while cover remains intact. Both clients exit 0 and save all dimensions.

Mesa cases take 101/166/149/166 measured ticks; Sodium takes 102/167/150/167. Each snapshot follows at least four consecutive frames with the real flight renderer and matching hull-palette pixels. Eight fresh frames, exact launches, marker data and logs are retained. Blast smoke obscures parts of some images; this is functional combat/navigation evidence, not an unobscured model showcase or a visual-quality pass. Initial target assignment is explicit. No manual entity ticks, manual velocities or drone-following teleports are used.

Game JAR SHA-256: `e2fa77412a5c6ced92537ca7b0a5b78e24c65a3f7f98f6e5c6e3197d6f56f480`.
Observer JAR SHA-256: `e2694da462cafaa7b7a1c2c6f949f58640f338b2ee9cd88c3c18c509c0d55619`.

`EVIDENCE/typed-combat-lanes-b59884b.zip` SHA-256: `9c42d19056ae5a8ee076ed3bfade1200cbba84bc858d11bc16815ced97d170ef`. Every included file hash and ZIP CRC was verified. It includes the original failures, intermediate patch/results, final unit/server evidence and both native clients. The initial observer compile error is also retained separately from gameplay failures.

GitHub source artifact 13496561956 / run 42205660832 is saved and not expired; digest `sha256:3fa113f69f25d3e7ea70db1146326f56b8b2e26592d5e3d3cf292f2ee2c9ba3e`. Remote Unit tests passed; Server GameTests and the complete workflow were still running when checked. Local results are not a claim that the entire remote workflow passed.

S11-T02 and S11-T05 remain in progress. Dense shared passages, all roles, moving hull timing, JET steering continuity through route corners, full projectile/destruction coverage, native 128-agent restart on this new AI revision, larger swarms, Survival and full v8 acceptance remain open. Software graphics and startup overload warnings do not establish performance acceptance. The version remains 1.1.35-ufo-dev.1; no new accepted distribution is declared.

## Follow-up: fast-hit assertion and the current 128-agent regression

Remote run 42205660832 subsequently failed one new JET test: the lane was clear but the sampler never observed a projectile within three blocks of the target. The exact failed artifact 13495644165, SHA-256 `eba4abfbae276886bca1aa45575227c91f982b45d189619f4e5000a8b3eaea73`, is retained. A fast bolt can hit and be removed between two samples; this position sampler was not reliable evidence of a miss.

Test-only source `8aeb5594d2c5bbb1616a47564fde58f36c910b5d` checks actual target health loss attributed to this drone instead. The stationary target has sufficient health to survive the check. The aperture, intact-cover condition, autonomous goal, 380-tick measurement cap and movement rules are unchanged. All 280 GameTests pass again with that final assertion. The game/observer JARs remain byte-identical to the hashes above. This is a measurement correction, not another claimed AI implementation fix. The new remote workflow 42207155405 was still in progress when checked.

The current game also completes a fresh Sodium mixed-type 128-agent write/read pair. At Y207 and Y2156 all 128 actors genuinely tick, move, synchronize, acquire natural vertical targets and shoot. There are 43 ORB, 43 ROCKET and 42 JET. The maximum examined neighbour candidates are 65 and 82, below the existing cap of 96. All 128 actual types/components restore before the first tick of a new process opening the same closed saved world. After restart all 128 remain active/moving/client-synchronized; 127 acquire natural targets and shoot. There are 2221 actual shots from three damaging roles and 86 rendered identities across all three kinds.

A first reader launch was blocked by the source-input guard because the test-only correction occurred between writer and reader. The blocked admission is retained. After the final server test, both remapped JAR hashes were independently compared with the closed writer, found identical, and the reader resumed that same saved world. Inputs record writer source 437312f and reader source 8aeb559 separately; there was no NBT insertion, new seed or replacement actor creation.

`EVIDENCE/combat-lanes-swarm-restart-8aeb559.zip` SHA-256: `74649463fd172b75863aa9535894151f5417a73e2c9ddffed0b30de0f18a3d9a`. It retains the remote first failure, corrected final server XML/log, both native process logs, actual saved NBT, verified closed-world snapshot, raw timing CSV, frames and the blocked-admission explanation. File hashes and ZIP CRC were verified.

This latest 128-agent regression is Sodium-only. It is functional acceptance of that bounded load, not performance acceptance: the Y207 sample observed about 16 TPS and server p95 106 ms. Larger swarms, dense shared passages, moving hull timing, route-corner thrust continuity and the remaining v8 work stay open. The earlier paragraph listing the current native128 check as open is superseded only by this narrowly scoped follow-up.
