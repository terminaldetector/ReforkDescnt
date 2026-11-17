# PS1 palette and ordered dither — verified increment

Exact tested production source `8f1b3b1b2a94cfe9aa3d9a861d99135f4a551fff` adds the previously absent colour quantization and ordered dither parts of S05-T02. The [first actual negative](PS1_COLOR_NEGATIVE_2026-11-17.md), its source `0741805`, raw controls, failed requested palette/dither and all evidence remain unchanged. This does not complete S05-T02's vertex-snap/affine requirements or the full S04/S05 specification.

Selected PS1 now defaults to 32 levels per colour channel and dither strength 0.5. Native remains the default profile. The settings screen provides palette/dither controls; raw256 explicitly bypasses the colour effect. New missing config fields supply the PS1 preset while retaining the stored presentation profile, custom internal resolution and independent hybrid/physics choices. Explicit values round-trip through stored properties; malformed/nonfinite/out-of-range values are bounded. English and Russian labels are included.

The new GLSL 150 pass reads the existing completed low-resolution world texture after outline composition. Its fixed 4×4 Bayer pattern is indexed by **internal scene texels**, so all output pixels belonging to a nearest-upscaled scene texel share the same colour. It runs before the full-resolution HUD, cockpit and viewmodel. It copies depth with the existing nearest blit, then draws colour with depth writes disabled. Native, Debug and raw256 retain the original combined colour/depth blit. No new FBO, world/mesh/cache/physics representation or dependency is introduced. The draw scope restores its texture unit/bindings, logical texture, shader/actual program, depth mask/test, culling and blend enable in `finally`.

## First verification and artifact identity

The first actual build/remap/stamp and nine targeted unit tests passed: five existing PresentationOptions tests and four new config/migration/round-trip/finite-bound/menu-cycle tests, zero failures/errors. A preceding unified-exec transport outage created no process/build result; its separate record is retained and is not counted as a compiler or native failure.

- Game JAR SHA-256: `b6c653a4729e7dd4a8158ba1b8704fc31434c89d0c7cabaefbee4e1266d3df8c`.
- Cubiccheck JAR SHA-256: `f6712df320502a4ff6e0412e031bbebffc4d081f018da00751bc3cddc215406f` — **byte-identical to the first negative's observer/reference**.

The first Sodium and first Fabulous/GL3.2 actual client runs both closed exit 0. They use the same already-built game and observer JARs, without a rebuild between backends. Actual driver strings are Mesa `3.3 (Core Profile)` / GLSL `3.30` with Sodium and Mesa `3.2 (Core Profile)` / GLSL `1.50` for Fabulous without Sodium. Every executing wrapper/helper matched its frozen pre-run hash after close.

The reference uses actual current-frame source colour/depth and actual main world colour/depth before native overlays; it does not draw or supply a reference GPU image. Each backend saves raw256 → 32/no-dither → 32/0.5-dither → raw-restored, then an actual resized 3× dither reference. Source/main epochs match. Full-resolution and resized references are respectively `640×360 → 1280×720` and `320×180 → 960×540`.

| Backend | Raw / palette / dither / restored / resized RGB agreement | Maximum channel error | Maximum nearest-depth difference | World nearest groups |
| --- | --- | ---: | ---: | ---: |
| Sodium | 100% / 100% / 100% / 100% / 100% | 0 | `5.960464477539063e-8` | 1.0 in all five states |
| Fabulous GL3.2 | 100% / 100% / 100% / 100% / 100% | 0 | 0 | 1.0 in all five states |

The unchanged limits require ≥99.5% RGB agreement within one byte/channel, ≥500 quantization-sensitive and dither-sensitive source texels, finite normalized depth with nearest difference ≤`1e-6`, nearest groups >0.97 and GL error 0. The smallest actual dither-sensitive sample count is 8,064 in the resized Sodium state. All ten states pass framebuffer/viewport, epoch and readback texture/pack/read-framebuffer restoration checks. The first negative had only ~45.68% agreement for requested 32-level palette/dither while its raw controls were exact; this source reaches 100% using the same observer and formula.

Both runs also complete the existing six actual profile scenes, Native returns, window resize/return, target exception restoration, independent hybrid choice and authored-wall blockstore controls. The existing Debug world-edge contrast gate remains `327/346 = 0.9450867052` on both backends (minimum 0.70). The projected merged mirror aperture retains reflected gold: PS1 27,772 / resized 15,837 pixels on Sodium and 27,780 / resized 15,741 on Fabulous, above the unchanged minimum 30. The one-physical-pixel native HUD witness passes.

The existing gun observer selects actual colour-on PS1 frames. Native and PS1 each retain **166** visible pixels in the projected gold geometry (required 125), with unchanged camera/modules and maximum projected vertex difference 0. The world colour shader therefore does not obtain a pass by quantizing or hiding the gun/HUD.

## Preserved evidence and limits

[`ps1-color-8f1b3b1.zip`](EVIDENCE/ps1-color-8f1b3b1.zip), SHA-256 `7fc5bbc6e6e2ed6a77dbb8a4d106188ebfa3606a59454e84e1a65baeb431ef77`, preserves 174 files (8,272,491 bytes): all **76 fresh positive PNGs**, 20 actual depth float arrays, ten colour-state references, six-profile and viewmodel proof per backend, first build/unit/native logs/exits/XML/stamp, exact executing helpers/receipts, source snapshots and original negative metrics. Every entry SHA-256 and ZIP CRC was checked. The [adjacent JSON](EVIDENCE/ps1-color-8f1b3b1.json) records exact per-state/profile results and references the separate immutable 13-frame negative archive. Nine positive key images were directly inspected; all 76 are retained. There are no JAR, dependency, world or cache payloads in this compact archive.

Actual closed runtimes are `build/cubic-client-final-ps1-color-reference-fixed-sodium` and `build/cubic-client-final-ps1-color-reference-fixed-fabulous-gl32`. Exact tested JARs and full build stamp remain under `work/ps1-color/positive-8f-artifacts`. Only disposable saves were reclaimed after 101 per-file hashes and ZIP CRC checks per runtime: Sodium archive `b5d98f9608bb7e730de56276fe96fee3b5936aecba06d4db3fd33f78f5b6126a`, Fabulous `e6fbb873392bb57cfb192eafbfff10d7ddf910a69da0cd5a928d6b7673f0f95d`. Frames, depth, inputs, mods and logs remain intact.

This checkpoint verifies colour quantization/dither and the scoped native-overlay/profile regression on these exact artifacts. It does not claim a current canonical full suite, physical Intel hardware/performance matrix, actual settings-menu clicks, mid-game resource reload or a new colour-shader fault-injection test. The existing target/Debug exception gates are retained; they do not imply that a new shader failure was injected. The wall hash and enabled-flight control are representative state controls, not universal geometry/cache/physics invariance.

Vertex snapping, affine UV, PS1 altitude/fog/cloud/far-LOD continuity, low-poly asset profiles and frame pacing stay open. Existing natural meshing/collision/capsule drilling/simple tree collapse must be preserved, while round trunk/branch morphology, broad cavity/seam passage acceptance and volumetric far-LOD remain separate work. Approximate collision proxies and vanilla clouds already exist; their absence is not alleged. Both primary specifications and remaining clauses continue under the root-owned inventory; no central ledger status is changed by this checkpoint.
