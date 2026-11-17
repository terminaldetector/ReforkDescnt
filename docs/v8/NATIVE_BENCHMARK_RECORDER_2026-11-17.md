# Native recorder — enabled instrumentation checkpoint

The recorder compiled/remapped successfully and one **clean enabled 1s/1s sidecar smoke passed** on test source `9426a6549fc91abc8c7e060a2eef9f184742dc3d`. This report/evidence checkpoint changes no game or observer source and does not rerun a full unit/GameTest suite.

The running main JAR matched retained source583's tested bytes, `cf3701b529b21633a8a3e910e6755d006d97d6349e8938d7701c82671027138d`. The rebuilt observer was `cb1056d89d1435a5211c5a1c1728934fde2caf92c3c85e28016e2c5ff4bce70a`. This is instrumentation evidence on that older main JAR, not a claim that the coordinator's later combined game was retested.

## First run: wrapper failure retained

The first native run wrote actual `target_window_complete` observations (8 frame intervals, 14 tick spans and 8 heap samples) and the existing five-scene mounted gate completed. The local shell runner was edited while it was executing. After the client closed, Bash resumed at a stale file offset and failed on leftover heredoc text with wrapper exit **2**, before writing the native exit receipt.

**The original native exit remains unknown. Normal shutdown logs do not establish exit code 0, and no clean-exit native PASS is assigned to this first run.** The original pre-run shell file was not snapshotted. Its original execution bytes/hash are unrecoverable as original proof. The saved shell file is explicitly the modified post-failure version; no reconstructed runner is presented as original evidence. Raw schema/provenance validation passing does not erase the wrapper failure.

The [first-failure archive](EVIDENCE/native-recorder-9426-first-wrapper-failure.zip) preserves the first build/stamp, actual JARs, raw observations, independent collector output, native log, five PNGs, failure classification and original-byte limitation. It also contains the immutable committed source patch, not a reconstruction of the lost untracked shell-runner input.

## Clean retry: exact copied inputs and early child exit

The retry used a fresh copied disposable world and unique run ID `9426-enabled-retry1`, with the same verified JARs and source. It ran the readonly copied Python runner and launch JSON. Their actual pre-run bytes exist in the proof archive and their hashes remained unchanged after execution:

| Input | SHA-256 |
| --- | --- |
| Executed runner snapshot | `1946e64f61de6d676f1cf4e09e519e59174fad3356de3eea56e13ed5ab56fc64` |
| Executed launch snapshot | `79d378df7b7021ebeb8927df9bc41ed070f858b2214616498e93c3c7d379c625` |
| Snapshot manifest | `6b39ccd427f85e1590d144baab86bb8813d31107eff4428647e26e275af74b0b` |

The child receipt was persisted immediately after `wait`, before postchecks/hashing/Xvfb cleanup: **returncode 0, timed_out false, forced_kill false**, at `2026-11-17T11:53:41.884455+00:00`. The copied runner also exited 0. Before/after execution-input receipts match. The existing mounted gate completed `connected-off`, `laser-fire`, `rocket-fire`, `fire-off-reversed`, `bearing-detached` and wrote five fresh PNGs. No new manual visual review is claimed.

The recorder's observed warmup was **1.052984478 seconds**, with window **11:53:14.098586725Z → 11:53:15.121478719Z** on 17 November. Raw arrays contain **6 positive finite frame intervals, 12 positive finite enclosing server-tick spans and 6 valid JVM heap samples**. The output status is `target_window_complete`; strict schema, units/types, heap bounds, artifact and immutable build-input checks passed. Raw observation SHA-256 is `3c18980d305cf2818922ac0eab0078891f489fb0e80e3d8b53ac7b069563e107`.

The observation was on actual `NATIVE_CUBIC`, client `native` presentation, hybrid enabled and synchronized expanded physics enabled. GL reported **llvmpipe (LLVM 19.1.7, 256 bits)**, Mesa 25.0.7, OpenGL 3.3. It is classified **software**, not physical Intel HD 4400. Runtime metadata reported Java 21.0.12.1, Fabric Loader 0.19.5 and Sodium 0.8.13+mc1.21.1. Distances are client options; coordinates are a client start snapshot.

These are short instrumentation/fixture timings. Loading, world generation and gate work occurring inside the window are included. There is no FPS/MSPT threshold, controlled actor cohort, comparative optimization claim or performance acceptance. The schema lacks per-sample timestamps; full time coverage remains unverified.

## Availability and remaining work

The following context remains **null with reasons**: seed; runtime-attested source/JAR hashes; requested/active/moving/rendered actor cohort counts; ragdolls; rigid bodies; authoritative loaded cubes; mesh queue; draw calls. The separate collector verifies source/build/artifact/staged-outer-mod receipts; this does not supply a loaded-runtime-mod inventory or make the runtime source/JAR fields observed.

Raw GPU-time, loaded-cube, draw-call and mesh-queue series remain null with reasons. CPU frame duration is not substituted for GPU time or a surface cache for backend cubes. No ordinary-full-suite, standalone, disabled-recorder, capacity16, pause/options/early-stop, long-flight, 32–1024 actor, physical-GPU, endurance or full-S16 acceptance follows from this smoke. Performance and release acceptance flags remain false; no task/ledger status is promoted.

## Helper controls and evidence

[Exact helpers/control receipt](EVIDENCE/native-recorder-9426-helpers.zip) contains the actual clean-retry snapshots, preparation/staging helpers and four Python-only controls. The controls passed: nonzero child exit retained, timeout retained, broken wrapper stdout retaining the exit receipt, and preparation copying/hash-binding real input bytes without starting a child. These use synthetic Python children, not Java or Minecraft. The structured receipt binds actual test IDs, 4 tests/0 failures/0 errors/0 skipped and unchanged helper/test hashes.

| Archive | Bytes | SHA-256 |
| --- | ---: | --- |
| [First wrapper failure](EVIDENCE/native-recorder-9426-first-wrapper-failure.zip) | 3649429 | `5706756a5f1af83d3f95bc89ac8513ecb94337af86046e934c08da0c3236a853` |
| [Clean enabled retry](EVIDENCE/native-recorder-9426-enabled-retry1.zip) | 957313 | `cfb96b793eea4ad1826585ff1df59828c649e1f517d1dda932575d091e5a6e40` |
| [Helpers and Python controls](EVIDENCE/native-recorder-9426-helpers.zip) | 19376 | `a784ee0ec5d7b5e39b75d91e374656552f116224acaadf7c2424ef710de3e69f` |

All archive payload hashes/member coverage and ZIP CRCs were rechecked. Embedded local `work/...` checkpoint references map to the copied tracked archives above; the bytes/hashes are unchanged. The clean archive references the first archive for actual JAR/source bytes and does not duplicate them. A separate machine-readable [checkpoint receipt](EVIDENCE/native-recorder-9426-checkpoint.json) records exact scope and locations.

The closed retry save was preserved under exclusive `session.lock`: all **102 files and 29 directories**, every SHA and ZIP CRC rechecked before reclaiming only that disposable copy. Its [receipt](EVIDENCE/native-recorder-9426-closed-save-receipt.json) references local archive `work/native-benchmark-recorder/checkpoints/9426-enabled-retry1-closed-save.zip`, SHA `030fe007c8bb01d59f2756a67894d2d2d3801e940953c391a7ee301b8da079a6`, 1360901 bytes. Original logs/frames/JARs/inputs and the original seed world were untouched. This is byte preservation, not an actual restored-world reload test.

The original implementation proposal remains in [NATIVE_BENCHMARK_RECORDER.md](NATIVE_BENCHMARK_RECORDER.md); its source-only section describes the pre-lease checkpoint. This lane launched no further Java/native process while packaging this evidence.
