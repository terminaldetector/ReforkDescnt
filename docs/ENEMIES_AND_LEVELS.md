# Enemies, projectiles and world levels

Hybrid-cyberpunk setting: industrial gunmetal hulls, neon seams, hazard banding.

---

## 1. Enemy roster

### Audit of what was already there

| Entity | Mesh | Texture (before) | Texture (now) |
|--------|------|------------------|----------------|
| `drone` | `DescentDroneModel` cuboids | own 64×64 (flat) | own 64×64 atlas on the real UVs |
| `mega_worm` | procedural cubes | `minecraft:block/magma` | `drmd:entity/mega_worm` |
| `drone_swarm` | procedural cloud | `minecraft:block/redstone_block` | `drmd:entity/drone_swarm` |
| `reactor_keeper` | procedural | `minecraft:block/sea_lantern` | `drmd:entity/reactor_keeper` |
| `end_reactor_boss` | procedural | `minecraft:block/crying_obsidian` | `drmd:entity/end_reactor_boss` |
| `sky_ufo` | procedural | `minecraft:block/oxidized_copper` | `drmd:entity/sky_ufo` |
| `air_mine` | procedural | `minecraft:entity/slime/slime` | `drmd:entity/air_mine` |

Six of the seven were sampling vanilla block textures. All twelve entity textures are the
mod's own now, generated from `scripts/gen_textures.py`.

### New machines

All three share `CyberMobEntity`: shield soaks first, then armour plating, then hull, each
scaled by a per-class resistance; a metered energy pool gates every trigger pull.

| | Tripod strider | Flying scanner | Spider turret |
|--|----------------|----------------|----------------|
| id | `drmd:tripod` | `drmd:scanner` | `drmd:spider_turret` |
| Size | 2.0 × 3.4 | 1.0 × 1.0 | 1.6 × 1.4 |
| Hull / shield / plating | 300 / 80 / 90 | 120 / 60 / 20 | 220 / 50 / 80 |
| Resists | kinetic .25, energy .10, blast .30 | energy .30 | kinetic .35 |
| Movement | walks, steps 1.6 | hovers, no gravity | walks, plants when it has sight |
| Weapon | charged plasma lance + stomp | laser → 3 rockets → recharge | MG bursts ≤18 m, laser beyond |

**Tripod strider** — cubic hull on three splayed legs. Holds a 12-block standoff and backs
away if you close, so it fights at its own range. The lance takes 1.1 s to charge and the hull
squats while it does, which is the tell. Anything under the hull gets stomped for AoE and
knock-back.

**Flying scanner** — the Descent-style sentry. Its firing cycle is fixed, not random, so it is
learnable: one laser lance, then three rockets launched one at a time off a rotating pod ring
(each from a different pod), then a 2.6 s recharge. Sidles around the target on a slow orbit
while holding a 14-block standoff.

**Spider turret** — repositions while it has no line of sight, then plants itself. Head tracks
independently of the chassis at roughly five times the turn rate, so it keeps shooting while
the legs are still coming round. MG fires 3-round bursts with a small cone; past 18 blocks it
switches to the laser.

### Swarms

`DroneSwarmEntity` is still the anchor. Two changes:

- **Cohesion.** Every 10 ticks, members further than 46 blocks from the anchor get a velocity
  nudge back toward it, scaled by how far they have strayed. Their combat AI keeps full
  authority inside that radius — this only stops a swarm dissolving into stragglers.
- **Mixed roster.** Roughly a quarter of the cloud spawns as scanners rather than drones, so
  the swarm presses in at close range and lobs rocket salvos from standoff at the same time.

---

## 2. Turning — one rule for everything that flies

`ai/FlightAttitude` is shared by every flying mob and follows the same rules as the player's
ship (see `docs/DESCENT_CAMERA.md`):

- **Bank reference** is the pole-safe zero-roll frame (`ShipAttitude.levelRightOf`), never
  `forward × worldUp`. The old cross product collapses when a drone climbs or dives vertically,
  and the fallback made it snap its roll.
- **Angles are approached along the shortest arc**, so a mob crossing the ±180° seam turns the
  short way instead of spinning all the way round.
- **Body yaw follows head yaw**, so hulls visibly pivot about their own axis instead of sliding
  sideways at a fixed facing.

---

## 3. Projectiles and fuses

The fuse is separate from the warhead, so one launcher can throw a contact rocket, an air-burst
and a seeded mine without three projectile entities.

| Fuse | Behaviour |
|------|-----------|
| `IMPACT` | goes off on first contact — guns, default |
| `DELAYED_IMPACT` | inert until armed, then contact. Rockets arm 0.12 s out so a point-blank shot does not kill the shooter |
| `TIMED` | air-burst when the fuse runs out, wherever it is. Flak bursts into an 8-sliver shrapnel cone |
| `PROXIMITY` | coasts through contact, settles where it lands, triggers when a target enters the radius. Mines: 3.5 m, arms after 0.8 s |

New kinds: `PROXIMITY_MINE`, `AIRBURST`, `BURN_LANCE`. The `deploy` weapons (grav mine, plasma
mine, energy trap, dark field) map onto `PROXIMITY_MINE`; `flak` maps onto `AIRBURST`.

**Combat lasers burn.** `LASER` and `BURN_LANCE` ignite what they hit — the struck face seeds a
focus in the shared fire sim, so spread and smoke are handled by `FireSystem`/`SmokeSystem`
rather than a one-off particle. Entities hit catch light for 4 s.

Meshes: bolts got a brighter leading tip so travel direction reads, and mines render as a
faceted shell with a pulsing triaxial core.

### Fire and smoke physics — two bugs found and fixed

- `FireSystem.isFlammable` used a `blastResistance < 1.5` catch-all, which matches **dirt,
  grass, sand, gravel and netherrack**. One energy hit on open ground could chain across the
  terrain until it hit the 200-focus cap. Now uses the vanilla burnable flag plus the
  `LOGS`/`PLANKS`/`LEAVES`/`WOOL` tags.
- Foci were tracked in one global map but ticked per world, so a fire lit in one dimension aged
  and spread against another. Each focus now carries its world key and ticking filters on it.

---

## 4. Laser drill rig

`drmd:drill_rig` — redstone-powered mining emplacement. Sinks a beam straight down, breaking one
layer per 12-tick pulse and dropping what it cuts, down to 64 blocks or until it reaches bedrock
or the bottom of the column. Depth is kept in blockstate so it resumes across reloads. The shaft
walls get a heat wash from `WeaponFx.melt`.

It breaks the target block outright rather than routing it through `melt()`: melt's staged path
turns stone into cobblestone, which the rig would find again next pulse and never sink past.

---

## 5. The world is one column, not three dimensions

`data/minecraft/dimension_type/overworld.json` widens the Overworld to **−512 … 1024** (96
chunk sections). The Nether and the End are *levels* inside it — bands you fly to, no portal, no
loading screen.

```
 1024 ┐
      │ END          880 … 1024   end-stone shards, obsidian spires, void gaps
  880 ┤
      │ ORBITAL      640 …  880   megastructures, vacuum flight
  640 ┤
      │ SKY          320 …  640   floating archipelago
  320 ┤
      │ SURFACE       40 …  320   vanilla terrain and sky
   40 ┤
      │ INDUSTRIAL   −64 …   40   vanilla stone, DRMD complexes
  −64 ┤ ← old world floor; descent shafts are cut through it
      │ ABYSS       −240 …  −64   open drop between levels
 −240 ┤
      │ NETHER      −420 … −240   basalt caverns, lava seas, glowstone ceiling
 −512 ┘
```

Vanilla worldgen still fills −64 … 320; `world/level/LevelBuilder` builds everything outside
that. Work is queued on chunk load and drained against a 2400-block-per-tick budget rather than
done inline, because a level slab is a few thousand block writes and doing that during chunk
load would stutter on every chunk border.

**Seamlessness.** Every 8th chunk on each axis gets a radius-3 descent shaft punched through the
bedrock plug and the stone above it, with a sea-lantern rim so it is findable from the surface,
plus a matching hole in the Nether ceiling underneath. Everything between −64 and −240 is
already void, so the shaft only has to clear the plug — you fly the rest.

`/d6 level` reports the current band and the column extent; `/d6 level <name>` lifts you to it.

`WorldRules` and `AtmosphereBand` are rebased on this column, so HUD biome labels, atmospheric
drag, thrust scaling and structure placement all follow the new heights.

> Writes outside the height limit are a silent no-op in Minecraft, so if the dimension override
> ever fails to apply, the level builder simply does nothing rather than breaking the world.
