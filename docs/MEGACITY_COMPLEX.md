# Megacity complex — biome plate (not spawn)

Megacity is the **`drmd:megacity` biome**: sparse seed-placed plates far from spawn. It is **not** generated at the player start point.

Flag: `WorldFeatures.SURFACE_DISTRICTS = true`.

## Spawn (pad only)

| Landmark | Role |
|----------|------|
| **Lunar Base** (Descent 1 disc) | Pad at spawn — turrets, Keeper, gravity, Pyro, SUPPORT drones |

UFOs / crystal reactors / outposts are **random surface events**, not hub satellites. See `TERRITORIES_AND_DUNGEONS.md`.

## Megacity biome

| | |
|--|--|
| Biome id | `drmd:megacity` |
| Placement | Grid cells ~3072 · ~1/6 cells · radius 160 · ≥1600 m from spawn |
| Inject | `MultiNoiseBiomeSourceMixin` (F3 / HUD show the biome) |
| Structure | Distance-queued when a chunk in the plate loads (`MegacityBiomeWorldgen`) |

### Plate contents (6DoF dungeon)

- Street canyons, sky highways, sky arena, artifact hangar
- Pyramid / sewers / garrison / ring AA / cyclic laser carts
- Under-plate primary dungeon (`TECH_RUINS` / `HOLLOW_RING` / `VERTICAL_SPIRE` / `ORBITAL_SHELL`)
- Satellite dungeon (`DRIFT_LAB` / `AUTO_FACTORY` / `SIGNAL_ARRAY`)
- Orbit ring + arch above; rift under

## Locate

- Join tip lists nearest plate distance
- `/d6 megacity` — status / nearest coords
- `/d6 megacity tp` — OP teleport onto the plate
- Explore until F3 biome reads `drmd:megacity`

## Layers

`LayerBridge`: title fade on band change; soft arrive when afterburning if districts or macro are on.

Full column notes: `WORLD_LAYERS_AUDIT.md`. Territory map: `TERRITORIES_AND_DUNGEONS.md`.
