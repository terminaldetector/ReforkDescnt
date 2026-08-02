# Voxel LLOD — Long Level of Detail

Pipeline for 6DoF sandbox volume: Minecraft chunks alone cannot keep kilometres
of megastructures loaded. Voxel LLOD keeps distant volume on screen as simplified cubes.

```
LLOD0     silhouette of object     ≈ up to ~2800 surface cubes / object
   ↓
LLOD1     large forms              thick structural voxels (~≤256)
   ↓
LLOD2     region                   few mega-proxies (≤12)
   ↓
CHUNK     vanilla blocks           Minecraft chunk mesh
```

Near → far: **CHUNK → LLOD2 → LLOD1 → LLOD0**.

## Distance bands (`LlodLevel`)

Tuned for Minecraft fog/view-distance (not Source-engine km). Older LLOD0 min of 3072
sat past fog → empty horizon in flight.

| Band | Distance (blocks) | Draw |
|------|-------------------|------|
| CHUNK | 0 – 96 | nothing (vanilla) |
| LLOD2 | 96 – 384 | region proxies |
| LLOD1 | 384 – 1280 | large forms |
| LLOD0 | 1280 – 48000 | dense voxel silhouette (fog disabled on draw) |
| NONE | further | culled |

## How it works

1. Server keeps `MacroWorld` catalogue of megastructures / mega fauna.
2. `MacroCatalogue.ensureAround` seeds deterministic **ghost** macros in a cell ring
   around the player (+ velocity look-ahead) so unloaded chunks still feed LLOD.
3. `LlodRegistry.queryVisible` scores by eye + foresight AABB distance, assigns bands,
   and applies per-band caps.
4. Compact `LlodPayload` sync (~2 Hz) — kind, center, radii, band, seed (no voxel flood).
5. Client `VoxelLodMesh` expands descriptors into voxels (cached per id+band).
6. `LlodSilhouetteRenderer` batches quads with fog disabled for the pass (frame budget ~14k).

LLOD0 uses **surface-only** occupancy so a far arch/ring/continent stays readable.

## Column constraints

Live Overworld datapack stays **−64…320** (Watchdog-safe). Sky macros and UFO cruise
Y are clamped with `WorldRules.skyY` / `clampBuildY`. Flight soft-walls bounce at the
real column edges so 6DoF cannot drift into empty void (e.g. Y≈−3800).

## API

- `world/llod/LlodLevel.java`
- `world/llod/VoxelLodMesh.java`
- `world/llod/LlodRegistry.java`
- `world/gen2/MacroCatalogue.java`
- `client/llod/LlodSilhouetteRenderer.java`

Command: `/d6 llod` — sync + counts per band.

## Why this matters for 6DoF

High-speed flight needs horizon content. Without LLOD the world pops in at chunk radius.
With LLOD0→Chunk the player sees stations, mountains and sky objects across the volume
while only the neighbourhood stays as real blocks.
