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

## Source-only first candidate, validation pending

Production checkpoint `24ce3e9295664e4d0988e5e46ccbdcb6a551d895` implements
the approved narrow physical path. No Java/Gradle or runtime gate has run on
this candidate yet; none of the following is a claimed positive.

The old owner-based factory remains null-owner rejecting. Its shared spawn
tail is reused by an explicit validated anchor overload, which also requires
the actual **already-loaded hostile missile and living owner**. Unknown,
friendly or unborn target UUIDs cannot admit a round even through that low-
level entry. Actual source/muzzle, finite predictable kinetic config and
remaining world capacity are checked. Tagged rounds use the existing KINETIC
speed/flight equations, station damage4, finite .5-second lifetime and exact
current station threat predicate. Source acquisition range12 and six-tick
opportunities remain unchanged.

Every new round waits until at least `bornWorldTime + 1` before interception,
a conservative full-frame birth guard. Its own ordinary terminal impact may
be deferred until the actual incoming movement is known or world-tick end;
ordinary missiles and every other projectile retain immediate ordinary impact.
Nearest cover wins ties. Actual relative-box contact enters the existing
missile and round hit/payload paths. No generic projectile damage/all-pairs
scan, manager missile deletion or safe disarming is introduced. New NBT tags
are optional; old shots have none, and restoration only accepts already-loaded
valid defensive context. Existing unassociated missiles bypass all new
geometry/owner work.

Six pure relative-sweep cases and twelve new stock cases are prepared. The
original two natural scheduled-block assertions changed only by an explicit
component lifecycle bootstrap; their semantic assertions remain unchanged.
Ten additional guards cover actual ticker order in both directions, nearer
solid/rotated hull material, actual station-friendly/unresolvable ownership,
unknown-source no-loading, old factory/NBT semantics,256 active-round admission
and96-candidate/32-detailed opportunity work. These cap cases prove admission
only if they pass; they are not a256-missile combat benchmark.

Actual `EntityList` bytecode inspected without launching Java uses
`Int2ObjectLinkedOpenHashMap`, so real entry order controls ticking. The
opposite-order fixture keeps a real UUID-loaded missile in a neighbouring
BLOCK_TICKING column, emits from the already ENTITY_TICKING source, then
obtains a genuine normal engine ticket for the missile column. It must observe
the actual round-first receipt; an interest promotion/deadline failure is a
fixture limit, not permission to remove loaded admission or manually tick,
change IDs, alter lists or follow poses.

Proposed first focused gate: six pure cases, then only if they pass twelve new
cases plus retained ProjectileVisual1/UfoWeapon16/FusionPortal12 = **41 server
cases**. Preserve the first compile/unit/fixture/gameplay outcome. Root still
owns final central `PointDefenseInterception.register()` integration; tests
explicitly bootstrap END_WORLD_TICK/unload. No central registration, renderer,
BlockBody, mounted-controller, registry/stat or recipe files were changed.

## First component candidate result:6 unit and41 server cases passed

Exact `4bafa39e142fb9519bba49f4baaaf531c1d6094a` passed the first declared
six pure tests and then all41 GameTests, both child exits0; no failures, errors
or skips. No retry, source or assertion change occurred during the lease. The
lease was returned at actual process close with no executing JVM remaining.

The original scheduled Point Defense case passed at eight real transaction
ticks, incoming age4, one observed emitted kinetic and one actual incoming
terminal entity hit. Its normal missile parameters and protected-source/
3.75-radius clearance criteria are unchanged. The unprotected trajectory
control still passed by an ordinary block impact at missile age8.

Both actual ticker orders passed. Incoming-first recorded
`roundMovedFirst=false`, missileAge3/roundAge2; genuine loaded-to-ticking
promotion recorded `roundMovedFirst=true`, missileAge3/roundAge3. Both contacts
had normalized fraction0.36990574920743563, and both real hit callbacks ran
exactly once with the actual counterpart identity. The latter fixture passed
explicit loaded UUID and **not entity-ticking** assertions before its normal
engine ticket promotion, then observed the real order receipt. A ticket-radius
argument or reversed pure geometry alone is not used as the order proof.

Both actual wall and rotated physical-hull material blocked the two flight
paths before any false pair contact. Actual station-friendly and unresolved
living owners were refused by the anchored primitive and ordinary controller;
their rockets retained ordinary flight/impact. The unknown-source column stayed
unavailable. Old null-owner factory rejection/NBT stayed ordinary. Real direct
component admission reached256 rounds and refused257, while120 loaded ordinary
candidates produced96 admissions/32 detailed checks at the scheduled controller.
These are finite-work/admission proofs, not a256-missile combat benchmark.
All29 retained projectile, mounted/UFO weapon and Fusion Portal cases passed.

Bundle: `EVIDENCE/point-defense-first-candidate-4bafa39.zip`.
SHA-256: `4ed8e6483383356d24c880bc1516227cc75d18666c569bd0a8311cfe47bcf4a7`.
All266 payload hashes and ZIP CRC passed: exact source/diff, initial inputs/
snapshots, executed frozen helpers, compiled actual production/unit/GameTest
classes, all fresh XML/case classifications, immediate exit receipts, every
order/cover/natural/cap/API row, and all195 closed-world files (52,632,147 bytes).
The paired index preserves both first outcomes,351 verified payload hashes.
This lease ran a compiled classpath, so no standalone installable JAR/remap
result is claimed or substituted from a stale package.

This is **component-bootstrap acceptance**. Root has authorized a separate
production lifecycle commit removing both test bootstraps and adding the one
main initializer registration. That different source still needs an unmasked
full gate before any ordinary-startup/package claim. Dedicated native/client
interception, the complete owner-mode/flood matrix, arbitrary moving-hull
timing, large barrages, player ownership/economy/mechanical mounts and complete
S12/S11/master/boarding remain open. No other production feature is changed
as part of the lifecycle activation.

## Separate production startup activation, unmasked full gate pending

Lifecycle-only `b552cf027689b640aac30b2490baab3308758eca` adds the service's
one call immediately after `ModWorldBlocks.register()` in `DescentMod` and
removes both component-test bootstrap calls. A source search finds only that
main initializer call. `register()` is synchronized/idempotent: it installs
one END_WORLD_TICK tagged-impact flush and one UNLOAD state cleanup. Ordinary
missiles are never queued by that cleanup. The original natural baseline
class is now byte-identical to the first `f4a03f7` source, and all assertions
are unchanged. No additional Java run occurred here.

The component proof remains exact4bafa; this activation source is a distinct
unmasked runtime candidate. Root will register only the two new classes/twelve
cases and run its complete843unit/442GameTest/71entrypoint gate, counting
actual XML. No full/startup/JAR claim is made before that different source
passes. The29 retained focused cases must not be registered again.

After committing the first archive, the closed private derivative's195 files
were checked against every archived SHA and ZIP CRC, with exclusive session
locks, then reclaimed. The committed preservation receipt retains the exact
52,632,147-byte inventory. Other active worlds/donors/canonical paths were
untouched.
