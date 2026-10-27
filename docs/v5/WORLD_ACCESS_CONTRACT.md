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
use nonblocking loaded-column accessors (`getWorldChunk` for stock servers, public native
`columnAt` for native servers, existing client arrays for clients). They never join generation
futures or request a new column/cube. The native public getter was inspected for interoperability;
its implementation is not copied into DRMD or redistributed with this checkpoint.

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

557 unit/99 GameTests passed, including real-world state/NBT transactions, live reads and
interest lifecycle tests. Read-only native source60ee887
verified all15 markers and an unavailable Y8192 query without changing loaded cube count12.
Native source0a33b4a moved a chest from Y2159 to2160, preserving its name and37 diamonds without
scattered duplicates. Bad entity type, stale inventory and an unavailable-cube batch were rejected
before partial mutation. Normal save/shutdown plus read-only reopen retained ownership and NBT.
Source44c2fc1 observed UNAVAILABLE -> AVAILABLE -> UNAVAILABLE -> CLOSED for a Y2300 cube
while its parent column remained available at Y2150. The single explicit test ticket expired;
the observer inserted none. The observation ran for1300 server ticks. Sourcec1417ca repeated
the native write/reopen and vertical lifecycle checks after removing blocking column access.
Its native client passed14 checks with Sodium0.8.13/GL3.3, including negative-Y rendering,
real warped terrain across cube seams, light, fluids, raycasts and flight. Test graphics use
llvmpipe; actual IntelHD4400 acceptance remains pending.

## Secondary world views

The native Y2156 mirror fixture first passed all14 scenes without Sodium but failed its gold
reflection check with Sodium, despite a successful direct view of the same wall. An offscreen
render must prepare its own frustum, just as GameRenderer does for the main view. Reusing the
main frustum culled the reflected wall. OffscreenWorldView now prepares visibility from the
transformed position/view and the original perspective projection, and restores the main
frustum in its finally block. A first candidate used the oblique clipping projection for
visibility too: the no-Sodium angled view then spun in vanilla's camera-box expansion.
Visibility is now conservative and independent of the aperture's GPU clip plane. The failed
thread dump and stopped test world are retained; passing Sodium alone was insufficient.
This uses the vanilla public API, without an Immersive Portals or Sodium dependency. Preserve
both failed controls and fresh fixed-run evidence; the full regression gate remains required
before distributing this candidate. The separate baseline fixture also waits for acknowledged
6DoF exit before teleporting and enables ordinary creative flight for its fixed-camera views;
otherwise the late disable packet/falling player could move the negative-Y wall out of view.

This is a compatibility foundation, not completion of the v5 worldgen/content, 3D LOD, universal
entity6DoF/ragdoll, combat/city or ship milestones. No original player save is converted.
