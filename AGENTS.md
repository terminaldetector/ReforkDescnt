# Recovery and checkpoint rules

The user works in the cloud. On 25 October 2026 they reported exhausted GitHub limits and
asked to continue here with source code. While this pause is active, keep local milestone
commits and downloadable source/bundle/patch/report backups; do not push or trigger Actions.
The earlier GHA checkpoint policy resumes only when the user asks to resume GitHub work.

- Read `docs/recovery/RECOVERY.md` before continuing recovery. The supplied historical handoff
  is context; current user instructions take precedence.
- Keep coherent milestones in Git on the recovery branch and push them to trigger `build.yml`.
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
