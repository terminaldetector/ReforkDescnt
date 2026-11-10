# Parallel execution of all specifications

The user's 9 November instruction explicitly authorizes multi-agent work across all plans. Six isolated workers and the root coordinator cover all 18 stages and 99 v8 ledger tasks. [MULTI_AGENT_ASSIGNMENTS.json](MULTI_AGENT_ASSIGNMENTS.json) records every task's responsible worker and current ledger status. Assignment starts work; it does not establish acceptance or replace earlier attached specifications and the Dynamic Boarding criteria.

| Worker | Primary scope | First concrete work |
| --- | --- | --- |
| `root` | S09 BlockBody, mechanisms, boarding; final integration and execution queue | Verified generic lever-fired weapons and contact corrections; next generic lifecycle/boarding acceptance |
| `world_cubic` | S02–S03 backend, WorldAccess and world migration | Reproduce native skylight after opaque roof becomes clear glass, retaining tinted/opaque controls |
| `ps1_render` | S04–S05 meshing/LOD/presentation; mirror/FBO render integration | Complete actual PS1 target/overlay/profile verification before subsequent rendering work |
| `gravity_ragdoll` | S06–S08 player gravity, mob-family6DoF and ragdoll | Negative for five vanilla flying species incorrectly entering the ground adapter |
| `combat_ai` | S10–S11 weapons, drills, bestiary and swarms | Loaded-only critical-health cover probes and actual rotated hull cover |
| `world_content` | S12–S14 Survival threat, authored zones, factions and missions | Persistent confirmed-alarm/warning/recon/search/hunt/cooldown increment; siege remains separately gated |
| `audit_integration` | S00–S01, S15–S17 evidence, cleanup, instrumentation and release readiness | Source/evidence audit for all99 tasks without automatic status promotion |

Related S01 tasks follow their technical owner; S15 mirror rendering follows `ps1_render`. Root retains the final S17 merges, ledger and delivery decisions. No publication or GitHub Actions are authorized.

Each new worker branches from the verified core checkpoint `4b2e243` in a separate sparse worktree. Large evidence/world/dependency directories are not copied. The renderer candidate already includes the same core changes and passes763 unit/376 full server cases, but its first native profile check is still a retained failure; that candidate is not yet accepted as a finished PS1 profile. Its native nearest-grid check measured0.9697917 against0.97, and the current audit found the final-image sample intersects the deliberately full-resolution cockpit crossbar. The failing evidence and stronger layer-specific measurement remain part of that lane.

Workers first establish what already exists and which exact acceptance is missing. `pending_reverification` is not an implementation inventory. Approved ownership stays narrow; shared registrations, manifests, language files, physics interfaces and camera/render files require coordination. Changes remain inside each worker's tree until reviewed integration. Replacing the existing engine, reactivating legacy Advanced generation, silently deleting assets or resetting current user choices is not an approved shortcut.

Java, Gradle and real-client tests use one explicit root-controlled lease. Independent source work and light read-only/Python checks can proceed in parallel. A lease names the source and exact gate; source stays frozen during that gate. At a failure or analysis boundary the worker preserves logs/source/artifacts and returns the lease so another lane can run. Test worlds are reclaimed only after closed-world preservation with hashes/CRC; byte-identical closed staged artifacts may share immutable storage while keeping every path and byte.

Acceptance requires the actual declared tests, current source/artifact hashes and fresh relevant runtime evidence. Missing, failed, blocked, partial and historical proof stay distinguishable. Root centrally updates STATE/TASKS/DECISIONS/REGRESSIONS and the evidence index. The independent audit matrix will add exact source anchors and per-task evidence; it will not infer PASS from a filename or completed compilation.
