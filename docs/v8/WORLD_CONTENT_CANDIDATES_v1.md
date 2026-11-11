# World content and faction capabilities — candidate v1

Status: **unapproved design proposal**, reconstructed 2026-11-11 from the reviewed
source/spec audit and temporary draft. No runtime tiers, new canonical factions,
balance activation, generated content or G5 completion is implied.

Requirements: master v8 §8–9, `SWARM_FACTIONS_SURVIVAL.md`,
`PLANET_RING_CITADEL_LABS.md`, `CITY_ASSET_PIPELINE.md`, TASKS S12–S14.
Implementation audit: `4b2e243`; scoped recon proof: `2446227`.

## Five proposed technology capabilities (S14-T01)

These are candidates for editorial review, not a chronology or a mapping of
existing Military/Engineer/Miner/Cultist/Scavenger enclave origins. Existing
identity and reputation records must survive any eventual versioned migration.
No proposal enters recipes, spawn choice or balance before author approval.

| Candidate | Economy / materials | Weapons / AA | Transport / patrols | Structures / missions | Visual dialect |
|---|---|---|---|---|---|
| C1 — repaired salvage | Vanilla iron/copper, recovered components, recoverable basic repair | Kinetic handheld guns, manual interception and cover | Foot patrols and repaired local machines | Salvage depot, maintenance shaft; rescue/recovery | Large repaired panels, exposed structural braces |
| C2 — reliable local power | Shared power stores; short direct crafting paths | Reliable laser/plasma supply, player-built tracking AA | Small coordinated groups and service craft | Powered outpost, pump station; repair/sabotage | Modular housings and visible power paths |
| C3 — vector flight | Flight components, captured drones, repair stock | ORB escorts, finite missiles and interception | Real 6DoF recon, pursuit and escorts | Dock, sensor station, underground flight route | Compact volumes with readable thrust/sensor orientation |
| C4 — mobile construction | Reusable BlockBody modules; repair and capture | Separate gun, bearing and drive mechanisms; rocket support | Convoys and independently moving hull modules | Mothership service bay, ring dock; boarding/convoy | Hard architecture, visible joints and structural weaknesses |
| C5 — regional infrastructure | Regional power/control dependencies, replaceable components | Heavy laser operator and layered AA | Citadel-directed groups; distant squads aggregated | Citadel/ring control hubs; infrastructure infiltration | Strong silhouette hierarchy, coherent regional scale |

`threatBudget` remains a separately versioned numeric tuning profile after real AI
benchmarks. Capability is not an unlimited spawning multiplier. Players can enter,
escape, capture and alter relations without a forced linear campaign or global
novice siege. No Create production chain is proposed.

## Mission data contract (S14-T05)

All three missions run inside the same actual cubic world. Bounds and routes use
current preset geometry, loaded availability and real local BlockBody coordinates.
Templates are original blockouts; external sources require recorded permissions.

Proposed schema v1:

- Identity: `missionId`, `contentVersion`, seed, world-storage identity, 3D region
  and anchor references. Unavailable storage is distinct from air.
- Participants: saved UUIDs, explicit selected side/faction references and access
  rules. Existing `FactionMemory` remains reputation owner.
- States: `offered → active → objective_complete → extracting → completed`, plus
  `failed` and `abandoned`. Unavailable volumes suspend evaluation; participation
  cannot force-load a continent or silently keep distant full simulation alive.
- Objectives: predicates on actual entities, local body cells, control state and
  traversed physical volumes. Cosmetic markers or unloaded targets cannot count
  as kills, sabotage or completed boarding.
- Extraction: reachable route/volume, evaluated in the owning body's frame when
  moving. No forced teleport or separate interior dimension.
- Settlement: persisted one-time completion/claim token; rewards and reputation
  are idempotent across reload, disconnect and repeated trigger callbacks.
- Limits: capped active missions, participants, enemies and per-tick predicates;
  full simulation quality is retained for active nearby participants.

### Convoy Ambush / Convoy Defense

One versioned contract supports two explicit sides, retaining both requested
vignettes. Offer the route and side before acceptance. Prerequisite: physically
traversable route and an actual moving cargo/control hull.

Ambush: disable a real drive/control module, board or capture cargo, then withdraw.
Destroying all cargo is a distinct outcome, not the same success predicate.
Defense: keep designated cargo/control functional until the real hull crosses the
route's final volume. No scripted invulnerability forces a result.

Branches: intercept escorts, sabotage drive, capture intact, withdraw, or defend
with player-built mechanisms. Extraction uses an accessible depot/withdrawal
corridor. Failure follows actual cargo/control destruction or loss beyond the
allowed route. Test mid-route reload, fragmentation, capture without velocity
reset, escort deaths and destroyed route markers; apply settlement once.

### Reactor Boarding

Offer an actual moving UFO/BlockBody with functioning control/reactor cells.
Prerequisite: Dynamic Boarding/contact/6DoF gates and at least two usable access
routes, via existing opening or destructible hull.

Objectives: reach hull → cross actual interior access volume → interact with real
reactor/control cells → extract or claim control. Capture reuses existing controls
and preserves body identity/momentum. Shutdown and destruction are separately
configured outcomes; unrelated damage requires actual state and attribution.

Branches: stealth access, blast passage, disable defenses, capture or withdraw.
Unavailable/destroyed required cores cannot fabricate success. Test full rotation,
broken entry, fragmented core, local gravity loss, disconnect and reload during
capture. Do not replace the interior with a static copy or another dimension.

### Lab Infiltration

Offer an original underground lab with shaft/surface access. Prerequisite: actual
cubic underground volume and safe entry/exit; retired Advanced generation remains
off. Objectives: enter facility → access a real system → retrieve a recorded
objective or disable an actual module → extract.

Mines, drilling and local gravity are optional tools with server permissions and
resource cost. Destroyed doors open routes only when real collision changes;
severed power affects mechanisms rather than advancing dialogue alone.

Branches: cover, breach, shaft, gravity change, restore power or abandon. Test a
breached alternate route, already-destroyed objective, real mine detonation,
blocked extraction and idempotent settlement after reload.

## First gated world blockout (S13)

Production remains deferred until Cubic, LOD and BlockBody core gates. First
candidate fixture: one original 256×256 district, eight building archetypes, one
lab shaft, one surface repair depot and an unobstructed 3D flight route. Store
seed, preset bounds, placement version, coordinates and material categories in
`CITY_FIXTURE.json`. Do not reactivate infinite city/sky-junk generation.

Measure one explosive, thrown structural block, 32 then 128 real drones, and two
rocket infantry only after warning/defense/escape progression is proven. Record
MSPT, FPS, debris, loaded cubes and serialization before/after destruction and
reload. Near physics, mid merged geometry and far silhouettes must use the same
authored geometry and orientation.

Existing recipes cover gun/plasma/PD/repair components. Next economy work should
prove their actual Survival acquisition, use and recovery instead of introducing
a duplicate resource hierarchy. Existing `EnclaveQuest` text offers do not prove
this mission runtime; none of the above mission systems is claimed implemented.
