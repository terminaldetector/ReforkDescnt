# Submerged fish gravity policy — reproduced negative

Test-only source `a7afbaf6b5913fe3a75b6103ec13c6c008e123fe`, based on canonical `f3d97844`, reproduces two local-field policy failures on a genuinely submerged Cod. Production classification and force code are unchanged in this checkpoint. The completed ragdoll checkpoint remains separate.

## First real result

The one offline `runGametest` compiles successfully, then runs the existing `EntityFieldGravityGameTests` entrypoint filtered to eight cases. Fresh XML is timestamped `2026-11-17T11:43:55.633385233Z`. Result: **two failures, six passes, no XML errors**, Gradle exit1 in47seconds. The six pre-existing Cow cases pass unchanged.

- `submergedNativeFishNeverBorrowStandingMountingOnSixAxes`: real Cod, first EAST axis, fails because the field pass changes its position to a standing surface.
- `submergedFishFieldForceExitAndSavedFlagsRemainSeparateFromNativeWaterTravel`: real Cod, first half-strength sample, fails the isolated force-pass position/propulsion assertion away from any mountable wall.

The genuine fluid-contact prerequisites pass: contact is populated by at most three ordinary Entity ticks in actual water, never by setting a fake water/swimming flag. In the second case a normal no-field `Fish.travel(Vec3d.ZERO)` movement/drag control also passes before the failing force measurement. Native fluid travel is measured independently; the assertion does not assume or remove a native drag coefficient.

Both new cases stop at the first Cod failure. They do **not** establish results for Salmon, TropicalFish, Pufferfish or the remaining gravity axes. Half/full force, flag/NBT/exit and actual post-exit travel assertions remain in the test for future verification; statements after the first failed assertion have not run. AI is enabled by default but isolated from the measured field pass. This is not natural aquatic navigation or native visual proof.

## Source diagnosis and proposed narrow policy

Cod/Salmon/TropicalFish/Pufferfish inherit native `FishEntity`, with their own water navigation/travel and dry flopping. They match neither the standing `ActorFrames` whitelist nor the explicit flight profile. Entering a field can therefore call `GravityMount.safeMount`, which requests a teleport, selects standing pose, zeros velocity, sets onGround and clears swimming. The generic ground force branch also adds its own damping during a field pass.

The proposed correction is a separate `FishEntity` predicate requiring actual touching-water and submerged-water state, used to bypass standing mounting and select additive field force. It must leave `flying()`/banked models, native `Fish.travel`/water drag, NoGravity ownership, runtime lease/alias logic and other aquatic families unchanged. No production correction is included in this negative checkpoint. It would not cover dry/partly submerged fish, Squid/Dolphin/Guardian/amphibians, climbers, anchors, bosses, or full C1–C7 completion.

## Evidence

[submerged-fish-a7afbaf-negative.zip](EVIDENCE/submerged-fish-a7afbaf-negative.zip) retains the exact source/patch, executing init helper, source/input hashes, raw compile/test log, fresh XML/exits/summary, actual compiled policy/test classes and the complete closed disposable GameTest save. This command did not generate an installable JAR; existing native JARs are not relabelled as tested artifacts. The source manifest remains unchanged; only built resources are filtered to one existing test entrypoint. All archived CRC and manifest SHA-256 are checked before removing only the archived closed test save. No Java retry or production edit was performed. Work remains local.
