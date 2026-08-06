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
four per face — into `RenderLayer.getDebugFilledBox()`. That layer is declared with
`VertexFormat.DrawMode.TRIANGLE_STRIP`. Quad-ordered vertices read as a strip are not six faces; they
are a run of degenerate slivers, so the rounds had no visible body at all and neither did the ship's
weapon modules. Both now use `RenderLayer.getLightning()` — `POSITION_COLOR`, `DrawMode.QUADS`,
additive blend, no texture — which matches what the emitters were always producing and glows, which
is what a bolt should do anyway.

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
glow with a whitened core, additive), `MESH_ROCKET` / `MESH_DRILL` / `MESH_MINE` take the model path
(oriented body, plus a camera-facing exhaust blob at a rocket's tail so one heading away from you is
still a light).

One liberty: the blob is stretched along its screen track. Descent's rounds are slow enough to be
discrete objects frame to frame; ours cross seventy blocks in a tick, so an unstretched blob would be
a dot in a different place every frame.
