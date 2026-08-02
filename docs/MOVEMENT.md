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
