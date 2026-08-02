# Mega Beam · Orbital belt sky · Far view

## 1. Mega Beam (FP special)

Hold right-click on `mega_laser` — sustained hitscan column:

- **White core** + **cyan sheath** (`WeaponFx.megaBeam` + FP `MegaBeamViewRenderer`)
- Energy drain per tick; damage through shields (`WeaponCore.hitscan` → `ShieldSystem`)
- Impact splash + melt; stops when energy empty

Reference: thick character-height beam with shield block point.

## 2. Orbital belt on skybox

`OrbitalBeltSkyRenderer` — dark structural ring + green installation lights, camera-locked (no chunks).

- Fades in approaching `SURFACE_TOP`, full in sky/orbital
- Client tip + `LayerBridge` ORBIT announce: relocate surface base before vacuum

Config: `orbitalBeltSky=true` in `drmd.properties`.

## 3. Far view

DRMD draws no far-field terrain of its own. Chunks stay local; everything past them is
[Distant Horizons](DISTANT_HORIZONS.md) (soft-dep, + Sodium).

What DRMD still draws past the chunk radius:

| Layer | What |
|-------|------|
| Skybox | Spark ring / Starlink train / Oblivion mass (`OrbitalBeltSkyRenderer`) |
| Seam curtains | Layer faces (`BoundarySeamRenderer`) |
| Real blocks | Klondike sky islands · End-band islands — generated, not drawn as shells |

```
Near:  CHUNK mesh
Far:   Distant Horizons LODs
Sky:   camera-locked belt / ring / Oblivion
```
