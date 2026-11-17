# Server trigger edges: first negative checkpoint

Test-only source: `ac1de7588f50946ae39097ff3f5856602fe3c630`.
Production `DroneArsenal` and the original six-scene native one-client-tick
driver were unchanged. This first focused stock Fabric GameTest run compiled,
executed nine actual receiver → arsenal tick → registered-item cases and closed
with exit **1**: **7 FAIL / 2 PASS**. Fresh XML, original inputs/source, executed
runner, log and immediate exit receipt were preserved before changes. No retry
or fixture correction was performed.

| Case | First result | Actual failing point or accepted control |
| --- | --- | --- |
| `sameTickReleasedPrimaryTapFiresTheRegisteredItemExactlyOnce` | FAIL | Released `1→0` does not produce the real twin-laser volley |
| `repeatedMixedMasksFireEachDistinctSocketOnceWithAnEmptyRealHand` | FAIL | Released repeated/mixed masks produce neither expected bank volley |
| `duplicateFallbackChannelsAndUnknownBitsCannotMultiplyATap` | FAIL | Released fallback click does not produce its one expected volley |
| `coolingTapIsDiscardedInsteadOfReplayedAfterCooldown` | FAIL | Earlier cooldown rejection/no replay checks pass; later fresh click fails |
| `sameTickFusionTapStartsAndReleasesOnceWithoutALingeringCharge` | FAIL | Released short Fusion click does not emit its actual zero-charge shot |
| `realHeldFusionChargeStillWaitsForReleaseAndGuiStillCancelsIt` | PASS | Existing sustained charge/release and GUI cancellation work |
| `invalidScreensAndSpectatorsCannotRetainOrRevivePendingClicks` | FAIL | Earlier rejection checks pass; later fresh eligible click fails |
| `edgeBelongsToItsOriginalSelectedStackInHotbarAndFlightSockets` | FAIL | Earlier stale-selection/no replay checks pass; later fresh click fails |
| `latestHeldInputExpiresWithoutARefreshAndCannotResumeLater` | PASS | Actual unrefreshed held input expires with the existing six-tick TTL |

The failures reproduce lost released clicks. They do **not** establish broken
cooldown, ownership or selection rejection on the original implementation. The
unchanged assertions also serve as post-fix regressions for those boundaries.
Players are ordinary connected server-player fixtures; projectiles and energy
effects come from registered weapons. No test invokes `Item.use` directly, sets
an arsenal held state, jumps the world clock or patches a weapon return value.
The cooldown case advances the real cooldown manager synchronously; it does not
claim native client timing. The TTL case uses one driver registered at test
entry, with no scheduler reentry. All fixtures remove their own projectiles,
arsenal state, equipment, connected player and embedded connection.

This is server API-layer evidence. The separate `07b2fa8` native negative
already records actual client keys, vanilla selection packets and decoded
press/release in the same world tick. Neither run completes all six native
gravity-tool transactions.

Bundle: `EVIDENCE/drone-trigger-edge-negative-ac1de75.zip`.
SHA-256: `d1fb5945199474d43bbbc522c3fdb8d6fcd160c7f6e64a80ff95c12f8503643c`.
All 113 payload hashes and ZIP CRC were verified, including exact test-only
source/diff, compiled test and arsenal classes, focused runtime manifest,
original logs/XML/exit/inputs/runner, and all 86 closed-world files (8,303,498
bytes). The Java lease was returned at first close before analysis.

Next authorized production change: a bounded three-channel rising-edge latch
with original stack identity, one-time consumption, existing cooldown/TTL and
control ownership, existing sustained firing, and immediate zero-age release
for an already released charged tap. Production correction, focused nine-case
positive result and unchanged native six-scene acceptance are still **OPEN**
at this checkpoint.

## First focused positive: unchanged nine cases

Production source `347cf76c5ea6997f191a909c0806ce27afab3a4d` adds only
the bounded rising-edge latch to `DroneArsenal`. Its separately granted first
focused run compiled and passed **9/9**, exit **0** (49 seconds). All nine test
assertions and the entire native observer/one-client-tick driver were unchanged
from the first negative. The lease was returned immediately at process close.

The implementation keeps three edge bits and three original stack references,
retains the latest held mask, discards invalid selection/ownership/TTL inputs,
and consumes pending edges before cooldown checks. Released charged taps use
the real item's start and immediate zero-age stop lifecycle. Existing held
charge, GUI cancellation and input expiry passed the same assertions.

Positive bundle: `EVIDENCE/drone-trigger-edge-positive-347cf76.zip`.
SHA-256: `cde25af4260ae333a526b39f6a090fe5ee5bb2e065cfbfd0e44ce0296281d527`.
All 130 payload hashes and ZIP CRC were verified. The bundle includes fresh
nine-case XML/log/exit, initial source/runner/input snapshots, actual compiled
test/arsenal classes and focused manifest, exact source archive/production diff,
and all 103 closed-world files (8,791,497 bytes).

This accepts the nine stock server-route cases only. A further charged-weapon
cross-bank overlap (`2→3→2` while one fallback stack is already charging) was
identified during source review and requires its own test-first checkpoint.
No cross-bank charge or native six-scene positive is claimed here.
