# Recorder capacity16: first actual lifecycle control

The first `9426-capacity16-v1` run passes its **capacity/closed-output control**.
This historical recorder-component result is not a run of the latest combined game.
Tested Java source: `9426a6549fc91abc8c7e060a2eef9f184742dc3d`.
Docs/host HEAD: `74722eefa2b8c1d2637e0109052986bbf6783cd9`.

| Matched artifact | SHA-256 |
| --- | --- |
| Main | `cf3701b529b21633a8a3e910e6755d006d97d6349e8938d7701c82671027138d` |
| Separate observer | `cb1056d89d1435a5211c5a1c1728934fde2caf92c3c85e28016e2c5ff4bce70a` |

No rebuild or recorder/physics/game-source edit occurred. All1438 original
Java/build inputs and the old helper bytes remain bound to9426. Actual copied
host wrapper SHA `9439b2087f063f90f5ddec6a3f8282eb483d205d61a0f0bbc730c3f5e88ce84f`,
native executor `1946e64f61de6d676f1cf4e09e519e59174fad3356de3eea56e13ed5ab56fc64`,
and external observer `6352a0991733890e35dbd6c51985cd3601887778649d6fd4a47972b938032fc9`
were frozen before execution. Their actual copied inputs stayed unchanged.

## Actual observations

- Requested warmup1s / window30s / max16 per series, with explicit final
  `capture_status=truncated_sample_capacity`.
- Actual counts: **13 frame intervals /16 enclosing server-tick spans /13 heap
  observations**. All series are bounded; no requirement that counts match.
- Actual warmup1.075192469s. Sampling UTC window
  `14:58:45.487696366Z →14:58:46.742002014Z`,1.254305648s. Frame intervals sum
 1254.295633ms; enclosing tick spans sum445.886009ms. These are different clock
  observations, not equalized into complete window coverage, FPS or TPS.
- First complete5758-byte output was observed at **14:58:46.790779Z**, with no
  pre-existing scene PNG names. Its SHA remained
  `82d8dbc23251597ad4209a2d3b00056fa596bef497dde21b0fd9cbab2eb52c30`.
  The exact first-byte snapshot equals the final original file; inode, size,
  modification and change timestamps remained unchanged through later activity.
- All five real mounted scene PNGs completed **after** that observation, from
 14:58:53.488739Z to14:59:18.592063Z. The host journal retains monotonic ordering,
  each complete PNG hash/CRC witness, and later render-thread gate log activity.
  There was exactly one `armed`, one `wrote`, no producer failure and one output.
- Native child76542 closed **14:59:19.937210Z**, exit0, no timeout/forced kill.
  Native-host, external observer, unchanged v1 verifier/strict collector and
  combined host all exited0. The lease was returned at first close; no retry.

The client continued its unchanged mounted-key/use/sneak gate after capture
closed; it was not stopped by the recorder. Operator-follow teleport and
fixture-supplied joint/shots remainedfalse. Timing counts are not claimed
deterministic across runs; the validated contract is bounded capacity, single
closure/write and immutable closed output under continued client activity.

The raw context is a start snapshot near initial world readiness; the label does
not attest an entity cohort, route or all mounted phases during sampling. GPU
timer/cube/draw-call/mesh/cohort/seed/source-attestation gaps stay explicit
null+reason. Actual software rendering does not attest physical Intel hardware.
This is not a full30second measurement, performance threshold, manual visual
review, dedicated-server, current-game/full-suite, fullS16 or release acceptance.

## Preserved proof

[Parsed receipt](EVIDENCE/native-recorder-9426-capacity16-v1.json) and
[compact proof archive](EVIDENCE/native-recorder-9426-capacity16-v1.zip) preserve
the actual raw samples, strict parsed context, source/build stamp, copied
wrapper/runner/observer/launch, first-output snapshot, event timeline, child and
host exits, original logs, five PNGs, readiness tests and per-member SHA manifest.
All ZIP CRCs and payload hashes were re-read after packaging.

No new binary/save bytes are duplicated: the exact prior9426 source/JAR archive
is referenced with its original hash. Closed disabled and capacity worlds remain
preserved in place; no save/cache deletion or restore acceptance is implied.
The original enabled-wrapper failure/native-exitUNKNOWN, later enabled proof and
disabled zero-emission proof remain separate. No central task status changed;
all development and checkpoints stay local.
