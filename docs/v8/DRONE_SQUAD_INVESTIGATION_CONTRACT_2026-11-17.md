# S11: scout-to-wingman investigation contract (pre-production)

Audit/base: `1a8593846bed3b02d03d68236c6698d26a5116d4`.
This is a new missing feature, not a claim that existing 3D combat is absent.
Master §8 and `reference/03_GAMEPLAY/SWARM_FACTIONS_SURVIVAL.md:18`
require squad tasks between the director and local steering. Current
`HostileEnvironment:52–71` installs independent volume target goals;
`DroneEntity:initGoals` has bombardment/combat/look goals, and
`DroneSwarmAi:175–192` indexes positions/velocities for local boids. None passes
an observed last-known enemy position to a targetless wingman.

## Resulting behavior

A flying scout that naturally acquired a legitimate enemy and currently has its
own loaded, unobstructed line of sight can send an investigation message to
nearby eligible allies. A targetless receiver investigates that immutable
position through the existing 3D navigation/collision/route budget. It acquires
and fires only through normal own-LOS target goals. A mailbox never calls
`setTarget`, fires a weapon, or supplies the hidden enemy's current position.

The implementation must use current `HostileEnvironment` and vanilla
target/allegiance predicates. Entity class identity is a participant filter,
not a new declaration of allegiance or invented faction canon. Existing role
statistics, preferred ranges, bombardment priority, critical-health behavior,
models and normal combat remain unchanged. A real existing combat target takes
priority over an investigation goal.

## Authoritative observation and bounds

- Producer cadence: once per **five** normal server ticks per scout, with no
  extra entity simulation. The observation must pass loaded collision-halo,
  real world/body occlusion and own-visibility checks before reading the target
  position for publication. Unknown space or excess physical occluders cannot
  become clear LOS. Reuse existing collision queries, do not edit their engine.
- Ally query: reuse `DroneSwarmAi`'s once-per-world-frame spatial index, at most
  **32** returned allies and **96** examined candidates. No new per-drone scan
  of all world entities and no all-pairs target pass.
- Per-world store: at most **256 receiver mailboxes**, one immutable observation
  per receiver. Newer accepted observations replace older ones. At capacity,
  bounded oldest-entry replacement admits new work without a queue.
- Message: sender UUID, target UUID, copied XYZ, observation tick and fixed
  `expiresAt = observedAt + 60`. Reads and UUID validation never refresh expiry.
  No world, entity, mutable position or navigation object in the pure store.
- Maintenance: no more than **32** examined entries in one cleanup pass, no
  more than one pass per five world ticks. Record actual occupancy, query and
  cleanup work. The whole store remains bounded even before a cleanup pass.
- Receiver UUID lookups may validate only loaded presence, alive/removed state,
  world, game mode and actual allegiance. They must not read or derive the
  hidden target's current XYZ, block position, bounding box, velocity or aim.
- The goal keeps the accepted coordinate snapshot and original expiry, releases
  its existing navigation reservation when stopped, and stops when its normal
  target goal wins, its message expires or source/target eligibility is lost.
  No teleport, direct target assignment, synthetic firing or physics rewrites.
- Stores are separate for each actual server world. Unavailable volumes must
  not be loaded by observation, delivery or validation. Existing movement and
  collision safety remains authoritative.

## Pre-production acceptance plan

The first test-only checkpoint must retain the following assertions while the
feature is absent; no gameplay implementation precedes its first retained
focused negative. Fixtures use actual typed entities, real normal target/goal
ticks and test-owned chunk tickets. They never assign a target to a drone.

1. ORB scout naturally locks a real enemy; an initially occluded ORB wingman
   receives the remembered coordinate, moves in XYZ around indestructible
   cover, naturally acquires its own target and emits its own real projectile.
2. Vertical sharing: a scout sees an enemy above the receiver's normal search
   volume; a real ROCKET/JET receiver investigates upward and then acquires
   through the unchanged volume target goal. This must be movement, not an
   entity spawn/target counter.
3. After a real publication, move the victim once into opaque test-owned cover.
   While it remains loaded/alive but unseen, message XYZ and observation time
   remain the old values and expire after the original 60 ticks. No renewed
   pursuit based on hidden current coordinates.
4. No observing scout means no mailbox-derived movement or shot. Removing the
   scout/target, switching a player target to creative/spectator, or making it
   an actual ally invalidates a received task without firing through cover.
5. Another server world must not expose the receiver's message. Unloaded
   observations cannot publish or materialize remote columns/cubes.
6. Actual query metrics remain within 32/96 and cleanup within 32 per five-tick
   pass. Pure-store tests flood past 256 receivers, read repeatedly near expiry,
   reject nonfinite/stale input and verify bounded eviction/cleanup and fixed
   coordinate/time snapshots. No counters may report success without the
   actual feature being present.

Only one GameTest task driver is registered at each test entry. It owns its
delays/observations; no scheduler registration from a callback. Cleanup removes
only fixture actors/projectiles/blocks/tickets. Source, first failures, logs,
XML and exact artifacts remain separate from later corrected results.

## Owned surface and exclusions

Planned owned files: new `ai/DroneSquadCoordinator.java`,
`ai/DroneSquadMailbox.java`, `ai/SquadInvestigateGoal.java`; narrow producer and
goal attachment in `DroneEntity`, bounded ally-query exposure in
`DroneSwarmAi`; new GameTest/pure-store tests and this scoped report.
`HostileEnvironment`, the faction director, physics/ActorFrames, renderer,
city and mechanical blocks are read-only dependencies. Central test manifest
registration belongs to root. Java/native runs require the shared lease.

This increment does not close full tactical allocation, faction progression,
new Shapes/Heavy Operator species, anti-missile gameplay, 256+ tier performance,
far aggregation, thermal drills, structured cluster launchers or either whole
technical specification. Existing key-route checkpoint `5f1ee2e` and all its
evidence are preserved on their original branch.
