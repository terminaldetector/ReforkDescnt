# SWARM / SHAPES / STRIFE / SURVIVAL THREAT SYSTEM

## Enemies and roles
- `ORB`: compact, highly mobile 6DoF unit. Local dodging, swarm flock, attacks and collision avoidance. Original design direction: simple geometry, low cost per agent.
- `ROCKET`: missile-focused aircraft/drone, anti-base pressure, controlled firing, retreat/reload.
- `JET`: high-speed pursuit/strike unit; velocity-based turning and acceleration; respect terrain collision and camera speed.
- `Shapes`: Endermen reimagined as armed organized cybernetic infantry from visual Texhnolyze reference. Vanilla teleport replaced by actual team movement, gun/energy fire, cover and coordination. Teleport becomes separate flying enemy role.
- `Heavy Operator`: mobile compact laser emplacement plus shoulder missile mounts, modular but not full ship.
- Baseline factions/NPCs: armed troops, guard posts, commanders, power infrastructure and civilian/noncombatant simulation.

## Threat Director and emergent encounter
Core user-defined vignette: after time playing Survival, an enemy faction declares pursuit and sends drones. Enemy behaviour must be stateful, fair enough to be counter-playable, and mechanically meaningful. Suggested Director states: `quiet → suspicious → aware → searching → skirmishing → pursuing → siege → recovery/cooldown`.
Signals: player attacks, faction reputation, detection confidence, line of sight, transmitted alarm, active base vulnerabilities, biome and progression tier. Counter-play: concealment, jammer, AA weapon, intercept drone, cover, decoy, repairs, escape via underground/space, capture enemy equipment.

**Balance:** two enemy rocketeers may burn down an unprotected base, but novice Survival must not become irreversible grief with no warning. Minimum progression resource path for gun/plasma AA, recoverable materials and simple crafting; no factories with complicated recipes.

## Boids and large-scale architecture
Graph: `FactionDirector → SquadCoordinator → 3D spatial hash / hierarchical groups → target assignments → near-range boids/steering → physics/contact → rendering LOD`. Use neighbor radius and capped `maxNeighbors`, bounded work scheduling. Far squads aggregated, not full per-agent physics at 10000. ECS/data oriented may help, but do not rewrite Minecraft entity framework without a measured bottleneck.

**Bench target ladder:** 32 / 128 / 256 / 512 / 1024 / 5000 / 10000. First mandatory acceptance from older tests: 128 active drones, >=96 genuinely moving; verify not just spawn count, AI responses and persistence. Above 1024 research scalability with mixed-quality simulation and impostors (do not claim 10000 fully physical).

## Shooter mission framework
At least three distinct test missions after core stability: `Convoy Ambush` (route and anti-armor), `Reactor Boarding` (UFO), `Lab Infiltration` (mines/terraforming/gravity). Missions use triggers, objectives, extraction, difficulty and faction state. Scriptable in data with code hooks, not editing dozens of Java classes per mission.

## Faction and roguelite roadmap
Five development stages from earlier branch are a **known requirement but unspecified content**; make versioned candidate table and validate before implementing. Add region factions, rising tech, civil order and sabotage. Roguelite variants in special areas only after balanced base survival; preserve vanilla exploration freedom.
