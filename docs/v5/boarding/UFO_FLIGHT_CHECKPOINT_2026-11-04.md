# UFO free-flight contact checkpoint — 2026-11-04

This is a development checkpoint. Full Master TZ, Dynamic Boarding12 and UFO9 are still OPEN.
No accepted1.1.35 distribution is published by this report. The latest accepted package remains1.1.34-joints.1.

## Exact source and artifact

- Tested game source: `22f3c10dc01a0f3be4d8b91730a965a7045ef145`.
- Branch: `feature/ufo-body-after-1.1.34`.
- Development version: `1.1.35-ufo-dev.1`.
- Installed main JAR SHA-256: `33028ec6b4aa18b18d95763a7b0aa1b7149c37994f1829a9bef07f288d045655`.
- GitHub Actions run: https://github.com/terminaldetector/ReforkDescnt/actions/runs/41864560304
- Source-artifact metadata independently observed through GitHub: artifact13340524562,
  `source-22f3c10dc01a0f3be4d8b91730a965a7045ef145-41864560304-1`,
  739092806 bytes, not expired, digest
  `sha256:df1cd61318ec9a012c2db495ece637b8901aa4f01264e0134cb8061b709f78b1`.
- Workflow-job status still reported queued at the last observation. Artifact availability
  is not a claim that gameplay CI or distribution validation has passed.
- The development-version distribution guard remains active.

## Implementation and local checks

The actual registered `drmd:sky_ufo` uses the existing BlockBody/D6 runtime and shared renderer,
not a second UFO identity or world-block relocation. The existing command remains `/d6 mega ufo`.

Free-flight suit movement now uses the shared moving-hull CCD/contact kernel after hull/joint
physics and before boarding. Thrust remains in the existing flight integrator; the contact
mode adds no walking gravity, field alignment or powered foot friction. Contact impulses react
on the real hull. Nearby authoritative contacts reject ordinary position-packet overwrite.
Prediction history and server command queues are bounded, and missing shape revisions pause
client prediction. A portal releases the obsolete prediction clock without zeroing momentum.

The actual native stall was a late-travel request retaining the previous tick's timestamp.
Current-phase enrollment now refreshes it before processing. The asynchronous regression
defers at END_SERVER_TICK and requires acknowledgment during later world ticks.
Connected pilot enumeration is independently protected against vanilla world-tracking loss.

Local results: **679 unit tests and238 server GameTests passed**. New tests cover thin-hull
CCD at512m/s, moving surfaces, open/rotated doors, equal/opposite momentum, real UFO material,
actual player travel, position overwrite rejection, clock bounds, portal clock reset,
untracked connected players, mode handoff and the real late-deferral phase order.

## Observed installed native runs

Both runs used production Fabric1.21.1 with upstream BetterCubicChunks and the same staged
development JAR, at the native high-altitude lab. These are distinct from Gradle/named-world CI.

Mesa GL3.2 completed all five scenes, with its JSON marker retrieved:

- external-approach;
- moving-bay-entry;
- flight-to-boarding;
- boarded-walking;
- rotating-roof-contact.

Marker values: actualUfo=true, externalEntry=true, actualThrust=true, walkingHandoff=true,
teleportFollow=false,15 roof contacts,127 UFO render calls,110 acknowledged prediction samples,
maximum prediction error0.34198911344663996m. All five fresh PNG files were created.
The moving-bay-entry frame was manually inspected and shows the pilot inside the actual
material UFO interior.

Sodium GL3.3 completed the same five scenes. The launcher returned0 only after requiring
`flight-body-ok.json`; the final roof-contact PASS was observed at16:12:13 UTC.
Observed trace maximum prediction error was0.02672344887009359m. Its full JSON counts and
PNG contents were not retrieved before execution transport became unavailable; do not copy
the Mesa counts onto this run.

The two runs produced ten new local frames. **Those new frames, markers and raw logs were
not committed/uploaded before the worker became unavailable.** This report preserves only
results actually observed through tool outputs. Recover and archive the raw evidence before
treating it as a complete publication checkpoint.

Earlier exact-source UFO interior/full-rotation evidence is durably stored separately in
[evidence/ufo-d4aa411-native/VERIFIED.json](evidence/ufo-d4aa411-native/VERIFIED.json)
and its checksum-verified archive:26 frames on source
`d4aa41101dfe29d7b8a7ef406775586c3f7fffd2`.
That earlier JAR differs from this checkpoint; its results are not relabelled as22f3c10 results.

## Retained first failures and limits

Local failed native fixtures retain their inputs, console logs and crash reports. They exposed
initial phase enrollment, a low-disk-space startup confirmation, accepted-but-unprocessed
commands, and an insufficient side-bay condition. The bay condition now also requires the
pilot inside the actual lateral opening, so a rotating hull cannot fake entry past an idle pilot.
The late-deferral fix resolved the accepted-command stall in both observed successful runs.

The server-test orientation fixture also retained its first exact-quaternion comparison failure
and now compares equivalent normalized rotations. No physical contact tolerance was widened.
The first handoff fixture omitted ordinary movement and erased carrier normal velocity; its
corrected version retains contact-point velocity and executes the actual phase order.

Suit collision geometry still uses the existing upright character shape. Dedicated/latency,
multiple-client, full world save/reload, arbitrary oriented suit geometry and maximum-load
acceptance are not certified by these runs.

## Resume state

At the transport interruption the working tree was clean for tracked files at22f3c10.
Work root: `/workspace/ungravity-v5/ufo-body-physics`.
Successful native directories:

- `build/cubic-client-final-flight-body-current-phase-gl32`;
- `build/cubic-client-final-flight-body-current-phase-sodium`.

Raw console logs: `work/ufo-body/native/final-flight-body-current-phase-*.log`.
Launcher/helper and retained failures are under `work/ufo-body/`.
Finished disposable worlds were preserved in checksum-verified archives; older releases,
source, mods, frames and logs were retained. Byte-identical generated Loom caches were linked
only after checking both file hashes, freeing about345MB without changing their contents.

The next requested command (a full13-scene ship/UFO regression on this latest JAR) was not
confirmed to start: exec transport disconnected before the command could be created.
Do not claim that regression passed. Recover the worker, preserve these ten new frames and
raw logs, rerun the full-rotation regression on22f3c10, and add the external-entry stand to the
ordinary-world CI/distribution gates before accepting a later release.

Functional ControlCore capture, full module/turret/bay/reactor design, expanded UFO interior,
durable escort death reconciliation, full3D combat AI and the remaining Master TZ are open.
The existing compatibility bay roster is not a claim that the final module/bestiary work is done.
