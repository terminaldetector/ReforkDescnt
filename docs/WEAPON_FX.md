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
