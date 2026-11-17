# Native S16 observation recorder — source candidate

This test-only observer writes `ungravity.benchmark-observations.v1` for the [S16 context collector](BENCHMARK_CONTEXT.md). It records actual completed frame intervals, enclosing integrated-server tick spans, JVM heap samples, process/GL strings and client options. It does not spawn, move or tick actors, edit blocks, change options, close screens or stop the client.

**17 November checkpoint: source only; not compiled or run.** The Java/native lease belongs to the coordinator. Cached named Yarn mappings contain the selected methods/descriptors; this is not compilation, mixin-application or measurement proof. No FPS/MSPT numbers, hardware benchmark, new native pass, full S16 acceptance or release acceptance are claimed.

## Files and opt-in boundary

The change consists of `NativeBenchmarkRecorder`, three new owned probes (`BenchmarkBootstrapProbe`, `BenchmarkFrameProbe`, `BenchmarkServerTickProbe`), their three client-only mixin entries and this document. Production code, PS1 code and shared frame probes are unchanged. Production JAR classes do not include this source set.

The flag is `-Ddrmd.gate.benchmark=true`; without it, the recorder does not register callbacks, allocate sample buffers or write files. The bootstrap probe runs before other isolated bootstrap cancellation. Its modes are explicit:

- `standalone` (default) registers the observer and cancels the ordinary `CubicClientGate` initializer. This prevents its default terrain-editing fixture from running. The observer itself does not create an encounter or control the player. The separate dedicated-server gate remains disabled unless its own unrelated server flag is explicitly enabled.
- `sidecar` registers the observer and lets the selected existing client gate initialize. That gate retains its own behavior. Scenario labels and world readiness do not establish that another fixture has finished setup; a short gate may stop before the requested observation window completes.

All three mixins are in the **client** list, including the one targeting `MinecraftServer`. Server durations therefore cover only an integrated server in the same client process; a dedicated server is not observed and does not load the client-only recorder.

## Configuration and output

Pass these JVM properties to the existing isolated native launcher. This source candidate is not an instruction to start another Java process outside the coordinator's lease.

| Property under `drmd.benchmark.` | Default / supported value |
| --- | --- |
| `runId` | Required actual ID: 1–80 characters, starting alphanumeric, then letters/digits/underscore/dot/hyphen. `REPLACE_` placeholders fail. |
| `scenario` | Required nonblank label, up to 200 characters without control characters. It is an operator label, not an actor counter. |
| `mode` | `standalone` or `sidecar`; default `standalone`. |
| `warmupSeconds` | `5`; finite 0–600 seconds. |
| `sampleSeconds` | `30`; finite positive duration up to 600 seconds, at least one nanosecond after conversion. |
| `maxSamples` | `65536`; integer 16–262144 per series. |
| `output` | `benchmark-<runId>.json`; a JSON basename, not an arbitrary output path. |

Output is beneath `<gameDir>/benchmark-observations/`. Existing output is rejected and the final writer uses `CREATE_NEW`; it never replaces old proof. A failed/truncated file is retained for diagnosis, with `BENCHMARK_RECORDER FAIL` on stderr. There is no OK marker or success gate. A non-increasing UTC window is refused rather than repaired with invented timestamps.

Three preallocated `long[]` buffers use at most about 6 MiB. The raw series are streamed after capture closes; they are not copied into another large collection or JSON tree. Capacity and bounded text fields keep the encoded document below the collector's 64 MiB input limit. No JSON serialization, file I/O, GL access or large array copy occurs under the shared sampling lock. Buffer references transfer at closure and no producer writes after that point.

## Clock and window definitions

`System.nanoTime()` controls warmup, target duration and all duration samples. UTC `Instant` timestamps describe the actual window only. Both client and integrated-server threads use the same process monotonic clock.

Warmup starts only with an actual client world/player, no current screen and no pause. Losing readiness before sampling resets warmup. The start snapshot records actual client coordinates/dimension, mirrored flight/physics state, current profile/options, JVM maximum heap and render-thread GL strings. Start-up/loading/menu frames are not relabelled as benchmark samples.

`BenchmarkFrameProbe` observes `MinecraftClient.render(Z)V` returns. A frame sample is the difference between consecutive completed outer client-frame returns. It includes intervening idle, frame limiting, vsync/backpressure and observer overhead; it is not just CPU world-render work or a GPU timer. Recursive mirror/portal world renders are inside that outer method and do not create additional samples. The first return establishes a baseline and supplies no invented interval.

`BenchmarkServerTickProbe` observes the enclosing `MinecraftServer.tick(BooleanSupplier)` method from HEAD to RETURN. This includes that mapped method's actual work rather than a nominal 50 ms target or selected Fabric callback counts. It excludes server-loop sleep and work outside the mapped method, including any integrated-server override wrapper. Only spans from the actual integrated-server identity that start at/after the measurement boundary are accepted; a tick crossing warmup is excluded.

Heap samples use `Runtime.totalMemory() - Runtime.freeMemory()` and `Runtime.maxMemory()`. A negative/inconsistent snapshot is omitted and explicitly marks a partial capture; it is not clamped to a plausible number. Non-positive clock intervals likewise produce a partial diagnostic.

The deadline is checked at a real completed frame. An overlong final frame is retained and the actual closure time is recorded; durations are not clamped to the requested window. At closure the state is frozen with its actual monotonic/UTC endpoint, after accepted server/heap observations. Serialization occurs afterward. This callback-based observer cannot interrupt a stuck game loop; the native runner's external timeout remains necessary.

Sampling ends on the target duration, capacity, invalid clock/heap span, lost readiness, world switch, profile/distance/physics-option change, or client shutdown. A partial capture preserves the real samples it obtained. The configuration/context is the start snapshot; movement during a flight scenario is expected and does not change that snapshot into an end-position claim.

## Explicit availability and capture status

Supported available context comes from the running process, not launch flags or staged mod filenames. GL vendor/renderer/version are queried on the render thread. Recognized software renderer strings (llvmpipe, softpipe, SwiftShader, software rasterizer) yield `gpu_hardware=software`; other strings yield `unknown`, never an invented physical-GPU attestation. Loaded Fabric/Sodium versions do not assert that a particular renderer path was used.

Seed, loaded-code source/JAR identities, actor/cohort counts, authoritative loaded-cube count, mesh queue, draw calls and GPU timer samples remain **null with specific reasons** in this increment. A surface cache is not counted as loaded cubes; CPU frame intervals are not GPU time. Client view/simulation values are the observed client options, not proof of the effective server streaming/ticking volume. The collector still performs the separate source/build/JAR/staged-mod receipt binding and reports missing loaded-runtime-mod evidence.

The existing schema has no arbitrary status property. The first `known_issues` entry is the explicit machine-readable string `capture_status=<status>`:

| Status | Meaning |
| --- | --- |
| `target_window_complete` | Target elapsed window reached; this says nothing about performance, full coverage or acceptance. |
| `truncated_sample_capacity` | A bounded buffer reached capacity; subsequent spans may be absent before the next client frame closes capture. |
| `partial_client_stopping` | Client lifecycle ended before a normal target-window closure. |
| `partial_readiness_lost`, `partial_world_changed` | Pause/screen/world/player transition interrupted the scenario. |
| `partial_options_changed` | Presentation, hybrid, synchronized physics or distance options changed within the capture. |
| `partial_invalid_clock`, `partial_inconsistent_heap` | An invalid observation was omitted and requires investigation. |

If the client stops before sampling begins, window and raw arrays remain unavailable with reasons. An empty array or zero percentile is not used to hide missing observations. The collector accepts the schema, derives percentiles only from actual supplied raw arrays, preserves these status/issues and still sets performance/release acceptance false. Per-sample timestamps and complete time-coverage proof are not implemented in this schema.

## Pending validation

Before any observed values can be reported, the coordinator must compile/remap the cubiccheck source, validate these exact mixin targets on the real client, run both opt-in and disabled controls, inspect the produced JSON and process it with `collect_benchmark_context.py`. Verify the first-frame exclusion, integrated tick identity/boundaries, pause/options/early-stop/capacity partial states, fresh-output rejection, bounded memory and CPU/GPU distinction. Dedicated-server/non-client behavior remains excluded.

Current source-only read checks are retained locally under `work/native-benchmark-recorder/`: named mapping lookup and exact schema-field/mixin inventory (29 context fields, seven raw series and three unique client-only probes). The existing **20 Python consumer tests passed**; they do not execute this new Java writer. These are metadata/source/consumer checks only; no new Java test, client measurement, actor stress run or benchmark acceptance has occurred.
