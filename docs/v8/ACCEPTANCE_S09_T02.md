# S09-T02 acceptance mapping

Task: Verify rigid block hull, thrusters and six-axis inertia in live client.
Status: completed for the authored reference UFO. Stage S09, gate G2 and dependencies S06/S08 remain open; completion of this atomic task does not certify the broader stages or a release.

| Required observation | Exact-source evidence |
| --- | --- |
| Rigid block hull in actual flight | Existing 1046-cell BlockBody/D6 UFO, same UUID through boarding, capture, cuts, repair and release. Eleven fresh scenes each on Mesa GL3.2 and Sodium. No separate engine/body engine or player-follow teleport. |
| Real physical thrusters | Four actual local-grid engine blocks apply bounded forces at transformed material points. Pilot and AI lose their contribution after destruction; missing motors get no free replacement capacity. Repair uses collision-checked real local placement. |
| Six-axis inertia | Production C2S commands independently exercise forward/right/ascend/pitch/yaw/roll, at least four actual drive samples each per renderer. Measured velocity/torque impulses match mass/world inertia (maximum errors 2.05e-15 and 6.26e-11). No pose/velocity reset supplies motion. |
| Damage and inertial continuity | Both clients accept simultaneous six-axis input with no motors, retain inertial translation/rotation and regain measurable acceleration after repair. Material point continuity error <4.55e-13m; server tests also cover partial damage and rotated weapon hits. |
| Persistence | Current schema2 and a real historical schema1 closed world load in new JVMs: exact grid/physical state before first tick, four motors, same UUID, no stale pilot/duplicates; delayed admission,40 grounded ticks and >2.95m keyboard walking. Missing-engine cases separately remain disabled in server tests. |
| Regression and source identity | `fd8950fa4be0aad8a97daa87220226b44c02311e`;728 unit/298 complete server tests,28 focused UFO cases. Exact JAR/source hashes, raw XML/logs, first failures, actual snapshots and frames in the verified archive. |

See [ENGINES_2026-11-07.md](ENGINES_2026-11-07.md) and [raw evidence](EVIDENCE/engines-fd8950f.zip). Mounted weapons/hinges (S09-T03), complete control lifecycle (T04), reactor sabotage/fracture (T05), complete module reload/legacy coverage (T06), broader Dynamic Boarding and master v8 remain open. This is local offline singleplayer proof; no performance or multiplayer certificate.
