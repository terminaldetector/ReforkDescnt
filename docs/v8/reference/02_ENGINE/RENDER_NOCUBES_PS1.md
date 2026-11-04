# ENGINE B — HYBRID RENDER / NOCUBES MORPHOLOGY / PS1 HYBRID
**Owner:** client rendering agent, coordination with WorldAccess and collision teams. All work must remain switchable and measurable.

## Separation of layers
A. Authoritative cubic blockstore. B. Near mesher generating naturally smoothed polymesh on selected materials. C. Collision mesh/convex decomposition as needed on server. D. 3D far LOD/atmosphere. E. PS1-style presentation. F. independent mirror and portal passes. G. UI/HUD and first-person viewmodel.

## Terrain material policy
- `SMOOTH_NATURAL`: dirt, soil, grass, natural stone, slopes, eroded caves, trees with round/poly trunk and branch silhouette. Mix NoCubes-like smoothing and deliberate low polygon counts, not high subdivision.
- `HARD_ENGINEERED`: metal hulls, reactors, structures, player buildings, selected industrial surfaces, machines; preserve sharp cubic silhouette unless explicitly marked.
- `TRANSITION`: exposed fracture and melt surfaces near machinery. Mask may vary per material and damage stage.
- `CAVITY`: support rounded tunnels/shafts, polygonal hollows, corners and mixed convex/concave forms beyond simple NoCubes organic smoothing.
- `QUARTER_DAMAGE`: pre-existing 4×4×4 microdamage may be stored as damage metadata and meshed in batches; do not spawn 64 entities per impacted block by default.

## Mesh algorithms experiment
Benchmark dual contouring/surface nets/transvoxel-like LOD seam handling vs simple vertex smoothing. Verify topology watertight, normal continuity across boundaries, UV, visibility, GPU budget. For sharp edges use feature-aware preservation. Mesh rebuild incremental by dirty volume and stable geometry hashing; no full-world re-mesh per drill tick.

## Smooth drilling
- Weak drill: block-by-block discrete removal, roughly rectangular/stepwise tunnel, chunks retain raw grid.
- Strong drill: thermal front and material-dependent state (heat/soften/melt/remove); nearby surface recomputed into controlled circular aperture of configurable R. Display temporary partially melted rim, not instantaneous disappearing cubes.
- Collision: server and client agree on actual accessible passage; collision proxies approximate smoothed mesh without invisible block wall. Lighting/fluid updates batched, destructive events authoritative. Rebuild budgets and safety caps enforce ms/tick target.

## PS1 Hybrid profile
Visual specification defaults to 640×360 nearest-upscaled presentation; settings: internal resolution scale, color quantization, ordered dithering, vertex snapping granularity, affine/UV warp amount, far fog density, lowpoly asset profile, frame pacing. The PS1 look cannot be achieved by blurring/smearing the whole scene or injecting noise on GUI. Viewmodel/HUD text, crosshair and screen interaction render in a stable high-resolution overlay.

Runtime profiles: `PS1_HYBRID` (preferred visual target), `HYBRID_NATIVE` (full-res), `RENDER_DEBUG` (normals/LOD/collision visualization). Configuration can switch without reload, preserving previous user choices; `hybrid_render=ON` by default even if `PS1_HYBRID` is disabled. Mesh cache untouched by postprocess toggle. No physics/world-state effect.

## Mirror vs portal
**Mirrors:** reflect camera about plane, clip wrong half-space, solve culling, FBO allocation/reuse and budget; 1/2/4 mirrors, GL3.2 fallback. Specific log defect: `mirror: nothing drawn: 2 visible but measured off-screen or no render target`. Mirror surface must itself draw even when reflection pass is gracefully unavailable.
**Portals:** linked two-way spaces, independent camera transforms and traversal; velocity and orientation through portal, safe collider and body transitions, cycle limits. Portals must not be required for world height traversal.

## Compatibility tests
Combinations `{Cubic on/off in isolated stand},{Sodium on/off},{Hybrid native/PS1},{NoCubes collision test},{Mirror 0/1/2},{Portal 0/1},{6DoF yaw/pitch/roll},{intel GL3.2 / representative modern GPU}`. No assumption that official NoCubes client-only implementation provides valid server physics. Verify and choose adapter or independent collision representation.
