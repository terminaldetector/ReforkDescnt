# ENGINE C — GRAVITY / UNIVERSAL 6DOF / RAGDOLL / DEBRIS

## One physical orientation model
`RigidState`: position (world double), translation velocity, quaternion orientation, angular velocity, mass, inertia, local-gravity vector, contact flags, motion mode; canonical state server-side. `PhysicsInterface` for living bodies, movable rigid components and articulated ragdolls. Quaternion frame conversion for vanilla renderer yaw/pitch only at boundary. No gimbal locks, no duplicate camera roll.

## Local gravity torches
Gravity fields use immutable field descriptors `{dimension, fieldId, center, radius, strength, vector/attractor mode, priority, blending, owner}`, spatial hashed-grid/cube index. Enter/exit transitions notify only once, no `Local gravity active` per tick. Combine overlapping fields with documented deterministic rules; restore vanilla default after crossing boundary/removing torch.

Case P1: screenshot #4 black/clipped camera in torch zone. Instrument eye offset, collision box, local up and actual block around eye, raycast normal, teleport/dismount. Camera cannot be corrected simply by toggling its rendering off. Tests ground↔wall↔ceiling and back with all mob types.

## Universal entity classes
C1 Ground: villagers/undead/animals; C2 Climbers: spiders; C3 Flyers: bees/phantoms/ghasts; C4 Aquatic: fish/water mobs; C5 Mechanical: ORB/JET/ROCKET/ships turrets; C6 Special: bosses/multipart; C7 Anchored: fixed support/attachments. Every entity receives a gravity/orientation adapter; specialized animation/AI only as needed, fallbacks never crash. Preserve species-specific motion when outside special fields.

### Spatial clusters
Use 3D hashed cells (initial experimental 16³ aligned with cube addresses; independently configurable larger cell size) with subscription to active gravity fields, active entities, budget and wakeup triggers. Do not globally query all entities per torch per tick. Reduced/dormant mode may thin expensive sensing/steering but must not skip essential contact/integration while entity actively interacts. Distinguish *render LOD* from *physics LOD* and *AI LOD*.

## Articulated Ragdoll
- Retain prior Oct 8–10 solver/contact work only if current build and tests substantiate it.
- 5-segment drone ragdoll historical baseline; general humanoid/other rigs via family skeleton and physical constraint graph. No forced one-size-fits-all rig.
- Constraint stability: joint limits, off-centre inertia, CCD for fast shots, sequential impulse/integrator or proven library, contacts during joint solving, wall-tunnelling regression, friction/rest, sleeping.
- Mass death 128/256/1024: debris budget, shards aggregations, pooled instances, lifetime, reset, save cleanup. Turn high-volume debris into cheap aggregate/particles beyond proximity.
- Damage impulses: shot to limbs, rail throw, drone explosion, falling BlockBody, moving ship deck. Correct local gravity and local orientation of body.

## BlockBody physics integration
Use `RigidBlockBody` for connected block volumes, graph-based joints for turrets, engine thrust/torque, mass/inertia, collision with terrain and other bodies, block-to-body-to-world transforms. Fracture may split into bounded connected components; expensive per-block rigid body is prohibited in mass destruction scenes.

## Safety and reset invariants
- Player not stranded in hidden proxy/vehicle. Always safe exit/dismount/reload/death; don't need lava to destroy proxy.
- All physics objects have clear ownership and lifecycle, dimension ID, no duplication on client/server, deterministic unregister on chunk unload.
- Client interpolation smoothing must not make hitboxes drift from authoritative state.
- Save/load/resume with mid-flight body; no orphan, stuck combat brain, endless player roll.
