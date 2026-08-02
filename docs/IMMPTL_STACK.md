# Immersive Portals (ImmPtl) — optional dimension stack

DRMD Path B (default): one Overworld column (−512…1024) with diggable mantle → Core.
Path A (optional soft-dep): Immersive Portals **Dimension Stack** for true see-through seams.

## Soft dependency

`fabric.mod.json` **suggests** ImmPtl. The mod runs without it.
`PortalComplexity.hasImmersivePortals()` detects common mod ids for HUD only.

Vanilla Nether / End portals still need **gate catalysts** (complex crafts) even with ImmPtl installed — dig-through remains the seamless survival path.

## Recommended stack (Fabric 1.21.1)

```
End (Oblivion)   ↑
Orbit / sky      ↑   ← optional separate dim, or OW sky band
Overworld        ↑
Nether (Core)    ↑
```

Align ImmPtl floor/ceiling links with `WorldLevels`:

| Seam | OW Y | Notes |
|------|------|--------|
| OW floor ↔ Nether ceiling | ≈ −240 (`NETHER_CEILING`) | Mantle dig path meets Core |
| OW top ↔ End floor | ≈ 880 (`ORBITAL_TOP`) | Techno-ring vista / Oblivion |

## Portal crafts (always on)

1. `plasma_granite` + `energy_cell` → **Portal Stabilizer**
2. Stabilizer + netherite scrap + obsidian + cells → **Nether Gate Catalyst**
3. Stabilizer + end crystals + pearls + plasma granite → **End Gate Catalyst**

Igniting a nether frame or placing an eye consumes the matching catalyst (creative exempt).

## Escape

Digging up through mantle / shafts, `/d6 level`, or DimensionSync aftermath cues keep the surface path readable after Core events.
