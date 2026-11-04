# LOG TRIAGE — UPLOADED OCT 26 EVIDENCE
Data inventory: see `08_EVIDENCE/LOG_COUNTS.json`. SHA256 ensure no double count.

## Distinct sessions
1. `2026-10-26-1.log.gz` at 15:02–15:05. Contains `Invalid characters in username` and shutdown GLFW NPE; direct Cubic corruption not proven. `(1)` file is exact duplicate.
2. `latest.log` at 15:06–15:52, contains **two world loads** 15:07 and 15:45; 55 server stalls, maximum 152216ms/3044 ticks; 33 DUMMY BE, Y -51..-2. `2026-10-26-2.log.gz` contains identical plaintext to this log.
3. `latest(1).log` at 16:09–16:55, one separate world: 41 server stalls, maximum **193148ms/3862 ticks**, 100 DUMMY BE, Y -48..-1, `mob_spawner` invalid on air at (334,-21,496), 21 mirror diagnostics.

## Known configuration and hard facts
`drmd 1.1.14-audit.4`, `FabricLoader 0.19.5`, `FabricAPI 0.116.17`, `Minecraft 1.21.1`, `Java 21`, `Sodium 0.8.13`, Intel HD 4400 OpenGL 3.2.0 and depth/framebuffer workarounds. `worldKind=STOCK`, `worldModLevel=ADVANCED`, netherBand/endBand/macroWorldgen/structural districts enabled. `cubic body prototype` announcement ≠ full Cubic backend. DH/Immersive Portals/Iris absent. `simulation distance 24` at 16:13:38; can exacerbate lag but earlier stalls show it is not unique proof of root cause.

## Required profiling report
- Headless game and full client with same seed, flags and JAR; record `TICK_SERVER` stages and side effects.
- Time breakdown: cube/column generation, structure placement, BE placement/deserialization, lighting, fluid, pathfinding, AI, entities, BlockBody, explosions, renderer mesh jobs, world saves/GC.
- `MSPT p50 p95 p99`, worst full stall, JVM allocations, GC pauses, loaded chunks/cubes and entities, pending task queue, disk I/O; inspect thread dumps/JFR around stalls.
- Run A/B 6/8/12/24 simulation distance, worldgen optional flags controlled, same seed and travel route. Never simply suppress log warnings.
- All negative-Y invalid BEs: source generator class and structure ID with original NBT and replacement blockstate. Suspect serialization/placement mismatch, no specific root cause claimed until code trace.
- Mirror diagnostics: separate surface registration, visibility, FBO allocation, `offscreen` check and reason codes, rate-limited debug.
