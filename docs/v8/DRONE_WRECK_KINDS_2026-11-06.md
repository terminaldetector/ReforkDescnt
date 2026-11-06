# Authored physical wrecks and actual world restoration

The ORB, ROCKET and JET now retain their authored hull geometry and scale after real lethal damage. Production a0cec9a6f8a3cfd6f01ac233fc8e3394cfacde7d extends the existing RagdollRig/RagdollSimulation rather than adding another physics engine. ORB has one physical component, ROCKET five and JET three. Every original mesh face belongs to exactly one component. Dimensions, rest offsets and joint anchors scale linearly; mass scales with volume. The original live meshes are unchanged. Legacy scout saves/callers remain supported.

The rig identity and scale are restored before physical part state. Actual saved positions, orientations, velocities, angular momenta and lifetime remain authoritative on the server. The client receives the corresponding variable-size rig and articulated poses; its geometry mirror does not run independent physical simulation.

Four actual death/scale tests first failed on 0c6ecdf. The implementation passes 710 unit tests and 276 server GameTests locally. The original failing XML/logs are retained. The four rig unit tests cover face containment, joint anchors, volumetric mass scaling and legacy identity. Remote run 42199633661 on the subsequent client-only production revision 7a92b08 reports successful Unit tests and Server GameTests steps; the complete workflow was still running when checked. This is not a release-pass claim.

## Cubic rendering regression

A Sodium reader initially restored all three server bodies and all three client entities but did not render every body. The observer on 11fcfd5 recorded all three inside ordinary render distance and the camera frustum while the base renderer still returned false. Sodium's terrain-section occlusion graph can lag the loaded cubic air volumes containing moving wrecks.

Production 7a92b08602c88b7e54256749e4a4e306815db7bd tests physical wreck bounds directly in native cubic worlds, retaining render-distance and frustum rejection. Stock worlds still use the original base renderer. Normal depth testing still occludes geometry behind walls; no global Sodium setting is disabled. General streaming/performance and other entity families are outside this narrow correction.

The first call-only candidate had a misleading success marker alongside an empty Sodium reload screenshot. It is explicitly NOT accepted as visual evidence. Retained diagnostics also include a stronger reader that did not complete and a subsequent successful pose comparison. Actual entity and rendered interpolation coordinates matched: no interpolation correction is claimed. The final observer requires four consecutive frames rendering all three wrecks and at least three palette pixels for each hull. A disposable observer follows falling bodies to keep their tracking volume and visible scale stable while software graphics initializes. It does not move, replace, freeze or change the velocity of a wreck. Observer camera movement is not evidence of gameplay boarding or player attachment.

## Fresh native proof

Observer/source 7635dd873375978e07548648b0e30f1366deda1c passes independent write/read pairs on Mesa GL3.2 and Sodium GL3.3. All four client processes exit 0. Six fresh frames show the live hulls, physical replacements and replacements after restart. Final reload frames have been inspected manually.

Each writer uses actual lethal damage and registers exactly one physical replacement per parent. Nine initial physical part transforms retain the live full yaw/pitch/bank basis. All three wrecks genuinely tick and move. There are zero visible dead parent duplicates. The scaled JET retains scale 2 and three components; ORB/ROCKET retain scales 1 and one/five components.

Each reader opens the same closed saved world in a new process, without NBT insertion or replacement actors. The observer compares actual saved physical state before its first tick, then checks real movement, synchronized typed rigs and four consecutive rendered frames. Reload palette counts are ORB 21 / ROCKET 752 / JET 2057 on Mesa and ORB 120 / ROCKET 758 / JET 2904 on Sodium. These are functional observations on shared software graphics, not hardware performance acceptance.

Game JAR SHA-256: `2791d16c577b03ff97656f01c7eea981865a2664b0aa1cb2bdbed69e8490e6b7`.

`EVIDENCE/drone-wreck-kinds-7635dd8.zip` SHA-256: `098637d7cb5fd5195601ad2dd499f2080958da421bd8582cd92fb085f28c7add`. The archive contains exact runtime inputs and JAR hashes, verified closed-world snapshots, actual serialized NBT, test XML/logs, fresh frames and explicitly separated failed diagnostics. Every archived file hash and ZIP CRC was checked.

GitHub source artifact 13494103818 / run 42201340338 is saved and not expired, digest `sha256:41296123d472df58961f4e81c97b1af7a4e628af5a990072b4d8d800d659cadd`.

This closes the narrow authored-wreck shape/scale and restoration component. Universal ragdoll, exact terrain narrowphase, player/drone wall-impact coverage, dense combat routes, larger swarms, complete bestiary and full v8 remain open. S08-T03 is in progress. Version 1.1.35-ufo-dev.1 remains development work; the accepted distribution pointer is unchanged.
