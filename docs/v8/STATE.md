# v8 intake and ongoing UFO work

Latest identity increment388eb84:696 unit/267 GameTests and two actual three-type restart pairs on Mesa/Sodium pass. S11-T01 is completed;2 tasks completed,6 in_progress,91 pending_reverification. JET acceleration and type-specific wrecks are next; fullG4/v8 remains open. See FLIGHT_ENEMY_IDENTITIES_2026-11-05.md.

Latest verified main d9d0fd8 / game4214bda3:690 unit/264 GameTests; seven combat-role range/retreat regressions fixed; actual128 restarts on Mesa/Sodium with125/128 moving and96 shooters across three roles. S11-T03 atomic proof is completed (ACCEPTANCE_S11_T03.md); the identity increment above updates the task totals. FullG4/v8 and accepted1.1.35 release remain open. See DRONE_COMBAT_RINGS_2026-11-05.md.

Latest verified game JAR c911bd5d (main5623e0a; native observer dbc5a0c):690 unit/257 GameTests; four off-grid static-aperture route scenes; two actual128-drone world restarts on Mesa/Sodium with128 active/moving/naturally targeting/client-synced and consistent two-role shot counts. See DRONE_ROUTES_WORLD_GRID_2026-11-05.md and DRONE_STATE_CURRENT_2026-11-05.md. Full bestiary/3D combat/shared passages/performance/v8 acceptance remain open; no accepted1.1.35 release.

Historical flight foundation: tested source22f3c10; development JAR SHA25633028ec6b4aa18b18d95763a7b0aa1b7149c37994f1829a9bef07f288d045655.
679 unit/238 GameTests passed previously on this source. Recovered raw native evidence now archived: five external boarding scenes each on Mesa and Sodium, ten frames total. These are recovered runs, not fresh reruns. Fresh full-rotation regression also passed all13 scenes on the same game JAR; its source metadata is recorded separately in EVIDENCE/index.json. Total23 frames preserved.

At intake no supplied task was marked completed: seven tasks (S00-T01/T02/T05, S09-T04/T06, S11-T02/T03) are in_progress and the other92 remain pending_reverification until their complete acceptance is mapped and checked. S00 inventory is in progress: tracked patches preserved, untracked runtime paths inventoried only. Old source/PR9/other branches are retained.

Current focus: finish UFO/BlockBody continuity; add mandatory newer-version physics gates; then ControlCore/module gameplay. Offline singleplayer is the current acceptance target; dedicated/multiplayer testing is deferred per v8, not claimed passed. No accepted1.1.35 binary.

Release evidence gates now inherit their required physics stages into future versions;47 Python checks passed. This changes packaging validation only, not game code.

ControlCore increment has bounded local native proof: right-click capture, exclusive ownership, bounded thrust/torque, H release, lifecycle cleanup and nonpersistent pilot connection.679 unit/243 GameTests passed; native control capture/pause/thrust/roll/H-release passed seven scenes each on Mesa and Sodium (14 fresh frames), exact source0dff43b. This is partial S09-T04 evidence, not full task acceptance. See CONTROL_CORE_WIP.md.

Current increment: native sourceea9c9eb6c2e8a004ccbe016ea4221b4cf183fbd9, game JAR8155bfc90682679d550fee7c11a891e008b0460dac2f44e7d27c7e5e71c0effe.679 unit/245 GameTests pass on unchanged main codedf8f67b. Fresh lifecycle runs on Mesa and Sodium each passed9 scenes including actual re-capture and player death (18 frames); source artifact13362489923 verified saved/notexpired. See CONTROL_CORE_LIFECYCLE_2026-11-04.md. Dimension/portal/actual reload acceptance and weapon/reactor modules remain open. No public1.1.35 acceptance.

Actual same-world UFO reload is now an explicit open regression: exact data/physical restore passes, but walking contact fails after restart. See UFO_RELOAD_WIP.md. This prevents claiming S09-T06 or full save/reload acceptance.

Latest verified main increment:4609134f game JAR, source main dc13381/native08d61c3;679 unit/250 GameTests. Actual two-process restart plus delayed admission, grounded contact and keyboard walking passed both renderers (12 fresh frames). See UFO_RELOAD_2026-11-04.md. The initial open restart bug is fixed for new context-bearing saves; pre-context migration, modules, portal/unload transitions and arbitrary-speed admission remain open. S09-T06 stays in_progress; no new public release. Next: audit actual multi-tick drone combat before navigation refactors.

Pre-refactor generic drone baseline f3b7cac passed four actual native cases:128 active/moving/indexed/synchronized/rendered at Y207/Y2156 on Mesa+Sodium, real projectile spawns and bounded neighbour queries. Performance remains open (p95 includes74ms); full bestiary/Survival/persistence/3D obstacle navigation not claimed. See SWARM_BASELINE_2026-11-04.md. Next audit: actual saved combat/bank/death state before navigation changes.

Drone persistence increment6cb317e:679 unit/253 GameTests pass. Native cdb2572 Mesa actual restart passed128 restored/active/moving/client-synchronized, natural targeting and two measured attack roles; Sodium actual restart also passed128 restored/active/moving/synchronized,127 natural targets and1414 actual shots from two damaging roles. See DRONE_STATE_2026-11-05.md. Full bestiary/Survival/3D obstacle navigation remain open.

Latest drone target increment8fd19bc:679 unit/255 GameTests; natural vertical target acquisition plus128 moving/synchronized/rendered actors passed four fresh Mesa/Sodium scenes. See DRONE_VOLUME_TARGETS_2026-11-05.md. Performance remains open; next implement bounded obstacle routes.

Bounded route prototype1ff65c8 passes690 unit/257 GameTests plus aligned wall/ceiling scenes on Mesa and Sodium. See DRONE_ROUTES_WIP_2026-11-05.md. Arbitrary fractional starts are an explicit next regression; full swarm obstacle/hull/bestiary acceptance remains open.

Off-grid route fix5623e0a passes690 unit/257 GameTests and four fresh narrow-aperture native scenes, with actual collider entry. See DRONE_ROUTES_WORLD_GRID_2026-11-05.md. Exact-current128 restart passed functional checks but exposed a non-atomic measurement/report race; the counter fix and fresh measurements are in progress. No complete stage or new accepted release.
