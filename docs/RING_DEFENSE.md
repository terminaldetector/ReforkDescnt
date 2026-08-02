# Ring defense, cyclic lasers, weightless fluid

## Turret rings + shield projectors

`RingDefenseStructures.placeTurretRing` embeds laser / plasma / PD / volume turrets in a closed deepslate casemate ring — not loose pads.

`placeShieldCross` puts iron+crystal projectors on the four approaches and registers them in `ShieldProjectors` for nearby 6DoF shield regen boost.

Used by: megacity (plate + arena), lunar hub, End giga-reactor.

## Cyclic laser barriers (minecart)

`LaserBarrierCartEntity` rides a powered-rail square loop and sweeps dual beams (inward + UP) via `LaserBeams`.

Build kit: `drmd:cyclic_laser_kit`
- Use on block → rail loop + 2 carts
- Sneak-use → loop + full turret ring + shield cross

Also seeded in megacity / lunar / End.

## Weightless fluid (End / atmosphere edge)

`AtmosphereRules.isWeightlessFluid` — End dimension, orbital / End column bands, near-space.

- `FlowableFluidMixin` — fluid velocity = 0 (no preferred down)
- `EntityFluidMixin` — fluid push loses gravity sink
- Water/lava kept as floating blobs (particles); thin-air still vaporizes free water below the edge
- Flight: orbital / End band = vacuum (no idle gravity), same as End dim
