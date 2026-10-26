# Gravity torch & generator — walking on walls

`drmd:gravity_torch` and `drmd:gravity_generator` share the Prey-2006 mechanic: the face they are
bolted to becomes the floor for anyone nearby. Walk up a wall, keep walking onto the ceiling, jump
off, fall back to the world.

| Emitter | Reach | Notes |
|---------|------:|-------|
| Gravity Torch | 8 | Compact |
| Gravity Generator | 24 (tunable 20–48) | Same rules, larger area; sneak-use cycles shape, use cycles power |

Both clip to the mount half-space. Players use `FootGravitySystem`; mobs use `EntityGravitySystem`.
On a hard reorient, `GravityMount.safeMount` places feet on the new surface and clears the standing
hitbox so entities are not crushed into a 1-cube crawl / suffocation.

The design rule everywhere below is that **on a level floor nothing may change**. Every formula
here reduces exactly to vanilla when local up is world up — that is what makes it read as stock
rather than as a mod bolted on top.

---

## Placement and reach

| | |
|--|--|
| Local up | the face you clicked — click a wall, that wall is now down |
| Reach | 8 blocks, falling off linearly from the emitter |
| Clipped to | the side the emitter faces |
| Capture | 0.18 rad-fraction per tick, ≈0.4 s to settle |
| Release | 0.26 per tick — 0.6 s off a wall, 0.7 s off a ceiling |

The half-space clip matters more than it sounds: without it a torch on a wall reorients whoever is
standing on the floor of the room on the *other* side of that wall.

---

## Four things that made it feel wrong

### 1. Headings collapsed on a vertical surface

Walk direction was world yaw flattened onto the surface. Yaw rotates about world Y, so flattening
it against a wall whose up is horizontal projects the entire circle of headings onto a single axis.

Measured over 24 evenly spaced mouse yaws on an east-facing wall:

| | distinct walk directions |
|--|--|
| before | **3** |
| after | **24** |

It now comes from the look vector flattened onto the surface. The look vector already lives in the
surface's frame, so projecting it out is the whole job — and on a floor it is plain vanilla forward.

### 2. Mouse look turned about the wrong axis

Yaw now turns about local up and pitch about local right, clamped to ±89° so the camera roll stays
defined. Sensitivity is vanilla's 0.15 °/count, unchanged.

### 3. The camera tipped in a direction that depended on where you looked

The view correction built a world-space axis-angle rotation and multiplied it into a matrix stack
that was already in view space, so the axis was interpreted in the wrong basis.

It is now a screen-space roll equal to the bank of local up against the current heading — the same
decomposition `ShipAttitude` uses for 6DoF, shared as `ShipAttitude.bankDegrees(forward, up)`.

Verified numerically:

- floor, 1300 headings sampled: max \|roll\| = **0.00°** — bit-for-bit vanilla
- east wall, 8 headings: the roll lands local up on screen-up to machine precision

### 4. Ceiling flips normalised a zero vector

The up vector was blended with a component lerp. Stepping from `(0,1,0)` to `(0,-1,0)` passes
through the origin at t=0.5, and normalising that is a division by ~0 — the camera tumbled through
garbage on exactly the transition the torch exists for. `ShipAttitude.slerp` walks the shortest arc
instead, with a stable perpendicular picked when the two vectors are exactly antipodal.

---

## Two more fixes

**Leaving a field never released you.** Outside the radius the last surface's up was kept forever,
so walking off the end of a walkway left you standing on thin air with wall gravity. It decays back
to world up and hands you to vanilla gravity.

**Fields were global across worlds.** They lived in one map with no world key, so a torch in the
Nether could flip a player standing in the Overworld.

---

## Walk speed

Ground friction is 0.84, so terminal speed is `accel / 0.16`. The old gain of 2.5 on movement speed
put that at 1.56 blocks/tick — seven times vanilla walking, which read as skating.

| | blocks/tick | blocks/s |
|--|--|--|
| vanilla walk | 0.216 | 4.32 |
| vanilla sprint | 0.280 | 5.60 |
| torch walk (both platforms) | 0.219 | 4.37 |
| torch sprint (both platforms) | 0.284 | 5.69 |

---

## MCPE

The mechanic is fully implemented on Bedrock and shares the PC constants — same reach, same capture
and release rates, same walk speed, same half-space clip.

Two implementation differences, both forced by the platform:

**Motion is teleport-driven, from our own position.** Bedrock has no per-tick velocity setter for
players and no way to switch a player's gravity off, so the engine keeps pulling world-down every
tick underneath the walk model. Stepping from `player.location` would fold that pull into the
result and the pilot would slide down the wall they are meant to be standing on. Surface travel
therefore integrates from a tracked position and overwrites the player's position each tick with
`tryTeleport`, which makes the engine's contribution moot; `checkForBlocks` supplies the collision.
Fast passes are split into sub-steps so a step cannot tunnel a one-block wall, and the tracked
position resyncs to the engine's whenever the gap exceeds 2 blocks — something else moved the
player and our copy is stale.

Hitting geometry stops a walk dead rather than bouncing it. A bounce is right for a hull at speed
and wrong for a pair of boots.

**Torches are found by sweeping.** There is no field registry to hook, so the script sweeps one
horizontal slice of blocks per tick around each on-foot player: 169 lookups, a full 13-slice cube in
0.65 s while a surface has you and 3.25 s idle. Placing or breaking a torch registers it instantly
through the block events, so the sweep only matters for torches you did not just place.

### The one thing Bedrock cannot do

**The view does not roll.** The script API exposes yaw and pitch only — there is no roll channel,
so a wall cannot be made to look like a floor. You genuinely walk the wall, stick to it, fall toward
it and jump off it, but the horizon stays world-level while you do. Everything else matches PC.

The HUD names the surface instead, so the state is at least legible:
`Гравифакел · СТЕНА · UP 1.00 0.00 0.00 · SURFACE`
