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
