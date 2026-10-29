# Local hull combat —1.1.33-combat.1

This stage connects weapons to the existing BlockBody/D6 local grid and fracture system.
The full master specification, Boarding12 and UFO9 remain OPEN.

## Behavior

Hitscan and the real ProjectileEntity contact handler call PhysicsTarget.weaponHit, independently
of gravity-tool impulses. Existing ragdoll adapters retain their physical response. BlockBody
transforms the exact surface contact into its local grid and accumulates finite damage on the
struck material. Strength is max(4,12*material hardness); supported inert materials do not query
world blocks at their local coordinates. Iron therefore survives one30-damage hit and breaks
on the second. This is block destruction, not microcell carving or deformation of the hull.

WeaponCore splash now delegates geometry selection to the physical target. BlockBody queries
its existing local material BVH and tests exact sphere/unit-box distance in the rotated grid.
A nearby end of a128-block hull can receive damage even when its COM is outside the blast.
One aggregate impulse is applied at the nearest surface; one atomic local edit removes all
failed cells and reuses connected-fragment creation, mass rebasing and geometry synchronization.
No item entity is created for each destroyed material cell.

Vanilla explosions also query the hull index from the existing expanded-physics explosion
adapter. KEEP/non-destructive explosion modes do not mutate material. A single snapshot of
candidates prevents newly spawned fragments from being damaged twice by the same explosion.
WeaponFx already uses its own splash/crater path rather than a second vanilla explosion, so
that weapon path does not duplicate the callback's hull load.

Partial damage is server-owned and saved in NBT. It moves with the corresponding material into
a detached body, including partial loads from the same radial batch that caused separation.
Replacing a block clears the former material's damage. Invalid/nonfinite loads are rejected.
If the existing256-new-fragment or512m/s COM admission rejects a destruction batch, grid,
revision and partial-damage changes are refused together; the physical impact impulse remains.
This safety limit is unfinished queued destruction work, not proof of unlimited city destruction.

A fresh fragment also inherits its parent's previous COM pose, previous quaternion and physical
update stamp. Its contact query therefore sees the same continuous material trajectory instead
of one artificial paused/infinite-mass frame. Stale or loading-paused parent material stays paused.
The existing player contact motor can change support IDs without resetting its input epoch or
following a teleported local anchor.

## Evidence requirements

Actual server tests exercise hitscan, a physical projectile tick, near-end splash on a large
hull, partial-damage persistence and transfer, same-blast transfer, replacement and invalid
loads, the real vanilla explosion callback, and atomic refusal of a400-component breakup.

The fracture client still requires11 fresh rendered scenes and strict position comparison.
Its three separation scenes now originate from actual hitscan, radial damage and projectile
collision, respectively. Surviving-material momentum expectations include the weapon impulse;
fast blast fragments must complete a full loaded physical tick above60m/s. The saved-fragment
scene remains an entity-NBT round-trip, explicitly not a full-world reload test. Old local-edit
proof cannot certify this stage: all three combat marker fields are mandatory for packaging.
Server player regressions also verify continuous contact on the separated deck, unchanged input
epoch, ordinary collision-checked movement, jump/release with carrier speed inherited once, and
paused-parent behavior. First failures retain the lost contact velocity and lost player speed.
This is server acceptance; a rendered networked player-on-fragment scenario remains to be added.
Exact source/JAR SHA, CI status and installed BCC/Sodium evidence are recorded separately.

## Remaining work

The radial model does not yet attenuate damage through intervening hull armor or terrain.
Partial damage currently has no client crack/deformation visualization. No heat, pressure or
automatic decompression subsystem is introduced. Huge bursts still need scheduling, simulation
LOD and measured city-scale stress. Rendered/networked passenger transfer onto fragments, movable joints,
functional block entities, ControlCore, physical large UFO conversion, full3D AI/bestiary and
dedicated/multiple-client/whole-world module persistence acceptance remain active.
