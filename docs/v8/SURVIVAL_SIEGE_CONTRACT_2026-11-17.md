# S12 siege: audited design and first non-autostart implementation packet

Pinned source: `6b50273c6896433a2d5caf68991150126e897245`. This document follows
master §8 and `reference/03_GAMEPLAY/SWARM_FACTIONS_SURVIVAL.md` (enemy roles,
stateful pursuit, two rocketeers, warning/defense/escape and no novice softlock).
It proposes work; it does not authorize automatic raids, establish new faction
canon/balance, or claim the complete S12/bestiary/boarding specs are finished.

## Existing implementation, without inventing duplicate infantry

| Source anchor | Actual current behavior and boundary |
| --- | --- |
| `entity/ModEntities.java:53`, `entity/FlightEnemyKind.java` | `drmd:rocket` is an independent `RocketDroneEntity`, default ARTILLERY, with its own existing hull/model. Reuse it for the first **airborne** pair. |
| `ai/AiRole.java:24`, `entity/DroneEntity.java:47` | RPG is a loadout on the gravity-free drone chassis, not a registered ground soldier. No registered ground rocket infantry or Shapes class was found. Those identities remain an explicit content gap; an airborne pair cannot close literal ground infantry. |
| `entity/ModEntities.java:146–200`, `entity/mob/*` | Tripod is a grounded lance/stomp unit; SpiderTurret is MG/laser; Scanner is an airborne three-rocket cycle; Oblivion Seeker is an airborne pre-boss with a four-rocket salvo. Preserve them; do not relabel or clone one as a new cheap rocketeer. |
| `world/threat/ThreatProgress.java:7–10`, `:48–65` | Persisted QUIET/WARNING/SEARCHING/HUNTING/COOLDOWN, real online novice grace and finite search/hunt. There is no SIEGE phase/order. |
| `world/threat/SurvivalThreatDirector.java:54–65`, `:96–102`, `:116–166` | Actual eligible Survival machine death produces an alarm; actual own LOS updates knowledge; the director spawns only ORB. Peaceful/mob-spawn mode, loaded/entity-ticking space, cover, player distance and finite work are respected. |
| `world/threat/SurvivalThreatState.java:12`, `:34–60` | Two lifetime recon actors per owner, eight per world; schema1 preserves players/roster/victim dedup. Loaded absence does not mean death. New automatic replacement waves would violate existing accepted semantics. |
| `entity/DroneEntity.java:101`, `ai/BombardVillageGoal.java:38–56`, `:110`, `:130` | ARTILLERY's priority0 village goal can autonomously find settlements and drop HEAVY_CLUSTER. A blind ORB→ROCKET substitution bypasses the intended limited site/volley. |
| `ai/DroneAi.java:69–114`, `entity/DroneEntity.java:186–213` | Retained combat target XYZ is used for routing/fire; role-homing shots retain a live entity target. A new siege task cannot inherit this as proof of hidden-position-safe site behavior. |
| `world/trap/DefenseTurrets.java:70–108`, `:149–159` | Real kinetic interception now exists, but the exact retained **station** predicate/living fallback attacks Survival players. A recipe is not proof of safe player-owned AA. |
| `world/contraption/mechanism/BodyMountedWeapons.java:46–52`, `:82` | Real permitted lever use and physical guns exist. Automatic mounted shots are owned by the body, not a persisted controlling-player/base identity. Do not invent an ownership ledger from those fields. |
| `world/build/AdaptivePlacement.java:79–115` | Actual successful player placement is authoritative, but there is no durable player-base claim map here. Neither nearest bed/spawnpoint nor arbitrary terrain is an authenticated base target. |

`SURVIVAL_THREAT_2026-11-10.md` proves scoped recon; current integrated proof is
843 units/442 GameTests plus same-JAR clients at the pin. `SURVIVAL_ECONOMY_FINAL_2026-11-17.md`
proves actual crafting, mechanical construction/fire, salvage and repair from
declared stock. Neither proves safe novice siege/ore access/AA balance.

## First packet nominated for implementation review

Implement **non-executable persisted `SiegePlan` intent plus pure transition/
order guards**. Reuse `ThreatProgress.Point`, deadline arithmetic and existing
world-state bounds. This first packet creates **no entities, targets, rays,
notices, delayed raid jobs or automatic ORB→ROCKET selection**. The ordinary
director and existing recon episodes remain unchanged. It is useful prepared
state/order logic, not a completed raid masked behind a feature flag.

Proposed exact files:

* New `world/threat/SiegePlan.java`: pure bounded plan/snapshot/order types;
  no Minecraft entity/world lookup or independent movement/physics engine.
* `world/threat/SurvivalThreatState.java`: optional bounded plan persistence,
  offer/read/remove methods, per-record validation, existing recon data intact.
* New pure tests under `src/test/.../world/threat/SiegePlanTest.java` and codec/
  saved-state tests under the existing GameTest threat package. Root registers
  new ordinary tests after their actual first gate, not from this proposal.
* No change now to `ThreatProgress`'s existing Phase enum/transitions,
  `SurvivalThreatDirector`'s automatic admission, entity registries, role stats,
  faction memory, recipe costs, goals, mechanical controllers or projectile API.

A plan has one existing hostile-machine faction, owner/plan identity, exactly
two reserved slot numbers, one immutable last-observed site snapshot, original
observation/expiry, warning/encounter/cooldown deadlines, and bounded per-slot
emission allowance/last accepted shot identity. Site data includes observer and
actual target-participant UUIDs, world identity, copied material world point,
and optional actual body/cell identity. UUIDs grant no location or firing
authority. No unbounded trajectory, hit, candidate or historical-position list.

Maximum stored plans: eight per world, one per participant; maximum two slots
and one site snapshot per plan. Refuse overflow without eviction/reset of a
live plan or existing cooldown. A plan may compute WARNING/SEARCH/SIEGE/
RETREAT/COOLDOWN intent under explicit pure inputs, but **the production
director never consumes these orders in this packet**. NBT data is never an
activation permit. Unknown/future/corrupt state cannot create a runtime wave.

## Proposed later live contract, requiring separate root review

### Same two-actor allowance and a fresh warning

An eventual fresh siege episode uses **two actual ROCKET actors instead of
the two ORBs for that new plan**. It does not convert existing recon actors,
add another invisible pair, refill a defeated slot or exceed the existing
eight-actor world roster. Legacy/schema1/current episodes stay recon. The
new-plan selector remains absent until reviewed observation/counterplay gates.

Candidate sequence: confirmed eligible alarm + witnessed site → full warning
→ loaded search/deployment → actual site reacquisition → limited siege order
→ physical retreat → cooldown. Use existing lost-sight/escape/defeat/offline/
death/dimension cancellation rules; never refresh a whole encounter deadline
by hiding/reacquiring. Once siege ends, its task must keep fire authority shut
while real actors retreat, even if a target remains visible. Current cleanup's
visible-combat expiry extension cannot silently re-enable endless siege shots.

| Existing value/provenance | Proposed reuse; not accepted new balance |
| --- | --- |
| `GRACE_TICKS=12000` | Preserve actual online Survival novice protection. Offline time cannot spend it. |
| `WARNING_TICKS=400` | Candidate full warning before a new dangerous order, never shortened by late processing/load. |
| `SEARCH_TICKS=1800`, `HUNT_TICKS=1800` | Reuse the existing fixed3600-tick encounter envelope; do not mint another full timer when siege starts. |
| `LOST_SIGHT_TICKS=200`, `COOLDOWN_TICKS=12000` | Preserve finite loss/escape and saved recovery; stale knowledge cannot renew itself. |
| ARTILLERY cost20, cooldown2.5s, damage40/splash30, radius120SU=1.5m, speed2200SU=1.375blocks/tick | Keep actual independent ROCKET loadout. This differs from the stock framework rocket used in the earlier3.75m interception fixture. |
| Existing capacity100, recharge20/s | **Five successful shots per actor** is only a proposed encounter-order bound (`100/20`), not current finite ammunition: real recharge otherwise sustains fire. Do not claim this balance is approved or change global recharge/cost. |
| Director cadence5; players4/candidates8/sight8/spawn1 per pass; actor cap8 | Site work must share declared budgets or receive a separately reviewed measured budget; no unbounded world/player/actor scan. |

Successful actual emission, not trigger attempts or `tryFireAt`'s boolean,
decrements an order allowance. Duplicate/replayed acknowledgement cannot spend
twice or restore shots after save. Delayed requests never bypass the current
role cooldown, energy, obstruction or expiry. No weakened incoming rocket,
new faction technology tier or invisible blanket base protection.

### Authenticated observation, not omniscient base tracking

There is no existing general claim system to read. Proposed first site evidence
is an **actual successfully permitted player interaction with a real modular
mechanism/material**, which a loaded living enemy directly sees. A future
single root-owned bridge after successful `BodyMountedWeapons.useLever` is a
candidate source; an attempted packet, nearby machine or body-owned projectile
does not authenticate the player automatically. Its observer still needs real
loaded terrain/body LOS. This initially covers witnessed mechanical sites,
not every house/cave or a new territory-ownership canon.

Store copied observed coordinates/timestamp. Receiver UUID lookup validates
loaded/alive/same-world/mode/allegiance only. Hidden player/body current pose,
bed location and spawnpoint cannot update a route or fire point. Observation
older than a warning can guide bounded **search**, but cannot authorize fire:
the deployed actors must reacquire actual visible material. Empty, moved,
destroyed or unknown old sites yield search/expiry, not remotely updated aim.

Later `SiegeTask` must exclusively own MOVE/LOOK/**fire** for its tagged pair,
above village bombing/ordinary retained-target combat. It prevents the assigned
pair from autonomous unrelated HEAVY_CLUSTER village runs or fresh hidden XYZ
chase; unassigned ROCKET/default roles retain their existing capabilities.
Reuse actual bounded navigation/boids, role motion, collider/hit and damage
pipelines. Do not setTarget to a proxy, teleport actors, create static hull
copies or call direct base block destruction from the director.

Firing at a material point needs its own reviewed real-weapon admission path.
Do not pass a hidden player as `homeTarget` and call it last-known targeting.
Point-guided vs explicitly fixed-site/dumbfire guidance is a later narrow ABI
decision; neither is silently substituted or accepted by this pure packet.

### Counterplay and the Create-style boundary

Station Point Defense is proven real interception; it is **not** friendly
player defense. Do not tell a novice to place it near themselves, relabel its
hostility, add an all-player ally exception or replace the user's separate
bearing/drive/gun construction route with a prefab cannon. Generic modular
player-AA source/ownership/target policy belongs to root's mechanical scope
and must reuse real emitted rounds/relative CCD if interception is enabled.

Before automatic siege activation require tested, actually obtainable escape,
gun/plasma counterfire, material cover and repair, plus an explicitly correct
player AA route where advertised. A hostile station at a convenient test
distance is not that route. Existing recipes/declared-stock economy proof alone
does not establish novice resource availability or affordability. Do not
auto-grant infinite ammunition, resistance, respawn restorations or disposable
shield structures and call that survival balance.

## Save upgrade and default compatibility for the first packet

Keep the existing top-level schema1 and players/actors/victims exactly as-is.
Add an optional, separately versioned `siegePlans` compound (schema1, max8
non-autostart records). Missing compound means no plans and ordinary recon
defaults. Old readers ignore only this non-executable extension; they do not
lose existing cooldown/roster or receive new actors. No enum name unknown to
the old `ThreatProgress.Phase.valueOf` goes into a legacy player record.

Validate bounds, finite points, UUID/world identity, timestamps/deadlines,
two-slot allowance and per-record version. Isolate corrupt neighbours; refuse
duplicate plan/actor assignment and amplification on reload. Preserve an
unsupported extension opaquely/read-only rather than overwrite it or reset the
ordinary threat ledger. Pure snapshots round-trip intent; loading cannot
start a warning, advance an offline timer or summon actors.

Runtime-active siege is a later persistence upgrade: the current top-level
reader returns an empty state for schema>1, so a blind bump would silently
lose legacy progress under older readers. Decide migration/backup/downgrade
semantics with actual saved fixtures before ever persisting active new phases.
No compatibility claim for that later format is made by an ignored intent
extension. Existing `FactionMemory` enclave reputations are separate and stay
untouched; no invented five-tier faction relationship is written.

## First gates and later natural Survival acceptance

For the nominated non-autostart packet, freeze expected codec/state behavior
before implementation. A raw versioned-plan NBT roundtrip through unchanged
state can reproduce missing persistence without disabling production hooks;
it is codec feature absence, not a live siege/AI negative. Pure first cases
must cover full warning/grace arithmetic, finite original deadline, unchanged
hidden last point, two lifetime slots, five proposed emission slots each,
duplicate-shot rejection, reload continuity, expiry/escape/death cancellation,
invalid/future state and max8 plan refusal. Pure input tests are not LOS proof.

Saved-state gates use actual PersistentState files plus an original schema1
fixture: legacy progress/actors/dedup exact, valid plans roundtrip once, corrupt
neighbour isolated, unsupported extension preserved. One normal connected
Survival fixture stores a plan and advances ordinary real world ticks, proving
no new rocket, notice, village bombing, actor slot or production activation.
Existing nine threat units/five real recon cases remain unchanged controls.

Before a later runtime claim, freeze and run genuine natural cases:

1. An actual eligible player action and living observer's own site LOS produce
   intent; Creative/Spectator/Peaceful/disabled rules/unknown/no sight/old corpse
   aliases cannot. Record exact sight/event provenance, not injected `verified`.
2. An unmodified connected Survival player gets the full actual warning; no
   actor/missile appears early. Pre-aging only the old novice state is declared
   fixture setup; it is not a ten-minute novice playthrough.
3. Exactly two registered ROCKET identities naturally approach the copied site,
   reacquire it and emit real finite current-loadout shots. An unprotected
   constructed test base suffers actual functional/material breach, not only
   particles or a direct damage helper. No incidental village bomb appears.
4. Hiding/moving the player or ship under real cover preserves last-known
   knowledge and blocks illegal fire. Natural escape/real defeat/exhaustion
   lead to physical retreat and saved cooldown; no replacement pair or resumed
   shots after reopening. Unloaded absence cannot free slots.
5. Prepared cover, actually crafted counterfire/repair and root's real modular
   AA each have measured outcomes: actual emitted/intercepted/damaging shots,
   lost blocks, remaining player HP/resources and time to breach/escape.
   Balance cases use normal20HP Survival and finite inventory; invulnerable or
   high-resistance fixtures can prove perception only, not novice survivability.
6. A dedicated real-client input route demonstrates warning presentation and
   physical escape/defense. Test setup pose is explicit; no repeated player
   teleport/follow, manual entity ticks, synthetic hit or free resource grant
   substitutes for the transaction. Then save/reopen and current full/native
   regression gates precede any installable package claim.

No first-packet pass may be promoted to two-ground-rocketeer, autonomous siege,
friendly player AA, complete economy or whole-spec acceptance. Root reviews
this contract and the first bounded packet before implementation or automatic
raid policy changes; no Java run is authorized by this design document.
