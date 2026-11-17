# Proposed shared ABI after the real point-defense negative

Review base: `8f66701b33c147401f582962e65dae9d47037831`, production still
`b745eb8`. This document proposes concrete source boundaries; it is not an
implementation or a positive. First `f4a03f7` remains 1 FAIL / 1 PASS.

## Existing constants and physical reaction

The normal ROCKET used in the unchanged fixture starts at 2200 Source units
per second: `2200 / 80 / 20 = 1.375` blocks/tick. Original gravity .08, drag
.005, delayed-impact arm .12 seconds, splash radius `300 / 80 = 3.75` and
five-second lifetime remain unchanged. The actual placed turret interval is
six ticks, range 12 and ordinary damage 4. The actual KINETIC factory speed is
5000 Source units per second: **3.125 blocks/tick**, with gravity .15/drag .01.

Proposed new defensive ammunition reuses that existing finite kinetic speed
and flight equations, point-defense damage 4 and existing amber visual colour.
It has no explosive payload/piercing/homing/pilot control and a .5-second
lifetime, limiting missed defensive rounds to ten normal age ticks. This is a
new finite round, not a rebalance of the existing KINETIC gun or other weapons.
Acquisition remains within range 12. `InterceptAim` receives tick units, the
actual missile velocity, the static source's zero inherited velocity and the
finite remaining reaction time; it must return no shot when unreachable.

Fixture timing is unchanged: place at T, launch at T+4, real first scheduled
opportunity T+6. The round must physically meet the approaching rocket before
its ordinary cube impact and outside the original **3.75 + .5** safety margin.
No reduced warhead, faster incoming-clock substitution, expanded range,
shortened turret interval or weakened transaction40 criterion is permitted.

## Exact proposed shared files and API boundary

1. `weapon/core/WeaponCore.java`: keep `fireProjectile(FireConfig)` and its
   null-owner rejection, owner velocity/recoil, metadata, fuses, payload and
   spawn return semantics. Extract only the already common entity setup/spawn
   tail to a private helper. Add an explicit
   `fireAnchoredProjectile(ServerWorld, BlockPos, FireConfig, UUID target)`
   entry for the real stationary source. It requires the server world thread,
   null `cfg.owner`, the actual loaded Point Defense block, its real muzzle
   and predictable non-homing impact-only kinetic config. No arbitrary
   ownerless fallback in the old API, fake entity, enemy owner, inherited
   velocity/recoil or registry entry. Configure defensive provenance before
   `world.spawnEntity`, preserving all ordinary old defaults.
2. `entity/ProjectileEntity.java`: add a read-only `getOwnerUuid()` to resolve
   loaded living ownership directly without the existing body-owner fallback
   scanning all loaded hulls. Add optional anchor/target provenance and
   narrowly scoped hooks for defensive rounds and the actual missile they
   address. All ordinary projectile target filters, fuses/arming, owner
   protection, piercing, portal/mirror behavior, existing hit routines and
   source returning hits retain their old path. Do not add generic projectile
   damage, make every shot target every shot or convert missiles to proxies.
3. `world/trap/DefenseTurrets.java`: only `PointDefenseTurretBlock.fire` asks
   the new helper for a real shot, otherwise retaining its existing living
   fallback and authored stats. Reuse exactly `BaseTurretBlock.isValidTarget`
   on the actual loaded living owner, including keeper/boss and player mode
   exclusions. Expose only that already existing predicate as needed within
   its package; do not adopt a different faction policy or owner/team state.
4. New `world/trap/PointDefenseInterception.java`: owns source selection,
   loaded muzzle/trajectory admission, bounded per-world active-round state,
   typed pair association, and the optional deferred terminal impacts below.
   Values use UUIDs, vectors and weak entity references, not world-owning
   strong entity references. A failed lookup is UNKNOWN/refusal.
5. New pure `weapon/core/RelativeProjectileSweep.java` (name provisional):
   slab intersection of the actual two translated projectile boxes over a
   shared normalized tick. Return earliest contact fraction/normal/material
   point, or miss, using actual dimensions and velocities in XYZ. Cases must
   include fast crossing, skew miss, starting overlap, equal velocity,
   covariance and ordering against nearer cover.

Root coordinates **one explicit lifecycle registration** for the new service:
`register()` connects END_WORLD_TICK terminal-impact flush and world unload.
No hidden runtime/static event registration and no changes in BlockBody,
mounted emitters/controllers, mechanisms, gravity or renderer files.

## Bounded admission and real muzzle geometry

* One emitted round per source per six world ticks, shared across its existing
  scheduled/random backup calls. Do not queue refused/cooldown requests. A
  bounded source cooldown table (256 entries, no eviction/replay of unexpired
  entries, cleanup at most32 per five ticks) refuses new anchors when full.
* At most256 active tagged defensive rounds per world. This is an explicit
  resource admission limit, not a 256-barrage performance acceptance. Existing
  living-target fallback remains available when admission fails. Ten-tick
  round lifetime and unload cleanup bound retained state; no backlog.
* Collect at most96 actual loaded projectile candidates in the twelve-block
  acquisition volume. Resolve owner UUID with loaded-world `getEntity`; only
  loaded/alive living owners in this world satisfying the existing station
  predicate qualify. Null, missing, dead, exempt-mode/ally, defensive round,
  non-missile or non-finite motion is rejected. This does not bound Minecraft's
  internal entity-section traversal; it bounds admitted entities and work.
* Test at most32 lead/threat candidates. The current finite incoming segment
  must approach and threaten the actual source cube expanded by the missile's
  original blast radius within the bounded reaction horizon. A receding,
  tangentially harmless or unreachable trajectory produces no shot. This is
  acquisition confidence, not forced guidance of either projectile.
* The real static muzzle remains `Vec3d.ofCenter(source).add(0,.6,0)`, already
  used by this turret. Validate the exit from just above the source's actual
  top collision face to this muzzle, then the actual outgoing segment. Use
  existing loaded collision-shape halo, vanilla shape ray and real rotated
  hull queries; no generic ignored source cube or immunity after launch.
* Each opportunity has a shared bounded budget: at most32 detailed lead/LOS
  candidates,32 block rays and32 hull narrowphase tests, rather than silently
  multiplying32 by32 for every candidate. More than32 admitted hulls/unknown
  volume or exhausted budget means refusal, not clear space. Other physical
  actors/bystanders retain material collision; no shot through allies to hit
  a missile. Root-owned index APIs are reused without editing the index.

## Relative CCD and tick order: why a scoped terminal phase is necessary

The target's present box alone cannot prove contact: the stock missile and
kinetic round can cross between their independent ordinary entity ticks. No
target motion prediction can be called actual CCD for a homing rocket.

For a **tagged defensive round only**, cache the current world tick's start,
the deterministically computed non-homing next velocity/segment and its nearest
ordinary loaded world/body/actor impact. If this round ticks first, move only
along that admitted segment and defer its terminal hit side effects until the
target's actual segment is known, or END_WORLD_TICK. Never defer ordinary
incoming missile impacts or other weapon projectiles.

The incoming missile hook runs after its normal guidance/gravity/drag produce
its actual next segment, and after its ordinary nearest contact is known but
before that contact's side effects. It looks up only explicitly associated
defensive rounds (bounded by the active-round cap), resolves the two real
segments and compares normalized contact time against **both** nearer block/
hull/actor contact times. The earliest actual pair may enter normal terminal
hit paths; cover wins ties. It does not skip an earlier living/world/body hit.

If the missile ticks first, prepare the predictable defensive segment without
calling another entity's tick or mutating the incoming target's state. Its
cached segment is reused when the defensive entity normally ticks, so motion
and contact cannot be applied twice. Cache guards use actual world time,
loaded identity, remaining life/arming and finite real state. Unavailable or
expired counterpart means no interception; deferred ordinary round impacts
flush once at world-tick end. No retained work queue across future ticks.

Only real pair contact enters the missile's existing `onEntityHit` terminal
path at the actual material point and with the actual defensive entity in the
hit context. Preserve original owner, explosion, warhead callback and arming;
invoke each once. The interceptor also terminates. No manager-discard missile,
synthetic hit, automatic safe disarming or source immunity is introduced.

NBT defaults stay backwards compatible: old projectiles have no anchor/target
and take their exact old path. Optional defensive provenance, finite remaining
life and no unresolved world-owning references can be saved; current-frame
motion/pending hits are transient. Reload rebuilds only valid already-loaded
associations; invalid context remains an ordinary inert/non-intercepting round,
never restores a remote missile hit or creates new entities. Existing projectile
serialization gaps outside the new fields are not silently declared closed.

## Gates and exclusions

Before implementation, review this source boundary and any need for a readonly
contact fraction receipt. Retain the first two assertions, then test relative
geometry, both actual normal tick orders, nearer solid/rotated hull cover,
original blast/payload once, existing friendly/mode/unknown ownership rejection,
unknown-volume refusal, finite work/cooldown and ordinary no-defense damage.
Keep default projectile, portal/mirror, mounted and returning-source tests.

This proposal has no production patch or test result. The dedicated native
pair, player-owned survival progression, generic mounted mechanism integration,
large-barrage scaling and whole master/boarding acceptance remain open.
