# Native sky / LOD draw attribution — first negative

Source `44238d747de26bd114054941e77e92255cf84c35`, based on `5ba617949782bdcb451cc7e84ae593002b0c03be`. This checkpoint adds an observer only; production rendering and SurfaceIngest are unchanged. Compile/remap/stamp passed (exit 0). The first actual Sodium client completed all three poses, preserved 51 fresh frames, then rejected the sky and LOD assertions (exit 255). This is a rendering negative, not a compiler, mapping or launch failure.

The installable game JAR is `cce6a9bb60a275542ab64b8f347757880718e2b76f609578e9562f70567baad2`; cubiccheck JAR is `5df6a1334549aec9ac9785e23daf10e310225276629a12b2386820da7419070d`. All eight actual executing wrapper/helper files still match their frozen pre-run hashes. The newer SurfaceIngest material-height correction is absent from these game bytes.

## Actual scene and domains

One ordinary copied cubic world, Native presentation, render distance 4, player `(8.5, 2200, 8.5)`, eye Y `2201.6200000047684`. A single initial teleport positions the fixture; each requested attitude is applied once through the existing flight/attitude APIs. Hybrid, level sky, real PlanetFloor and the default cockpit stay enabled. Nothing supplies a fake LOD mesh, replaces the projection or moves the eye to conceal a defect.

The observer captures the actual vanilla bright-sky VBO and celestial draws, PlanetFloor GPU draw, cockpit and six orbital-envelope quads. Every trace contains the actual matrices, draw order, shader/fog colour, far distance, framebuffer, viewport, scissor and depth state. Images are saved around the real draws. Sky pixels use actual inverse world-view-projection upper rays, excluding near geometry, real cockpit changes and actual celestial changes. LOD retention independently requires actual lower-ray terrain pixels changed by the LOD draw and still visible before the envelope.

Fixed limits are unchanged: stride 4; at least 2,000 valid upper samples and 200 lower LOD samples; maximum RGB distance 16 and maximum failure fraction 1% in each measured domain. An up-looking pose without a lower LOD domain is explicitly unavailable; it does not supply a successful LOD measurement. Overall acceptance requires both domains to be measured somewhere.

| Pose / requested yaw, pitch, roll | Frame epoch | Incorrect upper sky | LOD overwritten by envelope |
| --- | ---: | ---: | ---: |
| Level `(0, 0, 0)` | 262 | 0 / 21,264 | 7,922 / 7,922 (100%) |
| Looking up / banked `(0, -45, 25)` | 268 | 18,731 / 36,560 (51.2336%) | No isolated lower LOD domain |
| Looking down / quarter roll `(90, 45, 90)` | 269 | 0 / 4,750 | 15,080 / 28,233 (53.4127%) |

All three poses have GL error 0, main/draw framebuffer 1, full `1280×720` viewport and scissor disabled. Real LOD is ready: 66,106 quads, compressed reach 217.6; actual shader origin delta is zero at this stationary eye. The mesh contains 11–18 zero-RGB quads, but missing mesh/readiness does not explain these measured overwrites.

## Two independently observed causes

**Clipped sky envelope exposes vanilla fog-gradient colour.** The actual orbital cube has half extent 256 and the actual projection far plane is 256. All 18,731 failing up/banked sky samples are outside the CPU homogeneous clipping of the captured actual cube vertices. Covered-colour failures and scissor failures are both zero. The trace preserves actual emitted vertices and explicitly labels the CPU clipping model: GPU border raster can differ exactly on the far plane; the before/after images remain the authoritative observation.

For example, at `(908, 0)` in the up/banked pose, the framebuffer clear is RGB `(151,167,201)`. The actual vanilla bright-sky VBO changes it to `(80,83,108)` with real sky colour `(0.07,0.035,0.1)` and fog range `0..64`. PlanetFloor, cockpit and envelope all leave that pixel unchanged; the image lies outside the clipped envelope. This locates the surviving gradient in the vanilla bright draw and the exposed region in the later clipped envelope. Dark violet `(17,8,25)` elsewhere is the intended END-band colour, not itself a rendering defect.

**A later envelope draw destroys already visible terrain.** PlanetFloor draws before entities with depth writes disabled. The orbital envelope draws later, after translucency, also without depth writes and with LEQUAL depth testing. The actual `level/lod-draw-after.png` contains the real terrain; `level/envelope-draw-after.png` replaces it with the END sky colour. At `(128,452)`, LOD changes RGB `(151,167,201)` to `(27,85,128)`, cockpit does not change it, and it remains `(27,85,128)` immediately before the envelope. The envelope alone then changes it to `(17,8,25)`. The full semantic lower-ray/depth gate records the 7,922 / 7,922 terrain loss independently of the sky-domain result.

No production correction is part of this checkpoint. A bounded follow-up can give the existing envelope true background depth, avoid its far-plane clipping, and compose it before PlanetFloor while retaining the same intended band, alpha and cockpit. This first negative does not justify changing world generation, SurfaceIngest, colliders, default background colour or the intended space/asteroid layers.

## Preserved evidence and limits

[`sky-lod-44238d7-negative.zip`](EVIDENCE/sky-lod-44238d7-negative.zip), SHA-256 `ed91f40e4b709f839bcedc8a9fcd1cc96cf103523478ac4e9d5fbc23d1187c71`, contains 91 files (5,606,747 bytes): all 51 PNGs and three per-pose traces, original negative JSON, first build/native logs and exits, complete build stamp, actual helper snapshots/hash receipts, source snapshots and sample pixels. Every entry has a checked SHA-256; ZIP CRC verification passed. The adjacent JSON gives the compact machine-readable record. No world, JAR, dependency or cache payload is added to this archive.

The exact closed first runtime remains `build/cubic-client-failed-sky-layers-44238d7`; both exact JARs and the build stamp are preserved under `work/sky-lod/negative-44238-artifacts`. Its disposable saves alone were archived after close: 101 files verified by per-file SHA-256 and ZIP CRC, archive SHA-256 `f6a1297cbdef7eeaed34a3ed10d25aa9c0a36717d1944aa32556c047fcc8f164`. Frames, inputs, mods, crash and logs remain at the runtime path. The six owner-defining sky/LOD images were directly inspected; all 51 are retained for review.

This proves these two draw failures in the controlled Native/Sodium scene. It does not complete S04/S05, fix natural terrain meshing/material ingestion, verify every graphics backend or establish a general high-altitude performance result. The earlier PS1/Debug/viewmodel acceptance remains separately scoped to its own source, artifacts and frames.
