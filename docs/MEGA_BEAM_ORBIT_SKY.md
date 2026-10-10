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

DRMD does not provide true far-field terrain LOD. Chunks stay local; Distant Horizons is the optional
terrain LOD renderer. DRMD's procedural voxel horizon is a separate surface view, documented in
[`VOXEL_HORIZON.md`](VOXEL_HORIZON.md).

What DRMD still draws past the chunk radius:

| Layer | What |
|-------|------|
| Skybox | Spark ring / Starlink train / Oblivion mass (`OrbitalBeltSkyRenderer`) |
| Real blocks | Klondike sky islands · End-band islands — generated, not drawn as shells |
| Surface view | Procedural voxel horizon; not a terrain LOD |

```
Near:  CHUNK mesh
Far:   Distant Horizons LODs (when installed)
Ground: procedural voxel horizon
Sky:   camera-locked belt / ring / Oblivion
```
