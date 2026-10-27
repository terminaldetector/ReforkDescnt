# WorldAccess — live authority, 1.1.19 candidate

This contract reads the actual loaded backend. It owns no block files, chunk tickets, copied
world state or procedural fallback. A present AIR state and an unavailable cell are different
results. BCC dimensions are selected with the upstream public `CubicDimensions.isCubic(World)`
predicate; the DRMD `worldKind` setting does not select the authority. Native API failure returns
unavailable and logs the incompatibility instead of using the stock/supplemental snapshot path.

Stock worlds require an already loaded FULL column. Native server worlds additionally require
the actual cube's FULL status from public native column metadata. Native clients additionally
query `ClientCubes.isCubeLoaded`. Both paths reject coordinates that do not round-trip through
the actual runtime BlockPos packing. Reads run on the world's owner thread. Metadata getters
use `getChunk(..., FULL, false)`; they never request a new column or cube.

Tool safety now traverses all three axes with a supercover ray. At an edge/corner it checks every
touched cube and shape-neighbour availability. Old X/Z-only guards could read placeholder air in
an unloaded vertical cube. Available stock-world horizontal rays retain the previous policy.
Known space outside the dimension height remains distinguishable from an unloaded native cube.

## Server edits

`snapshotIfLoaded` returns defensive state/NBT copies. `modifyServerAuthoritative` accepts at most
256 unique coordinates and one MiB total old/new NBT (256 KiB per entity). Every expected state
and its complete NBT is checked before any writes. It rejects unknown volume, wrong threads,
wrong entity IDs/positions, lossy NBT round trips and nearby open container screens.

The initial quiet-edit adapters cover basic Block/AirBlock/PillarBlock/GrassBlock and exact vanilla
ChestBlock/BarrelBlock classes. Reactive blocks and arbitrary mod callbacks return an explicit
UNSUPPORTED_BLOCK result; ordinary gameplay retains its existing break/place path. Do not treat
this as universal block-ship assembly support yet. A dedicated adapter is required for fluids,
TNT, falling blocks, other block entities, multiblocks and other callbacks with external effects.

Within supported batches, inventory entities detach before state replacement, preventing vanilla
from scattering an inventory that will be owned by another coordinate. Actual readback verifies
state and NBT, and the bounded transaction restores attempted cells if the write fails. Neighbour
notifications and packets follow commit. Publication failures return NOTIFICATION_FAILED with
`committed=true`, because the committed state cannot be pretended rolled back after publication.

## Cube interests

`watchCube` observes interested cells without loading them. It reports initial availability,
availability transitions and closure, with a per-world session ID. It is not a global stream of
every backend cube. Up to1024 interested cubes/8192 listeners are retained per world; at most64
entries are probed per owner tick. The queue is bounded/compacted, including closed subscriptions.
Callbacks are asynchronous observations; gameplay reads still query live authority.

Budget exhaustion returns an empty registration, so a caller can keep using live reads. Closing
is idempotent. Server shutdown and client-world changes close the relevant subscriptions and
release callbacks. Closed handles use a weak world reference. Observers never insert tickets.

## Evidence so far

557 unit/96 GameTests passed before adding the lifecycle tests. Read-only native source60ee887
verified all15 markers and an unavailable Y8192 query without changing loaded cube count12.
Native source0a33b4a moved a chest from Y2159 to2160, preserving its name and37 diamonds without
scattered duplicates. Bad entity type, stale inventory and an unavailable-cube batch were rejected
before partial mutation. Normal save/shutdown plus read-only reopen retained ownership and NBT.
Full lifecycle/target graphics/regression acceptance is tracked separately as those runs finish.

This is a compatibility foundation, not completion of the v5 worldgen/content, 3D LOD, universal
entity6DoF/ragdoll, combat/city or ship milestones. No original player save is converted.
