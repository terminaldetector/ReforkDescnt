# Planetary Voxel LLOD — End ↔ Overworld stitch

Complex voxel LLOD that treats the Overworld surface as a **planet map** visible from End/orbit.

## Scene

From End (or high orbit):

1. **Floor is not void** — a curved voxel planetary surface fills the bottom.
2. **Explored** cells show real sampled height + biome tint (from player travel).
3. **Unexplored** cells are procedural voxels from world seed (fog-of-war terrain).
4. **Weather** stamps (rain/storm) tint cloud decks on the map.
5. **Reactor scars** burn into the map; on later Overworld chunk load the scar is applied as terrain damage.

Macros (city, rings) still draw via classic Voxel LLOD on top.

## Cell model

| Field | Meaning |
|-------|---------|
| `cx, cz` | Cell coords (`CELL=32` blocks) |
| `height` | Sampled surface Y (0–255 practical) |
| `tint` | Biome RGB |
| flags | `EXPLORED`, `SCAR`, `RAIN`, `STORM` |

## Pipeline

```
Overworld travel → PlanetMapSampler → PlanetMapState (PersistentState)
End reactor / scars ↗
        ↓ sync PlanetMapPayload (~1 Hz when in End/orbit)
Client PlanetMapClientState → PlanetFloorRenderer (voxel expand via PlanetVoxelMath)
        ↓ chunk load in Overworld
PlanetScarApplier → crater / scorched blocks
```

## Packages

- `world/llod/planet/` — math, state, sampler, scar apply
- `client/llod/planet/` — client cache + floor renderer
- Network: `ModNetworking.PlanetMapPayload`

Config: `planetFloor=true` (client).
