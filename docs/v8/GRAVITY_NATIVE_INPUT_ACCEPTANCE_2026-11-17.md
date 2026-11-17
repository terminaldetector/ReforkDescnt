# Gravity tools: actual key and inventory route acceptance

Exact tested source: `5f8eae13817256fdda4aebad7a57383877feaae5`.
This same source passed the first unchanged **10-case stock server-route run**
and the first corrected **six-scene native client run**. The final evidence-only
commit is not the tested source revision.

## Result and behavior

Short clicks could reach the server as press and release in one tick without
ever calling the selected weapon. `DroneArsenal` now keeps a bounded three-bit
rising-edge latch with three original stack references. It consumes those
requests once, preserving latest held input, cooldowns, TTL and ownership.
Rejected inputs are not queued for later firing. An active same-stack charged
weapon retains its own physically held bank, and genuinely released/replaced
charges stop before another use can bypass their resulting cooldown.

Production commits are `347cf76` (bounded edge latch) and `da3a5d3` (active
charge ownership). Native observers and the ten GameTests remain test-only.
No production client input, equipment, weapon statistics or physics engine was
changed. No publication was performed.

The fresh native runtime used the real client key bindings, actual hotbar/H/N
actions and handled-screen clicks. All six transactions reached the actual
server receiver and registered gravity-item implementation:

| Scene | Actual selection | Capture / throw cost | Normal hold ticks | Server equipment clicks |
| --- | --- | --- | --- | --- |
| Ordinary SWARM, LMB | Hotbar 0, channel 0 | 18 / 10 | 12 | 0 |
| Ordinary RAIL, RMB | Hotbar 1, channel 1 | 16 / 12 | 10 | 0 |
| Ordinary SHIFT, LMB | Hotbar 2, channel 0 | 20 / 18 | 10 | 0 |
| 6DoF SWARM, LMB | Empty hotbar 8, socket 0 | 18 / 10 | 11 | 2 |
| 6DoF RAIL, RMB | Empty hotbar 8, socket 1 | 16 / 12 | 10 | 2 |
| 6DoF SHIFT, RMB | Empty hotbar 8, socket 1 | 20 / 18 | 11 | 4 |

Every actual use observed real cooldown progress zero and debited its exact
synchronous cost. All flight press receipts show `minecraft:air` in the real
main hand with selected slot 8. Each scene used the same original body UUIDs
through capture and throw; SWARM held four bodies, RAIL one 100-cell body and
SHIFT one body. Normal hold springs moved material 2.52–4.02 blocks before the
second key. Every original target launched with forward physical velocity over
24 m/s, and SHIFT created its own directional field. All owned fields and
bodies were retired only between completed transactions; live bodies never
exceeded four. Natural energy admission took 0–2 server ticks in this run.

The driver never directly called `Item.use` or the trigger receiver, wrote an
arsenal held state, equipped sockets directly, or injected capacitor energy.
The real server remained authoritative. Provisioning and one initial player
placement were restricted to bootstrap; there was no following teleport or
target pose rewrite during a transaction.

The first build/remap and required refmap/actual target-method checks passed
(24 seconds), staging passed, and the single native process and postchecks
closed **0**, without timeout or retry. It used software GL 3.2/GLSL 150 with
the native Cubic backend and retained all original 300-second observer and
360-second external plus 15-second grace limits. The marker records 348
simulation ticks, 23 real binding actions and **12 fresh rendered frames**.
The lease was returned immediately at process close before analysis.

## Required physical pulse ordering

The original one-client-tick key functions, eight-tick minimum hold, real
cooldowns, energy/identity assertions and deadlines are unchanged. Ordering-only
commit `5f8eae1` corrects the observer's original assumption that release must
follow the item callback. Each pulse now has its own baseline, eligible decoded
press/release pair, actual scene item use after its press, and latest eligible
server mask zero. Pair receipts are preserved, not inferred from local keys.

For example, ordinary SWARM capture recorded press sequence 3, release 4 and
item use 5 at world ticks 9089, 9089 and 9090. That is the exact deferred-use
short-click case the server latch fixes. Other scenes also exercised use before
release. This is not a sustained-press substitution. The original observer and
failed `07b` source remain in their immutable bundle; the rationale is in
`GRAVITY_NATIVE_INPUT_ORDERING_2026-11-17.md`.

## Preserved evidence and failures

`EVIDENCE/gravity-tools-key-route-checkpoints-5f8eae1.json` indexes seven separate
bundles. Every archive CRC and all **928 payload SHA-256 values** were verified.
The history retains its original classifications:

- `c76`: pre-world required-mixin mapping failure, not a gameplay negative.
- `07b`: actual native decoded short-click loss; scene 0 failed, five unrun.
- `ac1`: initial stock receiver gate, 7 FAIL / 2 PASS; later positive controls
  explain the cooldown/ownership/selection case failures.
- `347`: unchanged stock nine-case positive, 9 PASS; broader charge not claimed.
- `4aff`: charged-bank negative, 9 PASS / 1 FAIL; premature shot count was not
  recorded, so no exact duplicate count is claimed.
- `5f8`: unchanged stock ten-case positive, 10 PASS, plus this native six-scene
  positive on the same source.

Native bundle: `EVIDENCE/native-gravity-input-positive-5f8eae1.zip`.
SHA-256: `ce7f562cb64ae23f6dc3ddd0b425d2c36ece89a8fcd6351940ce96e782c5237f`.
Its 151 verified payloads retain the exact tested source/diff, both actual JARs,
five executed immutable helpers, source/input/launch/refmap receipts, original
logs/immediate exits, complete marker/physical pairs, twelve frame PNGs and all
97 closed-world files (38,480,835 bytes). Shared immutable libraries and the
Minecraft-only derivative are identified by hashes instead of duplicated.

Main candidate JAR SHA-256:
`1d5b9364c9ba1e4bce7c256f8acd757480e238e5bf1d65ec071662a475393e9c`.
Test observer JAR SHA-256:
`362494302950d51991744774b39de0423052ca1ee0191ee3850b22e656d7b6c6`.

## Acceptance limits

Ordinary mode is a creative controlled lab with 6DoF disabled; this does not
certify Survival balance or ordinary walking. Native Fusion and other weapon
families, gravity-compensated material-hit RAIL ballistics, continuous roll and
hand/viewmodel stability, model/UI pixel quality, save/restart, large swarms
and performance remain outside this gate. Frames prove fresh real rendering,
not a separate visual-quality score. The root-owned combined ordinary suite
and merged build must run after registering
`com.terminaldetector.drmd.DroneTriggerEdgeGameTests` (ten tests). The planned
824-unit / 418-GameTest integration is not claimed as passed by this lane.
