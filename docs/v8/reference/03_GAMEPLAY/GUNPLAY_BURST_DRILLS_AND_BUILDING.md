# GUNPLAY / CLUSTER PROJECTILES / THREE GRAVITY GUNS / DRILLS

## First-person weapons
Reconstruct all inventory/held-weapon transforms and the history of Workshop weapon mesh/texture commits of 24.10. Eye-relative viewmodel must stay stable while camera performs arbitrary 6DoF roll; hand clipping (user screenshot #6) is independent P1. Legacy many identical item icons get separate models/silhouettes. Animated sights and muzzle transform in native and PS1 profiles, with no inconsistent hit positions.

## Cluster burst from first person
Accepted sketched idea: carrier leaves muzzle, then at programmed distance/time splits in a **structured, visible spatial pattern**, not random pellet soup. Parameters: number of stages, directional bias, radial ring, stagger/delay, arming time, projectile lifetime, distance safety, target orientation, optional tracking, impact damage. Two historical cassette counts 7/14 exist in old code; validate in client. Avoid unbounded recursive bursts. Each projectile uses bounded updates and single authority, impacts apply terrain damage/debris without duplicating FX.

### Proposed state model
`Inert -> CarrierFlight -> SplitArmed -> SubmunitionFlight -> Impact/Detonate -> Cleanup`; add `Expired`/`Disarmed`. Bounded fan-out and per-chunk rate limiter, no permanent ticking or hidden entity accumulation. Visual proof must show split and physical impacts from FIRST PERSON.

## Gravity weapons
`SwarmGrabber` captures and launches several physical blocks/objects; `HeavyRailThrower` sends large mass along controlled rail impulse; `GravityShifter` redirects local gravity to throw enemies and block objects. They share energy/physics query contracts but keep distinct combat roles. Verify prior PR commits, preserve good solver, never place all in one undifferentiated raygun mode.

## Drills / terrain melt
Weak: discrete chunky mining with honest cubic collision. Strong: heated face, partially melted transition, circular tunnel mesh, adaptive topology, server collision. Tunnel radius selectable by class/upgrade (not huge engineering tree). Update light, fluids, block saving, material response/thermal palette. Mines into underground laboratory without exposing vanilla invisible wall. Test against mixed materials, diagonals, cave intersections and near world boundaries.

## Building
Fast simple construction: batch placement, selected material, minimal UI. No Create-style logistics/automation. Server verifies permissions, cost, survival inventory, invalid materials, and max batch size; client renders preview. Prevent creative placement exploit in Survival. No direct editing of unloaded cubes from render thread.

## Acceptance
Inventory/hand 6DoF, projectile born/moves/hits, cluster geometry first person, drill time-course, save/load drilled hole, no missing textures, 0 ghost collision, measurable TPS and no run-away FX.
