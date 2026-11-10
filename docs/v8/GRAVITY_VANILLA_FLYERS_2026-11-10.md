# Vanilla C3 flyer gravity fields — 10 November 2026

Five native flying species now keep their flight locomotion when a local gravity field acts on them: Bee, Phantom, Ghast, Allay and Vex. The correction extends the existing `ActorPhysicsProfile` classification; it does not replace AI, renderers, gravity flags or the D6/BlockBody engine.

## Reproduction and correction

Before the correction, `ActorPhysicsProfile.flying` recognized six mod flight families only. Vanilla flyers entered the unsupported ground fallback in `EntityGravitySystem`. Near a torch wall that path invokes `GravityMount.safeMount`, requests a teleport, zeroes propulsion and sets ground contact. Away from a mountable wall it still applies ground-style damping rather than the additive flight force.

The negative source is `75ab292285a4fa3cd9cbab0fa660c79768d7e645`: two of three focused GameTests fail, first on a Bee being moved onto an east wall, and separately on half-strength field force/propulsion. The existing six-mod flying roster passes. Original log, XML, exit and source records are retained.

The production correction is `8b545af76bdfcf17471b53ee604f5557f8f48325`. It explicitly recognizes vanilla `FlyingEntity`, Bee, Allay and Vex locomotion. Phantom/Ghast inherit `FlyingEntity`; Bee and Allay own native flight controls/navigation; Vex owns its specialized flight control. `Flutterer` alone is deliberately insufficient: a Parrot also implements it and has mixed shoulder/ground behavior, while Allay and Vex do not implement it. The mapped-class contract record identifies the exact Minecraft dependency and its SHA-256.

Only `ActorPhysicsProfile.java` changes in production. Existing force integration, ground adapters, mod flying roster, `bankedModel`, renderers, AI and persistence code are unchanged. In particular, `NoGravity` remains an externally owned saved flag and never becomes a locomotion classifier.

## Verified scope

On exact source `8b545af`, all three focused GameTests pass, with zero failures/errors and Gradle exit0. No assertion or geometric tolerance changed between the negative and positive runs.

The two added cases exercise:

- All five native C3 types against all six gravity directions, beside a solid wall. The field must add one force while retaining the actual position, propulsion, yaw/pitch, ground state and original gravity flag; no foot frame or teleport is allowed.
- Both externally chosen `NoGravity` values, half-strength force, real NBT round-trip, a same-UUID unspawned alias that cannot inherit the live field lease, and field exit without a momentum reset or flag mutation.

The original six-mod flight roster is included in both focused runs. Tests observe that native AI starts enabled and retains its navigation/control instances after field exit. AI is disabled inside the force-measurement fixture to isolate physical behavior; these cases do **not** establish natural AI navigation, rendered roll or universal C1–C7 support. NBT aliases are not a two-process saved-world restart proof.

The source GameTest manifest retains all 60 entrypoints. The focused Gradle init script changes only built test resources and explicitly identifies the run as focused. A complete new suite and native client verification remain pending for this delta. The earlier canonical758-unit/376-GameTest results belong to the unmodified baseline, not this correction. No installable JAR or release is certified here.

## Related integration regression

The separate related run on exact documentation checkpoint `1008b61508d3503ca2d756e6748cbf83fe58b4ec` passes all sixteen GameTests with zero failures/errors and Gradle exit0. Its gameplay source is unchanged from the focused production correction `8b545af`: three flyer cases, six entity-field cases, six actor-frame cases and one live surface-combat case. This also checks that native C3 classification leaves field lease ownership, local body/eye frames and actual surface combat intact.

Original related log, XML, exit, tested source, init and the two tested source files are retained in [gravity-c3-related-1008b61.zip](EVIDENCE/gravity-c3-related-1008b61.zip). This is a focused regression set, not a complete suite or native-client visual/AI acceptance. The original negative and focused-positive archive remains unchanged.

## Replay

From the isolated source checkout, use Java21 and the existing offline Gradle cache:

```sh
JAVA_HOME=/workspace/jdk21-complete PATH=/workspace/jdk21-complete/bin:$PATH GRADLE_USER_HOME=/workspace/.gradle ./gradlew runGametest --no-daemon --offline -I work/gravity-roster/focus.init.gradle
```

The exact focused init and source patch are included in [gravity-c3-focused-8b545af.zip](EVIDENCE/gravity-c3-focused-8b545af.zip), together with both original result sets, source identities and mapped class-contract evidence. All archived hashes and CRC are checked independently.

For the sixteen-case regression, replace the init argument with `-I work/gravity-roster/related.init.gradle`.

## Remaining acceptance

S07-T01 remains partial: mixed perched/ground flyers, climbers, aquatic, bosses/multipart and anchored families require their own adapters/fallback evidence. Spatial actor subscriptions/interest budgets are separate from the existing field lookup index. S06 recovery and S08 articulated rigs already have source/tests and are audited rather than replaced. A separate S08 field-strength issue and loaded-cube ragdoll parity are queued findings, not changes in this increment. Full v8 and Dynamic Boarding remain open. Work is local only.
