# Distant Horizons — far view (replaces DRMD voxel LLOD)

DRMD **no longer** draws MacroWorld voxel silhouettes or hybrid horizon plates. That pipeline was
error-prone and heavy after the branch merge; 1.1.1 switched it off and 1.1.2 deleted it —
`world/llod`, `client/llod`, and both payloads are gone.

One thing did come back, rebuilt: the **planet floor under the End band** (1.1.3). It is not a far
LOD of the real world — it is a procedural map of the surface, scaled toward the camera, for the one
view DH cannot serve well: straight down from nine hundred blocks up, over ground the client was
never sent. See [`PLANET_FLOOR.md`](PLANET_FLOOR.md), and turn it off there if you would rather see
DH's real LODs.

## Use this instead

**[Distant Horizons](https://modrinth.com/mod/distanthorizons)** — Fabric/NeoForge 1.21.1  
LOD terrain outside vanilla render distance → extreme view (64–512+ chunks) without loading real chunks.

### Recommended stack (TLauncher Fabric 1.21.1)

1. Fabric API  
2. **Distant Horizons** (`distanthorizons`) — required for far vista  
3. **Sodium** (optional, strongly recommended)  
4. Iris (optional, shaders with DH support)  
5. `drmd-6dof-1.1.3-fabric-1.21.1.jar`

DRMD `fabric.mod.json` **suggests** `distanthorizons` + `sodium` (soft-dep, not hard).

### What DRMD still draws

- Spark / Starlink / Oblivion **skybox** (`OrbitalBeltSkyRenderer`)  
- Seam curtains (`BoundarySeamRenderer`)  
- Real Klondike block islands + the End-band archipelago (CHUNK_LOAD, real blocks)
- Planet floor under the End band (procedural map, not terrain LODs)  
- Cockpit / weapons / smoke  

Log line when DH is present:  
`Distant Horizons detected — far LODs delegated to DH`
