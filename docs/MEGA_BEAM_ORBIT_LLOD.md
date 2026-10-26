# Mega Beam · Orbital belt sky · Hybrid LLOD

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

## 3. Hybrid LLOD (orbit lands + clouds)

Chunks stay local. From altitude, `HybridHorizonRenderer` draws:

| Layer | What |
|-------|------|
| Macro Voxel LLOD | Stations / city / rings (`LlodSilhouetteRenderer`) |
| Hybrid land plates | Seeded heightfield cells beyond chunk radius |
| Cloud banks | Soft translucent decks when looking from above |

Config: `hybridHorizon=true`. Megacity has a dedicated LLOD silhouette (plate + towers + pyramid).

```
Near:  CHUNK mesh
Mid:   LLOD2→0 macros
Far / high Y: hybrid land + clouds + sky belt
```
