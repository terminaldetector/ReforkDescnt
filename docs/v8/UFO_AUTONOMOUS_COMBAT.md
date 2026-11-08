# Reference-UFO autonomous rigid-weapon combat

An unpiloted default UFO now acquires a real nearby Survival/Adventure player and aims its physical hull before firing its surviving laser/rocket blocks. Existing thrusters supply the bounded 3D turn and a 36m stand-off goal. Valid pilot ownership preempts autonomous firing immediately; release resumes the same body without resetting momentum. This extends the existing BlockBody/D6 and WeaponCore systems.

**Partial S09-T03/T04; both remain in_progress.** This verifies the reference rigid batteries. Child-turret AI, pitch/compound assemblies, complete hostile state persistence, energy/ammunition economy, arbitrary moving-hull navigation, portal-group transfers and full master v8/Dynamic Boarding remain open. No accepted 1.1.35 release or publication. Accepted 1.1.34 and the existing distribution files are unchanged.

Exact tested source: `3dd2263e3655bcd8c941a99b6fcca067d478f897`. Game JAR SHA-256: `cac3ed622e1e37738bf1c8cc86b57e1fa2223ed14857c9e3f4656693c6370e30`. Separate observer JAR: `3543452dc0a97c2627856f98258703ab56ca1eb71abace5c7864c8c7cc521cef`. The observer is not part of the installable game mod.

Raw evidence: [ufo-autonomous-3dd2263.zip](EVIDENCE/ufo-autonomous-3dd2263.zip), SHA-256 `5bed1235100dd54ae5ccab317e8f87d5bde188ea765908ff656a4900137df956`. All archived file hashes and ZIP CRCs were checked after writing. Includes both exact artifacts, source patch series, full/focused results, rejected rounds and real saved-world snapshots.

## Gameplay and physics

The server selects an eligible same-world external player within 96m from its authoritative player list, retaining a valid target and limiting new searches to once per 10 ticks. Peaceful, explicit autopilot disable, missing reactor, absent weapon cells, paused physics and an actual pilot lease suppress autonomous firing. Creative/spectator players are excluded by authoritative game mode. No fake pilot is spawned.

Constant-velocity interception includes all XYZ components and subtracts the launcher's actual material-point velocity. The controller requests orientation through the existing inertia/engine mixer; it never assigns a target pose, player position or physical velocity. Actual shots still travel along the current barrel quaternion and inherit `v + omega cross r`, with physical point recoil. A real completed burst changes the aiming battery; a bounded 160-tick fallback permits another battery when obstructed. Existing hull cooldowns and force/contact budgets remain.

Two live bugs were reproduced and fixed. Vanilla's standing player retains approximately −0.0784 block/tick of downward velocity after a floor collision. Extrapolating it aimed into the supporting floor and also made the UFO descend while following a stationary player. Prediction now removes the blocked downward component only for ordinary grounded movement. Real boarding world velocity, airborne descent, positive jumps, free 6DoF and local-gravity movement retain their XYZ values; the target's actual velocity is never edited.

The first surviving mount can be obstructed while its partner is clear. In the saved native case, the left rocket line crossed the bedrock platform front at Y2155.739, inside its solid layer; the right line crossed above it at Y2157.295. Aim selection now prefers an actual surviving gun whose ideal lane is open. Rear-target turning retains a fallback intent. This affects only the requested turn: actual emission still checks the current barrel direction, source compound collider, loaded world corridor, other physical bodies and living bystanders. Returning body-owned shots can still hit their source.

Weapon ownership now accepts a real Entity rather than requiring a LivingEntity. A saved projectile resolves its actual root UUID and attributes damage to that root, including the existing shield path. Existing player/mob ownership, guidance and pilot authority remain compatible. A physical launcher's own blast can still damage it.

## Fresh verification

**743 unit tests and 335 complete server GameTests pass**, with no failures/errors/skips. The complete 56-entrypoint manifest matches source. Fifteen focused AI cases cover actual root-owned fire and damage/NBT ownership, world/body/bystander cover, disabled/creative/destroyed-gun negatives, pilot capture/release, rear/elevated finite-torque aiming, stand-off, returning source hits, rotating gun survival, both real batteries, grounded prediction and alternative-mount selection. The seven pure interception cases cover XYZ lead, inherited velocity, rotational covariance, equal-speed roots and invalid/unreachable input.

Both fresh native clients use the actual Cubic Chunks backend at Y2156, the default 1046-cell UFO and a stationary real Survival player. The fixture supplies a 17x17 bedrock platform, 200 maximum health, 400 initial shield and knockback resistance 1 once; it does not make the player invulnerable or heal them. The stock hostile bay remains active. Initial fixture placement/bank is followed only by ordinary AI/physics; there is no assigned target, scripted shot, follow teleport or subsequent body-pose reset.

| Native renderer | Lasers / rockets | Actual damaging hits | Aim dot, initial → best | Disabled ticks / inertial travel | Projected shot pixels |
| --- | ---: | ---: | --- | --- | ---: |
| Sodium GL3.3 | 6 /1 | 5 | .76409 → .99998 | 27 /.19084m | 706 |
| Mesa GL3.2 | 6 /1 | 5 | .76409 → .99998 | 27 /.19363m | 1158 |

Actual muzzle and point-velocity errors are below1e-8. Each client captures four fresh scenes: target acquisition/physical aiming, actual fire, disabled inertial motion, resumed fire. A model/view/projection probe identifies the actual hull and root-owned projectiles. Three consecutive qualified frames require visible hull pixels; firing scenes also require colored pixels near those rendered projectiles. All eight images were inspected. In-flight projectiles continue during the pause; no new shots are emitted. Both cases finish within the unchanged 650-server-tick bound and save normally. Both graphics paths use software llvmpipe; this is functional singleplayer proof, not Intel HD performance or multiplayer certification.

A fresh Sodium living-pilot regression also passes on the same exact source/JARs. Distinct writer/reader JVMs (14207/14328) use the same actual closed world, with saved hot laser/rocket cooldowns 8/24. Before-first-tick comparison restores root, child and complete MOTOR registry exactly. The original carrier retains 40 grounded ticks, ordinary keyboard walking covers 2.36423m, and a new real console capture accepts yaw/fire/braking/H release. Post-load fire emits 5 lasers and 2 rockets. Maximum joint anchor error is 0.00009653m; final relative motor speed is 0.00072753rad/s. Eleven fresh frames accompany this separate two-process regression; its final restored-deck and turret images were inspected. This guards compatibility of the existing living-owner/manual control path after adding physical launcher ownership. Earlier Mesa hot-restart evidence is historical, not a new current-source pair.

## Retained failures and replay

The first missing-fire reproduction, owner/target implementation failures, original scheduler crash and all rejected native rounds are preserved. A 20 HP observer was killed by a real stock bay drone, so later combat fixtures use the explicit durable parameters above. Blind wall-clock battery changes alone did not fix the live laser failure. The grounded-motion correction enabled real damaging lasers but exposed the obstructed-first-rocket bug; that round remains rejected. The first ground test checked only the current lane and was insufficient: a strengthened intercept-direction assertion fails before the production fix. The alternative-mount regression also fails before its fix.

Two earlier 35-tick checks sampled only currently flying projectiles after successful lasers had already hit. They now retain UUIDs of real observed shots within the same deadline. New repeating observations register before GameTest starts iterating its callback map; the original nested registration rehashed that map and crashed the test harness. No physics solver timeout, firing alignment, damage assertion or image threshold was relaxed.

The archive includes exact game/observer JARs, source diff and local patch series, raw unit/full/focused XML and logs, first failures, native inputs/crashes/frames and saved-world preservation receipts. Closed disposable test worlds were archived and checked per file/SHA/CRC before their working copies were reclaimed. User saves and caches are untouched.

Replay with Java21/offline Gradle: `native/build-verified.sh`, `work/ufo-ai/run-native.sh autonomous-ufo-visible-{sodium,gl32}`, `work/ufo-ai/full-regressions.sh`, and `native/run-turret-restart-pair.sh sodium ufo-ai`. Use fresh scenario names and the exact source; scripts reject source/JAR drift. Build, Gradle server and native JVM rounds run sequentially.
