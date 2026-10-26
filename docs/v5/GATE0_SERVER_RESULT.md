# GATE-0: actual cubic runtime, server subset

Status: SERVER SUBSET PASS; FULL GATE PENDING. No migration of player saves or baseline branch merge.

The upstream JAR cannot be consumed by the current Loom 1.10.5 remapping pipeline: its build metadata requires Loom 1.17.21. Preserve this first failure. Do not strip metadata or change the closed-source JAR. Instead, a production-style dedicated runtime successfully loaded the unmodified artifact using Fabric Loader 0.19.5 and the official Mojang 1.21.1 server JAR (SHA-1 checked against Mojang metadata). The baseline Gradle build configuration has been retained; the unsuccessful experimental runner is archived in work/gate0/failed-loom-runner.patch.

The first integrated run with DRMD 1.1.15-terrain.1 created a cubic world and then hit the server watchdog inside DRMD NaturalGeometry.shape. Combining independently warped cell coordinates into VoxelShapes produced an expensive Cartesian coordinate grid. This regression was introduced in the preceding terrain candidate; it does not establish the cause of the user's earlier 1.1.14 logs.

A bounded collider patch is isolated in feature/physical-6dof-entities-v5 at 3da4231510eabb350916530258d55f6326c0f6f0. Candidate 1.1.16-physics.1 passes 543 JUnit cases and 82 required GameTests. Natural, explicitly organic, and carved organic shapes share a conservative fixed raster with 32 cells per axis across [-0.5,1.5], pitch 1/16 block. Collision coordinates no longer grow with every warped vertex. The raster can expand the previous conservative bounds by less than 1/16 per side; rendering and block IDs are retained. No subsystem has been disabled to obtain the result.

## Reproduction

Dedicated loopback-only server, new world, seed 261026, level-type=bettercubicchunks:cubic_default; view=4, simulation=4, upstream vertical load distance=8; Java 21, heap 2 GiB, no clients/Sodium. Preserved standard DRMD config and upstream defaults; no original saves copied. Observe native /bcc status and actual cubes/ region files, not DRMD supplemental snapshots.

1. Create an isolated runtime with the verified BCC artifact, Fabric API 0.116.15+1.21.1, DRMD candidate and Fabric server launcher 0.19.5. Inputs and first failure are retained in work/gate0.
2. After Done, run `seed`, then `jfr start`.
3. `/bcc load` takes WORLD block coordinates; `/bcc cube` displays cube addresses. Use `bcc load 8 2150 8 0`, `bcc load 8 -512 8 0`, and `bcc load 0 2160 0 0`. Wait for FULL/wanted=true before writing. The first attempted write to an unloaded high cube was refused and is retained in the log.
4. Place chest (8,2150,8), CustomName=gate-v5-Y2150; barrel (8,-512,8), CustomName=gate-v5-negative. Check both with `data get block`.
5. Place gold_block (1,2159,1) and diamond_block (1,2160,1), across adjacent cube Y addresses 134/135. Run `save-all flush`, `jfr stop`, `stop`.
6. Restart the SAME disposable world. Request the four positions with /bcc load again; do not issue setblock on reload. Read both BE NBTs and use execute-if-block for the gold/diamond markers. Both retained their states. Shutdown normally.

The fixed first run remained responsive past the previous watchdog point, recorded ~200 seconds of JFR and stopped normally. Fixed/reload runs have zero DUMMY warnings and zero lag alerts in their logs. This is an idle/command-driven server test with zero players, not a performance acceptance result for gameplay. Authentication public-key fetch failures are retained separately and did not prevent the offline loopback fixture.

Native telemetry reports generated/loaded/saved cubes and .mcr3d files. Its droppedWrites counter is nonzero; semantics and broader data-integrity implications remain UNVERIFIED. The successful marker round trip does not certify all block entities or all writes.

## Still required

Actual client travel/6DoF across Y, independent vertical unloading and packets; fluids, light, collision and raycast near boundaries; extended cold/reload tests on additional seeds; Sodium A/B; Intel HD 4400/OpenGL 3.2 validation; default gameplay worldgen/content parity. No claims about 128-drone load or the original user's 193-second stalls are made. The older GATE-0 NOT RUN preflight is superseded only for this server subset.
