# Submerged fish gravity policy — negative and correction

Test-only source `a7afbaf6b5913fe3a75b6103ec13c6c008e123fe`, based on canonical `f3d97844`, reproduces two local-field policy failures on a genuinely submerged Cod. Production classification and force code are unchanged in this checkpoint. The completed ragdoll checkpoint remains separate.

## First real result

The one offline `runGametest` compiles successfully, then runs the existing `EntityFieldGravityGameTests` entrypoint filtered to eight cases. Fresh XML is timestamped `2026-11-17T11:43:55.633385233Z`. Result: **two failures, six passes, no XML errors**, Gradle exit1 in47seconds. The six pre-existing Cow cases pass unchanged.

- `submergedNativeFishNeverBorrowStandingMountingOnSixAxes`: real Cod, first EAST axis, fails because the field pass changes its position to a standing surface.
- `submergedFishFieldForceExitAndSavedFlagsRemainSeparateFromNativeWaterTravel`: real Cod, first half-strength sample, fails the isolated force-pass position/propulsion assertion away from any mountable wall.

The genuine fluid-contact prerequisites pass: contact is populated by at most three ordinary Entity ticks in actual water, never by setting a fake water/swimming flag. In the second case a normal no-field `Fish.travel(Vec3d.ZERO)` movement/drag control also passes before the failing force measurement. Native fluid travel is measured independently; the assertion does not assume or remove a native drag coefficient.

In the negative run both new cases stop at the first Cod failure. They do **not** establish negative results for Salmon, TropicalFish, Pufferfish or the remaining gravity axes. Statements after the first failed assertion have not run in that negative. AI is enabled by default but isolated from the measured field pass. This is not natural aquatic navigation or native visual proof.

## Source diagnosis and narrow policy

Cod/Salmon/TropicalFish/Pufferfish inherit native `FishEntity`, with their own water navigation/travel and dry flopping. They match neither the standing `ActorFrames` whitelist nor the explicit flight profile. Entering a field can therefore call `GravityMount.safeMount`, which requests a teleport, selects standing pose, zeros velocity, sets onGround and clears swimming. The generic ground force branch also adds its own damping during a field pass.

Production correction `a4e9b9b223b04af93c7124b7595af3b56623705b` changes only `ActorPhysicsProfile` and `EntityGravitySystem`. `submergedFish` requires `FishEntity`, actual touching-water and submerged-water state. A single captured force-only decision is used consistently for both remount and force dispatch: it bypasses standing mounting and selects additive field force. `flying()`/banked models, native `Fish.travel`/water drag, NoGravity ownership, runtime lease/alias logic and other aquatic families are unchanged. Dry/partly submerged fish retain the existing policy. No production correction is included in the immutable negative archive.

## Scoped positive verification

On exact source `a4e9b9b`, the same eight GameTests pass with zero failures/errors, Gradle exit0 in51seconds. Fresh XML is timestamped `2026-11-17T11:49:44.804577975Z`. The actual executing init, test source and source-manifest hashes are identical to the negative; the six original cases remain unchanged.

The two new cases now fully reach Cod, Salmon, TropicalFish and Pufferfish, all six near-wall axes, half/full sampled field force, both externally chosen NoGravity values, real NBT aliases and field exit. Positions/attitude/fluid contact are retained without manufactured ground/frame state, native controllers survive, and post-exit `Fish.travel` reproduces the separately measured ordinary-water displacement and velocity. No native drag coefficient is hardcoded into that travel comparison.

These are isolated field-force and ordinary-travel ownership checks on stock Fabric. They do not prove autonomous aquatic routing, actual ongoing field-driven natural swimming, rendered six-axis models, fluid-surface/dry transitions, multi-process reload, Squid/Dolphin/Guardian/amphibians, climbers, anchors, bosses or full C1–C7 completion. A fresh combined suite/native run after integration is separate; earlier combined results are not inherited by this correction.

## Evidence

[submerged-fish-a7afbaf-negative.zip](EVIDENCE/submerged-fish-a7afbaf-negative.zip) retains the exact source/patch, executing init helper, source/input hashes, raw compile/test log, fresh XML/exits/summary, actual compiled policy/test classes and the complete closed disposable GameTest save. This command did not generate an installable JAR; existing native JARs are not relabelled as tested artifacts. The source manifest remains unchanged; only built resources are filtered to one existing test entrypoint. All archived CRC and manifest SHA-256 are checked before removing only the archived closed test save. No retry or production change preceded that negative checkpoint. Work remains local.

[submerged-fish-a4e9b9b-positive.zip](EVIDENCE/submerged-fish-a4e9b9b-positive.zip) retains the corresponding positive log/XML/exits/input hashes, exact production patch and source before/after, unchanged executing helper/test, compiled classes and complete closed positive fixture. CRC and every manifest SHA-256 are checked before removing only the archived closed disposable save. Original negative evidence remains immutable. No installable JAR or release is certified by these focused checks.
