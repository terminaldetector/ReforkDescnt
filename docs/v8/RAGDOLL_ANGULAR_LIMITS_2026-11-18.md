# U13 — bounded rest-frame angular limits and one C5 JET rig

Local-only source work based on `5057931efce99156ffdc8a195e90b1a926907ed8`.
Implementation: `0909645cfe79445ecf4cb31771fd3e04ef6f7b91`; additional family
counterfactual test: `f36df65a90f57d3416a6d37b5b0ed1bd450c3e58`.
The tested source includes the signed twist seam fixes and is pinned to
`5acde8915ee940b8d2c8086511810f112bbe21bf`.
This is a scoped U13/S08-T02 source increment, not acceptance of universal articulated
ragdolls, all families, moving-deck contacts, sleep, swept CCD or complete 3D AI.

## Contract

`RagdollRig.Joint` retains its five-argument constructor and original free ball socket
when `angularLimits == null`. An optional angular contract adds normalized, finite local
frames for both parts. Their X axes define twist; relative frame identity defines angular
rest. A finite swing cone uses radians in `[0, pi)`, with signed twist in `[-pi, pi]`.
The full `[-pi, pi]` twist interval remains free across its representation seam.

`RagdollAngularLimit` projects the cone and signed twist interval on the existing D6
bodies. It uses world inverse inertia, a maximum `.08` radian correction per angular
projection, and the existing `.12` metre positional cap divided by the two segment radii
to tighten the rotation budget for larger rigs. The existing anchor constraint follows
angular projection, and the existing terrain resolver follows every joint iteration.
Substeps, iteration budgets, gravity forces, point impulses and contacts are retained.
No `D6PhysicsBody` or BlockBody joint contract was changed; SLIDER bounds remain metres.

The angular stop is unilateral and inelastic: it removes outward relative angular speed
without restitution or angle/time bias. Its equal and opposite angular impulses conserve
the pair's world angular momentum. Inward and interior velocities remain free.

Positional angular stabilization rotates anisotropic inertia and may otherwise increase
kinetic energy at fixed world angular momentum. A pair energy cap removes only that
increase by scaling momentum. **This stabilization can dissipate angular momentum and
energy.** It does not claim exact system momentum conservation during pose projection;
the separate zero-bias velocity impulse has its own conservation test.

## First family and persistence

New C5 JET wrecks use rig version 2. Both wing joints have local frames pointing away
from the core, 35 degree swing and twist from -12 to +12 degrees. Their existing segment
mass, volume scaling, collision geometry, anchor topology and rendered face ownership
are retained. ORB, ROCKET and scout joints remain unchanged.

The hull identity schema stays at version 1. A separate optional INT `rigVersion` selects
the physical rig: missing or explicit 1 loads the original free joints; explicit 2 loads
the limited JET contract. Both saved state and tracked pose carry this identity.

An unsupported or wrongly typed `rigVersion` is refused, not migrated to legacy physics.
One owned opaque bundle preserves the raw version, `ragdoll` payload, hull identity and
life value. Its known hull is display-only at the saved entity position; unknown physics
is not parsed. Simulation, lifetime expiry and impulse/torque authority are disabled for
that refused state. A warning is emitted once per load. Re-saving echoes the opaque owned
fields, including nested future physics data. This is a bounded optional-codec change,
not a replacement of Minecraft's base entity codec.

## Verification status

`git diff --check`: passed before source commits.

The root integrator granted one exclusive scoped Java attempt on that exact source.
The first attempt passed: **17/17 selected units**, zero skipped or aborted, and all four
child processes exited 0. This is 11 new solver cases, two new family cases and four
pre-existing family cases: **13 new tests**, not 17 new tests. The five changed main
sources, two selected unit classes and the nine-method GameTest class compiled. Its
three new GameTests were **compiled only, not executed**. No Gradle, cache mutation,
whole-suite run, remap/build, installable JAR or native client was performed.

The frozen runner SHA-256 is
`3537e79e88546b0bfd5010cbcb51a3c28bc0bfd8312b7398a9b57ea44a0cc794`;
the tiny JUnit launcher is
`dbdeb1294f258c7196c507e6fef291728238e52b94fcd62d08bf22a94444ce33`.
Every child had a 120-second timeout. Isolated changed outputs precede reused baseline
outputs. Javac used Java 21, `-proc:none`, `-implicit:none` and an empty sourcepath.
Captured actual named classpaths supplied the dependency lists; only verified canonical
main/resources/GameTest output directories replaced historical project output directories.
No pre-existing test classes were reused. JUnit is pinned to 5.11.3 / Platform 1.11.3.
All **1,965 reused files** were hashed before and after and remained unchanged;
18 fresh changed output classes were separately hashed.

The named Minecraft JAR actually used has SHA-256
`d3cd9f1b9d828a1844fb2996815eff0c622b572a0c5f11e70b089150df3d2ee8`.
Python classfile inspection verified its actual `net/minecraft/entity/Entity` self-name,
Java 21 major version 65 and entry hash
`b08c8053b5936e6fc70a17d9d103878cb439922e37a268d40418fc754a7eab0c`.
One historical cache mismatch is explicitly preserved: captured runtime `mappings.jar`
hash `280424fea5932524a8e26eef0baada238068585a9c23fa5a3a94d40a2e55621b`
differs from actual current
`4ce5e82694ac7ab2d927c3f3bb2b6646e7456e8ba5e098f64acf57e6389d3c9b`.
The current input is hashed as this run's input; it is not attributed to the historical
run. All other actual JAR hashes matched their captured dependency receipts.

The small `RAGDOLL_ANGULAR_5ACDE891_FIRST_EVIDENCE_2026-11-18.zip` preserves the
metadata-only preflight, first actual attempt, exact commands, every exit, selected unit
summary, original captured classpath receipts, actual input/output manifests, frozen
runner/launcher and exact changed source/compiled outputs. No cache or world is copied.
Archive size: 693,683 bytes; SHA-256:
`1e9fa880be9b09d5357fa6148c3a5c96400a0e630857712c4cc6d3bb5a2747e4`.

The unit cases cover explicit rest frames, q/-q, pi swing/twist, malformed contracts,
capped inertia-weighted corrections, two-link bounds, nonzero spin with anisotropic
inertia, angular-stop momentum/energy, inward motion, off-centre impulse under rotated
gravity, short-arc asymmetric twist across the pi seam, and contacted-limb penetration
prevention. The JET stress case uses both wing
joints and requires an unlimited version-1 counterfactual to exceed 55 degrees of swing.
The three new GameTests cover the actual JET death/spawn adapter and restore, missing or
explicit version-1 legacy loading, and opaque future/wrong-type load-save-load behavior.

Existing strict ceiling/support/wall/jump AI failures, clear-space release matrix,
universal family coverage, native cold two-client family restoration and moving-body
ragdoll gravity/contact adapters remain open. No ledger or unrelated source was changed.
