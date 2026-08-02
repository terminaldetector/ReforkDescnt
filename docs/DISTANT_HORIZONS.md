# Distant Horizons — far view (replaces DRMD voxel LLOD)

DRMD **no longer** draws MacroWorld voxel silhouettes, hybrid horizon plates, or
planet-floor voxel expand. That pipeline was error-prone and heavy after the branch merge.

## Use this instead

**[Distant Horizons](https://modrinth.com/mod/distanthorizons)** — Fabric/NeoForge 1.21.1  
LOD terrain outside vanilla render distance → extreme view (64–512+ chunks) without loading real chunks.

### Recommended stack (TLauncher Fabric 1.21.1)

1. Fabric API  
2. **Distant Horizons** (`distanthorizons`) — required for far vista  
3. **Sodium** (optional, strongly recommended)  
4. Iris (optional, shaders with DH support)  
5. `drmd-6dof-1.1.1-fabric-1.21.1.jar`

DRMD `fabric.mod.json` **suggests** `distanthorizons` + `sodium` (soft-dep, not hard).

### What DRMD still draws

- Spark / Starlink / Oblivion **skybox** (`OrbitalBeltSkyRenderer`)  
- Seam curtains (`BoundarySeamRenderer`)  
- Real Klondike block islands (CHUNK_LOAD, not LLOD shells)  
- Cockpit / weapons / smoke  

Log line when DH is present:  
`Distant Horizons detected — far LODs delegated to DH`
