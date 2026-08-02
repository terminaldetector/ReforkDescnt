# Megacity complex (HL2 surface districts)

Sparse campaign landmarks without full `MACRO_WORLDGEN`. Flag: `WorldFeatures.SURFACE_DISTRICTS = true`.

## Spawn loop

| Landmark | Role |
|----------|------|
| **Lunar Base** (Descent 1 disc) | Hub at spawn — turrets, Keeper, gravity pad, Pyro, SUPPORT drones |
| **Approach beacons** | Sea-lantern pylons every ~32 blocks toward the plate |
| **Megacity** (NW, ~180 / +160) | Surface combat dungeon — Midgar / FF7 plate read |
| Crashed UFO | Artifact boarding fight |
| Sky UFO | Air lane target |
| Crystal reactor under spawn | Dungeon depth link |
| Tech ruins + rift under city | Dungeon satellite under the plate |
| Orbit ring + arch above city | High-altitude 6DoF after the plate |

Landmarks build when a player enters `SEED_RADIUS` (256) — join stays fast.

## Megacity (6DoF dungeon)

- Street canyons cleared to ~48 — flyable corridors between towers
- **Sky highways** (~Y+22) — maglev lanes + crossroads pad
- **Sky arena** (~Y+36) — open dogfight ring with PD rim
- **Artifact hangar** (south edge) — Descent grey bay, turrets, magnetic anomaly
- Plate rim + PD pillars (city plate silhouette)
- Atrium towers — hollow cores for vertical flythrough
- Rooftop laser / plasma / PD / volume turrets
- Sewer grid under streets (manholes / ladders)
- Central reactor pyramid + mako glow columns + depth shaft
- Garrison: street drones, sewer scanners, spiders, tripod, sky interceptors, hangar scanner
- **Ring AA** on plate + arena (embedded casemate turrets + shield projectors)
- **Cyclic laser carts** on powered-rail loops (also buildable via `cyclic_laser_kit`)

## Layers

`LayerBridge`: title fade on band change; soft arrive when afterburning if districts or macro are on.

Full column / ImmPtl notes: `WORLD_LAYERS_AUDIT.md`.
