# Combat range control and current128-drone proof

Fix sourced9d0fd82cbd060dbe95d432e501042eb6de44f4d restores the signed radial correction for flank, standoff and pressure/orbit styles. Positive distance error approaches the target, negative error opens distance. CombatGoal already computes target steering, so its flock blend now uses separation/cohesion/alignment without an additional enemy-attraction vector. Support retreat likewise avoids an opposing pursuit vector that previously cancelled its thrust and normalized the small residue into a dive. Shared DroneSwarmAi remains available for other callers; no new physics engine, registry identity or weapon damage/cooldown is introduced.

Seven actual goal/movement tests first failed on c7c828b while the previous257 passed. At24m, old Interceptor/Artillery moved out to26.32/26.10m and MG barely closed to23.39m in40 ticks. Starting inside the ring, they closed to1.12/7.60/4.88m instead of opening distance. Unaccompanied Support stayed near6.06m from a6m threat. After the narrow fix, all264 GameTests and690 unit tests pass. New assertions require long-range closure of>=2m; near-target separation for each style; and Support retreat to>=10m. They use actual role AI,40 measured ticks before RUN, a one-time target assignment, no manual drone motion, and clean up owned projectiles.

Exact development game JAR SHA2564214bda36aac4935215fcc788db4beda9eccd382b9ec35fee08bb5ff909bcd4d. Native observer JAR SHA256aaad79ca1c48c9177934b2aa445e42b352af21e7cccdaaacc88d4558c38ef5f8. Both renderer runs use this exact source, clean tracked inputs and matching JAR hashes.

Before save, the inherited128-agent fixture is repeated at Y207/Y2156. All four scenes pass128 alive/active/moving/indexed/registered/synchronized/rendered and96 actual shooters. Shots: Mesa3392/3382, Sodium3323/3324. The high scene's128 retained actors are intentional input to the subsequent restart, not failed cleanup. Separate reader processes compare all128 actual NBT restores before their first tick, then acquire targets autonomously with no replacements or forced target assignment.

| Restart renderer | Restored / active / moving / synced | Natural targets | Shooters | Shots | Assault / MG / Interceptor | Rendered identities |
| --- | --- | ---: | ---: | ---: | --- | ---: |
| Mesa GL3.2 |128 /128 /125 /128|128|96|3184|634 /1290 /1260|98|
| Sodium GL3.3 |128 /128 /128 /128|128|96|3134|665 /1313 /1156|96|

Each measured server snapshot is immutable before client report publication; role totals exactly equal shot counts. All writer/reader exits are0. EVIDENCE/drone-combat-rings-d9d0fd8.zip SHA25654d2f6b6f655800a109b188e676dfe561dd106f12870d717b2526cbc743d4b70 contains six fresh frames, full logs/launches/source/JAR hashes, raw saved actor components, verified first-process world snapshots and failing/passing server evidence.

These are short functional windows, not a performance pass. Writer server p95 ms: Mesa88.29/68.76, Sodium104.63/57.80. FPS percentiles and instantaneous loaded cubes/working memory were not measured and remain UNKNOWN. Runtime: Minecraft1.21.1/Fabric, Better Cubic Chunks0.1.1, Sodium0.8.13 for its run, Java21.0.12.1, software Mesa on shared cloud CPUs, seed271026. Recorded frame interval counts are not FPS percentiles. BENCHMARKS.csv retains the raw MSPT values and explicitly leaves performance OPEN.

S11-T03 closes only the original generic mixed-role128 active/>=96 moving proof, now with three measured attacking roles and actual persistence. S11-T01 class identities, S11-T02 full avoidance/shared passages, S11-T04 scaling/profiling, prerequisite stage audits and the complete G4 gameplay gate remain open. The earlier client aperture proof stays attached to its olderc911bd5d game JAR; current264 server tests include the aperture regressions, but no older frame is relabelled as a current-JAR run. Full v8/bestiary/Survival/UFO acceptance and releaseG6 are not claimed.

Source artifact13397996808/run41990504757 verified saved/notexpired, digestsha256:3757cf027b8cb90d857f81045f1e4510dd3af99874d505519aaaa6d53d788535. This is source checkpoint availability, not a claim that the full Actions workflow is green. The1.1.35 development binary remains outside accepted releases.
