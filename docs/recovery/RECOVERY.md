# Cloud recovery — 24 October 2026

## Preserved baseline

- Repository: `terminaldetector/ReforkDescnt`.
- Recovery branch: `codex/ungravity-cloud-recovery-20261024`.
- Recovered integration SHA: `963375bf60830461f338ed440dac1aed1d6b9aae`.
- Last source commit before its generated distribution commit: `18feed8`.
- Existing integration branch and PR #9 are preserved without reset, merge or force push.
- The initially opened `work` branch was at `74d500677a1008f42f159a6272258319d25ebe02`;
  it was clean and has not been deleted.
- Recovered version: `1.1.11-collapse-alpha`, Minecraft 1.21.1, Fabric, Java 21.

## Missing progress

The supplied MHT records later local work labelled **1.1.12**, but no commit or binary
for that work was found in the available filesystem or advertised remote branches.
Its claimed 512 unit tests, 22 GameTests and 10 weapon scenes are historical claims,
not results of this recovery. Do not relabel the recovered baseline as 1.1.12.

The lost changes described by the MHT were:

1. Projectile visibility beyond about 22 blocks; client motion between network updates;
   full-speed synchronization beyond the standard spawn-packet velocity clamp.
2. Soft projectile glow, distinct cores, rocket exhaust at the tail, muzzle and impact flashes.
3. Default weapon mounts inside ordinary FOV, shared by visible weapons and actual muzzles;
   saved custom layouts retained.
4. A separate `shootcheck` harness: light/dark backgrounds, bolt/orb/rocket ranges,
   FOV 70/80/90, roll and vertical fire; consistent client/server flight state.
5. A neutral colour shader, avoiding the Minecraft damage-overlay red tint:
   `shot_core.{vsh,fsh,json}`, `ShotRenderLayers`, `ShotFxRenderer` and client registration.
6. `REPORT.md`, screenshots and a package script with source/JAR checksums.

Full SCK editing/model import and an exact DXX Pyro model were not completed.
Restore one verified stage at a time, beginning with the existing baseline's tests.
The original supplied handoff is retained as `HANDOFF_2026-10-22.md` for context;
the user's current request determines scope.

## Every major milestone goes through GitHub Actions

Commit and push each coherent milestone to this recovery branch. `build.yml` runs on
every push, preserves its source archive independently of the game build, and retains
report/log/frame artifacts for 90 days. It does not cancel earlier milestones or
commit generated binaries back into the source branch.

Each run records the full SHA and run URL, unit/GameTest results, real-client evidence,
the current mod version, and a SHA-256 for the exact installable JAR. A failed or skipped
client is never reported as passing. The installable-mod artifact is published only
after the real client, fresh markers/screenshots and JAR contents pass validation.
Artifact retention is finite: download milestone archives for longer-term preservation.

## Local environment observations

- Git fetch works; `api.github.com` returns `Forbidden` through the configured proxy.
  `gh api` cannot inspect or dispatch runs here. Push triggers Actions instead.
- The first Gradle-wrapper attempt without Java proxy configuration failed at
  `https://services.gradle.org/distributions/gradle-8.12-bin.zip` with
  `java.net.UnknownHostException: services.gradle.org` before Gradle or tests started.
- Configure Java to use the inherited session proxy; keep TLS verification enabled.
  A denied dependency is an infrastructure blocker, not a failed gameplay assertion.
- Run links and artifact availability must be checked, rather than inferred from a push.
