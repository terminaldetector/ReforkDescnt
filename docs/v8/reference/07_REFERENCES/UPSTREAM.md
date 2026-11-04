# UPSTREAM RESEARCH / CURRENT VERIFICATION 04.11.2026
The following are **external project claims**, not a statement of compatibility with Ungravity. Recheck exact file + license before integrating. Do not copy third-party assets or code without license compliance.

| Candidate | Status on 04.11 | Link | Relevance / risk |
|---|---|---|---|
| Better Cubic Chunks 0.1.1 Beta | Fabric & NeoForge Minecraft 1.21.1, **All Rights Reserved**, 16³ cubic generation/save/render, 32768 default height claimed, early beta | https://www.curseforge.com/minecraft/mc-mods/better-cubic-chunks | main *experimental* Cubic backbone; no code copy |
| Better Distant Horizons | Fabric/NeoForge 1.21.1, 3D LOD in combination with Better Cubic Chunks, official page says replace normal DH | https://www.curseforge.com/minecraft/mc-mods/better-distant-horizons | optional 3D distant LOD, compatibility check |
| NoCubes | 1.21.1 Fabric supported on Modrinth, LGPL-3.0; client-side, marked development/unstable | https://modrinth.com/project/nocubes | inspiration/experiment for mesh; not a server-collision solution |
| Valkyrien Skies unofficial port | Minecraft 1.21.1 compatible listed, LGPL-3.0; independent port | https://modrinth.com/project/GE25t0ct | ship-engine comparison only; test specific jar |
| Eureka unofficial port | Minecraft 1.21.1 support listed, Apache-2.0 | https://modrinth.com/project/eureka-unofficial-port | ship control baseline; not Create clone |
| Official Valkyrien Skies | main 1.20.1, not direct official 1.21.1, compatibility wiki OpenGL3.3+ baseline | https://valkyrienskies.org/wiki/Compatibility_list | official documented constraints vs Intel GL3.2 test machine |
| Original CubicChunks | 1.12.2 Forge MIT / earlier versions | https://modrinth.com/mod/cubicchunks | architectural reference, not drop-in for 1.21.1 Fabric |
| Historic Ungravity PR #9 | supplemental 16³ snapshots, full Cubic world absent | https://github.com/terminaldetector/ReforkDescnt/pull/9 | must supersede old substrate after parity |
| Recovery branch 24 Oct | Workshop/fast weapon fixes | https://github.com/terminaldetector/ReforkDescnt/tree/codex/ungravity-cloud-recovery-20261024 | regression audit, not current JAR proof |

### Compatibility warning
Even if BCC description claims Sodium/Iris/Immersive Portals integrated, it is beta and may conflict with DRMD custom Mixins and GL3.2. NoCubes client rendering does not guarantee collision parity, and an unofficial 1.21.1 VS port does not grant known compatibility with Cubic backend. Run isolated matrices before choosing production dependencies.
