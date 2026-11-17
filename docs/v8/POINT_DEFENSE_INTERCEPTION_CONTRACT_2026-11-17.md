# Physical point-defense interception: pre-production contract

Base: `b745eb806788e2ca16f87514f56d4b68577887f9`, the sealed scoped Squad
acceptance checkpoint. This is the next unimplemented S12-T03 defense mechanic
from master §8 and `reference/03_GAMEPLAY/SWARM_FACTIONS_SURVIVAL.md`; it does
not close the complete survival resource progression or the other specs.

## Actual source audit and ownership

* `world/trap/DefenseTurrets.java:70–108` selects only `LivingEntity` and applies
  immediate living-target damage. `PointDefenseTurretBlock:148–176` adds only
  another optional shot at that same category. Its registered block is used
  in existing station, dungeon and settlement generators; it is not absent.
* `entity/ProjectileEntity.java:390–407` includes only living/`PhysicsTarget`
  entities in physical projectile traces. There is no projectile interception
  or projectile damage override. Real flying missile factories already exist
  in `ProjectileFramework`, `DescentRocketFire`, drones and mounted weapons.
* `weapon/core/WeaponCore.java:103–177` owns the actual projectile factory,
  metadata/velocity, fuse/payload configuration and recoil. It currently
  requires an entity owner. The block has no entity owner/team storage.
* Existing `BaseTurretBlock.isValidTarget` is the authoritative identity for
  this first static-station increment. It excludes keeper/boss and
  creative/spectator players. This is distinct from `HostileEnvironment` and
  must not silently be replaced with that faction predicate.

Combat lane owns this contract, new dedicated GameTests/helper, new point-
defense helper, and (after the genuine negative and ABI review) coordinated
narrow changes in `DefenseTurrets`, `ProjectileEntity` and `WeaponCore`.
Root owns `BlockBody`, mounted emitters/controllers, bearings and joints;
those files, shared registration, stats, recipes and registry stay unchanged.

## First test-only baseline, before production

Use the actual registered `POINT_DEFENSE_TURRET`, its ordinary scheduled ticks,
and a real `ProjectileFramework.config(ROCKET, livingOwner, position, direction)`
launched by `WeaponCore.fireProjectile`. No direct turret-fire invocation,
manual entity tick, teleported/followed rocket, injected hit, immediate discard
or test-created defensive proxy is allowed. A single driver is registered at
test entry and only reads actual flight/shot/impact state after fixture setup.

Two cases use isolated, genuinely entity-ticking loaded columns and the same
normal incoming missile parameters, arming, lifespan and geometry:

1. **Active defense:** after admission place the real block, allow its first
   six-tick schedule, and launch the incoming rocket shortly before that
   opportunity. Its living shooter is outside the 12-block turret range but
   is a valid current station threat. Require an actual emitted kinetic
   projectile, observed finite travel, then the incoming rocket's actual
   terminal hit callback identifying that emitted entity. Require a material
   contact point on the two real swept trajectories, outside the rocket's
   original blast radius from the protected block. The turret remains intact;
   TTL, owner death, unloaded state or an ordinary base impact cannot pass.
2. **Unprotected control:** an inert cube at the same protected position and
   the same ordinary missile produce a real block impact at that position,
   after actual age/travel and before expiry. No interceptor appears. This
   establishes that the launcher, arming, geometry and normal flight can
   actually reach the protected position.

Admission is bounded separately (80 ticks), observed transactions are bounded
to 40 ticks. Preconditions record exact initial source/parameters, loaded
volume, owner identity/allegiance, launch/turret times and real entity ages.
Natural incoming flight and damage/payload configuration are unchanged after
launch. Cleanup runs only after the terminal outcome or a preserved failure.
The first compile/fixture/gameplay outcome is retained without self-retry.

## Proposed production boundary after baseline and review

* Reuse the existing `ProjectileEntity` type and factory. Add an explicit
  server-world, anchored-block launch overload for this real ownerless source;
  never fabricate an owner entity, inherit an attacker's identity or loosen
  the existing owner-based overload. Validate actual source block/loaded
  world thread and finite muzzle/direction before emission. No registry or
  rendered model replacement.
* At most one finite-life kinetic round per existing six-tick defense
  opportunity. Existing damage/range/interval, optional living shot behavior
  and other turret classes retain their authored values. A failed/occluded
  missile shot does not create a queued volley or unavoidable remote damage.
* Select only a loaded, alive real rocket with a loaded living shooter that
  satisfies the exact existing station threat predicate. Null/unresolved/dead
  owner, exempt allies/modes, friendly interceptor, non-missile, receding or
  non-threatening trajectory are rejected. The turret does not acquire hidden
  owners by scanning worlds, creating tickets or inventing faction/team state.
* Collect at most **96 loaded projectile candidates** in the existing 12-block
  volume using the limited entity-collection API, then test at most **32**
  geometrical threat/lead candidates per opportunity. This bounds admitted
  projectiles and detailed work, not the engine's internal section traversal.
  Reuse `InterceptAim` for finite XYZ lead, original rocket velocity/flight
  and the emitted round's actual finite speed/lifetime. No homing guarantee.
* All source-muzzle and aiming segments must satisfy `WorldEditSafety`'s
  actual loaded collision-shape halo, vanilla block shape collision and real
  rotated hull collision. Admit at most **32 hull narrowphase candidates**;
  excess/unknown is a refusal, never a clear path. The source cube is excluded
  only along the actual barrel-exit segment, with muzzle outside its shape;
  it is not permanently immune to returning shots or blast.
* Explicit interceptor provenance/targetability extends only this pair's
  collision admission. No all-projectile all-pairs scan or global damage
  rebalance. Resolve earliest real **relative swept** contact against the
  incoming missile and nearer world/body cover, respecting both actors'
  movement and either ordinary entity tick order. A target's present end-box
  alone is insufficient for two fast projectiles passing between samples.
* Only actual contact can enter the missile's terminal hit/detonation/payload
  path at its material impact point, once. Preserve original arming, owner,
  blast and payload semantics; physical interception is not deletion or free
  disarming. The interceptor also terminates normally. It must not erase a
  protected missile through a wall, hull, unknown region or beyond lifetime.

The exact ABI and whether a read-only contact receipt is needed for tests must
be reviewed after the first baseline. No production change is authorized by
the presence of this document alone; the root agent coordinates shared files.

## Required post-negative regression/guard ladder

Keep the first two semantic criteria unchanged. Add real keeper/boss-friendly,
creative/spectator/null/dead owner and non-threatening/receding controls;
ordinary missile travel/damage without defense; real solid block and rotated
hull cover; unknown collision-halo refusal without generated columns; finite
96/32/32 work and duplicate random/scheduled opportunity controls. Pure relative
segment geometry must cover crossing, skew miss and covariance; server tests
must prove actual ordered impact and the missile's original explosion/payload
once. Retain relevant ordinary projectile, turret, mounted-weapon, returning
source-hit and portal/mirror regressions before integration.

No dedicated native proof, player-owned base policy/resource recipe, mounted
mechanical controller, universal anti-ordnance shield, 256+ barrage performance,
complete S12/S11 or whole master/boarding closure is claimed by this increment.
