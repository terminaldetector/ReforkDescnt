# Six combat actors share one firing aperture

Production `4a05958fc52e2d397cdbe505bd56bafbf6de25ad` addresses persistent convergence at route corners and lost known targets during cover recovery. It extends the existing steering, bounded spatial index, loaded-world collision queries and volume target goals. No second AI/physics engine, actor-follow teleport or chunk-generation request is introduced.

Close-range repulsion uses the existing deterministic escape direction for identical centres and hull-width-aware separation. A proposed adjustment is capped by the requested flight speed and checked against the complete swept actor bounds, including existing physical-body queries. A fallback escape is also collision-checked. This applies to participating flock roles; heavy/excluded roles are not newly accepted by these tests.

Nearby actors may advance at most two route waypoints when the full swept volume can reach the next point. Isolated actors keep their previous waypoint behavior. Flock steering, close separation and diagnostics share the same cached bounded query for an actor in one world tick. The existing 32-neighbour/96-candidate cap is retained without a second flock query. The cache contains frame samples/UUIDs and expires with the world-tick snapshot. Route admission/work/memory remain 16 searches, 48 queue pops per tick, quantum 8 and 512 stored cells per search.

Drone volume target goals configure visibility memory to 200 ticks. Acquisition still checks visibility; ordinary hostility, life and range restrictions remain. A real naturally acquired target remains selected at 100 ticks behind a closed enclosure and is forgotten by 300 ticks. The change does not implement unrestricted through-wall acquisition or alter non-drone target goals. Revenge-goal coverage remains separate.

## Retained failures and controlled server tests

The first six-actor wall case on c5e760f completed only five lanes/hits. Later controlled runs reproduced persistent near-coincident pairs and lost targets. Two fixture assumptions were corrected before accepting the implementation: training targets must survive repeated damage without projectile knockback, and lane checks must follow the actual AI-selected target after legitimate retargeting. Targets are healed only after actual damage attribution has been observed. No drone velocity, path or manual tick is supplied.

A lookahead-only attempt still failed group tests and regressed the isolated ROCKET contact case. The final implementation restricts shortcutting to close neighbours and adds collision-checked separation; all prior tests pass again. An initial syntax error is retained as a compile failure, not a gameplay result. Verbose failure text also exceeded Minecraft's 1024-character failure-book page limit in the disposable test world; details now go to the log and assertion text stays compact. Those original save diagnostics are retained and are not represented as user-world evidence.

Local verification passes **712 unit tests and 283 GameTests**, including both six-ORB cases, bounded natural target memory and all 280 preceding cases. Each group case requires every actor to obtain a ray through the same intact two-by-two offset aperture and cause actual attributed damage. It also rejects any pair remaining within 0.2 blocks for 20 consecutive measured ticks. The 580-tick group measurement limit is unchanged. These are centre-coincidence checks, not a claim of zero rigid-body interpenetration.

The final stock-world wall case completes in 155 ticks with maximum coincidence 2 ticks; the ceiling case completes in 331 ticks with zero coincidence. All six actors obtain firing lanes and damage targets in each case.

## Actual native cubic clients

Observer/source `81c0cebd367e1d180b69811a127d97471bb0c265` passes wall and ceiling cases at Y=2156 on both Mesa GL3.2 and Sodium GL3.3. Each client uses real registered default ORB combat actors, ordinary goal/entity ticks, actual damage and intact cover. Targets are initially assigned once; natural retargeting is permitted. There are no supplied drone velocities, manual entity ticks or follow teleports.

Mesa takes 258/378 measured ticks for wall/ceiling, with maximum coincidence 2/0. Sodium takes 285/270, with maximum coincidence 0/0. Every case has six lanes and six damaging actors, at least six indexed drones and maximum examined candidates 6. Four consecutive fresh frames reach all six flight renderers before capture; four fresh snapshots were inspected. These are functional combat observations, not an unobscured model showcase.

Game JAR SHA-256: `7560ec972aa528ddde26388b72edfc0c2586ceaedbbbf209bab4d75926aa3327`.
Observer JAR SHA-256: `6af2e0f4b907e48582b7856084db16c3a105c8e0234c96ee5c30cb47cacd9b2d`.

`EVIDENCE/shared-combat-81c0ceb.zip` SHA-256: `256a485bde37e09e1f86b191acd8abf3a8ad3ee92fa7b83f16a02a57ca44cdbd`. It contains exact sources/JAR inputs, first failures and fixture refinements, final unit/server XML/logs, native process logs, markers and frames. Included file hashes and ZIP CRC were verified.

## Current mixed128 regression and actual restart

The same new game and observer binaries pass an additional Sodium write/read pair. At Y207 and Y2156 all 128 actors genuinely tick, move, synchronize, acquire natural targets and shoot. The composition is 43 ORB, 43 ROCKET and 42 JET. Maximum examined candidates are 70 and 84, below 96.

A second process opens the exact closed saved world without NBT insertion, new seed or replacement actors. All 128 actual classes/components restore before their first tick. Afterwards all 128 remain active/moving/client-synchronized, naturally targeting and shooting. There are 2381 actual shots across assault773/artillery77/interceptor1531. Eighty-six identities render across all three kinds; all 128 synchronize, not all 128 appear inside the camera simultaneously.

`EVIDENCE/crowd-swarm-restart-81c0ceb.zip` SHA-256: `c0b0912531a702f8810c4f522f7118c4bf13989505ef34bf955dc5ca7f7d3b12`. It contains both processes, full verified closed-world snapshot, actual serialized components, raw timing CSV, frames and final server evidence. Included file hashes and ZIP CRC were verified.

Observed TPS are approximately20, but server p95 is72–74ms on shared software graphics. This is not performance acceptance or a controlled speedup comparison with an older run. Larger swarms and hardware profiling remain open.

## GitHub and remaining work

Source artifact13498639131/run42211437512 is saved and not expired, digest `sha256:3253b9a67068770f84a16d32ac182ef7c0ca463e511fd41138a78f14e3baac32`. Remote Unit tests and Server GameTests passed; the complete workflow and weapon-image step were still running when checked.

An older remote run42207155405 on8aeb559 passed its unit/server tests but failed `fast-motion-step-0` in the weapon pixel gate. The original artifact is retained as `EVIDENCE/remote-weapon-first-failure-8aeb559.zip`, SHA-256 `fc843a04fb1534898dad072a06ed6e6a08483824f6803dc003c1067be6e65293`. The frame shows opaque geometry over the projected sample position. Occlusion is a hypothesis; its exact cause is not established or claimed fixed. No weapon pixel threshold was relaxed and no weapon renderer was changed in this increment.

S11-T02 remains in progress. This covers six default ORB actors and a two-by-two shared firing aperture. Mixed-size crowded roles, single-file/bidirectional traffic, moving hull timing, precise live-body nonpenetration, revenge memory, JET acceleration through route/avoidance changes, 256+ scaling, universal AI and full v8 remain open. No accepted1.1.35 distribution is declared.
