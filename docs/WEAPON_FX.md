# Weapon FX — beams, melt, blasts, drill

Shared combat VFX/terrain effects on top of existing bomb-bay smoke/fire stack.

## Pieces

| System | Role |
|--------|------|
| `weapon/fx/WeaponFx` | Beam dust, melt stages, explode (splash+smoke+crater), drill carve |
| Hitscan weapons | Green/exotic beams + block melt on impact |
| Descent combat lasers | Dual/quad **travel-time bolts** from workshop modules (`DescentLaserFire`); converge on reticle; primary has LASER LVL 1–4 |
| Mining drill laser | Hold RMB continuous melt; sneak = 3×3 carve |
| Heavy explosives | Rockets / mega / concussion / frag / reactor / BFG → `WeaponFx.explode` |
| Projectile meshes | Bolt / rocket / orb / drill silhouettes (`ProjectileRenderer`) |
| Item models | 3D JSON for lasers + missiles using existing `textures/item/*` |

## Assets reused

- `drmd:item/weapon_d6_*`, `mining_laser`, `construction_laser`, `repair_laser`
- `SmokeSystem.emitExplosion`, `FireSystem.igniteBlast`, `AtmosphereRules.scaleBlast`
- Vanilla particles: `FLAME`, `LAVA`, `EXPLOSION`, `END_ROD`, dust beams

## Making a shot visible (tracer)

A Descent laser leaves the muzzle at 6200 source units — about **70 blocks per tick**. The trail was
one dust bead per tick at the round's position, so it drew one dot of light every seventy blocks and
the round was through the far wall before a second one appeared: at no point in the flight was there
a bolt on screen. `ProjectileEntity.drawTracer` fills the segment the round actually covered instead,
a bead every half block, capped at 48 a tick, fading toward the tail so the streak has a direction.

`ProjectileRenderer` matches it: the silhouette was `0.12` blocks across — under a pixel at any range
worth firing at — and is now `0.22`, and a bolt's body is stretched back down its own axis by the
distance it is travelling (clamped ×1…×26). Slow rounds stretch by nothing and keep their shape; fast
ones become the streak the eye follows rather than a dot that flickers once per frame.

Speeds themselves are unchanged — this is what the rounds already did, drawn so it can be seen.

## Why nothing was on screen (render layer)

Both `ProjectileRenderer.drawBox` and `WeaponViewRenderer.drawBox` emit six **quads** — 24 vertices,
four per face. Three render layers, in order, before this settled:

1. `RenderLayer.getDebugFilledBox()`, declared `VertexFormat.DrawMode.TRIANGLE_STRIP`. Quad-ordered
   vertices read as a strip are not six faces; they are a run of degenerate slivers, so the rounds had
   no visible body at all and neither did the ship's weapon modules.
2. `RenderLayer.getDebugQuads()`/`getLightning()` — right primitive (`DrawMode.QUADS`), wrong layer:
   both are special-purpose vanilla layers (the F3 debug overlay; the lightning-bolt entity), and per
   `CockpitRenderer`'s own finding from fixing the exact same class of problem, one that "often never
   appears under TLauncher / sodium-class pipelines." High-frequency gameplay geometry — every shot
   fired, the weapon view held every frame in first person — going through a layer that sometimes
   doesn't render, or renders one of several quad passes without the others, reads as disconnected
   fragments rather than a coherent shape.
3. `RenderLayer.getEntitySolid()`/`getEntityTranslucent()` — the ordinary entity layers
   `DroneSwarmRenderer`, `SkyUfoRenderer` and `MegaWormRenderer` already drew their own hand-built
   quads through successfully. Reliable, but this project cannot check locally whether these layers
   cull backfaces at all, or which winding they'd treat as front if they do — no decompiled Minecraft
   source and no live client are available here, only GitHub Actions CI, which compiles and runs
   pure-logic tests but never renders a frame. Trusting one winding blind was exactly what step 2's
   symptom looked like again after this swap (see the section below): a box whose faces are each
   internally consistent but might be **entirely** front- or **entirely** back-facing, never partway.
   `quad()` in every renderer built this way now emits each face in **both** winding orders instead of
   betting on one — whichever the true convention favours survives; if culling turns out to be off
   entirely, the second pass just overpaints the first, since it is the same four corners and the same
   colour landing on identical depth, not a different surface competing for it.

One casualty of steps 2→3: glow shells used to blend additively (`getLightning()`, chosen so an
all-glow bolt reads in a dark mine without washing out a sunlit field). `getEntityTranslucent()` is
ordinary alpha blending, not additive — a deliberate reliability trade, not an oversight. The opaque
core (alpha 1.0) still fully covers whatever is behind it either way, so it stays legible on any
background; the glow itself is a softer effect than before.

## Structure copied from the original (Descent 2 source)

`WEAPON.H` gives each weapon a `render_type`, and `LASER.C` is the whole of the dispatch:

| `render_type` | object `rtype` | drawn by | size from |
|---|---|---|---|
| `WEAPON_RENDER_BLOB` — lasers, plasma, fusion | `RT_LASER` | `Laser_render` → `draw_object_blob` → `g3_draw_bitmap` | `blob_size` |
| `WEAPON_RENDER_POLYMODEL` — missiles | `RT_POLYOBJ` | model, turned along flight | model radius ÷ `po_len_to_width_ratio` |
| `WEAPON_RENDER_VCLIP` | `RT_WEAPON_VCLIP` | `draw_weapon_vclip` (animated sprite) | `blob_size` |

The blob is a **billboard**: a sprite pinned to the camera at the object's position. That is why a
Descent firefight is readable — a bolt has no orientation of its own, so there is no angle it can be
seen edge-on from, and it is the same bright shape coming at you as crossing in front of you. A
missile is a model because a missile has a nose and a tail you are meant to read.

We follow the split: `MESH_BOLT` and `MESH_ORB` take the blob path (camera-facing quad, saturated
glow with a whitened core — translucent, not additive; see "Why nothing was on screen" above),
`MESH_ROCKET` / `MESH_DRILL` / `MESH_MINE` take the model path (oriented body, plus a camera-facing
exhaust blob at a rocket's tail so one heading away from you is still a light).

One liberty: the blob is stretched along its screen track. Descent's rounds are slow enough to be
discrete objects frame to frame; ours cross seventy blocks in a tick, so an unstretched blob would be
a dot in a different place every frame.

**Confirmed directly against the source** (`LASER.C`/`OBJECT.C`, both games — D1's `Weapon_info[]` and
D2's are never statically initialized in either source tree; the real per-weapon numbers live in a
`bitmaps.tbl` game-data file this checkout doesn't have, so exact stock `speed`/`fire_wait`/`blob_size`
values can't be pulled from source, only the mechanics around them):

- **`obj->size` for a blob weapon is set once, at creation, from `blob_size`, and nothing in `LASER.C`
  ever changes it afterward.** `draw_object_blob` (`OBJECT.C`) feeds that one fixed number straight to
  `g3_draw_bitmap`; the only thing that makes a far-away bolt look smaller is the ordinary 3D camera
  projection every object gets, not a per-weapon distance or speed term. The stretch/tracer machinery
  above exists entirely to cover a gap the original never had — Descent's engine advances a bolt a
  small fraction of the screen between rendered frames at its actual speed; a single 20 Hz Minecraft
  tick at the same speed does not.
- **Ship velocity is not inherited by weapon fire**, with one named exception: `Laser_create_new`
  (`LASER.C`) sets `velocity = direction * (weapon_speed + parent_speed)`, and `parent_speed` is
  computed — and used — only for `PROXIMITY_ID`, specifically to stop a mine dropped while flying
  backward from launching itself at the ship. Laser, vulcan, spreadfire, plasma, fusion and every
  missile fire at exactly `speed[Difficulty_level]`, full stop, regardless of how fast or which way the
  ship is moving. `WeaponCore.fireProjectile` here does the opposite by default —
  `dir.multiply(spd).add(shipVel.multiply(inheritFactor))` — for every weapon that doesn't zero
  `cfg.inherit`. Not flagged as a bug (Source-engine `prop_physics` projectiles inheriting carrier
  velocity is the GMod original's own behaviour, which is what this port is actually ported from, not
  Descent directly) — flagged as a real, source-confirmed point where the two lineages disagree, worth
  knowing before tuning either one against "how Descent does it."
- **No weapon has random aim spread except Vulcan and D2's Gauss** — every other weapon in both games
  fires dead straight down its gun point, spread only ever coming from *multiple simultaneous shots at
  fixed offsets* (Spreadfire's alternating fixed 3-shot cross, D2 Helix's 5-bolt fan at 22.5° steps),
  never from per-shot randomness. Vulcan/Gauss roll `(rand()/8 - 32767/16)` per axis per shot (Gauss
  divides that by 5 for a tighter cone) — real randomness, but confined to exactly those two weapons.
- **The base laser is always two simultaneous bolts**, guns 0 and 1 fired together every trigger pull —
  "single laser" already means a dual-bolt volley in the original, which is what this port's own
  dual-bolt implementation already matches; quad laser adds guns 2/3 for four bolts at once, not two
  pairs alternating.
- **A wall hit right at the muzzle silently cancels the shot** — the player-fire path raycasts from ship
  position to the computed gun point *before* creating anything, and drops the shot with no object, no
  effect, nothing, if that segment is blocked (`mprintf("Your laser is stuck thru a wall!")` in a debug
  build, nothing in a release one). The identical check against *objects* (including, per the source
  comment, potentially the firer's own ship) explicitly does **not** block firing — "we don't care if
  the laser is stuck in an object, we just fire away normally." A weapon that reaches its lifetime limit
  still detonates for splash damage if it has `damage_radius`; only zero-radius weapons (plain bolts)
  simply vanish.

## Fixed: multi-muzzle lasers firing at extreme angles close-up

Reported as "lasers and small arms are tied to the player model, so they fire in every direction
instead of where the player is looking." The aim vector itself checked out — `WeaponCore.aimDir` /
`DescentPlayerData.shipForward` / the client's mouse-look-to-attitude pipeline
(`ShipAttitudeClient.applyMouse` → `InputPayload` → `setShipAttitude`) all trace cleanly to the same
direction, with no player-model orientation anywhere in that path. The bug was in a different, real
place: `DescentLaserFire.fireModuleBolts`/`firePrism` aim each wing bolt from its own muzzle position
*at a shared convergence point* (`resolveAimPoint`, the first raycast hit under the reticle), so a
volley reads as bolts converging on the crosshair rather than firing dead parallel — correct, and how
Descent's own multi-barrel lasers work.

The point returned by `resolveAimPoint` had no minimum distance. A wing muzzle sits up to ~2 blocks off
the ship's centreline; aim that muzzle at a convergence point closer than that offset and the direction
swings wide — past 90° once the point is nearer than the muzzle's own forward offset (~1.2 blocks),
climbing toward 120° as the raycast hit approaches the eye. In the open that never happens (nothing to
hit within a couple of blocks), but this game is built around tight corridors and point-blank robots,
where the first raycast hit routinely lands well inside 2 blocks — exactly the geometry that produces
it, and exactly why it read as "fires in every direction" rather than as an occasional glitch.

Fixed with a floor: `DescentLaserFire.MIN_CONVERGE_SU` (800 units / 10 blocks) is the closest
`resolveAimPoint` is allowed to return. Chosen from the actual geometry, not guessed — at the floor
itself the worst wing-muzzle bolt (the quad layout's upper mount, 2 blocks out) is ~13° off forward;
unfloored, the same muzzle passes 90° before 1.2 blocks and keeps opening toward 120° from there.
Farther targets are untouched: the floor only ever raises a raycast distance, never lowers one.
Pinned by `LaserConvergenceTest`, which mirrors the muzzle offsets and the floor (both public/private
constants copied with a "keep these in sync" note, since importing `DescentLaserFire` itself would
require stubbing `PlayerEntity`/`ServerWorld`/`RaycastContext` for one number).

## Fixed: solid box faces missing after the debug-layer swap

Reported as "Забаговано отображение снарядов при выстреле" (projectile display is bugged on firing) —
still broken after step 3 above shipped (`getEntitySolid`/`getEntityTranslucent` in place of the
debug layers). Ordinary entity layers are exactly the layers vanilla mob models rely on backface
culling for, and get it for free: a `ModelPart` cuboid is built by code that guarantees consistent
winding. The seven renderers in this mod that hand-write box/quad vertices instead of using a
`ModelPart` don't get that guarantee automatically, and it turned out to matter.

Recomputing the actual coordinates in `ProjectileRenderer.drawBox`/`WeaponViewRenderer.drawBox` (cross
product of each face's own edges) showed both were already internally consistent — a comment claiming
otherwise was stale, left over from before those coordinates were last touched. But `MegaWormRenderer`,
`SkyUfoRenderer`, `EndReactorBossRenderer`, `ReactorKeeperRenderer` and `DroneSwarmRenderer` hand-build
cube/quad geometry through the same two layers too, and are *also* internally consistent — with the
exact opposite handedness. A uniformly-wound box is either fully visible or fully backface-culled,
never patchy, under any one convention, so at most one of these two groups could have been "right,"
and this project has no way to check which — no decompiled Minecraft source, no live client, only CI,
which never renders a frame.

Fixed the same way in all seven files: `quad()` now emits every face in both winding orders (`v0,v1,
v2,v3` then `v0,v3,v2,v1`), the true outward normal kept unchanged on both copies since the physical
direction doesn't change even though which copy culling calls "front" might. Whichever ordering the
real convention favours renders; if culling isn't active at all, both draw identically stacked and the
second pass just overpaints the first. Pinned by `ProjectileBoxWindingTest`, which mirrors `drawBox`'s
own six-face coordinates and confirms the two orderings are exact opposites and that the original order
is the genuinely outward one.

A custom `RenderLayer.of(...)` with its own `Cull` phase forced off would only need one winding
instead of two — the "proper" fix — but this codebase has no existing use of
`RenderLayer.of`/`MultiPhaseParameters`/`RenderPhase` anywhere to build from, and getting that surface
wrong is a runtime failure (a `VertexFormat` mismatch) CI's pure-logic tests would not catch; it would
ship green and only fail live. Left as a deliberate follow-up, not attempted blind here.

## Fixed: a giant flat colour filling the screen on every shot

Reported again after the winding fix above shipped, with a video: firing fills most of the screen
with two solid diagonal bars of colour for as long as the trigger is held, not a bolt at all. The
winding fix didn't touch this because it isn't the same code path — `drawBox`/`quad()` build the
*solid* rocket/mine/drill bodies and the weapon-view modules; bolts and orbs take the separate
billboard path in `renderBlob`/`billboard`, camera-facing quads rotated by a `Quaternionf`
(`matrices.multiply(camera)`), which can only encode a rotation, never a reflection — so their winding
as presented to the camera can't be the "wrong" one on some frames and not others the way a
hand-wound box's can. That ruled the billboard path out as the culprit going in; the actual cause was
in the same method for an unrelated reason.

`WEAPON_RENDER_BLOB` in the original (`LASER.C`) is `draw_object_blob`: one call to `g3_draw_bitmap`,
a fixed-size sprite (`blob_size`) that the standard 3D projection naturally shrinks or grows with
distance — Descent's own rounds never needed anything more, because they move slowly enough to stay
legible as a small dot from frame to frame. Ours cross roughly seventy blocks a tick, so a plain dot
would be in a different place every frame; "Making a shot visible (tracer)" above added a length
stretch along the round's own screen track to compensate, sized from how fast it's going
(`clamp(speed * 0.55, 0, 7)` blocks) — a deliberate addition beyond what the original ever had to do,
and the thing the original's fixed-size sprite made unnecessary to guard.

That stretch is sized from speed alone, with no regard for how far the round has actually travelled.
A bolt reaches its ~7-block half-length the instant it spawns, while it is still sitting at the
muzzle — a wing mount is on the order of a block or two from the camera (`WeaponClusters`/
`DefaultLayouts`: `fwd`/`rgt`/`up` around 19–32 inches, `/16`). A ~14-block camera-facing quad centred
a block and a half from the eye subtends on the order of 150° — most of the field of view — for
however many ticks the round stays that close, which at these speeds is effectively its first
rendered frame, repeated for every round in a burst as long as the trigger is held.

Fixed in `ProjectileRenderer.renderBlob`: the stretch is capped at `0.6 ×` the round's own distance to
the camera that frame, read directly off the render matrix's translation column (camera-relative,
unrotated at that point in the method, so it is exactly `renderPos - cameraPos`) rather than a new
camera lookup. Near the muzzle the cap binds and the streak grows in behind the round instead of
appearing full-size at zero range; a tick later, once the round is out far enough that `0.6 ×` its
distance already clears the natural 7-block ceiling, the cap stops applying and the streak reaches the
same full size it always did. `half` (the width term) was left alone — it was never speed-derived and
never reaches more than a fraction of a block, so it was never the source of this. Pinned by
`ProjectileBlobStretchTest`, mirroring the stretch formula: capped well under the muzzle's own
distance up close, reaching the untouched 7-block clamp once far enough out, and never negative even
at zero distance.

Bolt colour was the other thing checked against the source while in there: `LASER.C`/`WEAPON.H` don't
carry a bolt colour as a numeric constant anywhere — `Laser_render` dispatches purely on
`Weapon_info[].render_type`, and the actual colour lived in the bitmap art (`bitmaps.tbl`), which
isn't in this source-only checkout. `DescentLaserFire.primaryColor`'s magenta-to-cyan progression by
laser level is an existing, already-considered approximation of that art, not something this pass had
grounds to second-guess — left as is.

## Fixed: no projectile visible at all — sound and damage happen, nothing draws

Reported again after the distance-cap fix above shipped, this time with no visible bolt whatsoever —
confirmed (no video this time, so asked directly) that the shot sound and the hit itself both still
happen; only the round's own visual is missing. That rules out the firing path (`WeaponCore`,
`DescentWeaponItem`, energy/cooldown, `world.spawnEntity`) — the server side is doing everything it
always did — and narrows this to render-only, same territory as the winding fix two sections up.

That earlier section reasoned the billboard path couldn't be *that* bug's cause: a camera-facing quad
rotated by a `Quaternionf` can only encode a rotation, never a reflection, so its winding as presented
to the camera can't flip *frame to frame* the way a statically-oriented box's six faces can depending
on which way the box happens to be facing. That reasoning is correct for a symptom that comes and
goes — but it says nothing about a winding that is wrong *consistently*, every frame, for every round.
A billboard whose single quad is wound backward relative to whatever convention
`RenderLayer.getEntityTranslucent` actually culls against (never confirmed either way — see the
winding section's own note on that) would not flicker; it would simply never draw, which is exactly
"no projectiles" rather than "sometimes no projectiles."

`billboard()` — the one function every `MESH_BOLT`/`MESH_ORB` round's entire visible shape goes
through, laser and plasma included — never got the double-winding treatment `drawBox`/`quad()` did.
Not a deliberate exception: the box fix's own commit scoped itself to "hand-built cube renderers,"
and a billboard's single quad is a cube face in every way that matters for this specific risk (a
hand-emitted quad through a culling convention this sandbox cannot inspect), it just isn't part of a
cube. The gap sat unnoticed because the two bugs look nothing alike on the surface — a hand's-width of
solid colour filling the screen reads as "obviously something is drawing, just wrong," not "check
whether the thing that draws bolts at all is even reaching the GPU."

Fixed the same way as `drawBox`: `billboard()` now emits its quad in both winding orders, the reversed
copy sharing the first vertex the same way `quad()`'s does. Whichever convention this pipeline
actually culls against, one ordering survives; if it doesn't cull translucent geometry at all, the
second pass lands exactly on top of the first and costs four extra vertices per round, never a visible
difference. `ProjectileBoxWindingTest` now covers this quad alongside the six box faces it already
checked — same three properties (reversed winding is exactly antiparallel, the original order is
already outward, exactly one of the two orderings matches the true normal) — so a future change to
either shape's vertex order gets caught the same way.

## Fixed: rounds were travelling 20× their intended speed

Reported in the same follow-up as the winding fix above shipping and not being enough: still no round
visible along its actual flight path, and — new, specific detail this time, checked directly against
the uploaded Descent source rather than taken on faith — "excessively fast for Descent." Both are
exactly what a round covering its entire realistic flight path in one or two ticks looks like, no
matter how correct its billboard's winding is by then: there's barely a "along the path" for it to be
seen on.

Found while tracing the render pipeline for that fix, actually, and initially left alone there as a
named-but-unchanged finding — it's a balance decision wearing a rendering bug's clothes, changing how
every weapon in the game feels, not just how it's drawn, not something to flip alongside an unrelated
visibility fix without it being asked for. Confirmed as the real remaining cause once it was asked for.

`WeaponCore.fireProjectile` converted a weapon's `speed` field with `DescentMod.su(cfg.speed)` —
`× UNIT_SCALE (1/80)`, a pure length conversion, Source inches to blocks — and handed the result
straight to `proj.setVelocity(...)`. `ProjectileEntity.tick()` does `setPosition(getPos().add(vel))`
once per server tick. Nothing between those two calls divided by `DescentMod.TICKS_PER_SECOND` (20).

Every other velocity in this codebase that starts life as a per-*second* quantity — the flight model,
in `ServerPlayerFlightTravelMixin`, `FlightSystem`, `DescentFlightMotion`, `ModNetworking`,
`SeamWarmup`, and `MissileSteering.steer`'s own `turnRate / 20.0` for the identical projectiles this
bug affects — visibly multiplies or divides by `TICKS_PER_SECOND` at the hand-off, and
`docs/MOVEMENT.md` documents that conversion as a deliberate, named step. Weapon fire had no equivalent
line. `cfg.speed = 6200f` for the laser is a per-*second* figure — Source (and Descent) both express
`speed` that way, and this project's own docs called it "6200 source units **a second**" — so
`su(6200)` (77.5) was blocks per second, applied as if it were blocks per *tick*: 20× too fast, ~1550
blocks/second instead of ~77.5, every weapon in the arsenal, not just the laser. The same gap applied
to a piloted ship's inherited velocity (`DescentPlayerData.getFlightVelocity()`, also per-second) —
`cfg.owner.getVelocity()` in the non-player branch is already vanilla per-tick and needed no change.

This was already priced into the rest of the render stack without anyone naming the actual cause.
`docs/WEAPON_FX.md` itself called the result "**70 blocks per tick**" since the tracer was first added,
and both the tracer's 48-bead cap and the billboard's 7-block stretch clamp exist specifically to make
a round moving that fast per tick legible at all — and, since both are already derived *from* speed
(`travel / 0.5` beads, `speed * 0.55` stretch) rather than fixed numbers, both scale down automatically
and correctly now that speed does, with no separate retuning needed: a ~3.9-block/tick laser gets a
~7-bead tracer and a ~2.1-block stretch, proportioned the same way the old ~77.5-block/tick one was, at
new numbers that no longer need clamping to stay legible.

Fixed in `WeaponCore.fireProjectile`: both `spd` and the piloted-ship inheritance term now divide by
`DescentMod.TICKS_PER_SECOND` after their existing Source-units conversion — the same hand-off every
other per-second velocity in this codebase already goes through, applied to the one place it had been
missing. Confirmed against the Descent source rather than assumed: a blob billboard there is a
fixed-size sprite with no engine-level speed or distance scaling (`draw_object_blob`/`g3_draw_bitmap`,
sized from `blob_size` alone), so the original never had — and never needed — anything to mask an
overly fast round the way this port's stretch/tracer machinery incidentally did. Pinned by
`WeaponSpeedConversionTest`: the laser's 6200 su/s now converts to ~3.875 blocks/tick (not ~77.5),
exactly 20× smaller than the pre-fix value, and a 5-second laser lifetime now covers a plausible
few-hundred-block range instead of thousands.

## Fixed: streaks read as bars, and wobbled — sized for the old (20×-too-fast) speed

Confirmed with the speed fix above: rounds now travel a real path and are visible along it, but the
billboard streak itself — sized from speed by `clamp(speed * 0.55, 0, 7)`, unchanged since "Making a
shot visible" — was calibrated against the pre-fix ~77 block/tick laser and never revisited once that
speed was corrected to ~3.875. At the new, correct speed the same formula still produced a streak
several times longer than the ground a round actually covers in a tick, reported as "просто балки" —
just bars — with a reference screenshot of the original's short, clearly-directional bolts to aim for.

The wobble ("стабилизируй их положение") is the same root cause seen from a different angle, not a
second bug needing its own fix. `spin` — the angle the streak is laid along — comes from projecting the
round's own velocity into view space (`renderBlob`); dual/quad lasers deliberately aim each bolt at a
shared convergence point from its own wing muzzle (`DescentLaserFire`, see "multi-muzzle lasers" above),
so two bolts from one trigger pull *are* a few degrees apart by design, and any one bolt's own velocity
sync carries some ordinary quantisation. Neither is new. What changed is the lever arm: at the old
~7-block half-length, a few degrees of arm-swing lands a visible distance away at the tip; shortened,
the same angular variation moves the tip by much less, which reads as "steadier" without anything about
the angle computation itself needing to change.

Retuned in `ProjectileRenderer.renderBlob`: `clamp(speed * 0.3, 0, 3)` in place of
`clamp(speed * 0.55, 0, 7)` — both the multiplier and the absolute ceiling brought down together, since
the ceiling had gone slack at the corrected speed (no `MESH_BOLT`/`MESH_ORB` weapon's stretch reaches
anywhere near 7 any more) and was left at a proportionally lower but still genuinely defensive value
rather than removed. Not tuned against a source number — confirmed above that the original has no
stretch at all, a fixed-size sprite regardless of speed or distance — so there is nothing in `LASER.C`
to match here, only a proportion re-derived for the now-correct speed and re-checked against the
reference screenshot's shorter, steadier bolts; a first pass, like `SCRAPE_KEEP_SMOOTH` elsewhere in
this project, expected to want a further live-feel pass rather than treated as final. The
muzzle-proximity distance cap (`0.6 ×` distance to camera) is untouched — it guards a different case
(a fresh bolt still at the muzzle) on its own terms and was never part of this. `ProjectileBlobStretchTest`
now pins the new constants directly (laser and mega-laser speeds recomputed post speed-fix) instead of
the pre-fix ~70 block/tick figure it used to mirror.
