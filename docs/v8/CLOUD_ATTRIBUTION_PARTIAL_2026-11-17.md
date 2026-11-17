# Cloud attribution: three measured poses, upper attitude admission failure

Exact observer source `e7213db7e995bd204e7c43a3c83ad41e1dc767a6` first compiled/remapped successfully in25seconds. The first and only native case `ps1-cloud-attribution-stable-first-e7213db-sodium` closed255. This is an overall **control failure after three actual cloud measurements**. The complete five-pose negative contract remains unmet; no production cloud correction is included or certified.

| Actual admitted player Y | Actual cloud contribution / valid source samples | Outside actual far clip | Result |
| --- | ---: | ---: | --- |
|224|56,720 /56,720 (100%)|0|Near control passed|
|384|15,281 /15,707 (97.2878%)|0|Higher control passed|
|512|0 /9,694 (0%)|9,694|Measured cloud visibility failure|

All three captures used actual server/client positions exactly at the requested X714/Z1050 and height, zero velocity, ordinary Creative hover with6DoF off, real volume readiness, FANCY clouds, world time6000, Native presentation and all original sky/LOD layers enabled. Each actual cloud draw targeted the full1280×720main framebuffer with GL0. The near and above384final images visibly contain the actual cloud layer; far512shows the underlying LOD with no contribution from the scoped cloud draw.

The actual draw owner is Sodium's `clouds` shader. At512, actual cameraY513.6199998855591 looks down on cloudY194; the actual far plane is256. The uploaded source projected through the actual draw matrices covers9,694valid samples, all beyond the far clip, while the before/after cloud GPU images are identical. FogStart230.4/FogEnd256, shapeCYLINDER, also predicts zero alpha for4,411of those samples. Clip rejection is sufficient for this measured pose; this evidence does not separate how much those same pixels would contribute after correcting clipping alone. Depth is finite, source readiness and main FBO/viewport are valid, and there is no scissor/foreground/cockpit exclusion masking this domain.

The fourth pose is an independent viewer admission failure. At timeout, client and server were exactly at(714,2200,1050), stationary,6DoF enabled, Creative flying/permission disabled, with the actual volumes ready. The forward dot is1.0but the up dot is−1.0. Existing `DescentClient.applySync` resets the client attitude on the real H-off→H-on cold edge, superseding the observer's frame request issued before that edge. The strict original dot threshold correctly prevented a false capture. Neither upper pose was measured. The next observer refinement must acknowledge actual enabled state before issuing one requested frame through the existing view sync; no production cloud change follows from this incomplete run.

Provenance and preservation:

- Exact game SHA256 `b6c653a4729e7dd4a8158ba1b8704fc31434c89d0c7cabaefbee4e1266d3df8c`, byte-identical to the accepted palette game; observer `c61b7188d3f0107f304b5eb2ff330c15c0336608e01e2392bba3b20202a88c5f`.
- Build0, explicit game parity0, actual native255. All eight executing helper files and the external official stager match the frozen manifest `654a25ddab432f68e51012acef0c9029b88ff51588493bd7a90bfbc296615121` before and after the run.
- Original stock texture and actual VBO uploads retained. Copied source700,544bytes, GPU readback66,355,200bytes,3actual cloud draw events; all original caps remain enforced. No thresholds were lowered.
- [Compact evidence](EVIDENCE/cloud-e7213db-partial-attribution.zip):90files including15fresh PNGs, actual matrices/fog/depth/domain masks/VBO bytes, original crash/log, source, build stamp and helper snapshots. SHA256 `bccd19da9c4b255659d8ffe0c9a119ad6a0885e3d8c829dd7916f768590871a5`; every member SHA256 and ZIP CRC verified. All three final frames were visually inspected.
- Exact JARs/full stamp stay at `work/cloud-attribution/native-e721-artifacts/`; closed runtime at `build/cubic-client-failed-cloud-attitude-e7213db/`. Only its closed disposable saves were reclaimed after126per-file SHA256/ZIP CRC checks, archive `work/preserved-disposable-worlds/cubic-client-failed-cloud-attitude-e7213db-saves.zip`, SHA256 `a82bcde2194023ccddd71fdab3de179a177d164d03cc1186785a24195933447c`. Frames, logs, input geometry, markers and mods remain at their original runtime paths.

Earlier22cb/e3compiler failures and1704idle-sink failure remain separate, immutable outcomes. This run changes no game source and establishes neither a complete far-cloud fix nor fullS04/S05 acceptance.
