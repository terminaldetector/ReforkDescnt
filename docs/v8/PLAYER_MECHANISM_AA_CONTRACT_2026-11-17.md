# Player-built AA: first manual-axis contract

Status: **source audit and proposed contract only; no new main code or runtime
acceptance**. Root approved the manual-axis feature boundary before this document:
use the existing separate gun, bearing and drive blocks, an authorized wrench
interaction and a real attached lever. Automatic motor aiming is a subsequent
increment. No automatic raid or SiegePlan consumer is part of this packet.

## Pinned source and existing behavior

Audit production baseline: accepted canonical `20ef2c4c8fd551e4326d2841601f9c56fa4a3a0a`.
Relevant mechanism, projectile, WeaponCore and point-defense source files are
byte-identical to this lane's accepted `6b50273` base. Local documentation starts
after sealed SiegePlan checkpoint `d585f68`; that metadata-only protocol is not a
live siege. Canonical recipe/economy changes after `6b50273` are considered below.

| Source anchor | Existing implementation and exact missing boundary |
| --- | --- |
| `world/contraption/mechanism/BodyMountedWeapons.java:18,39,63,70,89` | Real local levers, 64 checks/32 shots/32 bodies per world, 16 checks per body; material-cell cooldown migration; eight-tick laser and 24-tick rocket cadence; schema1 cooldown save. No owner or AA mode. |
| `world/contraption/mechanism/MountedWeaponEmitter.java:17-35` | Six material directions, real rotated muzzle, loaded muzzle clearance, terrain/own/other-body checks, inherited surface velocity, physical recoil and body-owned projectile. No target acquisition or intercept marker. |
| `world/contraption/mechanism/MechanicalBearingSystem.java:36,56,64,90,119,144` | Wrench eligibility/reach/permissions, real two-body MOTOR/SLIDER joints, separate drives, bounded speed/force, passive coast on missing/competing drive. No player ownership ledger or generic automatic aiming. |
| `world/contraption/BodyBlockInteraction.java:80-101` | Normal revisioned local USE packet, actual nearest material ray, game mode/reach/modify permission, four-packet budget/four-tick action interval. This is the interaction route to reuse. |
| `world/contraption/BlockBodyEntity.java:381,386-387,544-557` | Post-commit material notification, current usable-block dispatch, mounted cooldown persistence. Gun cells currently have no wrench-use dispatcher. |
| `world/trap/PointDefenseInterception.java:65-72,74-132,167-210` | Real stationary emitted rounds, existing station policy, loaded target/owner admission, relative CCD, earlier cover, conservative birth guard and END-world deferred round terminal hits. Static source only. |
| `entity/ProjectileEntity.java:89-151,264-270,597-628` | Optional static DefenseSource, exact tagged-round integration/shared hit paths, old untagged defaults. Static tag requires null owner and KINETIC. Body-owned ENERGY bolts need a distinct admitted source kind. |
| `weapon/core/WeaponCore.java:123-169` | Validated ownerless anchored overload reuses the private original spawn tail. Ordinary owner API still rejects null. No mounted-defense factory/provenance overload. |
| `ai/HostileEnvironment.java:27-42,52-57` | Existing machine/End allegiance and actual hostile-to-living policy; no general player-base ownership or PvP relationship map. |
| `entity/DroneEntity.java:186-214`; `entity/FlightEnemyKind.java:10` | The real registered ROCKET defaults to ARTILLERY; its own `tryFireAt` consumes 20 energy, sets its 2.5-second cooldown and emits the authored homing 40-direct/30-splash, 1.5m-radius rocket at 1.375 blocks/tick. This differs from the earlier station test's framework ROCKET 3.75m-radius config. |

The registered station `point_defense_turret` retains its existing hostile
living-target fallback and player-targeting policy. It is **not** friendly base
defense and must not be renamed, silently claimed or repurposed by this work.
Existing UFO/reference modules remain regression cases, not the player assembly
workflow. No new turret prefab, controller entity, rigid-body engine, registry ID,
Create dependency or per-block rigid body is needed.

## Proposed player action and persistent identity

1. The player crafts and places existing separate `mechanical_bearing`,
   `rotary_drive`, `ship_laser` and vanilla lever blocks, and assembles/links the
   desired ordinary physical bodies with `mechanism_wrench`. One or two actual
   bearing axes can orient the barrel using the existing manual drive controls.
2. An ordinary validated wrench USE on the actual `ship_laser` cell explicitly
   claims that **cell** and enables AA mode. An owner-only repeated action returns
   it to ordinary laser mode and removes the claim. The action requires current
   ray/revision, loaded registered body, reach, alive unmounted player, supported
   game mode, main-hand wrench and current `canPlayerModifyAt` permission. There
   is no inventory-wide or UI-checkbox authority shortcut.
3. An already claimed gun cannot be seized by another nearby player, including a
   teammate or Creative player. This small packet grants no new admin bypass or
   capture rule. Returning to ordinary mode permits a later explicit claim by
   another player. Mining the material remains governed by the existing world
   permissions; this is not a land-protection mod.
4. The actual attached lever still gates firing. Wrong support, lever OFF/absent,
   blocked/unavailable muzzle or missing gun admits no AA shot. AA mode with no
   admitted missile does not fall through to ordinary firing. Ordinary unclaimed
   guns retain their exact current behavior and cadence.

Claim data belongs to `BodyMountedWeapons`, keyed by actual local gun cell:
`{schema, principalUUID, claimUUID, localCell}`. One transient target UUID may be
considered during a firing opportunity; no persistent live target list, base map,
team copy or arbitrary protected-region definition is introduced. Entries are
bounded by actual admitted weapon cells, at most `BlockBodyEntity.MAX_BLOCKS`;
invalid local cells, duplicate identities and non-laser material cannot activate.

Packet authority uses the **current** shape revision. A claim is not invalidated
by an unrelated neighboring material edit: it follows the continuous gun cell,
identified by its claim token. Any actual replacement/destruction/reorientation
of that gun revokes its claim. Failed/rolled-back edits do not mutate claims.
Fracture moves a claim and its existing cooldown to exactly one fragment only
when that same intact material cell moved there at the successful commit boundary;
the parent loses it. Removed material and independently placed replacement guns
never inherit a claim merely by reusing coordinates. Lever removal disables
firing without resetting the gun's cooldown or claiming another cell.

Use an optional versioned subcompound inside existing mounted schema1. Missing
data leaves every old gun ordinary. Unsupported/oversized/corrupt optional data
is non-authoritative and preserved as a bounded raw owned compound where feasible;
it must not prevent legacy cooldown restoration or invent an owner. No new
global chunk loading, persistent-state registry, networking event or automatic
conversion of old saves is required. Persisted claims contain UUIDs/data only,
never a live player reference.

## Proposed player-friendly source policy

This first policy protects a loaded active Survival principal against the
existing combat-machine/End faction, reusing its existing policy rather than
inventing factions, friendly fire switches or arbitrary player hostility:

```
principal: actual loaded alive ServerPlayerEntity, same ServerWorld, SURVIVAL
shooter: actual loaded alive LivingEntity, same ServerWorld
HostileEnvironment.isAlly(shooter)
&& HostileEnvironment.isHostileTarget(shooter, principal)
&& !shooter.isTeammate(principal)
&& shooter.canTarget(principal)
```

The first condition uses the already authored enemy allegiance. It does not
declare every `DroneEntity` allied to the player or every `MobEntity` hostile.
The second method by itself is insufficient: it accepts generic living self
arguments, so it must not classify a cow as an enemy rocket launcher. Explicit
loaded/alive checks precede `isAlly`, whose null/dead behavior is intentionally
permissive for its original callers. Unknown/unresolved/null owners, ordinary
animals, owner/teammate player missiles and body-owned player missiles are not
admitted. A teammate machine is rejected even if its normal faction could target
the principal. General PvP and projectiles emitted by unknown nonliving launchers
remain outside this first policy.

SURVIVAL-only firing admission deliberately reuses the existing enemy-versus-
Survival contract. Creative and Spectator are excluded by the current hostile
target predicate; Adventure has no reviewed counterplay/progression contract.
Creative may configure a claim through the normal supported construction path,
but cannot substitute for the active Survival principal in firing acceptance.
This is a narrow first Survival AA mode, not full/default AA for every mode or
every owner/alliance type.

Principal logout, death, removal, world change or exit from Survival pauses new
launches and pair-contact authorization without clearing the saved claim. There
is no fire-on-login catch-up or queued missile. Returning to valid Survival may
resume at the existing retained cooldown. The gun must not fall back to ordinary
unconditional laser fire merely because its AA principal is unavailable.

An already emitted bolt keeps normal physical movement, lifetime and ordinary
cover/actor hits when authority becomes invalid; it is not erased or frozen.
Its missile-specific contact authorization fails closed. A destroyed/detached
source does not retroactively disarm a bolt that already left it. Restoration is
stricter: only an already loaded matching current source/claim, principal and
hostile missile may restore AA authority; otherwise the saved bolt remains an
ordinary body-owned bolt. Offline persistent defense, arbitrary base territory,
player-hostile PvP policy and delegation/capture are explicit later decisions.

## Real barrel and shared physical interception

Prefer the actual existing `ship_laser` ENERGY bolt rather than silently turning
the block into a new kinetic gun. Keep its existing exact values: muzzle `.65`
along local facing, speed `2.5 blocks/tick`, damage8, no splash, cyan model/visual
scale `.75`, life4 seconds, point recoil24 and eight-tick successful-shot cooldown.
Its owner remains the **real firing BlockBody**, preserving existing body damage
attribution/owner-contact semantics; the player UUID is separate admission
provenance, not a substituted projectile owner. No new energy/ammo cost or faster
cadence is inferred from the word AA.

The bolt launch direction is exactly the current rotated material barrel axis.
Inherited velocity remains `body.surfaceVelocityAt(muzzle) * .05`. Neither the
emitter nor target selection may bend the shot toward a computed lead, rotate
the body, assign a joint pose/velocity or give the material a fake visual aim.
Existing drive speed/torque/coast and manual controls remain unchanged. A useful
incoming path must intersect the current fixed-axis bolt trajectory; a side or
rear target must not cause a shot around the barrel. Bounded latest-velocity
prediction may select a possible interception, but is not hit authority and does
not promise that a later steering missile cannot evade it.

Tagged bolts retain the existing ordinary terrain and living/PhysicsTarget
collision rules, including actor expansion, owner filtering and real reflective
block behavior. Friendly living cover may be hit as with the ordinary laser;
this packet does not grant friendly-living immunity. Ordinary friendly missiles
are neither targeted nor deleted by this service; the original projectile path
does not collide projectile-to-projectile without admitted defensive provenance.
The normal tracked velocity, tracer and shot FX remain unchanged. The existing
source body's ordinary collision is not permanently excluded, including after
a ricochet or return into its material.

Candidate constraints: actual already-loaded live `isPointDefenseMissile()`
identity; actual owner policy above; finite motion; range at most12 from current
muzzle; approaching the local protected gun/body envelope; a possible straight
physical-axis interception within nine normal ticks. No hidden target actor
position is needed. An unavailable tail is UNKNOWN, not empty space. Both muzzle
exit and predicted lane must pass the real loaded geometry and earliest-cover
checks; the firing hull is excluded only for the already proven short outgoing
muzzle interval, not permanently ignored by the bolt.

The low-level mounted-defense entry must repeat admission against actual loaded
body UUID, current gun/claim token/lever, actual muzzle, fixed axis plus inherited
velocity, strict existing laser config and actual loaded target/owner. It may not
admit an unborn target to make an opposite-order test easier. Public ordinary
WeaponCore and emitter behavior remain compatible. A separate mounted source
kind records only `{bodyUUID, cell, principalUUID, claimUUID, targetUUID,
bornAt, eligibleFrom}`. An invalid static tag cannot become a mounted tag, and
mounted data cannot inherit station hostility. Exact schema/ABI layout follows
review; retain existing static constructor/accessors or adapt retained tests
without weakening their semantics.

Reuse the single existing `PointDefenseInterception` target index, tagged-round
integration, `RelativeProjectileSweep`, actual terminal callback and lifecycle.
Refactor global station hostility checks into a source-independent missile shape
check plus an exact **per-source** station/mounted policy; station behavior and
its current tests must stay unchanged. Preserve the full-frame birth guard:
an outgoing bolt cannot catch a missile segment already processed in its birth
tick. Only tagged outgoing terminal side effects defer to END-world. Ordinary
missiles keep original immediate collision/fuse/blast/payload. Interception must
be an actual relative swept box contact before nearer world/hull/actor material,
then invoke both original on-hit paths exactly once. No manager discard, damage
substitution, free safe disarming or global projectile-pair scan.

Keep existing global mounted work limits64 checks/32 shots/32 bodies, body16,
existing per-cell cadence and shared256 tagged-round cap. First implementation
candidate acquisition/detail/ray/hull/actor limits may not exceed current station
96 candidates/32 detailed/32 rays/32 hull tests/32 actor tests per opportunity;
cap exhausted means refusal rather than tunneling. Both source kinds share the
same world capacity, not independent256 pools. No target/source scan grows with
all world entities. Retain bounded cleanup/unload and report observed work.
This is admission/constant-work acceptance, not a 256-projectile performance
benchmark. The longer existing laser lifetime does not authorize unbounded rounds.

## Frozen test intent before any main edits

First add a source-only natural defended/inert pair, then obtain a separate Java
lease for its **first baseline**. Existing production is unchanged. No baseline
failure is claimed before execution; compile/mapping/interest admission failure
is not missing-AA evidence. Prefer public existing interactions that compile on
old source: actual local USE packet aimed at the gun with main-hand crafted
wrench, then actual lever USE. Baseline may additionally report that gun claim is
not yet accepted, but the decisive AA test must observe the real incoming missile
and terminal outcome rather than fail only at a new missing method.

The natural physical setup consists of player-chosen separate bearing, drive,
laser and lever blocks on ordinary registered bodies, connected with the existing
validated wrench interactions. Bodies are initially placed once with admitted
loaded geometry; no later follow teleport, pose assignment, manually ticked
entity or pre-injected joint/shot/hit. Principal is a real normal Survival player.
Use an actual registered hostile ROCKET and its real `tryFireAt(principal)`
weapon action, preserving its ARTILLERY homing/energy/cooldown/damage/splash
configuration and normal WeaponCore path. A read-only terminal observer must not
replace its payload. This exercises real actor weapon emission and natural
projectile/defense ticks, not autonomous AI acquisition or a live raid. The earlier
station test's framework rocket is a separate retained regression, not a substitute
for the authored drone loadout here. Future AI targeting/ballistic spread must
not be invented as a fixture shortcut. Admission
must establish actual entity-ticking source/flight corridor/shooter/player,
correct manual barrel axis and live material joint before the transaction starts.

| Acceptance | Material distinction required |
| --- | --- |
| Natural defended / ordinary unclaimed or lever-OFF control | Armed mode observes a real emitted body-owned ENERGY bolt age/travel and actual missile-to-bolt terminal contact outside original missile blast reach of protected material. Control retains real material impact, not expiry. Original warhead payload runs once; protected body/material/owner survive in defended case. No direct AA tick/fire/intercept call. |
| Physical axis / actual moving bearing | Banked/translating body uses measured muzzle, actual axis, inherited material velocity and point recoil. Existing powered drive really changes angle; off-axis phase cannot magically intercept, aligned crossing can. No trace-only or rendered-aim surrogate. |
| Friendly / unknown | Actual owner/teammate-player and matching-team hostile-machine owners, null/unloaded owner UUIDs, ordinary animal-owned missile and body-owned player missile do not acquire player AA. Independent hostile control proves active opportunity. Station policy is unchanged. |
| Owner authorization lifecycle | Second player cannot seize/release claim; stale revision, wrong hand, range/cover, Spectator, unmounted/dead/offline/wrong-world principal deny. Principal return retains cooldown and resumes without replay or unconditional ordinary shots. |
| Unavailable / earlier cover | Unknown corridor does not materialize columns or admit a shot. Real own muzzle armour, nearer world wall and rotated unrelated hull win; cap exhaustion refuses. Free-path hostile control proves normal loaded opportunity. |
| Both actual ticker orders / birth | Actual already-loaded target is present at launch; normal entity interest/lifecycle yields both observed relative-contact orders. Same-tick born-late bolt cannot retroactively catch an earlier missile impact. Pure CCD complements, never substitutes for actual order receipts. |
| Fracture / save | One intact gun claim/cooldown follows one real fragment without duplication or motion reset; replacement loses it. Actual saved/reopened body retains optional owner data and existing timers; old, corrupt and unsupported optional entries do not activate or break legacy cooldowns. |
| Capacity / old regressions | Bounded actual candidates/details/rays/hulls/rounds; no shared-cap multiplication, repeated delivery or per-failed-opportunity cooldown reset. Retain original station12, mounted mechanism, reference weapons, projectile visual/fusion and material-contact tests. |

Use one pre-registered test driver; no callback adds scheduler tasks while it
iterates. Freeze source/helpers/inputs, preserve first exit/XML/log and genuine
failure before production changes. Focus counts follow actual proposed classes,
not a claim that this whole table has already been implemented or run.

## Survival obtainability, cost and release constraints

Current canonical accepted economy source `16a25a9` and later combined root gates
already establish real crafting UI/result consumption/refund, separate
bearing/drive/wrench assembly, actual local laser/rocket/lever placement, real
projectile emission and ordinary mining/drop/pickup/replacement. Do not erase or
repeat those as if no path existed. Preserve the exact canonical recipe data:

| Item | Existing ingredients/output |
| --- | --- |
| mechanical_bearing | 4 iron ingots + 1 iron nugget ->1 |
| rotary_drive | 4 iron ingots + 4 copper ingots + 1 redstone ->1 |
| mechanism_wrench | 3 iron ingots + 1 stick ->1 |
| alloy_plate | 2 iron + 2 copper ->2 |
| energy_cell | 4 copper + 4 redstone + 1 amethyst shard ->2 |
| ship_laser | 8 alloy plates + 1 energy cell ->1 |

No prefab turret kit or new factory is a dependency. The wrench mode action
consumes no extra gun/block and does not duplicate a crafted stack; ordinary
placement/mining remains authoritative. New acceptance should obtain the AA
gun through the **real current crafting/result slot and local Survival placement**,
arm it through the same normal material interaction, and demonstrate interception.
Missing ingredients and interrupted placement must not grant a free AA weapon.
Retain the actual resource/drops/repair tests rather than adding a synthetic
creative-only inventory assertion and calling it progression.

Existing laser shots currently have no consumable ammunition or powered energy
inventory. This packet preserves that authored behavior; it does not invent a
new per-shot cost, technology tier or balance approval. Local resource spawn
availability, novice acquisition time, manual-axis coverage against two mobile
attackers, offline defense, automatic physical aiming and the complete fair
day/night siege/counterplay vignette remain open. Therefore even a successful
manual AA focus is not authorization to activate live SiegePlan raids or close
S12-T01/T02/T03/T05. Root still owns subsequent full integration, identical-JAR
native material-key/visual proof and packaging.

## Proposed ownership of source changes

Combat lane may own a small new player-AA policy/helper, exact source-kind
admission/relative-CCD adaptation in `PointDefenseInterception`, the explicit
`WeaponCore` provenance entry and optional projectile source data. Existing
mechanism owner/root must review and coordinate the narrow claim/cooldown hooks
in `BodyMountedWeapons`, shared emitter config/provenance reuse and the two gun
USE-dispatch predicates in `BlockBodyEntity`. No solver, bearing drive refresh,
joint controller, renderer/input handler, auto-raid director, faction registry,
recipe or ordinary stat edits are proposed. Shared central entrypoint/test
manifest changes remain root-owned.
