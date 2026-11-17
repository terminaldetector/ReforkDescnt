# Point-defense interception: first genuine missing-mechanic baseline

Exact test-only source `f4a03f76d7b327ebb008d8cda30529313ba3e127` retained
production byte-identically to the sealed `b745eb8` Squad checkpoint. The first
focused stock Fabric GameTest process compiled and closed exit 1: **1 FAIL /
1 PASS**. No retry, production correction or assertion change occurred.

Both fixtures passed actual entity-ticking position admission, the loaded
collision-shape halo check and the exact current station threat predicate.
The real living shooter was 24 blocks from the block, outside its existing
12-block range. Both missiles came from the ordinary stock ROCKET config and
weapon factory: initial speed 1.375 blocks/tick toward the cube, original
gravity/drag/fuse/arming/blast/life preserved, original splash radius 3.75.
No manual entity tick, follow teleport or defensive-fire API was used.

The inert-cube control passed: its actual missile aged eight ticks, travelled
about ten blocks and produced exactly one terminal block-hit callback at the
real protected cube face. The active registered Point Defense block was
placed at world time 102, missile launched at 106 and hit the turret at 114.
Its first six-tick scheduled opportunity elapsed within those twelve normal
transaction ticks, but the callback invocation itself was not directly
instrumented. No emitted kinetic projectile was observed. The incoming missile
again aged eight ticks, produced one actual block hit and terminated normally;
the required physical interception before base impact failed. This establishes
the missing mechanic rather than an inactive rocket/expiry/fixture shortcut.

Immutable first bundle: `EVIDENCE/point-defense-first-negative-f4a03f7.zip`.
SHA-256: `ad6fcad902f9bfc3e1e7b0cea9d28f258f9a56c3f7a55021f49fb427ef8483d4`.
All **85 payload hashes** and ZIP CRC passed. The bundle contains original
source/diff/contract, actual compiled relevant classes/focused manifest,
executed frozen helper, initial input/source snapshot, first raw log/fresh
XML/immediate exit and flight receipts, and all 45 closed-world files
(14,531,921 bytes). The actual JVM processes had closed when the lease was
returned; remaining `/proc` Java entries were old zombies, not executing JVMs.

Next is coordinated source ABI review and the narrow real emitted-interceptor
implementation from `POINT_DEFENSE_INTERCEPTION_CONTRACT_2026-11-17.md`.
Ownerless anchored launch must not invent an entity owner; real relative swept
contact must enter the missile's original hit/explosion/payload path rather
than manager discard/disarming. Existing station allegiance and normal turret
values remain authoritative. No production fix, positive, native, player-owned
base resource progression, mounted-mechanism or complete S12 acceptance is
claimed by this first negative.
