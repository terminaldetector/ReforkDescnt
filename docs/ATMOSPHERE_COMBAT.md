# AI Sector A — Atmosphere, Bombardment, Smoke & Fire

Minecraft stays recognizable; physics **shifts by altitude** instead of becoming a different game.

## Atmospheric bands (practical Overworld mapping)

| Band | Spec Y | Practical Y | Rules |
|------|--------|-------------|-------|
| Deep Pressure | −50k…−20k | −64…−20 | Higher blast scale, steam vents near magma, longer tunnel shockwaves |
| Classic | 0…10k | −20…120 | Vanilla water/lava feel |
| Thin | 10k…30k | 120…200 | Less air drag, longer smoke life, bombs accelerate |
| Near Space | 30k+ | 200…320 | Free water sources suppressed, thrusters primary, full 6DoF combat |

Resolve with `AtmosphereBand.at(y)`. Flight applies `airDrag` / `thrustScale`. Explosions use `AtmosphereRules.scaleBlast`.

Commands: `/d6 atmosphere`

## Aerial bombardment

TNT becomes air-to-ground ordinance:

| Item | Ordnance |
|------|----------|
| `drmd:bomb_tnt` | High-explosive TNT bomb |
| `drmd:bomb_cluster` | Cluster munition (sub-blasts) |
| `drmd:bomb_incendiary` | Fire + smoke columns |
| `drmd:bomb_guided` | Steers toward laser mark |
| `drmd:laser_designator` | Marks impact point (256-block ray) |

While falling (thousands of blocks in tall worlds):

- long colored smoke trail
- speed grows as air drag falls
- impact → scaled crater explosion + smoke/fire foci

Commands: `/d6 bomb [tnt|cluster|incendiary|guided]` · `/d6 laser`

## Dynamic Smoke (gameplay, not decoration)

Sources: TNT, fire, engines, reactors, damage, volcano, industrial, bomb trails.

Behaviour:

- volumetric clouds with buoyancy / near-space spherical expansion
- tunnel-friendly drift; longer life in thin/near-space air
- obscures HUD radar range + smoke % readout
- LLOD draw: LLOD0 far columns → LLOD1 large puffs → LLOD2 local blobs

## Advanced Fire (3D)

- spreads on walls / ceilings / industrial halls
- bombardment creates many foci → smoke pillars
- Unstable Reactor accidents emit reactor smoke + secondary fires

## Performance

Smoke capped (~256 clouds). Fire foci capped (~200). Smoke LLOD culls far detail.

## 6DoF vertical combat loop

One pilot dives and drops bombs; another evades between megastructures, vertical shafts, and underground reactor tunnels. Height is a combat dimension — smoke and fire are navigation and cover.
