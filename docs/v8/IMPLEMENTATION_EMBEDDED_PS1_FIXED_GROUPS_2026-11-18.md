# Embedded PS1 shader, fixed boarding groups and working ordnance

Prepared source: `9226e7a04fa39fbfd12ada8d1770549bacd36264`.
Installable game SHA-256: `65731fbda613eaf9d3babe7c54e9fe82a74ecbb65102c41342922503754bd674`.
Final observer SHA-256: `5f6f15026ad2645448375366c1b37be3f0de1eb3ec5da35a13e6d0953f57c2dd`.
Development snapshot; no new full-suite, complete specification or hardware-performance acceptance. GitHub/source publication was paused during these runs; the user subsequently authorized publication to main.

PS1 means the **shader/render pipeline embedded in the mod**. The existing GLSL150 snap/affine program now also handles the authored scout and spider world cutout materials. It preserves native lighting, overlay, lightmap, alpha and fog phases; queued batches recheck the world/profile domain. Coarse mesh variants are a separate optional setting. Terrain/Sodium geometry, smooth/translucent materials, reflected style and the complete PS1 packet remain open.

FIXED boarding groups now carry one bounded server snapshot containing actual member UUIDs, shape revisions and relative COM/orientation. Client collision prediction and rendering use one anchor trajectory and matching interpolation time. Offset point velocities follow the carrier at each substep. Missing geometry pauses prediction before replay; articulated joints retain independent motion. Protocol 6 requires updating both client and server. The implementation does not teleport actors after their carrier or create another physics engine.

The same increment also fixes the native small-component velocity cleanup that erased weak boarding gravity, enables quiet SUPPORT drones to reach injured allies through real material-aware routes, adds bounded native Cow contacts, displays actual socket weapons/programs in the HUD, and resolves default E/Q flight-roll conflicts with inventory/drop actions. The Cow scope is dry native adults under world-up gravity, not universal mob boarding. Scout/spider coarse meshes retain their rigs and reduce baked cuboids 6→4 and 12→8; their enabled coarse GPU matrix remains separate.

| Actual source/run | Result and boundary |
| --- | --- |
| `5047d333` | 2 baked-model units PASS; 27/30 GT PASS, three first failures retained. |
| `00c43052` | Corrected original Cow/SUPPORT cases and adjacent routing: 8/8 ordinary GT PASS. |
| `21559ab4` | Real network-clock weak-gravity/external-impulse case plus seven field cases: 8/8 ordinary GT PASS. |
| `6d932620` | Actual native mouse primary/secondary transactions, 7/14 children, E roll, then H-off and ordinary E inventory: PASS, 3 PNG. The final JAR retains all 18 input/HUD/ordnance classes byte-for-byte. |
| `c72ead54` | Actual BCC/Sodium/Mesa Native→PS1→Native: both unchanged 6/12-cuboid models, eight completed shader draws per material, GPU snap=2/affine=1, zero fallback/GL errors, Native restoration, 3 PNG. Final game retains all 54 presentation/mob-render/shader entries byte-for-byte. |
| `a65590eb` | 11 relevant units PASS; 13/14 GT PASS. Only the new three-body graph fixture lacked entity ticking in its adjacent column. |
| `f2b28c57` | Corrected footprint, original deadlines/assertions: registry 6/6 GT PASS, fresh remapped JARs. Production and unit sources are identical to the preceding 11-unit run. |
| `9226e7a0` | **Actual occupied connected portal PASS on the final game/observer pair:** 2 original bodies cross, one staging teleport/one launch impulse, normal input, preserved contact/frame/momentum/joint, 12 consecutive matched acknowledgments, 2 PNG. Render anchor separation before/after 2.80e-7/1.32e-6 m; matched prediction position error 5.77e-5 m. This is one small unpowered group, not the complete Dynamic Boarding matrix or multiplayer/performance proof. |

Original runtime failures remain evidence. They include inherited-owner observer remapping, startup dialogs, an initial client Mixin package mistake, a marker obstructing the flight path, an off-centre occupied-island aperture premise, and the real support-only/child-old-timeline rendering defect. Geometry authoring fixes preserve the 4 m opening, 0.125 m safety margin, normal simulation, controls and deadlines. The first 13/14 graph run is retained with its save and exact fixture-footprint calculation.

The optional final clean-frame PS1 reshoot **did not pass**. `f2b28c57` stopped before its first model state because a fixture mob was removed; the reason was not recorded and remains unproven. `9226e7a0` stopped at viewer admission without creating actors: its saved player ended 0.674 blocks from the exact START condition, flying=true. Its screenshot shows an ordinary world screen. These are distinct NONPASS runs; neither replaces the original successful `c72ead54` GPU run. The source-specific GPU result and component parity above are the accepted bounded evidence. Further diagnostic-only retries were stopped; the final installable JAR has the fresh actual portal pass.

Known LOD/sky planes remain visible in actual frames. Broader 3D AI, all-species/local-field boarding, articulated modules, full shader/terrain compatibility, cold multiplayer persistence, full reactor/Survival and the requested destruction demonstration remain open. Both latest primary specifications, all 99 task IDs, 21 original packets and seven supplements are retained; zero whole packets accepted.

Evidence: `EVIDENCE/mob-shader-boarding-9226e7a0-focused-20261118.zip` (55,307,381 bytes, SHA-256 `5964226f89e6c30332f86f11fd38769030d38259b0ddc5a5eae0f3b6aba764fd`). Its manifest maps 543 logical files to 357 unique exact payloads, including original failures, closed saves, actual frames/JARs, handoffs and 49 final source postimages. Outer CRC and every payload SHA-256 were checked. Input component parity is also retained separately in `EVIDENCE/input-parity-9226e7a0.json`.

Delivery readback: [changelog293](https://t.me/abadonsoft/293), [build294](https://t.me/abadonsoft/294), [actual frames295–296](https://t.me/abadonsoft/295). Document name/size were confirmed; remote bytes were not rehashed. Current main-publication instruction supersedes the earlier pause.
