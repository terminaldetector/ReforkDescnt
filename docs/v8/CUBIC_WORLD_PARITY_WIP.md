# Cubic world parity — isolated work, 10 November 2026

World worktree: `experiment/cubic-world-20261109`, based on main `4b2e243`. The main's latest verified gameplay source is `4fede1d`, native observer `8e5111b`. This document does not promote old results to a new complete Cubic gate and does not modify the central task ledger.

The actual backend and loaded-only facade already exist. `CubicAvailability` selects the native world through public BCC dimension metadata, reads already-ready native columns without joining generation futures, checks actual FULL cube state and rejects coordinates that do not round-trip through the runtime BlockPos packing. `WorldAccess` distinguishes unavailable cells from loaded AIR, owns no block storage or tickets, and exposes guarded state/NBT edits plus bounded cube-interest observations. Those systems are preserved.

## Artifact verification

The supplied unmodified artifact is `bettercubicchunks-fabric-0.1.1+mc1.21.1.jar`, 806,910 bytes, SHA-256 `8660f6e18fafaaa1dea2ef344e86188a943b0f3661c65fdcd294f326f3c6e8b6`. Its ZIP CRC passes. Actual `fabric.mod.json` reports `bettercubicchunks`, version `0.1.1+mc1.21.1`, environment `*`, All-Rights-Reserved, Minecraft `~1.21.1`, Java `>=21`, Fabric Loader `>=0.19.5`, Fabric API `*`. Inspection is metadata-only; upstream implementation is not copied or redistributed. The machine-readable receipt is `work/cubic-light/upstream-artifact.json`.

## Scope map

| Task | Current code/evidence | Remaining acceptance |
|---|---|---|
| S02-T01 | Exact supplied artifact/version/license/dependencies/CRC/hash inspected | Keep this distinct from broad interoperability acceptance |
| S02-T02 | Historical isolated seeds261026/271026 and native presets are documented in GATE0_SERVER_RESULT and WORLD_ACCESS_CONTRACT | Full fresh clean-world matrix on current integrated source |
| S02-T03 | Native bounds, actual FULL cube status and independent vertical unload have prior runtime evidence | Reverify broader boundaries/large signed coordinates on current source |
| S02-T04 | Existing loaded-only WorldAccess, defensive state/NBT batch edits, three-axis cube addressing and bounded CubeLifecycle | Migrate remaining consumers; a read facade is not proof of every caller's parity |
| S02-T05 | Prior container item/name ownership, water, block light and opaque-roof-to-AIR skylight checks exist | Transparent replacement, wider BE/fluid/light lifecycle and interrupted-save matrix remain partial |
| S02-T06 | Prior GL3.2/3.3 llvmpipe and Sodium controls exist | Actual Intel HD4400 hardware and broad upstream beta compatibility remain unverified |
| S03-T01 | Active body/portal/nav consumers use WorldAccess and loaded-volume guards; guarded batches reject incompatible reactive callbacks | Audit remaining direct world queries/edits individually; do not replace all gameplay with a generic ownership transaction |
| S03-T02 | Retired legacy auto generation is blocked by `LegacyGeneration.retired()` | Authored structures/biomes need independent cubic adapters and content gates |
| S03-T03 | ServerBlockEdits rejects wrong BE type/position and invalid state/NBT round trips | Old DUMMY/spawner logs do not establish all current producers fixed; retain generator ownership traces |
| S03-T04 | Prior high/negative native travel/unload/reopen evidence exists | Broader current-source cold/seam/flight matrix still open |
| S03-T05 | Old identifiers remain loadable and automatic column/layer mutation is retired | Do not delete supplemental/local-body storage or old save paths without actual parity/migration proof |

Ragdoll's remaining two-dimensional loaded-column guard was identified by the physics agent; that agent owns the adapter correction. It can use the current three-dimensional WorldAccess contract without changing backend ownership.

## First concrete increment

The previous native sky-source repair explicitly covers opaque material removed to AIR. Its change hook, arrival scan and repair each only recognize AIR; clear-glass replacement is documented as unmeasured. Test observer source `7e9d478` with saved-flight fixture acknowledgement `878cce6` adds an isolated six-stage native probe: AIR → IRON → clear GLASS → TINTED_GLASS → IRON → AIR. The roof is at Y2208, with real client/server sky sensors at2207/2191/2175, three fresh actual renderer frames per accepted stage, and an iron-wall brightness witness. The observer only writes its disposable roof/marker through normal world APIs. It never writes lighting arrays/native source metadata or inserts cube tickets. Each stage has a 400-tick bound; failures retain exact light values, camera positions and a fresh failed frame.

Build/launch helpers resolve this worktree, verify both actual candidate artifacts and source/helper input hashes, use the unchanged official stager, and borrow only hash-verified immutable vanilla libraries/Minecraft-only intermediary data. No old DRMD code, save result or success marker is borrowed. First compilation/native execution is queued behind the shared test slot. **No new runtime pass or production skylight fix is claimed yet.** If the native case passes without a change, that is parity evidence, not a fabricated bug fix; if it fails, preserve the first failure before changing production.
