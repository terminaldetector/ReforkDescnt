# Cubic space migration — 2026-10-02

## Decision

The user's current direction supersedes the older dimension-stack proposal: the target is an XYZ-symmetric world made of independently addressable 16³ cubes. Portals connect spaces; they must not emulate the world's vertical extent. Mirrors are orientation-reversing optical views, not another vertical layer. Moving assemblies own local cubic spaces and a rigid transform into their parent space.

The intended world remains Descent/Prey-like: traversal in six degrees of freedom, inhabited and abandoned volumetric infrastructure, local gravity, moving industrial structures, distant megastructures and destructive world events. The target +100,000 / −50,000 height range cannot be achieved merely by raising a dimension type's height.

## What this branch actually implements

- `D6Cube<T>`: editable palette-backed 16³ data, compacted serialization, independent snapshots.
- `D6Volume<T>`: sparse XYZ cube addresses; negative coordinates and large Y share the same addressing rules as X/Z.
- `CubicBlockCodec`: registry-named block states rather than transient raw IDs; block bodies save this same volume format.
- `BlockBodyEntity`: operator-created inert assemblies, centre of mass, inertia tensor, world angular momentum, impulses, free XYZ movement and quaternion rotation. Geometry is removed once at assembly and restored once at disassembly. Entity NBT persists shape, rotation, velocity and spin. Rendering uses the local cells and quaternion.
- Conservative per-block rotated bounding boxes and bounded substeps stop bodies at terrain, world bounds and unloaded chunks. Contacts stop linear/angular motion; this is not a general contact/friction solver.
- Disassembly snaps to one of the 24 proper cube rotations supplied by the existing attributed Immersive Portals math. Selection and landing validate before mutation; failed writes roll back. Only inert blocks are accepted; inventories and ticking machines are rejected.
- Gravity gun applies impulses to the new body; ordinary entities retain their prior handling. UUID lookup replaces repeated scans of every entity.
- Native portal traversal rotates the player's forward/up basis, saved flight velocity and local up. An explicit client correction resets velocity prediction. A frame epoch prevents older input packets from restoring the pre-portal orientation. Bodies carry orientation and angular momentum; aperture and destination terrain checks precede their transfer.
- Native mirror and portal views preserve the outer camera's roll. Mirror view matrices retain a negative determinant and reverse/restore triangle winding during the nested render.
- Last-mass removal resets mass properties instead of dividing by zero. Portal body transforms preserve the movement remaining after crossing. Mixed-material overlap is updated during legacy block movement. Virtual UFO creation no longer places and immediately erases terrain.
- Far-view `SurfaceStore.peek` now respects the resident cap, returns the winning cached object after a race, and repeated `put` calls do not append duplicate residency entries.
- Immersive Portals is compile-only: the default development/test launch no longer force-loads it without its `dimlib` dependency.

## Playtest commands

Requires operator permission level 2. Use the same build on client and server; the input packet changed to `input_v2`.

```
/d6body assemble <from x y z> <to x y z>
/d6body impulse @e[type=drmd:block_body,sort=nearest,limit=1] 4 0 0
/d6body spin @e[type=drmd:block_body,sort=nearest,limit=1] 0 2 0
/d6body disassemble @e[type=drmd:block_body,sort=nearest,limit=1]
```

Selection must fit 16×16×16 and contain at most 256 occupied cells. Supported blocks: stone, cobblestone, iron/gold/diamond blocks, obsidian, glass, bricks. Each block currently has unit mass. `impulse` is linear momentum; `spin` adds world angular momentum. Speed caps: 20 blocks/second and 2 radians/second. Bodies have no automatic gravity. This is an experimental construction/physics path, not a complete Create replacement. It does not yet simulate redstone, inventories, passengers, body/body contacts, or interactions with individual rendered blocks.

## Terrain adapter, explicitly transitional

`-Ddrmd.cubicSnapshots=true` enables budgeted cubic snapshots around players (radius two cubes in each axis, at most two captures/world/tick, 512 resident and pending cube budgets). Snapshots persist by dimension under `drmd/cubes/<namespace>/<path>/x_y_z.nbt`, with compressed NBT, atomic file replacement and a bounded/coalesced write queue. Block-change hooks invalidate resident snapshots. Reads are historical observations, not authoritative terrain.

This adapter **does not replace** vanilla chunk ownership, tickets, generation, lighting, height checks, networking, block ticks or rendering. It deliberately reads already-loaded sections without forcing columns to load. The snapshot flag is off by default. Existing saves and `LayerBridge` remain on the legacy path; enabling snapshots does not silently convert a save.

## Remaining blockers to an actual cubic world

| Area | Required next implementation |
|---|---|
| World ownership | Authoritative cube lifecycle and 3D tickets, eviction and transactional saves; distinguish missing cubes from empty cubes |
| Generation | Volumetric generator stages with neighbor dependencies, without heightmap assumptions; migrate world bands to volumetric regions |
| Engine coordinates | Audit all BlockPos packing, section indexing and build-height guards before enabling large Y |
| Light and ticks | Cross-face lighting, fluids, scheduled ticks and block entities per cube |
| Network and rendering | Cube subscriptions/deltas and a client mesh graph; vanilla column packets cannot express this lifecycle |
| Existing saves | Versioned import and rollback; disable dimension-stack travel only after the new backend exists |
| Physical assemblies | Block state/BE lifecycle adapters, welding/connectivity, moving interactions, riders, contact solver and structure destruction |
| Portals | Cross-dimension ownership/network handoff, complete vehicle trees, swept spatial broadphase beyond native 32-block/tick budget, bidirectional visibility and collision during straddling |
| Mirrors | Exact surface mask and depth-aware composite; current rectangular scissor still leaks around oblique edges and partial occluders |
| Far distance | True volumetric LOD; the existing height/color surface store cannot represent caves, stacked rooms or floating interiors |

The CubicChunks donor generations target different loader/game versions; none can be declared a working Fabric 1.21.1 drop-in. Eureka's useful separation is assembly/disassembly and local storage; its upstream physics backend cannot be replaced by a moving visual mesh alone. The existing 24-orientation donor math is now used by live disassembly. No new third-party source has been copied into this branch.

## Validation

Run with Java 21:

```
./gradlew test
./gradlew runGametest
./gradlew build
```

The isolated `gametest` source set is not packaged into the release. World tests cover block assembly, NBT restoration, blocked landing, rejection of inventory blocks, collision against solid terrain, and stable cubic block-state persistence at Y=100,000 and Y=−50,000. The large-coordinate test validates storage only, not placement into a vanilla world.

JUnit covers palette churn, snapshot isolation, coordinate seams, mass removal/rebuild, impulses, post-crossing distance, material overlap, view-matrix handedness/roll and the far-view cache bound. Graphical rendering and latency behavior still require an interactive client playtest; headless tests are not proof of visual seamlessness.
