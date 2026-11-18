# Recovery and checkpoint rules

Latest user instruction (18 November, after snapshot 9226e7a0 delivery): publish the current work and the remaining requirements to GitHub `main`; the user will configure the next task afterward. This explicitly resumes GitHub publication and supersedes the earlier local-only pause below. Preserve existing branches/history, use a normal non-force push, retain compiled snapshot/source-specific validation, and provide an actionable remaining-work brief. Do not create a scheduled automation on the user's behalf. Start the next task from `docs/v8/REMAINING_WORK_2026-11-18.md`, the canonical task/packet ledgers and both referenced primary specifications.

Current PS1 clarification (18 November): PS1 is the shader/render pipeline embedded in the mod. Optional coarse model variants are one asset setting and do not establish completion of the PS1 render requirements. Continue the built-in world presentation and material shader paths alongside MASTER v8.

Current user steering (18 November): prioritize working native portal traversal, including 6DoF and physical constructs. Implement concrete features across the two latest primary specifications (MASTER v8 and ENGINE B / PS1). Keep validation focused on actual changes; do not replace implementation with repeated diagnostic-only runs. Retain real first failures and fix their actual causes. The user separately authorizes posting ready builds, important actual screenshots and changelogs to Telegram `@abadonsoft`; this does not resume GitHub/source publication.

Current user instruction (6 November): development is local only. Do not push, publish, upload to GitHub or trigger Actions. Preserve local source/evidence checkpoints and continue both master v8 and Dynamic Boarding/BlockBody work. This supersedes the historical publication instructions below until the user explicitly resumes publication.

Current turret direction (8–9 November): use Create-style player-built mechanisms with separate weapon, bearing and drive blocks, on the existing BlockBody/joint solver. Do not continue adding UFO-specific turret recipes/controllers as the main implementation. This is an assembly principle, not authorization to add a Create dependency. Existing UFO proof remains regression evidence; generic construction, world anchoring and reusable weapon control need their own acceptance.


The user works in the cloud. On 27 October 2026 they resumed GitHub publication and explicitly
requested that the compiled mod be committed directly to the repository. The 25 October pause
is superseded. Publish each verified installable JAR under `dist/releases/<version>/`, with
its exact tested source SHA, SHA-256, installation notes and local verification report. Update
`dist/README.md` to point to the latest verified package. Keep older versions and local backups.
Source, binary and evidence commits are distinct: never imply that packaging changes were
the source revision used by an earlier test run. Check GitHub Actions separately from local proof.

- Read `docs/recovery/RECOVERY.md` before continuing recovery. The supplied historical handoff
  is context; current user instructions take precedence.
- Keep coherent milestones in Git on the recovery branch. After the user resumes GHA, push
  them to trigger `build.yml`.
  Do not treat local files or an unpushed commit as a durable checkpoint. Verify that the
  workflow exists and its source artifact was actually saved.
- Preserve the original integration branch and PR #9. Do not force push, rewrite history,
  merge, or remove branches/releases as part of routine recovery.
- Each milestone records source SHA, report, test logs, fresh client frames, and (when verified)
  the installable remapped Fabric JAR with SHA-256. Evidence from another run is not a pass.
- `scripts/checkpoint_report.py` gates distribution on the fresh unit/GameTest/client evidence.
  Run `python3 -m unittest discover -s scripts -p 'test_checkpoint_report.py' -v` after changing it.
- Run relevant Gradle checks for production changes and retain the first failure. Distinguish
  network/tooling blockers from gameplay failures; never weaken assertions to green a run.
- The recovered baseline is `1.1.11-collapse-alpha`. The MHT's 1.1.12 changes were not in that
  saved commit; do not claim they are restored until reconstructed and independently verified.
- GHA artifact retention is 90 days; Git source history remains the primary durable source.
