# Recovery and checkpoint rules

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
