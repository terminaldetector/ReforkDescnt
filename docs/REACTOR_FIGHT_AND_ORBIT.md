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
5. **Orbital / End** detonation → recorded scars + scheduled meteor falls to surface  

Giga-reactor (End fight) calls the same aftermath with a denser asteroid rain.

## Endings

Canon: [`WORLD_PHILOSOPHY.md`](WORLD_PHILOSOPHY.md) — *the world already lost*.

| Ending | Trigger | Effect |
|--------|---------|--------|
| **1 · Silence** (obvious) | Destroy End giga-reactor | `WorldFate.SILENCE` — machines culled / no longer spawn; scars + slow decay |
| **2 · Void** (hidden) | Arm `dark_energy_bomb` at planetary core (`NETHER_FLOOR`) | `WorldFate.VOID` — matter clears, black sky, endless 6DoF flight, **no credits** |

Most players reach (1). (2) needs deep dig + expensive craft — the reactor was only a switch; the wound is the planet.

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
soft pitch + bank spiral while 6DoF is on — inspect scars / meteor impacts after an orbital detonation. Scars are recorded in `ScarMapState` and cut into the ground by `ScarApplier` on chunk load (`/d6 scars`).

## Full column · orbit · End sync

`DimensionSync` + Overworld `DimensionSyncState` (`drmd_dimension_sync`):

1. **Persist** armed/breach facilities + queued meteor falls (+ last detonation stamp) across restarts  
2. **SmokePayload** (~5 Hz when dirty) — volumetric clouds to dedicated clients  
3. **ReactorSyncPayload** (~2 Hz) — breach timers, pending falls, last detonation for OW sky+/End pilots  
4. Shared `ScarMapState` scars (already)  

Join pushes an immediate smoke + reactor snapshot. HUD strip shows nearest breach / fall count even outside 6DoF.

ImmPtl see-through stack: optional soft-dep — see [`IMMPTL_STACK.md`](IMMPTL_STACK.md).
