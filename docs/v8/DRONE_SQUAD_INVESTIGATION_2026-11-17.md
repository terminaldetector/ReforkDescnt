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
