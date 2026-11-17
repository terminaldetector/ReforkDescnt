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
