# Spark ring + Klondike islands

## Ring (кольцо)

The orbital “ring” is the Spark the Electric Jester vista: a planet sphere with a
**dark solid band** and a **neon-green outer halo**. Implemented in
`OrbitalBeltSkyRenderer` as a camera-relative skybox (no chunk load, no R=2048 junk).

`WorldFeatures.ORBIT_JUNK=false` — techno-ring satellites do not compete with this vista.

## Islands

Sky presence = **real voxels** via `KlondikeIslandGenerator` (grass disk + stone underside).
Sparse CHUNK_LOAD + spawn seed. No ARCH/RING/FLOATING_CONTINENT silhouette zoo.

## Connection (stabilized)

| Hook | Job |
|------|-----|
| `LayerBridge` | Seam announce + 6DoF hop |
| `SeamWarmup` | Background Nether/End before 10 blocks |
| `BoundarySeamRenderer` | Face curtains |
| Spark ring + Klondike | Orbit layer *display* |

Macro LLOD (`MACRO_LLOD`) stays compiled but off.