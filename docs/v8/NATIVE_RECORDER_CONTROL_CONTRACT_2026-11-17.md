# Recorder disabled/capacity controls — source-only contract

This adds a **host-side read-only Python observer**, not a recorder/physics change.
The two real controls remain **NOT RUN**. Twenty synthetic Python tests pass;
they do not constitute Minecraft, graphics, performance or S16 acceptance.
The main 99-task ledger remains unchanged.

## Verified gap before implementation

The existing recorder source is frozen at
`9426a6549fc91abc8c7e060a2eef9f184742dc3d`:

- `register()` returns when disabled; producer callbacks also return before sampling.
- Per-series long arrays are bounded. A server-tick capacity hit is closed by a
  subsequent completed frame. Counts therefore legitimately interleave.
- `close()` freezes ownership at `CLOSED`; `CREATE_NEW` protects output identity.
  The recorder does not stop the client. The existing mounted gate owns completion.
- The earlier enabled1s/1s real capture passed only its instrumentation scope on
  the older matched JARs; the first wrapper failure/native-exit UNKNOWN remains
  preserved. Neither result proves disabled behavior or capacity closure.

The six existing Python controls validate properties and immutable snapshots.
The old post-verifier checks one output/status/count cap but does not witness
unchanged output while later client events occur. No actual disabled/capacity
native result was found in the lane's retained readiness/evidence.

No frozen Java class, native executor or earlier v1 helper was edited. New host
tools live under `docs/v8/recorder-controls/` so the frozen v1 policy allowing only
later `docs/v8/` commits still accepts the unchanged 1,438 Java/build inputs.

## Frozen identities

| Input | SHA-256 |
| --- | --- |
| Main JAR | `cf3701b529b21633a8a3e910e6755d006d97d6349e8938d7701c82671027138d` |
| Recorder observer JAR | `cb1056d89d1435a5211c5a1c1728934fde2caf92c3c85e28016e2c5ff4bce70a` |
| Actual copied native executor | `1946e64f61de6d676f1cf4e09e519e59174fad3356de3eea56e13ed5ab56fc64` |
| v1 flag/snapshot contract | `870c65d6d708c77fceb5a432dd2dc56eb2448b187b2874380e5ab1f876d48dbb` |
| v1 prepare/verify helper | `8c3393d6be31c7d151260f2a28b577235b59fd937ac3ad6bd84d696639e06a04` |

These controls use the old matched source/artifacts. They must not be presented
as a run of the latest combined gameplay source or a new full-suite regression.

## Required real disabled control

Fresh run: `9426-disabled-v1`. All recorder properties are absent, including an
explicit `benchmark=false`; the only gate flag is the existing mounted gate.

The external observer starts before Java, polls with a bounded deadline, and
requires no observed output members of any extension, no `armed`, no `wrote`,
no producer failure, actual native child exit0 without timeout/kill and the
unchanged five-scene mounted gate with complete PNGs. It checks final files too.
This establishes zero observed emission in the declared run, **not zero memory
allocation or the absence of every possible sub-poll filesystem event**.

## Required real capacity16 control

Fresh run: `9426-capacity16-v1`; sidecar, warmup1s, requested window30s,
`maxSamples=16`. The existing mounted gate continues and stops itself normally.

The external observer requires:

1. Exactly one complete observation file, correct run/scenario, one explicit
   `truncated_sample_capacity`, the declared30s/16 configuration, positive UTC
   window shorter than the requested window and observed warmup>=1s.
2. Every actual timing/heap series has at most16 values; at least one reaches16.
   A missing integrated tick series is allowed only with its explicit reason.
   Valid interleavings include3frames/16ticks/3heap or16frames/fewer ticks/16heap.
   Do not demand identical counts/timing across runs.
3. Finite positive CPU durations, integer used heap within actual JVM maximum,
   explicit null/reasons for unobserved seed/cubes/draw calls/cohorts/GPU timers,
   and the actual software-renderer classification. No inferred metrics.
4. A first complete-output byte snapshot and identity (SHA, size, inode/device,
   modification/change timestamps), preserved in the observer's own directory.
   Incomplete JSON is never accepted as a capture.
5. At least one **new completed client PNG** whose name was absent when the first
   output completed, plus later observed render-thread mounted-gate log activity.
   PNG framing/CRC/IEND are checked; old frames, empty touches, partial files or
   later log lines alone cannot satisfy this condition. The frozen
   `NativeMountedWeaponCheck` writes these scenes from actual rendered frames,
   with its existing three-frame capture gate, not an external file generator.
6. Unchanged closed-output bytes/identity through later events and clean native
   completion; exactly one `armed` and one `wrote`. Duplicate/disappearing/replaced
   output, producer failure, substituted fixture-only gate, timeout or kill fail.

This tests interleaving-independent **closure behavior**, not deterministic raw
timing values or full-window coverage. Frame interval sums, enclosing server
tick spans and UTC wall duration are reported separately. They must not be
forced to equal each other. CPU intervals are not GPU durations or tick-rate
measurements.

## Execution sequence after separate root grants

No staging or Java execution is authorised by this document. Each real control
still needs its own root Java/native lease. Do not automatically run the next case.

1. Use the unchanged `native_control_plan.py prepare disabled` (or `capacity16`)
   under that grant. It checks the1438 inputs, old matched JARs and fresh copied
   disposable runtime, then creates the actual readonly native-runner snapshot.
2. Run the new observer's `prepare` with that actual `manifest.json`, the case and
   a fresh `closed-output-observer-v2` subdirectory. Preparation copies this
   observer's exact bytes read-only and binds the original/copied launch,
   native executor and all staged mod hashes. Staged mods are not called a
   loaded-registry measurement. External Java property injection is rejected.
   Actual observer/configuration hashes are checked before and after observation.
3. Start **that copied `observer.snapshot.py`**, using its `watch --config` action,
   in a separate host process. Wait for its `ready.json`; no Java is launched by
   the observer. Starting after native log/child-exit creation is rejected.
4. Execute only the unchanged copied native runner with its manifest. It saves
   the real child-exit receipt immediately after wait. The observer neither edits
   launch/source/world/output nor sends client stop signals.
5. Return the Java lease on actual process close. Preserve the observer's result,
   event journal, first output, runner before/after receipts, all original logs,
   gate frames, source/JAR/stamp inputs and first failure. Then run the unchanged
   v1 post-verifier and the strict benchmark collector as CPU-light validation.

The observer polls every0.2s, bounds input sizes and directory fan-out, and stops
observing after270s if no valid close is available. Its timeout fails the
observation; it never terminates Java. The native runner's own240s external
timeout remains unchanged. Do not reclaim the closed runtime while the observer
is finishing its final reads.

## Pure controls and limits

Run:

```text
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s docs/v8/recorder-controls -p test_observe_control.py -v
```

Result: **20 synthetic tests PASS**. They cover alternate count interleavings,
overflow/no actual capacity, wrong close status, missing/invented metrics,
nonfinite spans, bad heap/window, unrelated old frames, missing later render
events, output mutation/disappearance/duplicate emission, disabled unexpected
non-JSON output/arming, incomplete PNG/JSON, native timeout/producer failure,
substituted gates, exact copied bytes, late observer start, changed launch and
external property injection. Synthetic data and PNGs are plainly test fixtures;
they are never saved as measured benchmark results.

No actual disabled or capacity capture, dedicated-server check, physical GPU,
FPS/TPS acceptance, mass-entity benchmark, full S16 completion or release is
claimed. New world/cohort/loaded-cube/draw-call counters remain separate work.
