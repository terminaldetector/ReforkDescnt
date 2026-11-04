# EVIDENCE PROTOCOL & PERFORMANCE REPORTS

## Definitions
**Active entity:** spawned, registered, ticking or scheduled by explicitly defined simulation tier (not merely visible). **Moving:** actual velocity over a minimum observation period, excluding idle or jitter. **Visible:** included in camera frustum and present in resolved frame. **Physics body:** object with solver contacts. **Ragdoll:** articulated >=2-body chain, not a particle corpse animation. **Cube loaded:** backend authoritative section acknowledged by server; view LOD alone doesn't count.

## Bench row
`timestamp,seed,coords,dimension,git_sha,jar_sha,java,fabric,sodium,cubic_backend,ps1_profile,hybrid_on,expanded_physics,view_distance,sim_distance,gpu,scenario,active_entities,moving_entities,rendered_entities,ragdolls,rigid_bodies,cubes_loaded,mesh_queue,server_mspt_p50,p95,p99,client_fps_p50,p95,heap_mb,gpu_ms_p95,known_issues`.

## Standard incident evidence
- video clip or high fidelity screenshots, reproducible commands and expected/actual behavior;
- log window for 30 s preceding/following (where available), with `world seed`, safe coordinate info;
- match executable SHA and Git commit, flags, hardware;
- unit/Genome/GameTest/replay results, honest status and limitations;
- rollback pointer and test to prevent recurrence.

## Strict constraints
Never forge `runGametest PASS`, visual render screenshots, benchmark numbers, source commits or change logs. If an external library is unstable, present experimental results and reason for not merging. Preserve older user logs and maps exactly; use test copies for migration and cleanup.
