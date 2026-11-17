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

## First charged cross-bank negative

Test-only source `4aff9c5d0cbf32b00c7ddecfe39640f00de5cf73` appended
`quickPrimaryFallbackTapCannotReleaseOrDuplicateAnExistingSecondaryCharge`;
all earlier nine methods/helpers, production `347cf76` and the native driver
were unchanged. The first separately granted focused run compiled and closed
exit **1**: **9 PASS / 1 FAIL**.

The new real-Fusion case first confirmed the original RMB charge was quiet,
then received `2→3→2`. It failed because actual projectiles appeared while the
original secondary button remained held. The first assertion did not record
the precise premature projectile count, so it does not prove a specific number
of duplicate shots. It does reproduce a premature same-stack charge release;
the old nine controls all continued to pass. No fixture exception, retry or
threshold/assertion change occurred. The lease was returned at first close.

Bundle: `EVIDENCE/drone-charge-bank-negative-4aff9c5.zip`.
SHA-256: `ab8a9a19e79fd464910d870fe417222b853a49315c9027d0f9c354a02a9fb5bc`.
All 139 payload hashes and ZIP CRC were verified, including source, actual
compiled classes/focused manifest, raw run/input/exit/XML/helper receipts and
all 112 closed-world files (8,654,492 bytes).

The authorized correction must retain an already active same-stack charge on
its physically held bank, consume competing requests without replay, stop
genuinely released or replaced charges before another use, and honor the real
cooldown resulting from that stop. Distinct stack banks remain independent.
The unchanged ten-case positive and native route are still open here.

## First corrected ten-case positive

Exact source `5f8eae13817256fdda4aebad7a57383877feaae5` passed all
**10/10 unchanged cases**, exit **0**, in its first separately granted positive
run (48 seconds). Production correction `da3a5d3` prefers a valid already held
same-stack charge's bank, consumes the competing request, and stops genuinely
released/replaced charges before checking a new use's real cooldown. Different
stack banks retain independent requests. No hold-age, TTL, energy or fire-rate
rebalance was made. The Java lease was returned immediately at first close.

Bundle: `EVIDENCE/drone-charge-bank-positive-5f8eae1.zip`.
SHA-256: `a943607f4b669749a94c98056f9b857ad1b8a0670aba352b8ff26e863f423d88`.
All 135 payload hashes and ZIP CRC were verified, including unchanged test
source, actual compiled test/arsenal classes, focused manifest, first raw
inputs/log/XML/runner/exit and all 108 closed-world files (8,670,648 bytes).

The separate real-client six-scene result on the same exact source is recorded
in `GRAVITY_NATIVE_INPUT_ACCEPTANCE_2026-11-17.md`. Combined ordinary-suite
integration remains a separate root-owned gate; neither scoped run certifies
the entire weapon system or the full technical specification.
