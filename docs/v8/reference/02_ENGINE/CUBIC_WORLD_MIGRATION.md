# ENGINE A — CUBIC WORLD / SOURCE-LEVEL MIGRATION
**Owner:** independent world engine agent. **Priority:** P0. **Do not edit:** gunplay/GUI/swarms/UFO production worktree.

## Baseline and false equivalence
Historical PR #9 explicitly states vanilla controls full column terrain, lighting, chunk lifecycle/network, rendering; `cubic snapshots` are supplemental loaded-terrain data. The current requirement is a true 16³ voxel-world backend, not a new `LLOD` facade or more Y-band datapacks. Delete old code only after backend parity; render-only approximations cannot store authoritative blocks.

## Candidate choice and license
- Main research candidate: **Better Cubic Chunks 0.1.1 Beta / Fabric 1.21.1**. Authors claim fully cube-based generation/save/render, separate vertical view distance, default height 32768; beta and All Rights Reserved. Integrate via runtime dependency/API and compatibility adapters, not source copy.
- Reference open-source old CubicChunks 1.12.2/Forge is *not* drop-in compatible with this project. CubicChunks3 status and NeoForge 1.21.6 branch do not satisfy direct Fabric 1.21.1 use.
- Candidate Better Distant Horizons optional to provide 3D far LOD. No standard DH jar alongside Better DH without verified compatibility.
- Verify actual file hashes, dependency graph, Mixins, server/client support; prefer plugin adapters rather than invasive forks.

## API contract (required new interfaces)
```java
// Conceptual API. Names must be reconciled with actual mappings/upstream.
interface VolumetricWorldAccess {
  WorldBounds bounds(RegistryKey<World> dimension);
  CubeAddress cubeAt(BlockPos pos);  // floorDiv x/y/z by 16, negative values correct
  LoadedState cubeState(CubeAddress addr);
  Optional<BlockState> readBlockIfLoaded(BlockPos pos); // never force load on render thread
  MutationResult editBlocks(ServerWorld world, BatchEdit job); // bounded + server-authoritative
  void subscribeCubeLifecycle(CubeLifecycleListener listener);
  void scheduleMeshAndLightingInvalidation(VolumeAabb dirtyVolume);
}
```
- `cubeAt` uses signed 64-bit intermediate arithmetic for chunk-related offsets and negative boundaries, handling floorDiv correctly.
- `readBlockIfLoaded` must not block async rendering. Explicit async/weak-reference handling; `worldgen` cannot mutate client mesh directly.
- Layers of orbit/core/citadel are content/generation regions, **not hard-coded emulated dimensions**. Optional dimension portals are separate.
- Collider and fluid queries must use actual updated blocks; no ghost solid surfaces.

## Removing old world systems (only after parity)
1. Make an inventory via search for `cubic`, `snapshot`, `column`, `altitude`, `border`, `LLOD`, `voxel horizon`, `world generation`, `surface store`, `layer resolver`, `buildHeight`, `minY`, `maxY`, `WorldPreset` etc. Classify `REMOVE/ADAPT/KEEP/UNKNOWN`.
2. Introduce `WorldAccessAdapter`; redirect one consumer at a time (terrain, physics, players, entity nav, explosions, structure placement, light, save).
3. Test same seed/biome on a new Cubic preset. Run multi-Y hot loads during 6DoF roll/high speed, VRAM/CPU budget and render seam diagnostics.
4. Validate `BlockEntity` NBT: only legitimate blockstate may create corresponding BE; if worldgen replaces a spawner with air, its NBT must not persist.
5. Keep prior save worlds read-only and converted separately; do not promise migration that upstream cannot support.
6. After core parity, delete old emulation code and associated default settings, replacing with a safe feature flag for experimental future worldgen.

## Reproduction gates
- GATE C0: run **vanilla + Fabric + Cubic dependency** cleanly using Java21 in two new saves; report `minY`, `height`, cube section count, exact file version.
- GATE C1: add DRMD minus custom generator, move across Y(-256,-64,0,320,880,1024,1640,2150); test X/Z boundary and large negative coords with correct floorDiv.
- GATE C2: enable biome/structures/worldgen one subsystem at a time; freeze on first DUMMY BE with generator stack/source. Verify all fluids and chunk/cube boundaries.
- GATE C3: integrate 6DoF player and ships, flight 0/50/300/628 m/s, loaded-before-render status, priority streaming forward cone and bounded jobs.
- GATE C4: integrate hybrid near mesher and 3D distant LOD, client rendercheck on Intel GL3.2 compatibility profile and a representative GPU; use fallback if unsupported.
- GATE C5: unload/reload, death/respawn, remote portals, block destruction, reboot save hash, exact original save untouched.

## Anti-patterns
- Not another custom chunk backend from scratch.
- Not make cubic mesh a post-processing trick.
- Not claim infinite all axes unconditionally; coordinate and CPU/memory limits remain.
- Not copy proprietary upstream source.
- Not let low-precision PS1 vertex snapping modify server block coordinates.
