# WORLD DESIGN — SCI-FI PLANET / UNDERGROUND / ASTEROID FIELD / CITADEL
**Status:** AUTHOR-APPROVED direction 29.10, **DEFERRED production** until CubicWorld, LOD and BlockBody core gates pass.

## Vertical spatial macrograph
```text
           [CITADEL]   <- faction/state hub, armed stations and elite troops
          __/     \__
  damaged techno-ring / orbital debris / stations / satellite fields
      floating asteroids, old shipyards, battle wreckage
         near-orbit / haze / thinning atmospheric layer
---------------------------------------------------------------
           sky gradient + cloud ceiling / LOD transition
                    SURFACE
    settlements / cities / state territories / laboratories
---------------------------------------------------------------
        UNDERGROUND LABS / TUNNELS / INDUSTRIAL VAULTS
                mantle / hot cavities
                PLANETARY CORE
```
Not literal Earth scale required and not separate unloaded dimension by default. Coordinates/y ranges should come from cubic preset bounds, not hard-coded fake Y levels. Distances are designed for short compelling missions, with huge structures at high LOD stages.

## Zones
**Core**: dense tectonic materials, large voids, lava/geothermal, old machinery, zone challenges and hazards; avoid giant ungenerated void leading player to vanilla death. Death at 10:26 in old video establishes need for true boundary and collision test.
**Underground**: tunnels with smooth/rough variants, industrial reactor vaults, xenotech archaeology, underground labs inspired by Xenogears. Ownership of source concepts is reference-only; no copyrighted ripped maps.
**Surface**: Minecraft existing biomes adapted to sci-fi ecology, faction outposts, cities, strategic resources, repair basins, facility entrances and rover corridors. Hybrid geometry makes natural terrain smooth and human technology crisp.
**Sky**: Citadel as navigable fief/hub, game-state factions, flights, ruins, orbital weapons. The vast damaged ring surrounds planet; distant silhouette remains consistent in map orientation, no randomly floating junk. Asteroids have near/mid/far LOD; only near bodies simulate physics, far are lightweight meshes/impostors. Abandoned stations can be boarded and sabotaged.

## Procedural construction / performance
1. Use a deterministic world seed and region tickets to place major landmarks across X/Y/Z in a multi-scale planning graph.
2. Large authored anchors: Citadel, ring fragments, major labs, mothership dock; procedural districts and modular repeated sections around them.
3. Air-to-ground horizon: continuous fog/transmittance and ground LOD decreasing with altitude, atmosphere to dark upper layer, asteroids integrated with silhouette not random particles.
4. Keep generated structures and authored templates versioned with metadata, permissions and compatibility migration. Avoid past BE problem: no stale `mob_spawner` NBT after replacing spawner with air.
5. Navigation routes connect shafts, surface, orbit and ring. Portals only special optional spatial mechanics.
6. Benchmark generation time, loaded cubes, persistent debris, GPU face count, cave mesh, lighting updates after explosions.

## Five faction tiers (not designed yet)
Do not invent canonical names or exact chronology in code. Data schema proposed: `tierId, economy, weapons, transport, patrols, structures, anti-air, threatBudget, materials, missions, visualDialect`; later editorial acceptance necessary. Factions may occupy Citadel, surface, underworld, ring; conflict and captured devices affect player progression.

## Reference hierarchy
System Shock (interactive systems), Strife (faction worlds), Xenogears (underworld architecture), Teardown (destructible structures), Descent/Terminal Velocity (spatial flight), Minecraft (modifiable base terrain). License-safe independent reinterpretation, not direct asset ripping.
