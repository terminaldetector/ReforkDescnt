# TEST & REGRESSION MATRIX — REQUIRED REAL CLIENT
At all gates record build ID, JAR SHA, seed, world coords, flight state, dimension, GPU, JVM, and screenshots or video with time. Do not copy earlier `*-ok.txt` and claim they were re-run.

| Case | Scenario | Expected / numeric measure | Prior evidence |
|---|---|---|---|
| BASE-001 | boot clean Fabric1.21.1 Java21 with actual mod pack | no crash, features logged | Oct logs |
| CUBE-001 | newly created cubic world | true cubic provider active, height & bounds logged | PR#9 says old backend incomplete |
| CUBE-002 | `Y=-64,0,320,880,1024,1640,2150` | render, collision, light, fluid, save/reload | video void/LOD |
| CUBE-003 | X/Y/Z negative cube boundary | correct `floorDiv`, no missing neighbor | Y negative BE |
| CUBE-004 | cold save/restart generated underground structure | 0 new invalid spawner or DUMMY BE | Oct logs BE errors |
| CUBE-005 | 6DoF 628m/s fast movement, landing | bounded queues, no invisible hard wall | video |
| RND-001 | Hybrid ON default + user OFF override | preserves setting and terrain | request |
| RND-002 | PS1 native↔PS1↔debug toggles | physics unchanged, no world reload | Oct29 scope |
| RND-003 | 640×360 texture/jitter/dither/fog vs full-res | stable HUD/crosshair, no wild distortion | Oct29 scope |
| RND-004 | round cave/strong drill | truly traversable, collision matches mesh | user |
| RND-005 | high-altitude view/asteroid/ring | no forcibly hidden ground | video |
| MRR-001 | 1,2,4 mirror slabs facing camera | slab exists; FBO has valid image; fallback | Oct mirror log |
| PRT-001 | linked portal orientation and high-speed player | correct orientation/velocity, no traversal loop | separate portal spec |
| PHY-001 | gravity torch 0/90/180/270 orientations | no camera clipping, correct UP | screenshot #4 |
| PHY-002 | remove torch after overlap of two fields | deterministic return to vanilla | historical commits |
| PLY-001 | ship mount/dismount/kill/unload | no lava workaround / orphan entity | video |
| VIEW-001 | hand+gun with pitch/yaw/roll full rotation | no screen-sweeping arm | screenshot #6 |
| ENT-001 | zombie/villager/spider/bee/fish/complex boss | 6DoF gravity graceful behavior | new goal |
| RAG-001 | humanoid and 5-piece drone ragdoll wall contact | no tunneling, no duplicate visual corpse | PR #9 claims |
| RAG-002 | 128+ simultaneous deaths | cleanup budget & no permanent ticking | scale goal |
| UFO-001 | UFO stable idle→flight→board→hijack→sabotage→reactor | can complete core loop | user direction |
| UFO-002 | jointed weapons under ship rotation | attached transforms coherent | prior prototype |
| WPN-001 | primary/secondary held weapons | visible in normal + 6DoF | video |
| WPN-002 | cluster carrier first person→spread→impact | correct structured split, projectile count | user sketch |
| DRL-001 | weak vs strong tunnel | cubic vs circle with thermal transition | user request |
| BUILD-001 | survival building cost and undo on failure | no free-placing exploit, valid batch | code-path suspicion |
| INV-001 | six red-marked slots identified | registry mapping and safe removal | screenshots |
| AI-001 | 128 drones (>=96 moving) | real active/moving metrics and target combat | historical 128 |
| AI-002 | 256/512/1024 graduated | measured FPS/MSPT p95, memory, debris | future |
| AI-003 | two rocketeers vs base | destroy without instant unavoidable softlock | gameplay |
| LAB-001 | cave/lab access from ground | no broken void entry, new cubic saves | planned world |
| ORB-001 | surface→sky→asteroids/ring transitions | smooth LOD/sky gradient, no ghost junk | Oct29 concept |
| CITY-001 | urban explosion & ragdoll 128 drones | frame-safe, restore save, no infinite debris | city plan |
| REL-001 | integrated candidate save load in actual TLauncher | no prior P0, report unresolved issues | all evidence |

## Hardware/profile matrix
- October 26 diagnostic machine: Intel HD Graphics 4400, OpenGL 3.2, Sodium workaround. Re-test accessible fallback, no requirement for advanced shader/VS GL3.3 without safe fallback.
- Reference modern GPU: create second profile for source-vs-driver separation if available. If not, mark BLOCKED rather than fabricate result.
- `viewDistance={8,12}` × `simDistance={6,8,12,24}` with fixed seed; version and video capture for comparative metrics.

## Acceptance targets
For simple terrain idle: stable 20 TPS subject to hardware, MSPT should trend below 50ms at normal test load; report p50/p95/p99 rather than assuming target achieved. For 128 drones: `>=96 moving` historical target, full combat without locked loop. For 1024+ choose explicit quality tier and declare number of full-physics active vs aggregate. For performance acceptance, do not set arbitrary FPS 60 on low-end GL3.2 hardware; compare baseline and state target platform.
