# Actual local-route motor, native drag and pounce provenance

Exact test-only source `06286166866ada74f014559749aaa67475eeaed0` ran once with production bytes identical to `341b5b961d2b68eb76bb0f137296ad914679e42d`. First result: **four passes, one failure**, zero XML errors, Gradle exit 1 in 43 seconds. Fresh XML suite timestamp is `2026-11-17T16:43:34.278012850Z` (file written at `16:43:43.4943027Z`). The unchanged five-case physical fixture SHA-256 is `a4b11c68ed7724409a418e528c2afd960b448b35a0b633d4f896171422793f69`; actual executing helper SHA-256 is `0d3118b5d9eee095eb0cfe6fcb0eb4daae67ea010a6a725c3cff7c664aaa3de7`.

This is a diagnostic of an isolated, still unaccepted production candidate. [The original195 request observations and first3/2 result](CLIMBER_NATIVE_DESTINATION_BOUNDARY_2026-11-17.md), [earlier genuine barrier failure](CLIMBER_WALL_ROUTE_NEGATIVE_2026-11-17.md), and all first outcomes remain unchanged. No gameplay, geometry, native goal/controller, motion, contact, planner-cap, timing or physical assertion was modified for this run.

## Actual result and limits

| Case | First diagnostic result |
| --- | --- |
| Original in-place entry and dark-floor native AI controls | PASS |
| Ceiling, both species | PASS: 60 valid supports each; tangent advances 4.523565127514303 / 4.950217640958726 blocks |
| Simple wall, both species | PASS in this natural run: 60 valid supports each; advances 5.43730299792583 / 5.223768076724792 and distance improvements 4.56269700207417 / 4.776231923275208 blocks |
| Barrier/side passage | **Post-admission watch310 timeout**, not a final120-tick route assertion. Both actual actors were admitted and observed moving; no final `WALL_ROUTE_PASSAGE` result row was emitted. The exact cause of incomplete fixture observation is unobserved. |

The preceding CaveSpider 9.15-block overshoot is not declared fixed merely because this run passed. Production did not change, and normal goal/random scheduling remains native. Likewise, this new barrier timeout is not relabelled as another completed120-tick failure or a route pass. The earlier6ea/1a completed negative route proofs remain the evidence for that defect.

## Passive observer and actual work bounds

The old motor/input/travel/move/field/probe/snap recorder is retained, with passive `LocalSurfaceNavigation.follow` and `PounceAtTargetGoal.start` HEAD/RETURN observations. Actual private native pounce fields were checked against the exact named Minecraft artifact before the run. Follow metadata reads the existing bounded manager cache; it does not search, command a path, call geometry or mutate a goal. Request-only probes are disabled in this helper. The ordinary GameTest manifest remains unchanged; only this helper registers test probes.

There are **886 actual phase events on six actors**, at most151 per actor, below the unchanged256-event cap. The original maximum4 active actors and the new maximum6 seen UUIDs are retained. Original surface fixtures keep their old admission-age0–8 window; the barrier records first actual follow at age21 and the eight ordinary age ticks21–28, with no rearming. Spider has six actual follow calls in this window because native pounce interrupts following; CaveSpider has eight. Raw `spawnAge=20` for these auto-armed rows denotes the observer baseline, not entity birth. `firstFollowActualAge`, `autoArmedAtFirstFollow` and the explicit baseline flag preserve that distinction.

Every support query in these finite observed windows was FULL/available, every measured body was clear and every measured support normal matched local up. There are three actual pounce invocations: simple-wall Spider and CaveSpider at age7, barrier Spider at age27. CaveSpider barrier pounce and every invocation after an actor's finite window are **UNOBSERVED**, not absent.

## Measured divergence

These are ordinary Entity velocity units in **blocks/tick**; positions/displacements are blocks. The derived drag ratio is `TRAVEL_AFTER / last native MOVE_AFTER` per nonzero world component within that same travel call. It is not the ratio to pre-acceleration travel input. Raw collision flags and intermediate values are preserved.

For wall-local up `+X`, real locomotor acceleration is now tangential: approximately `+0.08820001237392461` worldY per forward tick, with no added wall-normal velocity. However native travel dissipates that worldY tangent at **0.9800000190734862–0.9800000190734864**, while worldZ tangent dissipates at **0.546000063419342**. The same0.546 factor is measured on the ceiling's worldZ tangent. The field subsequently applies its separate0.86 factor; it is not included in these native travel ratios. This is actual frame-dependent tangent momentum, not a conclusion inferred only from source code.

For barrier Spider UUID `306515b3-cc8e-4232-82de-1d14c4043f5c`, the first concrete turn sequence is:

1. Age25, sequence83–90: native movement reaches Y23.576391215702248 and leaves velocity Y0.27179138041748746 after native travel and the field.
2. Age26, sequence92–108: actual native Path index advances1→2 and requests the sideways waypoint `[−3972384.98, 23.5, 5765110.5]`. Input adds Z−0.08820001237392461 but retains Y+0.27179138041748746. Travel moves upward to Y23.848182596119734 before the side turn is complete, retaining Y0.2290657798741063 after the field. Support remains real, gap0.02000000001862645 and normal1.
3. Age27, sequence109–110: real native `PounceAtTargetGoal.start` changes velocity `[0, 0.2290657798741063, −0.041415202620784644]` to `[0, 0.4000000059604645, +0.3917169594758431]`. Native navigation is already idle/currentPath absent at pounce entry. The goal directly overwrites worldY and reverses the needed side-passage Z direction; this bypasses the locomotor-input transform. No follow occurs this tick and native travel input is zero.
4. Age28, sequence134–136: actual native move hits the plate underside, stops at Y24.30000001192093 with `verticalCollision=true` and zeros Y velocity. Remaining Z velocity still receives native0.546 drag. The wall support is still valid.

CaveSpider's observed barrier motion separately demonstrates Y carry-through at its later turn: at age28 it begins the sideways waypoint but still enters travel with Y0.33004113042682187; native collision zeros that component under the plate. No pounce was captured for this actor in ages21–28.

This proves two distinct production gaps worth a reviewed correction: the locomotion acceleration is transformed but native anisotropic travel dissipation still uses world axes, and actual pounce impulse still uses world axes outside that transform. It does not by itself establish that either single change solves the complete detour, later replanning/stopping or the post-admission timeout. A proper local-up jump can legitimately leave a support surface, so continuously supported-route acceptance and native pounce semantics need an explicit contract; silently suppressing a goal, zeroing momentum or weakening the existing route assertions would not resolve that conflict.

## Preservation

[climber-follow-phases-0628616-first-diagnostic.zip](EVIDENCE/climber-follow-phases-0628616-first-diagnostic.zip), SHA-256 `1049ece71b09a9c604aa98b522b69a46fe670cd890f9118b2dfbccb844010024`, is1,177,696 bytes. All122 payload hashes and ZIP CRC are verified. It contains original first log/XML/exit, exact source/patch, actual executing helper, launch input/class identities, source and processed manifests, all886 raw phase exports, derived per-tick comparisons and parser, native pounce classfile audit, and the complete32-file7,064,580-byte closed disposable world. Up-to-date main input classes can predate this test-only run; their identities and mtimes are recorded. Existing08:36 `build/libs` JARs are stale for this source and are not represented as generated or verified packages.

No installable JAR, broad suite, BCC client, rendered orientation, natural all-family6DoF, universal C2 or Dynamic Boarding acceptance is established. No source physics correction or retry followed this first diagnostic.
