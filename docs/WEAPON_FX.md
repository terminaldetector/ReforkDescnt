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
