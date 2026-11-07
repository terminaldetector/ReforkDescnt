# Rigid UFO weapon mounts — 7 November 2026

The reference UFO now carries two real hull lasers and two rocket launchers. A captured pilot fires them with LMB/RMB through the existing WeaponCore/projectile system. The mounts, aim, inherited launch velocity and recoil follow the physical hull. Destroyed or obstructed barrels do not fire; local repair restores their actual blocks without replacing the carrier.

**S09-T03 is in_progress, not completed:** this increment covers rigid mounts. Hinged weapon assemblies and their full module lifecycle remain acceptance work. Master v8, G2, Dynamic Boarding and an accepted 1.1.35 release remain open. All work/evidence is local; no GitHub publication or Actions.

## Exact revision and proof

- Final server/fixture source `479719ee4a2824fd6645b4dffd2c957df6599840`; native runtime source `f3ae888d1f086ab022163c7b1b1e83c70281c6a1`; final production change `b33247b81378b65fd6f9727bc594a322f7eff8e8`. The intervening delta is confined to GameTest code. Both game and observer JARs were rebuilt and their exact bytes verified identical to both native runs; both source revisions are recorded separately.
- Installable development game JAR SHA-256 `45d6a87a05422e5d099325aa9271e63317a042b33df1f8adde6442436f08acb4`.
- Separate cubic observer JAR SHA-256 `da88753dbf3db8eed24c271a9940339e3fbce89f1af6ea58b841f5fb6f327ef9`.
- Verified archive [mounted-weapons-479719e.zip](EVIDENCE/mounted-weapons-479719e.zip), SHA-256 `276a244d37f9162917306980a5fff352945ef799a30a64edb35156db2138ff39`. Contains both JARs, changed sources/resources, full/focused XML, first failures, logs, source/JAR stamp, 22 fresh native frames and exact repaired-body data. Every manifest file SHA and ZIP CRC is checked after writing.

## Implementation

`SkyUfoWeapons` defines four authored local-grid mounts: lasers at `(±3,0,10)` and rockets at `(±6,0,9)`. Fresh UFOs replace four existing shell cells, preserving the 1046-cell hull. No new BlockEntity or per-mount RigidBody is created. Models, item models, ru/en names, recipes, drops and mining tags are included, using vanilla textures. Older grids are not silently given new material; they can be retrofitted through existing local construction operations.

The transformed muzzle is 0.65m ahead of the cell centre and aims along the hull's rotated local+Z. Actual projectile launch velocity is `direction * perTickSpeed + (v_body + omega × radius) / 20`. WeaponCore has an optional material-point velocity override; other callers retain the previous path. Laser/rocket speeds are 2.5/1.4 blocks per tick and lifetimes 4/5 seconds. Their physical recoil impulses are 24/160 in the opposite muzzle direction, applied to the carrier at each firing point. The pilot receives no separate hand/suit recoil.

A connection-scoped Fire snapshot shares the existing owner epoch, rejects replay/invalid masks, admits at most four updates per server tick and expires after four ticks without refresh. Receipt never performs simulation. The hull owns 8/24-tick laser/rocket cooldowns, retained by NBT. Its actual reactor must be present. The ordinary held arsenal and portal projector cannot also consume the pilot's mouse buttons. Release/death/removal still use the existing lease cleanup.

Emission runs after the world's hull/contact phases in the normal end-server phase, once per driven tick. This is essential: spawning from a pre-integration pose lets an outgoing round begin inside the subsequently moved hull. Added material and world walls are checked across each actual muzzle corridor; no permanent launcher immunity is used.

## Moving projectile contacts

The first native rounds exposed immediate source hits: pilot-owned lasers at life 79 and a rocket at life 99 collided with their own carrier, damaging eight nose glass cells. Raw attributed hit data and failed frames are retained. Repair itself preserved the material point to floating-point precision; the extra damage came from the outgoing rounds.

`BodyMotion.traceProjectile` now tests the projectile path in the moving material frame, reusing its immutable compound BVH. Translation is exact. Rotation is approximated by short local chords, at most 32 segments (nominal angular interval 0.005rad). PhysicsTarget's default projectile query retains existing static behavior; BlockBody overrides it with its tick trajectory. Hitscan aiming remains an instantaneous query.

The result keeps a trace point for collision ordering and a separate current material point for damage, splash and impact effects. This prevents selecting hits by a point displaced off the projectile segment or damaging the wrong moved cell. Returning rounds and real moving-material interceptions remain collisions. This is bounded point-projectile work; it is not a certificate for arbitrary angular speeds, swept projectile volume, all tick-order/broadphase cases or the full dynamic-collision specification.

## Current verification

Fresh complete final regression passed **732 unit tests and 308 GameTests**, zero failures/errors/skips. A focused run passed 38 UFO/weapon cases. Four new pure trajectory cases reproduce the static end-pose false positive, a moving hull intercepting a stationary point, a returning shot's actual time of impact, and fully rotated outgoing/incoming traces. New server cases cover material mounts, launch inheritance/recoil, authority/replay/flood, damage/repair/reactor loss, blocked barrels, actual target damage, input expiry, exclusive mouse ownership and ordinary ticking with genuine boarding contact. The moving-launch regression keeps both banks' four rounds alive outside an intact, undamaged moving source.

Both final native rounds use production mouse input and ordinary world ticks on the same exact runtime source/JARs. The final server rerun has a GameTest-only admission correction; its game/observer bytes match those runtime artifacts exactly. Each passes eleven scenes: external approach, moving bay entry, boarding transition, walking, console capture, thrust/roll, rotating fire, destroyed banks, repaired fire, H release and exterior item/model inspection. The measured weapon response comes from production input and ordinary physics.

| Measurement | Mesa/OpenGL 3.2 | Sodium |
| --- | ---: | ---: |
| Original lasers/rockets emitted | 6/2 | 6/2 |
| Repaired lasers/rockets emitted | 4/2 | 4/2 |
| Held-trigger world ticks with destroyed banks | 29 | 28 |
| Accepted fire snapshots | 63 | 59 |
| Maximum muzzle / launch velocity error | 0 / 4.97e-16 | 0 / 0 |
| Material-point continuity error, m | 4.55e-13 | 4.55e-13 |
| Actual hull rotation observed, radians | 2.9461 | 2.6797 |
| Synchronized / rendered unique shots | 16/14 | 14/12 |
| Maximum boarding prediction error, m | 0.02571 | 0.02541 |

The native callbacks record no pilot-owned source hit. After real collision-checked placement, all 1046 cells match the original grid, with no extra repaired shell material. Both projectile meshes synchronize, and the projectile renderer observes 12/14 distinct rounds; exterior frames show both new item icons and real nose faces. Gameplay fire frames are interior views and do not establish unobstructed weapon pixel/range acceptance. Creative camera placement is used only after actual H release for the final exterior inspection; it does not implement boarding or gun motion.

## Retained failures and limits

- `3fbadbb`: two negative tests reproduce absent laser/rocket material. Original 30-case XML/log/source archive is preserved.
- Initial GameTest compile used an unmapped mob AI setter; corrected to the mapped API. First native observer compile used a wrong Cell accessor; the original compiler logs remain.
- `e28e881`: first installed observer round failed on an inherited vanilla method mapping. `8adcc8f` resolves those calls through Entity, preserving the failed crash and inputs.
- Two repair rounds fail the unchanged exact-grid assertion. Detailed `309d20e` data identifies the eight missing glass cells; `84e35a9` attributes immediate source impacts to pilot-owned rounds. Four separate native failure archives remain.
- First moving-launch attempts lacked real boarding or assumed loaded columns were entity-ticking. A b33247b focused round reports two channel-count failures; diagnostic-only f3ae888 retains counts and its focused rerun passes, but the first full run again catches the moving-launch case. All original XML/logs remain. The final fixture now owns AiFixtureChunks tickets and verifies all 49 corridor columns entity-ticking before spawning either actor, with bounded 80-tick admission. Its 140-tick outer limit includes that admission; the measured 8/14-tick windows, four-round requirement, exact grid and damage assertions are unchanged. The admitted 38-case run and the complete final run pass. This changes test readiness, not gameplay collision tolerances.

- The second full run caught an independent cold-root fixture race: it assumed the assembly stayed absent for five ticks despite an adjacent player legitimately triggering a fast reload. The fixture now confirms actual absence while the player is away, then returns the player and keeps the original five demand ticks for the 16-request round-robin loader. A first two-demand-tick revision was too strict for that budget and is also retained. Cleanup discards any genuinely reloaded object with the fixture's UUID, as well as its old removed instance. Original root/far-end/unrelated-body assertions remain; no production loading limit was raised.

Replay helpers are archived under `work/ufo-body/native`: `run-weapons.sh`, `build-verified.sh` in the existing workspace and `weapons-final-checks-20261107.sh`. Use Java 21, verified game/observer byte hashes, fresh disposable runtime names, GL3.2/150 or Sodium GL3.3/330 with llvmpipe. The final Sodium native round precedes the final-checks script; that script runs Mesa and the preserved first complete regression. The final `weapons-final-regression-checks-20261107.sh` rebuilds and verifies identical runtime JAR bytes, restores the complete manifest and runs the fresh final unit/server regression after the test-only admission correction. No Gradle/native builds run concurrently and source stays immutable during each round.

An old closed disposable portal-gun GameTest world was preserved with per-file SHA checks, ZIP CRC verification and an exclusive session lock before reclaiming its working copy. User saves and dependency caches remain untouched. Raw closed native worlds are also retained in verified archives; preservation is not itself gameplay reload acceptance.

Remaining: physical hinged turrets/actuators and their parent-child transforms, full firing-state cold restart, AI weapon control, reactor energy/ammunition economy and balance, arbitrary moving-body/tick-order collision coverage, portal-group transitions, complete 3D AI/bestiary/Survival and full v8. Current cooldown NBT checks do not substitute for a new two-process firing-state reload. Earlier remote weapon pixel regression remains open. These short software-renderer runs do not certify target Intel performance or dedicated/multiplayer behavior.
