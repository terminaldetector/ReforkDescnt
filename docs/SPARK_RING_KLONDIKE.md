# Sky ring and floating islands

This document tracks the current sky presentation and its related world-generation options. The
current implementation status and broader rendering limits are in
[`PROJECT_STATUS.md`](PROJECT_STATUS.md).

- `OrbitalBeltSkyRenderer` draws the planet, dark ring and green halo as a camera-relative sky view.
- `KlondikeIslandGenerator` creates sparse floating islands from real blocks when the feature is
  enabled.
- `OrbitJunkWorldgen` independently controls debris plates and techno-ring structures; its toggle
  does not depend on surface biome districts.
- Distant Horizons supplies terrain LOD when installed. The built-in voxel horizon is a procedural
  surface view, not a full 3D far-distance renderer.

World-generation switches are available from the Create World screen. See
[`WORLDGEN_MENU.md`](WORLDGEN_MENU.md) for their persistence and apply behavior.
