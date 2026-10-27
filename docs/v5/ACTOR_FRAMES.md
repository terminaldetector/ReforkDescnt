# Actor frames and bestiary — 1.1.21 candidate

Parent: verified `54637997f7e88da1c8c0fc1736c0a0f3dc4cb27a` / 1.1.20-gravity.1.
Work is isolated on `feature/actor-frames-v5`; the verified parent worktree is unchanged.

## Physical pose and metadata

Standing actors have one normalized local up for the actual collision AABB, interaction eye,
camera eye, look vector and living model transform. Built-in LivingEntity metadata supplies
initial spawn/late-join state and later changes. There is no separate UUID client cache.
Versioned `drmd:actor_frame` NBT restores the physical pose without restoring a temporary
acceleration lease. Non-finite, tiny, oversized and unknown-version save data is rejected.

Transitions retain the feet position. Before a change, the replacement body's collision query
and one-block shape halo must be in already FULL volumes. An obstructed turn is rejected.
An obstructed upright release keeps the safe pose, pauses the actor and retries; it never
teleports through the obstacle. Removing the last emitter still visits actors with a saved
frame through a weak physical-pose index, without scanning all actors in worlds with no fields.
Only the exact entity registered in that ServerWorld can be processed: an unspawned NBT copy
with a duplicate UUID cannot acquire a deferred-release acceleration lease.
An unchanged frame avoids repeating clearance queries or sending metadata.

The box is the conservative axis-aligned envelope of the oriented standing cylinder, **not**
an oriented-box/capsule narrow phase. This stage does not replace vanilla travel, friction,
step height, water behavior or navigation globally. The deterministic tangent frame is not
a persistent six-degree quaternion and has a reference seam near its chosen pole.

## Mod roster first

The user's 27 October clarification prioritizes mod drones and the bestiary, enabled by
default. There is no experimental settings switch for the adapters implemented here.

| Actor | Implemented adapter | Still outside this stage |
|---|---|---|
| Drone, all role loadouts | Flying force preserves propulsion/attitude; shared pitched/banked model basis; target uses actual body centre | Fully oriented hull collider, angular dynamics, general 3D obstacle routing |
| Scanner | Flying force and shared pitched/banked hull basis | Oriented collider, full obstacle routing |
| Oblivion Seeker | Flying force and shared pitched/banked hull basis | Oriented collider, full obstacle routing |
| Tripod | Standing frame, local eye/look/muzzle; tangent pursuit/retreat and local steering | Surface path planning around obstacles, all locomotion/animation cases |
| Spider Turret | Standing frame, local eye/look/muzzle/head aim; tangent pursuit and deployed stop | Surface path planning around obstacles, all locomotion/animation cases |
| Reactor Keeper, End Reactor Boss | Explicit flying force adapter, no foot mount/snap/velocity reset | Custom procedural-renderer orientation and collider adapters |
| Air Mine | Explicit flying force adapter | Full attitude/collider model |
| Drone Swarm anchor | Existing virtual swarm and spatial budgets preserved | Anchor field/attitude adapter; anchor is not a LivingEntity |
| Sky UFO | Existing separate hull/contraption behavior preserved | Integration with the new actor-frame contract |
| Cow, pig, sheep, zombie, husk, skeleton, stray, creeper | Standing geometry/eye/look/model adapter | General local-gravity vanilla AI/navigation |

Flying profiles are explicit classes, not inferred from `NoGravity`: another feature may own
that flag. Fields add acceleration once and cannot invoke foot mounting, set ground contact,
zero propulsion or overwrite a flyer's chosen yaw/pitch/bank. These profiles preserve the
authored flag on exit/save. A hull's full pitch/bank transform is applied once after vanilla
body yaw; the old outer world-Z bank and duplicate DroneModel pitch were removed.

All living machines are constructed with their normal AI enabled. Test fixtures disable AI
only while measuring isolated forces/transforms; that is not proof of autonomous combat.
One separate live server case leaves Tripod's ordinary AI enabled, gives it a target on a
wall and requires real pursuit, surface contact, local eyes and an owned fired projectile.
The existing targeting, weapons, swarms and drone ragdoll remain separate systems. Universal
ragdoll, full-volume AI, all roster-specific physical hulls, Survival hunting/base destruction
and large city stress acceptance remain open Master v5 work.

## Evidence

New server cases cover six-axis standing body/eyes/rays, obstruction and safe release,
saved-pose validation, last-emitter removal, custom ground steering, and six-axis flying
force/propulsion ownership for all six living flying profiles. Pure tests cover invalid
metadata and 1000 basis/angle/handedness/round-trip samples.

The new production-client fixture uses an actual native cubic world at Y=2155.5. Its six
scenes include cow, Tripod, Spider Turret, Drone, Scanner and Seeker. It checks initial spawn
metadata, client body/eyes and the transform actually appended by LivingEntityRenderer;
screenshots must also be inspected for visible models. Test harness classes are excluded
from the installable mod. See the checkpoint report for fresh results, failures and limits.

First server run retained in evidence: one lifecycle assertion incorrectly compared signed
zero components through Vec3d.equals; a geometric distance assertion tests the intended
direction. The other new geometry/save/obstruction cases passed. Do not count that run as
a complete pass. New source changes require fresh regression evidence before distribution.
The first client probe also ran before the production RETURN handler. It was moved to the
caller's position after setupTransforms returns, so the assertion observes the complete
transform including all handlers. That crash/log is retained; the failed run is not a pass.
