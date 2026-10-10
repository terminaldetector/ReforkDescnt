# Rocket-cut tree and column — 1.1.11

A rocket striking the support of a small straight tree or freestanding one-block-wide column
can detach the remaining upper part into a sparse rigid block body. The projectile path invokes
this adapter before its existing impact effects. Trunk/column height is limited to 12 blocks;
selection fits 16³ and at most 256 supported inert cells. Simple nearby foliage follows the trunk.
Adjacent logs from another trunk and lateral attachments on a column reject the operation before
mutation. Inventories and unsupported blocks are not assembled. This is a deliberately narrow
support-cut adapter, not a whole-world structural stability solver.

Mass/inertia determine the reaction to a blast impulse applied at the lowest surviving section.
Detached bodies have explicit gravity, persisted and synced in their pose. Outside a station field
they fall along world -Y; inside a gravity torch/generator field they follow its local down vector.
The contact solver treats the aligned world floor, wall or ceiling as support, projects small
penetration and applies point impulses with friction, allowing tipping and settling instead of
freezing the entire rotation. Other bodies retain the existing zero-gravity behavior. Conservative
transformed cell AABBs still guard walls, world bounds and unloaded chunks. This is not exact convex
contact, general stacking, riders or body/body physics.

Projectile raycasts now include block bodies and test their occupied local cubes after inverse
rotation, rather than treating the broadphase bounding sphere as solid. Direct impacts transfer
momentum. Per-cell quarter damage, fragment separation and collision between fallen bodies remain
open. Tree leaves are rendered and carried as inert foliage; their normal ticking resumes only
if disassembled back into terrain. This stage does not globally collapse forests or buildings.

World tests fire an actual ProjectileEntity into a column, then check gravity-driven tipping,
bounded velocity and save/load. A second test checks canopy retention and rejection of attached
inventory structures. A third places a physical block in a wall-facing local gravity field and
checks sideways fall, wall support and bounded contact velocity. Pure contact tests check energy
loss and separating/massless cases.
The isolated client scene fires two real rockets and records before/flight/after plus intermediate
frames. These fixtures never enter the playable JAR.

The expanded authored arsenal is retained. Original Descent D1/D2 weapons and project-specific
extras will receive distinct coherent visuals/gameplay in the weapon pass; this collapse slice
does not delete those extras or claim the whole arsenal has been rebalanced.
