# Spark ring + Klondike islands

## Ring (кольцо)

The orbital “ring” is the Spark the Electric Jester vista: a planet sphere with a
**dark solid band** and a **neon-green outer halo**. Implemented in
`OrbitalBeltSkyRenderer` as a camera-relative skybox (no chunk load, no R=2048 junk).

**Night (surface):** same ring as a **Starlink-style train** of bright green pearls across
the dark sky — no climb required. **Oblivion** appears as a distant violet/magenta skybox
mass (stronger at night, larger when climbing toward Orbit).

`WorldFeatures.ORBIT_JUNK=false` — techno-ring satellites do not compete with this vista.

## Weapons (separate PRs — not this jar)

Today’s arsenal work lives on other draft branches, not in megacity `1.0.9`:

| PR | Branch | Content |
|----|--------|---------|
| #6 | `cursor/arsenal-descent-closed-02fc` | Closed Descent arsenal, orbs, loot, HUD |
| #5 | `cursor/descent-laser-bolts-02fc` | Dual-bolt combat lasers |
| #3 | `cursor/ordnance-cluster-rockets-02fc` | Rockets / bombs / mega laser |

Jar from PR #8 is world/6DoF. For weapons, take the arsenal PR jar (or merge those branches).

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