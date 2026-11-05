# Decisions

Continue existing UFO branch and D6 solver. Preserve supplied v8 reference files separately from executable project instructions. Keep historical task claims unverified until checked against actual source/runtime evidence. Do not apply PS1 presentation changes to physics.

AI increments: continue the existing movement/collision system with bounded loaded-only A*,16 admitted searches/48 queue pops per world tick. World-cell anchors are reached physically, never teleported. Role goals own range/retreat intent; flock separation/cohesion/alignment avoid duplicate enemy attraction. Evidence: seven reproduced failures,690 unit/264 GameTests and exact-source128 native restarts. These decisions do not replace the entity framework or certify performance.

Independent flight enemies reuse DroneEntity combat/navigation and original cached meshes; no new engine or per-block bodies. Conservative bounds are explicit. S11-T01 closes identity only, with exact source388eb84 native restart proof; JET motor and matching wreck rigs remain separate work.

Native generation: reuse the upstream pass allowance in recursive requests; no backend fork, dropped queue entries or invented ready cubes. Normal and controlled-fault read-back evidence are distinct. New-generation backlog is not treated as an unsaved disk queue. AI fixtures acquire distinct leases and spawn only when the real world reports entity ticking; admission/movement thresholds remain unchanged.
