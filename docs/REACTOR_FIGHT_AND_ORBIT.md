# Reactor fights, dungeon vitality, orbit ≠ End

## Dungeon vitality (technogenic)

| Class | Look | Content |
|-------|------|---------|
| **DEAD** | Cold ruins | Beacon core, sparse magnetic hazards |
| **SEMI_ALIVE** | Looks dead outside (cracked shell) | Hidden living reactor deeper + inner traps |
| **ALIVE** | Active installation | `unstable_reactor` core, turret ring, laser barriers, volume/AA turrets |

Hostile 6DoF mobs: **deferred**. Living sections use traps/turrets only.

Territory bias: technogenic sea / megacity skew alive; scorched / surface events skew dead.

## Reactor breach (Descent-style)

1. Break registered `unstable_reactor` core  
2. **90s escape timer** + procedural exit tunnel (nav node)  
3. Escape by exit / dig / mines — or timer expires  
4. Detonation: ash + smoke + blackstone/basalt scorch; facility wiped  
5. **Orbital / End** detonation → planet-map scars + scheduled meteor falls to surface  

Giga-reactor (End fight) calls the same aftermath with a denser asteroid rain.

## Orbit structure (stock column — not End)

```
ORBITAL Y 640…880
  LAYER_A  ~680   space junk plates
  RING     ~730   techno-ring islands / stations / artifacts (radius 2048)
  LAYER_B  ~780   second junk slab + left/right of ring
```

- Inverted villages / 6DoF high-altitude prefs stay in this band.  
- **End** = separate: overcome max orbital height → techno-ring vista → End seam.  
- Portals become easier to drift into accidentally (harder/more complex — TBD ImmPtl stack).

Commands: `/d6 orbit`, `/d6 orbit ring`, `/d6 reactor`.

## Fall aftermath mode

Client option **Fall aftermath (corkscrew)** (`fallAftermath` in `drmd.properties`):
soft pitch + bank spiral while 6DoF is on — inspect planet scars / meteor impacts after an orbital detonation. Uses voxel planet map + `PlanetScarApplier` on chunk load.

## 3-dimension sync (architecture note)

Full sync of Overworld column · orbit content · End/giga aftermath requires:

1. Shared `PlanetMapState` scars (done)  
2. Facility / breach state persistence across dims (partial — in-memory fight map today)  
3. Optional Immersive Portals dimension stack for true see-through seams (`WORLD_LAYERS_AUDIT.md`)  
4. Networked smoke / meteor fall cues for dedicated servers  

This pass lays the gameplay hooks; full ImmPtl + MP smoke sync remains a follow-up.
