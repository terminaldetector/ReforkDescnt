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

The recovered baseline passed a new complete run on source commit
`0adcfa967f95d21268b4bdcaa7b7d24230451ed2`:
https://github.com/terminaldetector/ReforkDescnt/actions/runs/40079571590 .
Unit tests, GameTests, build, real client, five fresh markers and all 15 required
frames passed. Source, evidence and verified JAR artifacts were confirmed present.
This result verifies the recovered baseline, not the missing later 1.1.12 work.

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

## Reconstruction stage 1: projectile movement and range

`1.1.12-recovery.1` reconstructs client tick motion, a full-range tracked velocity vector,
collision-size-independent visual range, the neutral vanilla overlay UV, and exhaust at
the physical rocket tail. Server damage, speed, collision and lifetime rules are retained.
The added GameTest checks metadata above the standard packet clamp, vector replacement,
and bounded visibility at 100 blocks. It does not establish client image quality; that
requires the separate weapon harness in the next reconstruction stage.

## Reconstruction stage 2: neutral glow, transient FX and mounts

`1.1.12-recovery.2` adds an unlit radial shader without vanilla's damage overlay or
directional colour modulation. Glow quads have one winding through a non-culling layer.
Server-sent, bounded muzzle/impact events remain visible after short-lived projectiles
are removed. Rocket exhaust uses its tail position. Default mounts fit FOV 70/80/90
and remain shared by WeaponCore and the viewmodel; saved override coordinates are not
rewritten. Weapon model orientation uses the full ship basis, including vertical fire.
Unit tests exercise actual default layouts and shared muzzle coordinates. The original
client regression still gates packaging; the dedicated weapon/image harness follows.

## Reconstruction stage 3: independent weapon client

`1.1.12-recovery.3` adds `runShootcheck`, a test-only source set and copied GameTest fixture.
Ten real-client scenes exercise three mesh types against dark/light backgrounds, banked and
vertical fire, short-lived muzzle/impact events and full-speed 8-block/tick motion. Screenshot
pixels must show green near/mid/far shots; FOV 70/80/90 images must show both weapon banks.
The CI distribution gate now requires this fresh weapon evidence as well as the original
five-marker terrain/physics/swarm regression. Publishing 1.1.12 is withheld until this chain passes.

The first weapon run failed the middle-range green-pixel check. Inspection of the preserved
frame showed join chat covering the reticle and projectiles. The isolated harness now clears
chat/tutorial toasts while retaining the HUD required by the weapon renderer, and removes
the disposable world's entry hull after dismount. Colour thresholds are unchanged. CI uses
an explicit Bash shell so a failing client or timeout cannot be hidden by `tee`.

The candidate metadata now uses `1.1.12`. This is a reconstruction from the saved baseline
and the MHT's functional record, not a byte-for-byte recovery of the lost binary. A prerelease
is created only when the full chain passes on that exact source SHA. Its separate assets are
the installable JAR, SHA256SUMS, REPORT.md and screenshots.zip; failed diagnostics never contain
a certified binary. Current test counts must be taken from that run, not copied from the MHT.

Further inspection isolated the far-shot failure to geometry in the copied fixture: clearing
the disposable corridor made the three initial range/colour scenes pass, with actual far-shot
render calls recorded and unchanged pixel thresholds. The following transition exposed a spawn
synchronization race. The harness now waits for all three client entities before starting range
scene timing, pins the fixture camera on the server, and builds its backdrop once. Day/night
provides contrast without repeatedly rebuilding thousands of blocks. Test-only HUD masking
removes the inverted vanilla crosshair from the centre colour sample.

The weapon fixture uses simulation distance 8: its 90-block target must remain inside the
active server volume. The inherited terrain fixture's distance 5 is sufficient for its
nearby bodies, but places the weapon range target at/beyond the active boundary. Terrain
regression settings and gameplay distance preferences are not changed.

Frame captures now execute after actual world rendering, with scene advancement held until
the capture completes. Capturing in a client tick could read the preceding framebuffer when
several catch-up ticks ran before one frame, falsely labelling FOV 80 pixels as FOV 70. The
fast-shot measurement requires at least three newly rendered frames, full speed 8 and at least
24 blocks of observed travel; it finishes as soon as those conditions are satisfied.

The range fixture also distinguishes loaded chunks from entity-ticking chunks: one trace
showed the server's far projectile still at its spawn while the client predicted forward.
Only disposable corridor chunks are force-loaded, and scene timing waits for a real server
tick of all three projectiles as well as receipt of all three on the client.

The same trace revealed a production prediction flaw: the initial reconstruction advanced
client shots forever when their server entity stopped ticking. `ProjectilePrediction` now
limits visual lead to four ticks from the latest authoritative position and resets on each
position packet. Four unit tests cover full speed, a long update gap, resumed corrections,
zero velocity and vertical travel. Server damage, lifetime and speed rules remain authoritative.

The first full reconstructed chain passed on `f080db9` in run 40103268810 (483 unit,
23 GameTests, 10 weapon scenes, five physics/terrain markers, all 128 drones moving).
Manual inspection then found the fast-shot screenshot occluded by the preceding impact
wall. The latest harness removes that test obstacle and requires green projectile pixels
in each measured fast frame in addition to full velocity, three frames and 24 blocks of
travel. `fastPixelsChecked` is a mandatory distribution proof, not a coordinate-only pass.

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
