# START HERE / INSTRUCTION TO CODEX
**This is a handoff; do not treat memory as proof of source code state.** Read `00_MASTER_SPEC.md`, `01_CONTEXT/RECOVERED_BRANCHES_AND_DIFF.md`, `05_EXECUTION/TASKS.yaml`, `06_VALIDATION/REGRESSION_MATRIX.md`, `04_BUGS/ISSUE_REGISTER.md` and `08_EVIDENCE/LOG_COUNTS.json` before code edits.

## Phase 0 — Inventory exact state
1. Inspect current local `git status`, every relevant worktree/branch, PR #9 `codex/d6-spatial-physics-integration`, recovery branch `codex/ungravity-cloud-recovery-20261024`, local fixes and external artifacts. Get binary version and SHA256 for every candidate JAR, map it to source commit.
2. Historical agent fixes **before the conversation of 26 Oct** are mandatory review: gravity torches, ragdoll joint/collision, 128 drone test, Workshop hybrid meshes, client range shots, per-agent UI settings. Explicitly record `PRESERVED/REGRESSED/OBSOLETE/UNVERIFIED` and reason.
3. Preserve current running UFO/BlockBody episode. If there is another active agent, do not touch its files/branch without reconciliation and completed transaction. Request current agent state from artefacts, not guess.
4. Create `STATE.md` and `EVIDENCE/index.json` and populate with actual host OS, Fabric/Gradle/Java, active mods, GPU, JAR SHA. Leave unknowns marked UNKNOWN. Do not fabricate task status.

## Phase 1 — Reproduce core defects
- Clean, comparable Minecraft 1.21.1 singleplayer worlds, vanilla Fabric+DRMD baseline, no external preexisting save changes.
- P0: ship/player proxy stuck requiring lava; P0: world vertical void/ghost columns; server stalls; DUMMY BE. P1: mirror missing surface/target; gravity torch clipped camera; 6DoF hand; invisible guns; bad GUI.
- Reproduce `128 active` / `>=96 moving` test only after baseline stable. Capture screenshots, gamerules, coordinates, seed and recording references.

## Phase 2 — Run independent worktrees
A. `experiment/cubic-world`: world substrate only, ownership of `VolumetricWorldAccess` contract.  
B. `feature/physics-ufo`: existing UFO ownership, gravity field, Ragdoll and player controls in coordination with active agent.  
C. `experiment/render-hybrid-ps1`: near/LOD/PS1 viewmodel, mirror/portal only through explicit shared API.  
D. `feature/warfare-director`: enemy AI/swarm/ordnance/survival on established sample world.  
E. `cleanup/legacy-items`: red inventory slots 6 items, registry ID identification and removal/compat mapping.  
F. `design/planet-world`: assets/levels/factions without production commits before world engine parity.

## Phase 3 — Implementation order and anti-regression
- A must PASS cubic 1.21.1 GATE before production worldgen migration.
- B must PASS exact in-game UFO control, reactor and boarding and preserve previous physics solver.
- C must PASS visual/motion parity and GL fallback. PS1 is presentation only; Hybrid and Expanded Physics remain ON defaults.
- D must run on reproducible, stable world, not require unfinished Citadel generator.
- F is independent conceptual/art content, limited to license-safe source and design documents.

## Tools access
`Loom` availability was stated by author to be **Codex-only**. Do not assign Loom-only operations to another agent; Codex should use available environment and report if Loom unavailable in that task. GitHub connector state and local worktree can diverge; no silent cherry-pick. The work must happen in the actual runtime/build environment.

## State files required after every segment
- `STATE.md`: last tested commit/JAR, completed/pending/blocked tasks, current stage and point of resume.
- `TASKS.yaml`: 99 atomic entries, `status`, `owner`, `acceptance`, `evidence`; only mark completed after real checks.
- `DECISIONS.md`: choice, alternatives, reversible flag, experimental evidence.
- `REGRESSIONS.md`: previous failure/reproduction, test guarding against it.
- `EVIDENCE/index.json`: artifact hashes, logs, captures, test version/time, simulator hardware.
- `BENCHMARKS.csv`: seed, GPU, JVM, loaded cubes/entity count, FPS p50/p95, MSPT p50/p95/p99, memory.

## Commands (adapt to actual gradle tasks, no arbitrary repo mutation)
```bash
git status --porcelain=v1 -b && git branch -avv
git log --all --since=2026-10-01 --date=iso --oneline --decorate
sha256sum /path/to/drmd*.jar
./gradlew tasks --all | grep -Ei 'test|gametest|rendercheck|runclient'
./gradlew test
# Run GameTest and rendercheck ONLY if tasks actually exist, then record exact result.
```

**Definition of Done per issue:** defect reproduced, root cause demonstrated, narrow fix, unit + GameTest + live client depending on class, before/after evidence same scenario, no regression save reload, exact code/JAR SHA. If blocked by beta/upstream/GL, report `BLOCKED`, keep production safe.
