# Voxel LLOD — Long Level of Detail

Pipeline for 6DoF sandbox volume: Minecraft chunks alone cannot keep tens of kilometres
of megastructures loaded. Voxel LLOD keeps distant volume on screen as simplified cubes.

```
LLOD0     silhouette of object     ≈ up to ~2800 surface cubes / object
   ↓
LLOD1     large forms              thick structural voxels (~≤192)
   ↓
LLOD2     region                   few mega-proxies (≤8)
   ↓
CHUNK     vanilla blocks           Minecraft chunk mesh
```

Near → far: **CHUNK → LLOD2 → LLOD1 → LLOD0**.

From altitude, **hybrid horizon** adds seeded land plates + cloud banks without loading distant chunks (`HybridHorizonRenderer`). Macro silhouettes and the orbital belt skybox sit on top. See `MEGA_BEAM_ORBIT_LLOD.md`.

**Planetary Voxel LLOD** (`PLANET_VOXEL_LLOD.md`): End/orbit floor is a complex voxel map of explored Overworld + procedural fog-of-war, with weather stamps and reactor scars that apply on descent.

## Distance bands (`LlodLevel`)

| Band | Distance (blocks) | Draw |
|------|-------------------|------|
| CHUNK | 0 – 192 | nothing (vanilla) |
| LLOD2 | 192 – 768 | region proxies |
| LLOD1 | 768 – 3072 | large forms |
| LLOD0 | 3072 – 96000 | dense voxel silhouette |
| NONE | further | culled |

## How it works

1. Server keeps `MacroWorld` catalogue of megastructures / mega fauna.
2. `LlodRegistry.queryVisible` assigns each entry a band from player distance.
3. Compact `LlodPayload` sync (~2 Hz) — kind, center, radii, band, seed (no voxel flood on the wire).
4. Client `VoxelLodMesh` expands descriptors into voxels (cached per id+band).
5. `LlodSilhouetteRenderer` batches quads (frame budget ~12k cubes).

LLOD0 uses **surface-only** occupancy so a far arch/ring/continent stays readable without filling solid volumes.

## API

- `world/llod/LlodLevel.java`
- `world/llod/VoxelLodMesh.java`
- `world/llod/LlodRegistry.java`
- `client/llod/LlodSilhouetteRenderer.java`

Command: `/d6 llod` — sync + counts per band.

## Why this matters for 6DoF

High-speed flight needs horizon content. Without LLOD the world pops in at chunk radius.
With LLOD0→Chunk the player sees stations, mountains and sky objects across the volume
while only the neighbourhood stays as real blocks.
