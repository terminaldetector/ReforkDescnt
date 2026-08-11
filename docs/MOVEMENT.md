# How 6DoF movement actually works in Minecraft

The GMod original could not be ported straight across, and the reason is structural rather than a
detail. This is the part that had to be rewritten.

---

## The engine difference

**Source / GMod.** `CreateMove` runs on the client and produces a `CUserCmd`. That command goes to
the server *and* is used for client prediction, and both ends run the identical movement code over
it. Which end you call authoritative barely matters — they agree by construction.

**Minecraft.** There is no shared movement command. A player's position is decided entirely by its
own client, which walks the entity through the world and then reports where it ended up in a
`PlayerMoveC2SPacket`. The server keeps a copy, sanity-checks the claim, and otherwise accepts it.

The mod was written the GMod way: the server integrated the flight model and the client waited to
be told its velocity. Two consequences, both fatal:

1. **The velocity never arrived.** `setVelocity` on a `ServerPlayerEntity` marks the entity dirty
   and the tracker broadcasts an `EntityVelocityUpdateS2CPacket` — to everyone tracking that
   entity. A player is never a listener on its own tracker, so the packet reached every client
   except the one flying the ship. Thrust did nothing at all.
2. **Even fixed, it would lag.** Routing your own velocity through the server costs a full round
   trip on every input. Locally that is a tick; on a remote server it is however bad the ping is,
   and it reads as the ship sitting still after you press thrust.

## What it does now

The client runs the same integrator, from the same inputs, and treats the server's value as an
authority to **converge on** rather than a command to obey.

```
client tick:  sample thrust axes  ->  integrate locally  ->  move(SELF, v)  ->  report position
server tick:  integrate from the input packet  ->  send velocity as an authority
client:       predicted += (server - predicted) * 0.25
```

Thrust answers on the same tick it is pressed. Anything the client cannot know about — an energy
cut, a collision the server resolved differently — arrives as a correction that is blended out
instead of snapped:

| ticks | gap remaining |
|--|--|
| 4 (0.2 s) | 31.6% |
| 8 (0.4 s) | 10.0% |
| 16 (0.8 s) | 1.0% |

In the ordinary case both ends run the same model over the same inputs and there is no gap to
close, so the correction does nothing at all.

It also replaces `travel` outright rather than adding to it. Vanilla's would apply air drag and
gravity on top of the flight model, and in creative it rewrites Y as `previousY * 0.6` every tick —
each of which quietly dismantles a 6DoF velocity vector.

## Units

The flight model works in blocks per **second**, because Source velocities are per-second
quantities and the constants were ported unchanged. `Entity.setVelocity` is per **tick**. Feeding
one to the other made the hull top out at 19.4 blocks/tick — 388 m/s, a full chunk every 0.82
ticks, far past what chunk streaming or any collision sweep can follow. The conversion now happens
once, at the hand-off:

| | blocks/tick | blocks/s |
|--|--|--|
| cruise, surface | 0.97 | 19.4 |
| afterburner, surface | 1.58 | 31.6 |
| afterburner, near-space | 2.83 | 56.6 |

### Afterburner cruise (Descent)

Hold **R** — not a toggle. While held: energy drain, accel/speed boost, and if the stick is
idle the hull still thrusts nose-forward (corridor cruise). Release R to cut the burn.
`/d6 alwaysrun` remains a legacy toggle for scripts.

For scale: vanilla sprint is 0.28 blocks/tick, elytra under rockets roughly 1.5–2.

The wire format matters here too. Vanilla's velocity packet encodes each axis as a short scaled by
8000, which caps at 4.09 blocks/tick — a dash overruns it. The ship velocity therefore rides the
mod's own sync payload instead.

---

## The pilot's model

`LivingEntityRenderer.setupTransforms` returned early whenever 6DoF was on, so the model stayed
world-upright while the hull pitched and rolled underneath it. It now takes the ship's basis.

The obvious implementation — rotate the model's up onto the target by the shortest arc — is a trap.
It is exact for a pitch alone and exact for a roll alone, but the two do not commute:

| attitude | nose error, shortest-arc |
|--|--|
| 30° nose-up, 0° bank | 0.0° |
| 0° nose-up, 90° bank | 0.0° |
| 30° nose-up, 90° bank | **30.0°** |
| 30° nose-up, 180° bank | **180.0°** — flying inverted faces the model backwards |

`ModelOrientation.applyBasis` builds the rotation from forward *and* up instead. Verified exact on
both axes across 1701 attitude samples, determinant 1 so nothing is mirrored, and collapsing to the
identity at level flight so vanilla is untouched. The gravity-torch wall walk uses the same call
with the surface basis.

Only the local pilot is oriented: no other player's attitude is synced to this client, so remote
players are left to vanilla.

---

## Level skyboxes

The Nether and the End are bands of one column rather than separate dimensions, so there is no
loading screen to hide a change of sky behind — you fly across the boundary and the sky has to come
with you.

Anchoring a colour to each band and switching at the boundary puts a seam exactly where the player
is looking. Instead the anchors sit at band **centres** and the colour is interpolated between
whichever two you are between, with a smoothstep so the rate of change goes to zero at each anchor.
That makes the function continuous everywhere by construction, with no special case at a boundary.

| boundary | colour step across it |
|--|--|
| Nether / Abyss | 2.0e-03 |
| Abyss / Industrial | 2.0e-03 |
| Industrial / Surface | 4.2e-03 |
| Surface / Sky | 1.1e-03 |
| Sky / Orbital | 4.2e-03 |
| Orbital / End | 4.0e-04 |

One 8-bit colour step is 3.9e-03, so none of these is a visible edge. The steepest gradient anywhere
in the column is 5.9e-03 per block.

Surface is a **plateau at weight 0**, pinned at both edges of the band rather than at its midpoint.
Anchored at the midpoint alone the tint was exactly vanilla only at y=180 and blended away in both
directions, which put sea level 0.51 of the way to industrial grey — the one altitude the game is
actually played at. Pinning both edges keeps y=40..320 untouched, day, night, weather and biome
tint included, and moves the whole ramp outside the band. The unit test asserts it edge to edge;
that is how the single-anchor version was caught.

One hook does both the sky dome and the fog: vanilla's background renderer takes its overworld fog
colour from `ClientWorld.getSkyColor`, so blending there carries through to the horizon haze
without a second injection into the renderer.

---

## Fixed: diagonal tunnels felt worse than the geometry alone explains

Reported as flying and building diagonal tunnels being uncomfortable — investigated first as a
possible gap in `TunnelCarving`'s wall geometry, but that geometry already chamfers a bore's boundary
to real, quarter-cell collision through `CarvedBlock`'s mask-driven `VoxelShape` (see
`docs/MICROBLOCKS.md`). The actual mechanism was in collision *response*, not shape.

`ServerPlayerFlightTravelMixin.drmd$serverDescentTravel` moves the hull with
`sp.move(MovementType.SELF, perTick)`, which — like any vanilla `Entity.move` — already zeroes
whichever axis a collision sweep actually blocked before the mixin's own code runs. What ran next,
unconditionally, was `sp.getVelocity().multiply(0.86)`: not a tax on the blocked axis, a tax on
*everything that survived*, every single tick a wall is touched. A ship threading a corridor that
isn't axis-aligned grazes on one axis continuously while still trying to travel on the others, and
paid 14% of its entire remaining speed on every one of those ticks, compounding:

| consecutive grazes | speed remaining |
|--:|--:|
| 1 | 86.0% |
| 5 | 47.0% |
| 10 | 22.1% |

— regardless of how gently the wall it touched was chamfered. This is also architecturally why finer
geometry alone could never have fully fixed it: Minecraft's `VoxelShape` is always a union of
axis-aligned boxes, at any resolution — there is no true sloped/diagonal collision primitive in the
engine to chamfer *toward*. Rendering has no such limit (a real mesh can show a genuine diagonal
face), but collision can only ever be a finer staircase, never truly smooth.

Split the graze tax by context instead, via `TerrainClassifier` (`world/micro/TerrainClassifier.java`
— reuses `MacroWorld`, the existing structure catalogue built for radar/HUD contacts, no new spatial
index): `SCRAPE_KEEP_CUBIC = 0.86` unchanged for a position inside any known structure's bounds, so a
built wall still feels exactly as solid as it always has; `SCRAPE_KEEP_SMOOTH = 0.95` everywhere
else — every natural mantle/cave/corridor context, which is what the Descent shaft, the drill rig and
the engineer's tool all already are. Ten grazes down a natural corridor now keep ≈60% of speed instead
of ≈22%. 0.95 is a reasoned starting point, not a tuned one — this sandbox has no live client to feel
it against.

Classification only runs on a tick an actual collision happened, not every tick, and only server-side:
`TerrainClassifier` needs `MacroWorld`, which is never synced to clients, so the client-predictive
twin (`DescentFlightMotion`) is left exactly as it was — the correction-blend above (`predicted +=
(server-predicted) * 0.25`) already exists to absorb exactly this class of divergence, "a collision
the server resolved differently," and does so within 8–16 ticks even for the largest possible jump
between the two constants.

Not done here, and worth naming rather than silently dropping: wiring the classifier into
`TunnelCarving`'s own carve methods, so a drill aimed at a structure wall falls back to a plain
whole-block carve instead of the chamfered one — held back because that pipeline just had a
`StackOverflowError` fixed this session and is still delicate, for a benefit (sharper hole edges in a
structure wall) that is more cosmetic than the actual reported discomfort. And true
marching-cubes-style smooth *rendering* for natural terrain — a real, separate rendering-pipeline
project, and one that, per above, would still buy nothing for collision even once built.

## Fixed: no visible ship, and no way to get out of it

Free 6DoF flight has never drawn a hull — see "The pilot's model" above: the fix there was making the
*pilot's own vanilla body* bank and pitch with the ship basis, not drawing a ship around it. There was
nothing to exit either, because there was nothing separate from the player to begin with — `H` just
flips a boolean on the player entity.

A real ship already existed for this, just not as the default: `PyroShipEntity` (`entity/PyroShipEntity.java`,
"Pyro GX") is an ordinary vanilla-riding vehicle — `startRiding`/`removePassenger`, one seat, spawned
via a placeable item — that a player could already mount and, critically, already had a fully worked
out dismount (`removePassenger`): hang in place in a zero-g zone, switch to foot-gravity walking on a
gravity field, lock local orientation to a floor if one's found underfoot, or fall back to free 6DoF in
open air. It just wasn't anything a player would find without already knowing to place the item, and
its own hull model (`entity/model/PyroShipModel.java`) was an explicit placeholder — a plain fuselage,
two flat wings, a fin, a thruster — with no roll: `PyroShipRenderer` only ever applied yaw and pitch,
so barrel-rolling in it showed your own body rolling while the hull under you stayed level.

**Auto-mount, not a new mechanism.** `FlightSystem.enable()` now spawns a Pyro GX under the pilot and
mounts them the moment 6DoF turns on (`autoMountPyroShip`, guarded on `!player.hasVehicle()`), reusing
`PyroShipItem`'s own spawn call verbatim rather than a new one. That guard is what keeps this safe
across every existing caller of `enable` — the join path (6DoF is native every join), the reactor-room
and psychedelic-dock and void-ending triggers, `/d6` — and, the one that actually matters for
correctness and not just tidiness, `PyroShipEntity` calling `enable` from its own `interactMob`/`tick`
always does so *after* the player is already riding it, so the guard makes that a no-op rather than a
second ship spawning out from under the first. Exiting needed nothing new: sneak-to-dismount is
standard vanilla vehicle behavior, and `removePassenger`'s existing branches already do the right,
context-sensitive thing — the ship is left parked in the world, re-enterable the same way any placed
one always was (walk up, interact).

**New hull, and the roll it never had.** `PyroShipModel` is rebuilt to the ÆRis/concept-art layout — a
tapered fuselage, twin outboard weapon pods (each carrying a laser + missile cuboid), a top twin-tube
cluster-bomb dispenser, and a ventral mining/construction-laser rig — matching how weapons actually map
to hardpoints as reported live (top = cluster bombs, sides = combat lasers + offensive missiles, below
= construction/mining beam), not just the concept art's own generic-rocket read of the top slot.
`PyroShipRenderer` now orients entirely through `ModelOrientation.applyBasis(matrices, 180f, forward, up)`
— the same helper "The pilot's model" uses, but the `bodyYaw=180` constant (not the entity's live yaw)
matches how `ProjectileRenderer` calls it for rocket/mine bodies, not how the `LivingEntityRenderer`
mixin does: this renderer builds its whole transform from scratch the way projectiles do, with no
prior vanilla `180-bodyYaw` rotation on the stack for `applyBasis`'s own internal undo-step to cancel
out, so passing 180 makes that step a no-op and the rotation comes straight from world forward/up. Roll
is only known for the local pilot's own ship — nothing syncs a remote ship's attitude, same limitation
the pilot-model mixin already has — so every other ship falls back to a plain yaw/pitch look vector
with world-up, i.e. no visible roll, which is what this renderer did for everyone before this change.

**Pilot hidden, locally only.** `LivingEntityRendererMixin` gained a second injection, this one into
`render` itself rather than `setupTransforms`, cancelling the local player's own body render outright
while `getVehicle() instanceof PyroShipEntity` — Descent never shows its own ship's pilot from the
outside, only the hull. This is deliberately local-only: it reads `mc.player == entity`, so it hides
your body from *your own* client, not from anyone else's — a nearby player watching someone else pilot
the Pyro GX still sees that pilot's body seated in the hull normally. Making that universal needs a
server-synced visibility flag, a bigger change than this pass makes.

**Not done, and worth naming:** weapon muzzle/aim math (`WeaponCore.aimDir`/`muzzle`) is unchanged and
was never tied to any ship mesh to begin with — it's always been player-relative offsets, independent
of whatever (if anything) is drawn around the player, so the new hull's wing-pod barrels are not
guaranteed to land exactly where the first-person weapon view (`WeaponViewRenderer`) draws its own
boxes; the two were already independent before this change and stay that way. A player who disconnects
mid-flight and rejoins gets force-re-enabled 6DoF (per "every join is native," unrelated to this
change) and therefore a brand-new ship — their old one, if it persisted through the save, is left
behind parked and un-piloted rather than being found and reclaimed; harmless (a stray hull, not a
crash), but not cleaned up here. And the whole hull is new geometry with no way to render a frame in
this sandbox: the shape, the UV-to-texture-region mapping, and the roll math are this session's best
effort, not something confirmed to look right — see the PR/report for exactly what to check first.
