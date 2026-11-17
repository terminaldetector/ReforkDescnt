# Scout investigation: first missing-feature baseline

Contract: `DRONE_SQUAD_INVESTIGATION_CONTRACT_2026-11-17.md`.
Test-only source: `faf2983528d26bcede9f1e0907db1c6b00b07b8f`, based on
`1a8593846bed3b02d03d68236c6698d26a5116d4`. Production was unchanged.

The first focused stock Fabric run compiled, executed both natural-AI cases,
and closed exit **1**, **1 FAIL / 1 PASS** (53 seconds). The actual child exit
was written immediately, and `/proc` showed no active Java process before
returning the shared lease. No correction or retry preceded this checkpoint.

The scout case failed the missing investigation behavior. At 120 normal entity
ticks, the real scout had naturally acquired and seen the legitimate enemy;
the victim remained alive at 1024 HP without position drift. The initially
occluded wingman also advanced 120 actual entity ticks but climbed **0.0**,
never acquired a target and never emitted its own projectile. Fixture chunk
admission, actual allegiance predicates and initial LOS checks passed. This is
a genuine absent squad communication result, not a fixture or defined-target
shortcut. The no-scout control passed without inventing a pursuit or shot.

Bundle: `EVIDENCE/drone-squad-first-negative-faf2983.zip`.
SHA-256: `ea348d2f225eb1fab3dc397f52686fcd9e8b5262c6890e0bb50513ea7b9222cb`.
All 77 payload hashes and ZIP CRC were verified. The archive contains exact
initial source/contract, compiled test and relevant production classes, actual
focused manifest, executed helpers, raw log/fresh XML/immediate exit/inputs,
and all 45 closed-world files (14,773,417 bytes).

The next authorized implementation is the bounded receiver-mailbox and normal
investigation-goal increment from the contract. Additional immutable hidden
position, TTL, invalidation, per-world and resource-bound tests must accompany
it. The first two natural behavior assertions remain unchanged. No positive,
native, whole S11 or complete-spec acceptance is claimed here.

## First implementation candidate (not a completed positive)

Exact source `0cdb5ecc195a64ee4ccc145914740998fb5b2ba2` compiled and
passed all four pure mailbox tests. Its first focused seven-case server run
closed exit 1: six passed, the original natural investigation case failed.
The hidden-position/original expiry, actual teammate, removed sender/target,
cross-world and bounded-work guards passed, as did the no-scout control.

The original natural case advanced 120 real actor ticks. Its scout naturally
acquired and saw the live, unmoved victim. The receiver now moved about 5.947
blocks along X but only 0.050 along Y, never acquired its own target and did
not emit its own projectile. This proves partial communicated movement, not
successful 3D investigation. The route/steering cause is unclassified here;
there is no justification to raise budgets or TTL or inject a path/climb.

Bundle: `EVIDENCE/drone-squad-first-candidate-0cdb5ec.zip`; its SHA-256 is
recorded in the adjacent `.sha256` file. The first raw unit/server logs, fresh
XML, source/input/runner snapshots and closed world are retained with verified
per-payload SHA-256 and ZIP CRC. Both original semantic assertions remain
unchanged. The lease was returned on first close, with no live Java, before
diagnosis or correction. A separately authorized test-only read-only task/nav
trace is next; production must remain `0cdb` until the cause is measured.

## Read-only route diagnosis, same original two criteria

Test-only `0a60556a7bea394245095c231b0392e80334cdc6` retained production
`0cdb` byte-identically. Its first two-case run compiled and closed exit 1,
1 FAIL / 1 PASS. All thirteen bounded read-only rows and existing navigation
debug output are preserved in `EVIDENCE/drone-squad-route-trace-0a60556.zip`,
with its digest in the adjacent `.sha256` file. No retry preceded diagnosis.

The authoritative ORB role is ASSAULT, 540 Source units × 1/80 = 6.75 m/s or
0.3375 blocks/tick. The search started around fixture tick 19, had 41 visited
nodes at tick 20 and found a 33-cell/32-edge route before tick 30. No limit,
stalled state or expiry occurred. Index advanced only 1@30, 2@40, 3@50, 5@60,
7@80, 12@120. Per-cell issued velocities decayed from approximately .095 to
.035 to .012 as the actor approached each exact grid centre. At 120 the route
was still active at Cell(7,5,0), climb 4.997, issued Y .03533 and actual Y .03463.
The message was fresh (observation 188, expiry 248), with actual max allies 1,
candidates 2, cleanup examined 1 and 21 publications/deliveries. The earlier
untraced run's .050 climb remains its distinct original result.

`DroneNavigation` brakes by `min(roleSpeed, deltaLength*.4)` until the actor is
within .02 of every cell centre. Its existing maximum-two-waypoint FULL swept
clearance lookahead only runs when close-neighbour separation is nonzero.
That causes unnecessary corner-centering crawl for this solitary wingman.
The actual role can traverse the approximately 13 metres to a sight line in
about 39 ticks, so no speed/TTL/search-cap or 120-tick assertion increase is
justified. The approved correction is exactly to allow that existing bounded
FULL-clearance lookahead without a nearby crowd, retaining each sweep, the
four-block displacement bound, role speed, search budget and .02 threshold.

## First global lookahead result: new feature passes, retained combat fails

Exact `d6ca593ff82b9ac85b7436480922359f5ce63037` passed four unit tests;
the first focused server run was **19 PASS / 1 FAIL**, exit 1. All seven squad
cases passed unchanged. The natural scout/wingman case succeeded at 62 real
ticks, with pre-acquisition climb, own natural lock and a real owned projectile
(8.064 blocks climb). Both shared six-actor apertures retained six firing lanes
and six damaging actors. The real hull/unknown-cover controls passed.

The retained `rocketFindsALaneWhileAlreadyTouchingTheWall` failed at 380 ticks:
a firing lane was observed, but no attributed target damage. This global
combat-behavior change is **not accepted** and cannot be waived because the new
feature passes. Original aim, weapon/role values, thresholds and assertions
remain unchanged. Exact cause of the missed rocket damage is not claimed from
position alone.

First-result bundle: `EVIDENCE/drone-squad-global-lookahead-d6ca593.zip`, digest
in the adjacent `.sha256` file. All raw 4+20 results, natural/shared/debug rows,
source/input/runner snapshots and closed-world files were verified by payload
SHA-256 and ZIP CRC before any correction. No retry occurred.

The approved narrower contract preserves the old `steer` two/three-argument
entry points and their separation-gated lookahead. Only a new explicit
investigation entry enables the existing bounded two FULL swept-clear steps.
Only the new squad goal calls it. This retains established combat following
while adding the missing investigation feature; it does not change role speed,
weapons, TTL, search limits, waypoint tolerance or the 120/380-tick criteria.

## Accepted scoped investigation and retained combat regression gate

Exact source `075c2c1931030b2d68fda06b2e9ed7fd06caa20a` passed its first
four-unit then twenty-GameTest run, both exit 0. The original natural two
criteria, all five mailbox integration guards and thirteen retained typed,
shared-aperture and cover tests are unchanged. No retry or assertion change
preceded this result. The Java lease was returned immediately after the
actual processes closed; no active Java remained.

The natural scout/wingman case succeeded at **65 real entity ticks**:
pre-acquisition climb 8.070182539101566 blocks, normal own-LOS acquisition and
a real owned projectile. The live victim remained unmoved. No target or path
was injected into the receiver. The no-scout control still invented no target,
investigation or shot. Hidden victim motion could not rewrite the original
observation or renew its expiry; actual teammate, removed sender/target and
cross-world invalidation controls passed.

The final navigation boundary preserves the old two/three-argument `steer`
entry points and their separation-gated lookahead. Only the new package-local
`investigate` entry, called only by `SquadInvestigateGoal`, opts into the same
maximum-two FULL swept-clear steps. The normal typed ORB/JET/ROCKET combat
routes and the rocket already touching a wall passed, including **actual
attributed rocket damage** within its unchanged 380-tick criterion. Both
six-actor wall/ceiling apertures and finite target memory passed. Real rotated
hull cover, unknown-column safety and vertical flight/retreat controls passed.
The previous global `d6ca593` failure remains an unaccepted separate first
outcome; this narrower correction does not retroactively turn it green.

The actual natural-run sample at tick 60 had one receiver mailbox, maximum
one allied recipient/two examined candidates, one cleanup entry, eleven
publications/deliveries and zero evictions. Separate pure guards offered 512
receiver UUIDs and retained exactly 256, evicting 256 without a queue. Cleanup
examined at most 32 entries per five ticks; reads could not refresh the
60-tick original observation lifetime. These are bounded-work proofs, not a
256-actor combat performance benchmark.

Positive bundle: `EVIDENCE/drone-squad-narrow-positive-075c2c1.zip`.
SHA-256: `2809d9020297d96b80185dbd2d8ead94eb778609b9c24a407ee80d4f35c6f4b5`.
All **214 payload hashes** and ZIP CRC passed, including exact source/diff,
actual compiled relevant classes and focused manifest, initial source/input
snapshots, executed frozen helpers, raw first logs/XML/immediate exits,
bounded route/work receipts, and all 165 closed-world files (78,413,041 bytes).
The index `EVIDENCE/drone-squad-checkpoints-075c2c1.json` preserves all five
original checkpoints (683 payload hashes verified) and their classifications.

For the ordinary suite, register only `DroneSquadTaskGameTests` and
`DroneSquadGuardGameTests`: **seven new GameTests** plus the **four new mailbox
unit tests**. The other thirteen focused regression cases already belong to
the ordinary suite. Root integration, full combined server suite and same-JAR
client gates remain pending here; the focused twenty do not substitute for
them. This new stage is stock Fabric server proof, not dedicated native/client
Squad acceptance. Broader tactical task allocation/director integration,
anti-missile interception, additional bestiary identities, moving-hull and
256+ performance tiers, and full S11/master/boarding completion remain open.
