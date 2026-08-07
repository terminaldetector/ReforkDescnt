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
