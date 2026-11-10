# Critical-health drone cover: loaded queries and a 3D retreat cone

This is a scoped S11-T05 increment, based on stable core `4b2e243`. It preserves the default ORB/ROCKET/JET identities, stats, weapons, movement collision and existing normal-combat route budgets. It does not close S11-T05, S11-T04, the broader S10/S11 stage, or Dynamic Boarding.

## Implementation

The old `DroneAi.CombatGoal.nearestCoverDirection` traced four raw Vanilla rays. A query could generate an unavailable column, ignored actual BlockBody material and omitted lower cover. Its upper ray repeated the away direction for a vertical escape.

Production source **`aa2b02655a7ca32ea089ac3924b0adc10e917db9`** changes only `DroneAi.java` and `FlightRouteSpace.java`:

- Clamp each 25-block cover ray through existing `WorldEditSafety.loadedEnd` before querying terrain or physical hulls. An unavailable ray tail provides no cover.
- Sample five fixed directions: away, both side offsets of 0.6, and both perpendicular rise offsets of 0.5. A deterministic nondegenerate basis retains vertical-flight coverage and the previous horizontal upper-cover option.
- Query the existing body spatial index, then raycast actual physical collider geometry. At most 32 admitted hulls receive a narrowphase ray per probe. If the index returns more, body cover remains unknown; known terrain cover can still be chosen. This does not declare a movement corridor free or bypass authoritative collisions.

Five probes and 32 admitted hulls per probe are explicit limits, not proof of complete 1024-drone performance. The broadphase remains the existing `BodySpatialIndex`; no duplicate physics engine or per-block rigid bodies were introduced.

## Exact negative and positive evidence

| Case | Original production, test-only `6f04d28` | Fixed production `aa2b026` |
|---|---|---|
| Unavailable remote cover query must not generate a column | FAIL: remote column materialized | PASS |
| Actual arbitrarily rotated iron BlockBody provides cover | FAIL: real material ignored | PASS |
| Existing upper terrain cover remains selected | PASS | PASS |
| Flying drone can select lower cover | FAIL | PASS |
| Vertical retreat retains useful perpendicular probes | FAIL | PASS |
| Ordinary low-health ORB goal moves to cover without firing | PASS, original fixture | PASS, stronger fixture |

The negative source is **`6f04d28cf0f1fdebb274f32d769d5c7bec97b493`**, unchanged production: **4 FAIL / 2 PASS**, Gradle exit 1 / game process exit 4. Compilation succeeded. Raw logs, XML, source and exit are retained unchanged.

The original live fixture placed the Cow's feet on the drone's initial retreat line, while CombatGoal targets the Cow's middle. Commit **`119178e`** places the actual target centre on the horizontal line and asserts alignment before enabling AI. The original live PASS is not relabelled a reproduced negative. The corrected live fixture has a new post-fix PASS, with no claim that it was run on the old production.

The positive run uses exact **`aa2b02655a7ca32ea089ac3924b0adc10e917db9`**: **6 PASS / 0 FAIL**, Gradle exit 0. The six tests ran in a **stock Fabric GameTest server**, without the native Cubic backend. The ordinary-goal case uses actual default ORB/Cow entities, normal entity/goal ticks and 20 observed ticks, with no supplied route, actor velocity, manual entity tick or following teleport. It requires forward retreat, descent toward intact cover, retained target and zero owned shots. Direct geometry cases invoke the existing private cover query reflectively and are not full-encounter proof.

Both runs used Java 21 and the offline sequential test lease:

```sh
JAVA_HOME=/workspace/jdk21-complete \
PATH=/workspace/jdk21-complete/bin:$PATH \
GRADLE_USER_HOME=/workspace/.gradle \
./gradlew runGametest --offline --no-daemon \
-I work/combat-cover/focus.init.gradle
```

The focused init registers only `DroneCoverProbeGameTests` in generated test resources. Source `fabric.mod.json` is unchanged for the root-owned centralized registration at integration. No full regression or native/client run is claimed on this branch.

## Local evidence

Bundle: `EVIDENCE/drone-critical-cover-aa2b026.zip`.

SHA-256: `6a266281ee61a3a201e1e3e31a4e39403e53167e9e08d19c22d673b9cf3e7294`.

The bundle includes both first-run logs/XML/exit/source, case summaries, exact focused init and both closed disposable worlds. Each world was locked, archived and independently verified by CRC and every-file SHA-256 before reclaiming its original build directory. World preservation is not save/reload gameplay acceptance. The bundle itself passed CRC and per-file hash verification. No installable JAR was certified or published.

## Remaining acceptance

- Native Cubic loaded-volume behavior and client encounters, moving physical cover and complete role/projectile/path/destruction handling remain open.
- S11-T04 256/512/1024 physics/AI/render LOD and profiling remain open; this change does not reduce default combat capability to satisfy a load test.
- The existing heavy-drill aggregation already limits new drops to 8 and nearby item entities to 32, with `DrillSafetyGameTests`; it was inventoried rather than duplicated here.
- S10-T02 already has distinct `GravyPhysics.Mode.SWARM/RAIL/SHIFT` implementations. Existing `GravityWeaponGameTests` proves only a transient field lifecycle; actual three-weapon capture/fling and ballistic rail accuracy still need their own acceptance.
