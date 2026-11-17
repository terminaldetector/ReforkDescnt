# END sky background and LOD retention — verified increment

The clipped END envelope and its later overwrite of real distant terrain are corrected in production commit `ff8c2ec`. Exact tested source is `b2dd3f6bb1a5c7895a56026a1438034a43a49695`. The [first negative](SKY_LOD_ATTRIBUTION_2026-11-17.md), its source `44238d7`, original exits and 51 fresh frames remain preserved unchanged.

The existing orbital cube retains its radius, band, colour and alpha. A dedicated GLSL 150 POSITION_COLOR shader projects its directions at far background depth (`clip.z = clip.w`), so arbitrary 6DoF attitudes do not clip its faces at the terrain far plane. It draws before entities, registered before PlanetFloor, with LEQUAL depth testing and depth writes disabled. Opaque terrain stays in front and subsequent real LOD, entities and cockpit compose over the background. `ctx.positionMatrix()` supplies the terrain view at this earlier event; no unavailable object stack or second camera rotation is used. A `finally` restores the prior shader, depth test/function/write mask, culling, blend enable and all four blend factors.

Only the orbital renderer, its new shader registration and three shader resources change production. SurfaceIngest, PlanetClientState, LOD geometry, world generation, physics, colliders, cockpit and presentation profiles are unchanged. Other intended sky/space layers remain enabled.

## First positive verification

The first compile/remap/stamp passed. All five PresentationOptions unit tests passed with zero failures/errors. Both real client scenarios below use the same exact compiled artifacts, without an intervening rebuild:

- Installable game JAR: `1dbb11d34293889fc04bc6a49b421b00caac18ff02cfae53f242cba78d4a6e71`.
- Cubiccheck JAR: `169c4bd06bb7d845a1ac2bccee562eee83cb19539b008fa085a28b951b706477`.

One fresh Native/Sodium run and one fresh Native/Fabulous run with Mesa GL 3.2 / GLSL 150 overrides both closed exit 0. The latter contains no Sodium and explicitly sets `graphicsMode:2`. Each preserved 51 fresh PNGs and actual draw traces across the same unchanged three fixture poses, eye and world. All actual executing wrapper/helper files matched their frozen pre-run hashes after each close.

| Renderer / pose | Frame epoch | Wrong sky / valid upper rays | Lost terrain / valid lower samples | Lower-domain verdict |
| --- | ---: | ---: | ---: | --- |
| Sodium / level | 250 | 0 / 21,264 | 0 / 7,838 | Pass |
| Sodium / up and banked | 251 | 0 / 36,544 | 0 / 0 | Unmeasured |
| Sodium / down and quarter roll | 253 | 0 / 4,750 | 0 / 28,126 | Pass |
| Fabulous / level | 244 | 0 / 21,264 | 0 / 9,522 | Pass |
| Fabulous / up and banked | 247 | 0 / 36,586 | 0 / 0 | Unmeasured |
| Fabulous / down and quarter roll | 248 | 0 / 4,750 | 0 / 28,177 | Pass |

All six actual draw traces show envelope sequence 17, real LOD sequence 22 and pre-cockpit sequence 25; actual far-depth shader bound, full main framebuffer/viewport, GL error 0 and no state-restoration mismatches. Seed and actual LOD draw are ready in every pose. This is successful normal-path GL restoration; a new forced-exception test is not claimed.

The strict observer contract was fixed before production: every pose must measure at least 2,000 unobstructed upper-ray samples with at most 1% RGB-distance-16 mismatch. Level and down must independently measure at least 200 isolated terrain samples and retain them with at most 1% loss. Missing seed, real draw, expected ground domain or retention endpoint fails. The up pose's absent lower domain is explicitly **unmeasured**, never a successful lower-domain result. If that pose provides a measured lower domain, it must obey the same limit.

The earlier negative measured terrain immediately before and after its late envelope draw. The corrected order is verified with actual LOD-after to pre-cockpit snapshots, after all background passes; every unexpected pixel loss above distance 8 counts, with sky-colour replacement also reported separately. Near geometry and actual cockpit/celestial changes remain excluded. The raw pre-shader cube clip coordinates are retained alongside clip coordinates after the actual bound shader's far-Z rule. GPU images remain authoritative; CPU homogeneous clipping is a diagnostic model. No colour threshold, pose, eye, material layer or cockpit was relaxed to pass.

At the same banked pixel `(908,0)`, the vanilla bright draw produces RGB `(80,83,108)`; the corrected envelope changes it to the intended END colour `(17,8,25)`, retained through the pre-cockpit checkpoint. At level pixel `(128,452)`, the early envelope produces `(17,8,25)`, then real LOD draws `(27,85,128)` and keeps it through the pre-cockpit checkpoint. The old negative records the opposite terrain-to-sky overwrite at this point.

## Evidence and scope

[`sky-lod-b2dd3f6.zip`](EVIDENCE/sky-lod-b2dd3f6.zip), SHA-256 `875d1fde6d869d9c7d769f6e73f1b1981f7ad1ad67a5a391bca4aef11fdce486`, contains 166 files (6,573,212 bytes): all 102 positive PNGs and six actual traces, first build/unit/client results, XML, complete build stamp, exact helper receipts/copies, runtime launch/input/options, source snapshots and original negative metrics/pixel witnesses. Every entry has a checked SHA-256 and ZIP CRC verification passed. The [adjacent JSON](EVIDENCE/sky-lod-b2dd3f6.json) records exact per-pose results and references the separate immutable 51-frame negative archive. No JAR, world, dependency or cache payload is added to these archives. Three key frames per positive backend were directly inspected; all 102 remain available for review.

Closed actual runtime paths are `build/cubic-client-final-ps1-sky-layers-fixed-sodium` and `build/cubic-client-final-ps1-sky-layers-fixed-fabulous-gl32`. Exact JARs and complete build stamp are retained under `work/sky-lod/fixed-b2dd-artifacts`. Only their disposable saves were reclaimed after per-file SHA-256 and ZIP CRC verified archives: Sodium `f3a2275896ae1f7a46c515aa4b657250885c995a85d995213940d54d26ee3d65`, Fabulous `e5c0992d6364a3b9915a32b3ca959bb76bff7ccd64ef219ef756b6b998ed4a13`. Frames, mods, logs and inputs stay at the runtime paths.

The visible vertical blue LOD edge remains a separate observed issue. This checkpoint does not complete S04, S05 or v8, validate terrain topology/material ingestion, establish universal collision/cache invariance or claim a current canonical full suite. Both new sky gates used Native presentation; current-source PS1/Debug/profile/mirror integration remains a separate regression gate. The earlier PS1, Debug and viewmodel checkpoints retain their own exact source/artifact acceptance.

Next presentation work is the separately scoped S05 colour quantization/ordered dither contract. Vertex snapping, affine UV, altitude fog continuity, full debug views and the remaining terrain/LOD work stay open.
