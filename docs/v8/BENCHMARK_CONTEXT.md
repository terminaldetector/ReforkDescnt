# S16 benchmark context collector

`scripts/v8/collect_benchmark_context.py` records exact local source/artifact provenance and supplied runtime observations. Missing observations remain `null` with an explicit reason. It launches no Java, modifies no game/save files, and never marks performance or release acceptance.

This is instrumentation preparation for S16-T02. It does not close the 32/128/256/512/1024 actor, physical Intel HD 4400, modern GPU, full flight/LOD or long endurance benchmark requirements. Historical [BENCHMARKS.csv](BENCHMARKS.csv) remains unchanged. The [99-task audit](TASK_EVIDENCE_AUDIT.md) retains its independent pinned `4b2e243` scope.

## Capture workflow

Use Python 3.9+ and local Git. No third-party Python packages are needed. The tool imports the adjacent `audit_task_evidence.py` for immutable Git-object reads.

Generate the supported observation format in a new path:

```sh
python3 scripts/v8/collect_benchmark_context.py template --out work/bench/run-observations.json
```

The template contains no example benchmark numbers. Its `REPLACE_` run/scenario names are rejected until replaced with the actual capture identity. A native observer should write the observed fields and raw arrays into a new JSON file using schema `ungravity.benchmark-observations.v1`. Fields it cannot observe retain their reasons. The collector accepts this schema only; legacy CSV aggregates, old OK markers and undocumented JSON fields are not silently imported.

After the runtime has stopped writing these files:

```sh
python3 scripts/v8/collect_benchmark_context.py collect \
  --repo . \
  --source FULL_40_CHARACTER_SOURCE_SHA \
  --game-jar build/libs/EXACT_GAME_JAR.jar \
  --mods-dir PATH_TO_CAPTURED_RUNTIME/mods \
  --observations work/bench/run-observations.json \
  --build-stamp PATH_TO_RECORDED_BUILD_STAMP.json \
  --out work/bench/run-context.json
```

`--build-stamp` is optional. If absent, source/JAR association is `value:null` with a reason. If present, every recorded `inputs` hash must match the pinned commit's `src/**`, `build.gradle`, `settings.gradle` and `gradle.properties`, and `jars["build/libs/<game basename>"]` must match the supplied game JAR. This verifies a recorded build receipt; it does not execute a build or prove benchmark behavior. `--source` defaults to checkout HEAD, which by itself does not establish that HEAD built the JAR.

Output paths must be new. Existing outputs, input files and historical observations are never overwritten. Unsupported input exits nonzero without writing a result. Preserve the first failure and fix the producer/schema or provenance before retrying with a fresh result path.

## Observation format

Top-level fields are `schema`, `run_id`, `scenario`, `window`, `context`, `samples` and `known_issues`. Unknown fields and duplicate JSON keys are rejected. The run ID identifies one capture and the scenario describes its actor population, route and render/backend combination.

Each scalar context field uses one of these forms:

```json
{"value": null, "reason": "Backend counter is not instrumented"}
```

An observed field has the actual typed `value` and `reason:null`; JSON `false` and seed `0` are valid observations. `UNKNOWN`, `N/A`, `UNAVAILABLE` and `NOT_PROFILED` text sentinels are rejected for version/renderer/backend text fields. Use null plus the precise reason instead. Availability is not inferred from a filename or a scenario label.

| Context fields | Supported values and source expectation |
| --- | --- |
| `seed`, `coords`, `dimension` | Signed 64-bit integer seed from the server world; three finite world coordinates; namespaced dimension ID. No guessed seed or vanilla-height assumption. |
| `flight_state` | Actual observed flight/control state. |
| `runtime_source_sha`, `runtime_game_sha256` | Runtime capture identities, if observed; they must match the collected source/JAR. Missing identities stay null. |
| `java_version`, `fabric_loader_version`, `sodium_version`, `cubic_backend` | Actual running-process versions/backend. Staged dependency metadata does not fill these fields. Absence of an optional component can be recorded as null with a specific absence reason. |
| `ps1_profile` | `native`, `ps1` or `debug`, from the runtime configuration. |
| `hybrid_on`, `expanded_physics` | Actual boolean configuration. |
| `view_distance`, `sim_distance` | Nonnegative integer runtime distances. |
| `gpu_vendor`, `gpu_renderer`, `gpu_version` | Strings from the running graphics context, not the collector host or a launch flag. |
| `gpu_hardware` | Observed classification `physical`, `software` or `unknown`. A software-renderer string such as llvmpipe cannot be paired with `physical`. Classification alone is not physical-hardware acceptance. |
| `heap_max_bytes` | Positive integer JVM maximum heap in bytes. |
| `requested_entities`, `active_entities`, `moving_entities`, `rendered_entities`, `ragdolls`, `rigid_bodies` | Nonnegative integers with the scenario's cohort/measurement definitions. Active, moving, visible and articulated ragdolls retain the definitions in the master evidence protocol. |
| `cubes_loaded`, `mesh_queue`, `draw_calls` | Nonnegative observed counters. Cube count must come from the authoritative backend, not a surface LOD cache. Define draw calls per frame and the queue snapshot point in the capture protocol. |

The observation window is also a nullable descriptor. Its observed `value` has exactly `started_at`, `ended_at` and `warmup_seconds`. Start/end are ISO-8601 timestamps with explicit timezones, end must follow start, and warmup is finite/nonnegative. Any supplied raw sample array requires an observed window. A window by itself does not prove that the raw samples cover its entire duration.

Each `samples` entry uses `values` rather than `value`:

```json
{"values": null, "unit": "ms", "reason": "No client frame observer attached"}
```

| Raw series | Required unit | Meaning |
| --- | --- | --- |
| `server_tick_ms` | `ms` | Complete actual server tick work duration; positive finite samples. |
| `frame_time_ms` | `ms` | Actual rendered-frame intervals; positive finite samples. |
| `gpu_frame_ms` | `ms` | Actual GPU timer observations, if available. CPU frame duration is not substituted. |
| `heap_used_bytes` | `bytes` | Nonnegative integer JVM heap samples. Must not exceed a supplied JVM maximum. |
| `cubes_loaded`, `draw_calls`, `mesh_queue` | `count` | Nonnegative integer counter samples with the definitions above. |

Observed arrays must be nonempty and use the exact supported units; booleans, negative counters, zero frame/tick durations, NaN/Infinity and precomputed percentile objects are rejected. Units are not guessed or converted from undocumented CSV columns. The JSON input is limited to 64 MiB; longer captures need a separately specified streaming/aggregation protocol rather than silent truncation.

For each observed series, the output records count, minimum, maximum, mean and nearest-rank p50/p95/p99 (`ceil(percent * count / 100)`). FPS samples are computed as `1000 / each frame duration`, then sorted independently for FPS percentiles. Inverting p95 frame time would give a different tail and is not used. Missing frame intervals yield unavailable FPS. Raw sample arrays stay in the hashed observation file instead of being duplicated into the result.

Every observed scalar/series summary contains the input path, SHA-256 and JSON pointer. Collector/reader script hashes and the Python version also identify the transformation. The result explicitly retains `sample_coverage_in_window_verified=false`: the current schema has no per-sample timestamps and cannot prove complete time coverage or that a producer measured the correct event. This collector establishes reproducible data/provenance, not authenticity of arbitrary supplied observations.

## Artifact and source boundaries

The supplied game JAR must have Fabric ID `drmd`. Staged `.jar` files are hashed, their outer `fabric.mod.json` IDs/versions are recorded, and the staged game must have the same bytes as the supplied artifact. Duplicate outer IDs, unresolved metadata versions, missing/non-Fabric metadata, nested directory entries and symlinks are rejected. Non-JAR entries are listed as ignored, not reported as loaded.

This is an inventory of **staged outer JARs**, not the effective runtime mod list. The `loaded_runtime_mods` result stays null with a reason: nested JARs, loader-provided modules and actual loading need a runtime observation. A BCC/Sodium JAR being present does not prove the benchmark used that backend/renderer. The collector invokes neither a launcher nor `java -version`.

The source revision is a full locally available Git commit. Committed build inputs are hashed from Git objects; dirty tracked inputs and untracked source paths in the checkout are listed separately without relabelling them as part of that commit. File signatures are checked during hashing and again at the collection boundary; changed artifact/mod inputs and changed checkout HEAD fail collection. These checks are not an atomic filesystem snapshot or protection against a malicious file producer preserving all metadata.

`completeness` lists every missing context/metric and whether a window was observed. It does not promote the benchmark. `gameplay_launched`, `performance_accepted` and `release_accepted` are always false. No threshold pass, physical Intel measurement, new full suite or S16 task completion is inferred.

## Verification on 11 and 16 November 2026

The dedicated synthetic tests cover null/reason preservation; seed zero/false values; raw percentile/FPS ordering; Git/build/JAR receipts; dirty-source disclosure; wrong runtime identities; duplicate/mismatched mods; unsupported/symlink metadata; units/types/nonfinite arrays; missing/timezone-invalid windows; heap bounds; software/physical GPU contradiction; input replacement during collection; duplicate JSON keys; and output preservation/failure behavior. These fixtures contain toy metadata and are not executable game benchmarks.

```sh
python3 -m unittest discover -s scripts/v8 -p 'test_benchmark_context.py' -v
python3 -m unittest discover -s scripts/v8 -p 'test_*.py' -v
```

The 16 November checkpoint passed **20 collector tests plus 19 immutable-audit tests: 39 total**. These are Python tool tests, not Minecraft unit/GameTest/native results.

A separate read-only CLI check used the retained `8e5111b2fbe80d7fa8a99ee7b8884188bb0211f1` runtime and its `native-final-build.json`. It matched all 1414 committed input hashes, game SHA-256 `328307bc4b8db3b41d47f4b1b2bce8de9eb03eb0ccde78d46b9d33dcda0c02a7` and five staged outer JARs (`bettercubicchunks`, `drmd_cubiccheck`, `drmd`, `fabric-api`, `sodium`). All **29 runtime context fields and 8 metric results remained unavailable**; the sampling window remained unavailable. This was a provenance-only tool check, with no game launch or measurements, and both acceptance flags stayed false.

The same metadata-only CLI check passed again on 16 November with the final collector script hash recorded in its output. Local logs/results are `work/benchmark-context/checkpoint-20261116-python-tests.log`, `checkpoint-20261116-cli.log` and `checkpoint-20261116-context.json` in the isolated worktree. No runtime/JAR/evidence files, historical CSV rows, shared ledgers or launch hooks were modified. Connecting a native producer and running the actual matched hardware/scenario matrix remain future measured work.
