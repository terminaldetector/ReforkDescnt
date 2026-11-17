# Altitude clouds: first corrected Sodium positive

Frozen source `7317ddb055d9096a0208257607fd1e446d373894` first compiled/remapped successfully, passed **18 fresh targeted unit tests** (9 altitude-cloud policy, 5 presentation, 4 PS1 colour), passed the actual 34-class inherited-owner audit with zero forbidden calls, and admitted 249,864,192 free bytes against the unchanged 140 MiB guard. Its one five-pose Sodium native run closed **exit 0**. No retry or source/helper edits occurred during the lease.

The correction supplies the greatest float below 1 only for the actual existing strict `GL_LESS` cloud function. It keeps ceiling 1 for `LEQUAL` and the original near path, without changing depth enable/function/write mask. The pure regression covers sky acceptance, nearer opaque rejection, 24-bit depth and unchanged `LEQUAL` mapping. The prior actual f6 strict-depth negative remains immutable in [its report](ALTITUDE_CLOUD_DEPTH_LESS_NEGATIVE_2026-11-17.md).

| Actual pose | Valid cloud samples | Changed samples | Changed fraction | Visible world/body pixels | Nearer/behind LOD pixels |
| --- | ---: | ---: | ---: | --- | --- |
| 224 | 68,844 | 68,844 | 1.000000 | 1,106 / 1,396 | Not required |
| 384 | 22,962 | 22,133 | 0.963897 | 268 / 339 | Not required |
| 512 | 13,957 | 12,414 | 0.889446 | 268 / 339 | 227 / 661 |
| 2200 | 667 | 386 | 0.578711 | 16 / 197 | 20 / 16 |
| 2200, roll 90° | 691 | 405 | 0.586107 | 16 / 197 | Not required |

All five captures use actual 1536×864 framebuffer/viewport, FANCY clouds and strict admitted player/camera/control states. Near 224/384 execute and return from the original `clouds` shader with altitude weight 0 and ceiling 1. High scenes execute `drmd:altitude_clouds`, weight 1 and ceiling 0.99999994 under the actual unchanged `GL_LESS` function. All actual private draw HEAD/RETURN pairs match; all poses report **GL 0 and fallback 0**. This checks the original near-path dispatch and invariants; it does not assert byte-identical pixels between different live-world runs.

Every world/body occlusion control passes with zero changed colour/depth in its genuinely visible masks. The 512/2200 nearer LOD masks receive zero cloud paint. The independent RG32F true-ray witness agrees at **888 / 36 pixels**, zero mismatches against the original 0.5 m / 0.01 opacity limits. Behind-cloud LOD changes at **576 / 16 pixels**. The original Horizon pass still writes no main depth: measured main-depth changes are zero and no main-depth witness is invented. Semantic depth readbacks preserve the actual FBO, viewport and pixel-pack state. Combined readback is **180,678,768 bytes**, below 192 MiB; source copies total 454,592 bytes across five uploads and five actual cloud draws.

Exact tested game SHA256: `f035bb52329ae811d5d8450ad35b8d6209374b3f4e4ade0f26a7ab571fbe2422`.
Observer SHA256: `ec945c9e925e8152ec2fb1e689fc086c4aff58ef4d55c441ac993b6020e97125`.
Against preserved production `26b8d5538eb95db8d7ba7f70086a99c7dc7c240d00b6f89ad751c220c97b7f06`, exactly six approved shader/policy/renderer entries change, zero unexpected entries. This candidate is based on the renderer branch; it is **not yet combined with the current canonical gameplay source**. The actual eight executing helpers match manifest `528908ed0ebd501ee16da6ce27356cfae4678d93298b4cf031336320355b44be` after close; the external official stager remains unchanged.

[Verified evidence](EVIDENCE/cloud-depth-ceiling-7317ddb-first-sodium-positive.zip): **250 files / 29 fresh PNGs / 2,332,730 bytes**, SHA256 `56e4b0641023cf54edd9655761920c291d13d5009b38207dd4df8f98c043d701`; all member SHA and full ZIP CRC verified. [Machine summary](EVIDENCE/cloud-depth-ceiling-7317ddb-first-sodium-positive.json). The archive includes actual cloud/Horizon source and RG/depth arrays, masks, states, inputs, all first logs/XML, executing helper snapshots, exact source and build stamp. Exact tested JARs remain in `work/cloud-depth-ceiling/first-artifacts/`. All 29 images are retained; no claim that all were manually reviewed. Closed runtime: `build/cubic-client-final-ps1-cloud-attribution-occlusion-altitude-fixed-7317ddb-sodium/`.

The separate Vanilla/Fabulous GL3.2 backend and same-JAR profile/palette/mirror/HUD/viewmodel regression gates remain pending, followed by canonical merge and its complete checks. The older e447 missing-near-control cause remains unexplained. Conservative opacity handling, finite native cloud geometry and blue LOD skirts remain limitations. Vertex snap, affine textures, fog/material-stage policy, natural geometry transitions/cavities and whole PS1/atmosphere/v8 acceptance remain open.
