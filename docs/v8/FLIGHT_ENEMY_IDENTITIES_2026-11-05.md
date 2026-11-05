# Independent ORB / ROCKET / JET identities

Source388eb845099ff4d400759f0e01e42bee1181322e registers drmd:orb, drmd:rocket and drmd:jet as OrbEntity, RocketDroneEntity and JetEntity, separate from the existing generic DroneEntity and UFO BlockBody. All three extend the existing drone combat/physics implementation. Their defaults are Assault, Artillery and Interceptor respectively, full authored health and active AI. Missing optional saved role uses the type's own default. Legacy drone IDs, eggs and commands remain available.

Each has an original cached low-poly hull, full yaw/pitch/bank transform, Minecraft entity scale and a registered tinted spawn egg. Conservative rotation-safe cubic entity bounds enclose the mesh; this is not a claim of precise hull OBB collision. Spawn commands and creative inventory expose the real types.

Three registry/NBT tests first failed on4db8403 solely because the IDs were absent. After implementation,696 unit tests and267 GameTests pass. Geometry tests check bounds, unit normals, distinct immutable meshes and face budgets; pose tests include vertical headings and corrupt inputs. The first pose test failure (non-unit Minecraft trig approximation) is preserved; the basis now explicitly normalizes/orthogonalizes its axes. The last renderer scale line was compiled in the verified native build after the full server/unit run.

Game JAR SHA256:8e62abeced3177e55623caf8a6fe903f69b79850b693f05001558a2822188fd9. Observer JAR SHA256:b15ffee2bda5d07ade7e1713264980c831356ef8cdb8bcd635ecf75786fdec2e.

Two actual offline singleplayer process pairs use Better Cubic Chunks at Y2400. The writer invokes real /6dof_spawn commands, verifies full defaults, records three differently pitched/banked hulls and actual inventory models/tint providers, then enables normal AI against a targetable resistant cow. No manual entity ticks, motion or target assignment are used during combat. All three naturally acquire a target, move and launch their role's weapon; ROCKET launches actual missile mesh objects. World serialization is observed without injecting replacement NBT. A separately launched reader compares exact class/type and saved combat/motion components before first entity tick, then requires all three active/moving/targeting/firing and rendered again. Exits are0 throughout.

| Renderer | Writer shots ORB / ROCKET / JET | Reader shots ORB / ROCKET / JET | Reader active / moving / natural targets |
| --- | --- | --- | --- |
| Mesa GL3.2 |24 /2 /47|16 /1 /38|3 /3 /3|
| Sodium GL3.3 |22 /2 /48|15 /1 /27|3 /3 /3|

EVIDENCE/flight-enemy-identities-388eb84.zip SHA2560b5920859be0ab023f5b6b96745f61ee3c0692c1719534586e78e423e784911c contains six fresh frames, raw NBT, verified closed-world snapshots, launch/input/JAR hashes, full client logs, failing tests and passing XML. All input source SHAs match and tracked diffs were clean. Tutorial/chat overlays remain visible in the first writer frames; reader frames retain the default world presentation. This is identity/model binding proof, not approval of LOD/sky/image quality.

S11-T01's independent-identity requirement is met. This closes that atomic task only. JET acceleration/velocity-based turning, type-specific wrecks, full mixed typed swarms, dense obstacle avoidance, Survival director, performance and the complete G4/v8 remain open. Software Mesa/shared cloud CPUs are not the user's GPU. No accepted1.1.35 release is declared;1.1.34 remains the public accepted package.

GitHub Actions source artifact13411532511, run42021487071, is saved/notexpired, digestsha256:51970e86c62993cbcc69d96190654bbef944de3ce719976c7fde7a1243f56fe4. Source availability is verified separately from full workflow success.
