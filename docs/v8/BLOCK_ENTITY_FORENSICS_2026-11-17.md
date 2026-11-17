# S01-T05 — Block Entity forensics, 17 November 2026

The original 26 October packet confirms inconsistent block state / deferred Block Entity data. The producer of that inconsistency and reproduction on the current game remain **OPEN**. This checkpoint identifies the original failure, inventories the relevant code, and specifies the next reproduction. It does not repair a world or close S01-T05.

Source inspection is pinned to **`bfbcee6696ae019152846fe3f0e71910ccd1c481`**. It does not automatically cover later commits. The accompanying [JSON receipt](BLOCK_ENTITY_FORENSICS_2026-11-17.json) contains original log hashes, numbered excerpts and warning positions, 33 source-file hashes, 30 source excerpts, seven constructor/provider chains, bounded textual queries, and cached vanilla method metadata. No Java, Gradle, Minecraft, native client or new gameplay test was executed for this audit. Production code, saves, NBT and central task ledgers were not changed. The earlier all-99 audit remains pinned to its own `4b2e243` baseline.

## Original failure and identity

The primary archive is the user-supplied `UNGRAVITY_MASTER_TZ_AND_LOGS_2026-10-26_v5.zip`, 7,040,188 bytes, 44 entries, SHA-256:

```text
e945e52e462905e91689b5e7e5ae49ba77965b248a7f8aee6b4d1ae48f9e4b73
```

All archive members passed ZIP CRC checks. Each original log member also matched its locally extracted copy byte for byte. The archive contains specifications/evidence, not the source or installable JAR for the failing session.

| Original member | DUMMY warnings / unique positions | Current states reported at those positions | Y range | Largest reported server lag |
| --- | ---: | --- | --- | ---: |
| `latest.log` | 33 / 33 | 30 air, 3 polished deepslate | −51…−2 | 152,216 ms |
| `latest(1).log` | 100 / 100 | 96 air, 3 deepslate bricks, 1 polished deepslate | −48…−1 | 193,148 ms |
| `2026-10-26-2.log.gz` | exact uncompressed duplicate of `latest.log` | same session | same | same |
| `2026-10-26-1.log.gz` and `2026-10-26-1.log(1).gz` | 0; identical to each other | separate startup/shutdown failure | — | no such warning |

There are **133 DUMMY warnings across the two distinct affected log texts**, not 166 independent observations. These are log warning counts; the lag messages do not identify which subsystem consumed the time or establish that Block Entities caused the lag.

For the 100-warning session, `latest(1).log:71–75` identifies **DRMD `1.1.14-audit.4`, Minecraft 1.21.1, Fabric Loader 0.19.5**, `STOCK / ADVANCED`, with all six listed legacy world-generation feature flags enabled. Line 86 says the legacy LevelBuilder was online. This is a historical configuration, different from the pinned source's disabled effective flags. Sodium is present, ImmPtl/DimLib/Iris absent. The full mod list contains no BetterCubicChunks. Original log lines 93–95 report physical Intel HD Graphics 4400 / OpenGL 3.2; those observations say nothing about performance of later cloud runs.

The original file SHA-256 values are:

```text
latest.log     975edd9ee33597299fe39147b64bc80d59767f6f770237594c6a6a3ca5197730
latest(1).log  bff41178a85e659077c364fa2abd8de849c69982434dcb29b5caf8a36844b020
```

Original timestamps below are verbatim local log times. Their timezone is not established. No explicit seed or exact installed audit.4 JAR/source hash is present in this packet. A separate locally available source ZIP labelled `1.1.14-audit.3 / 5dd0d5e` is not the failing audit.4 runtime and cannot establish its implementation.

### Save-time spawner mismatch

`latest(1).log:286–316` records:

```text
[16:29:42] Saving and pausing game...
[16:29:43] Saving chunks for level 'ServerLevel[Новый мир]'/minecraft:overworld
[16:29:50] Failed to create block entity minecraft:mob_spawner
java.lang.IllegalStateException: Invalid block entity minecraft:mob_spawner
  // net.minecraft.class_2636 state at class_2338{x=334, y=-21, z=496},
  got Block{minecraft:air}
...
[16:29:50] Skipping BlockEntity with id minecraft:mob_spawner
[16:29:50] Tried to load a block entity for block Block{minecraft:air} but failed ...
```

Cached Yarn mappings identify the relevant stack as `BlockEntity.validateSupports` → construction of `MobSpawnerBlockEntity` / `BlockEntityType` → `BlockEntity.createFromNbt` → `WorldChunk.loadBlockEntity` / `getBlockEntity` / `getPackedBlockEntityNbt` → `ChunkSerializer.serialize` → server chunk/world save. The JSON preserves the original intermediary stack and mapping notes; unnamed synthetic methods are not given invented names.

**The save exposed a mismatched spawner NBT/state pair already present at that position. No DRMD writer frame appears in this stack.** It does not prove whether the mismatch arose during generation, an overwrite, a copy, an earlier save/load, or another path. The DUMMY warnings have no originating writer stack either. Matching depth/palette or nearby lag is a lead, not attribution.

Three supplied 27 October logs identify runtime `1.1.22-portals.1` and contain no DUMMY/invalid-BE messages. Their exact hashes are in the receipt. Their scenarios, seed and source/JAR binding are not established; they are **not a regression pass or closure** of the earlier defect.

## What DUMMY means and why observation must be careful

Read-only Python inspection of the cached, named vanilla 1.21.1 JAR found the exact warning literal in `WorldChunk.loadBlockEntity`. `ChunkRegion.setBlockState` contains the literal NBT id **`DUMMY`** and a call to `Chunk.addPendingBlockEntityNbt`. This is vanilla's deferred creation marker, not evidence of a missing DRMD `BlockEntityType` registration. The real `minecraft:mob_spawner` NBT failure is a separate observed form of state/NBT mismatch.

The receipt includes the metadata reader's exact source, its SHA, the cached JAR/mapping/class hashes, and selected method call sites. This is inspection of untransformed cached vanilla code, **not** proof of the runtime BetterCubicChunks transformation or execution of a particular branch.

Two lifecycle details matter to a useful reproduction:

1. `WorldChunk.getBlockEntity(pos, CreationType)` accesses/removes pending NBT and calls `loadBlockEntity` before its later `CreationType.IMMEDIATE` check. `CHECK` therefore does **not** mean pending NBT cannot be consumed/materialized. A diagnostic that first calls `getBlockEntity(CHECK)` can change or hide the condition it intends to inspect.
2. The inspected `WorldChunk.removeBlockEntity` body removes materialized entity/ticker data, while `ProtoChunk.removeBlockEntity` touches both materialized and pending maps. This difference alone is not a current DRMD defect: actual caller, state-replacement callbacks, hydration order and backend transformations must be traced before attributing a stale pending entry.

Future instrumentation must observe raw pending data without materializing it. Clearing invalid NBT or replacing its state would destroy the evidence and is outside this work.

## Pinned source inventory

Paths and line numbers in this section refer to the pinned revision, under `src/main/java/com/terminaldetector/drmd/` unless stated otherwise.

| Entry/path | Confirmed static behavior | Boundary of the finding |
| --- | --- | --- |
| `entity/ModBlockEntities.java:21` | Seven registered types: soil, gravity generator, locator core, carved, mirror, charged mirror, portal panel. Each registration names the matching allowed block and constructor. Each block provider passes `(pos,state)` to that constructor, which passes the matching type to `super`. | Static consistency of all seven chains; no fresh runtime factory/network/save acceptance. No missing registration established. |
| `DescentMod.java:60` | Config loads before generator registration. World blocks register before custom BE types. | No constructor-order failure demonstrated. |
| `world/access/ServerBlockEdits.java:31` | Loaded-only snapshot reads state and, for BE-owning states, requires a nonremoved, position/type-compatible entity before capturing identifying NBT. | Uses `CHECK`, which can hydrate pending data. A state without a BE returns null entityData; this is not a raw pending-map integrity scan. |
| `world/access/ServerBlockEdits.java:54` | Server/thread/border/availability/conflict/budget checks precede state/NBT ownership writes. Normalization checks matching provider, supported state, NBT type/position and lossless round trip; callbacks have a restricted whitelist and explicit portal-pair adapter. | Protects this API. It is not the implementation of every generator, explosion, vanilla placement or direct block-state writer. |
| `world/access/ServerBlockEdits.java:109` | Ownership write detaches an existing entity, changes state, and reconstructs validated entity data when supplied; transaction readback/rollback and publication follow. | No current lazy-pending-NBT failure or lifecycle closure inferred from the static code. |
| `world/portal/gun/PortalProjector.java:119` | Panel data is built from actual `PORTAL_PANEL` state and matching entity; owner-pair transaction is explicit. | This does not explain historical vanilla spawner data. |
| `world/contraption/BlockBodyEntity.java:609`, `BlockBodyWorldAccess.java:41`, `mechanism/MechanicalBearingSystem.java:95` | Sparse assembly rejects unsupported blocks and non-null snapshot entityData. Local placement explicitly rejects BE-owning states. Local hull data describes block states; it does not transfer vanilla BE NBT into the hull. | Not a full functional-BE-on-moving-hull implementation or proof against pre-existing invalid pending data on an inert world cell. |
| `world/cubic/CubicWorldSystem.java:125` | Supplemental cube snapshots copy BlockStates into `D6Cube`; historical reads remain separate from authoritative live terrain. | Not a BE owner/copy layer or a target for deleting/replacing world NBT. Native backend parity remains separately owned. |
| `world/store/SurfaceIngest.java:100` | Colour lookup reads `getBlockEntity` for a carved material override; it does not explicitly add pending NBT. | Lazy reads can expose existing inconsistent pending data; no producing transition or historical caller stack established. The separate loaded-height/material fix is not DUMMY closure. |
| `network/ModNetworking.java:433` and custom BE update methods | Construction payload carries weapon id/modules. Soil/carved/charged-mirror/panel updates use vanilla BE NBT/update packets. | A packet containing NBT is not automatically a world-BE placement path. No supplied error stack identifies one here. |

Textual search of pinned `src/main` found one explicit `BlockEntity.createFromNbt` and one `.setBlockEntity` call, both in the guarded ownership writer. There are no matches for direct `new MobSpawnerBlockEntity`, `Blocks.SPAWNER`, `addPendingBlockEntityNbt`, or `setBlockEntityNbt`. These bounded searches do not rule out vanilla internals, external datapacks/mods, reflection, differently spelled calls, or code in the historical audit.4 binary.

The declared `WorldAccessBatchGameTests.wrongEntityTypeCannotProduceDummySavedData` test changes chest NBT's id to `minecraft:furnace` and asserts rejection before mutation. Its name must not be read as a reproduction of vanilla's lazy DUMMY lifecycle, cold generation, save/reload or the historical producer. No tests were rerun in this audit.

### Legacy writer and activation boundaries

`LevelBuilder.set:908–929` retains a direct `WorldChunk.setBlockState` writer and a comment about **43** older DUMMY warnings at depths −22…−32. That comment expressly describes a suspect, not a captured writer; it is not the original 100-warning session. The current guard only logs if the previous *block state* owns a BE. It does not inspect raw pending NBT at an already non-BE state.

At the pinned revision, `LegacyGeneration.retired()` is constant true and `LevelBuilder.register:162` returns before registering its chunk-load/tick handlers. `DescentSession.seedWorld:105` also returns. In addition, `DrmdServerConfig.effectiveFeaturesFor:186` always returns disabled flags, including for saved/configured Advanced choices, and normal init applies these flags before `ModWorldgen` / `ModWorldgen2` registration. Those modules gate chunk-load handlers on the effective macro flag.

This complete activation trace matters: `DescentMod:163–174` still invokes both live-enable methods and `DescentSession.drainSeedQueue:58` has no retirement guard of its own. They do not by themselves establish a normal-init automatic generation bypass because the registration/feature gates suppress that route. Conversely, a single retired seed method cannot stand in for reviewing all independent entries.

The industrial feature type stays registered for compatibility. Its `generate` method and explicit manual generator methods still contain real code. The only `industrial_underground` reference found in pinned `src/main` is Java feature registration; no included resource activation reference was found. Activation by an external/old datapack is not established. Retained manual/registered entrypoints must be classified separately from the disabled normal automatic route.

The world agent's newer two-file retirement work concerns **EnderDragonFight tick/respawn and PortalComplexity catalyst callbacks**, with narrowly scoped routing tests. It is not a producer trace or BE-content/save parity proof. It is not silently substituted for the pinned source here.

## Next reproduction contract — proposed, not run

The original seed, pre-hydration affected save/NBT and exact audit.4 source/JAR receipt are missing from the inspected packet. That limits historical attribution; it does not prevent building a current disposable-world lifecycle test. Coordinate instrumentation/backend ownership with `world_cubic`; no central owner or task status is changed here.

1. Freeze a declared source, main/observer JARs, mod hashes, backend, seed and unique scenario. Use a disposable world copied for the test. Keep stock and actual native cubic runs separate.
2. Obtain valid BE state plus pending `DUMMY` / real vanilla spawner-NBT through actual `ProtoChunk` / `ChunkRegion` generation before materialization. Start with a valid pair. Injecting an inconsistent pair at the outset only tests the error detector and cannot identify a producer.
3. Invoke a real registered/current writer route and separately test reachable manual entries. Use the exact old binary only if one is recovered; do not re-enable retired generation in production for convenience.
4. With bounded, test-only observation, record pending-NBT insertion/removal, original/new state and the **first** incompatible transition: source/JAR binding, thread, writer stack/job, chunk status, cube availability, coordinates, raw NBT id/position, and save/hydration order. Do not preflight through `getBlockEntity(CHECK)`.
5. Compare no-writer/ordinary vanilla and guarded ownership-API controls. Preserve the first failure and original copied save. Save, close and reopen the disposable world; verify exact state, contents, ownership and no duplicate entities. Never clear pending entries to make warnings disappear.

The next useful result is an observed first incompatible transition, or a narrowly defined current lifecycle regression with its controls. Unrelated warning-free logs, a correctly registered mirror factory, or the existing wrong-type rejection test do not supply that result. **Historical defect confirmed; producer attribution, current reproduction and S01-T05 acceptance remain open.**

## Verification of this evidence checkpoint

The local Python receipt check passed **305 checks**: it re-read the original archive, compared hashes/decompressed duplicate groups and every recorded event/excerpt, verified all seven provider/constructor chains and all 33 pinned source-file hashes/30 source excerpts, and compared selected cached method metadata and its embedded reader bytes. This validates the receipt's bindings and arithmetic; it is not a gameplay or performance test. The validator's exact source/hash are embedded in the JSON; the local result receipt is retained under the lane's `work/be-forensics/` checkpoint. Rechecking requires the recorded original inputs and pinned Git objects; their absence is not a pass. No source test was changed and no hardware/performance claim is made.
