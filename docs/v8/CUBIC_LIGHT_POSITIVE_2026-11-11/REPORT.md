# Native cubic transparent-roof parity, 11 November 2026

**PASS for this six-stage native client case. No production lighting change was needed or made.** The original failed observer baseline remains separately preserved in `../CUBIC_LIGHT_EVIDENCE_2026-11-10`.

Exact test source: `67011234785a0469568a8dfe9bad4e106eacc6d6`, based on canonical `4b2e243`. Build/remap/stamp succeeded in20s. Both native process and wrapper exited0; the closed runtime has no crash report. Main JAR SHA-256 is unchanged from the first run: `328307bc4b8db3b41d47f4b1b2bce8de9eb03eb0ccde78d46b9d33dcda0c02a7`. Observer JAR SHA-256: `d15749eebb99f4d117ba2ba4c9446b879a807d8cd91fe4b9fddee59a782c0cbc`.

The actual BCC backend reports TALL packing in the copied disposable seed271026 world. Roof is atY2208; three real client/server SKY probes are at(8,2207,8), (8,2191,8), (8,2175,8). The wall has15 synchronized client block states. Four visible authored colored corners are projected from the actual camera quaternion and actual render FOV, with at least90/121 matching pixels per marker. Initial brightness baseline requires three stable fresh renderer frames, preventing the original absent-wall/LOD false witness. Center brightness ROI is unchanged at5000 pixels, and the original400-tick stage budget, light values, opaque/tinted controls and15% recovery tolerance remain intact. The observer writes no light arrays/native source metadata or tickets.

| Fresh frame | Client SKY | Server SKY | Iron witness mean | Visible markers / initial stable frames |
|---|---|---|---:|---|
| [open-sky](frames/open-sky.png) | 15,15,15 | 15,15,15 | 165.126 | 4 / 3 |
| [opaque-roof](frames/opaque-roof.png) | 11,11,11 | 11,11,11 | 116.4801 | 4 / 3 |
| [glass-restored](frames/glass-restored.png) | 15,15,15 | 15,15,15 | 165.126 | 4 / 3 |
| [tinted-glass-control](frames/tinted-glass-control.png) | 11,11,11 | 11,11,11 | 116.4801 | 4 / 3 |
| [opaque-replacement-control](frames/opaque-replacement-control.png) | 11,11,11 | 11,11,11 | 116.4801 | 4 / 3 |
| [air-restored](frames/air-restored.png) | 15,15,15 | 15,15,15 | 165.126 | 4 / 3 |

Each frame records actual wall-state acknowledgement and5000 valid center pixels. The actual rendered frame shows the required wall/corners and expected bright/dark response; this is not merely a server-counter assertion. See `transparent-skylight-ok.json`, `native.log`, both exits, full source/JAR/build/runtime manifests and `SHA256.json`.

Scope remains narrow: clear glass, tinted glass, iron and AIR in this loaded four-cube roof/sensor region under llvmpipe/Sodium. This does not close all fluid/BE/save-reload parity, other transparent materials, vanilla-renderer parity, Intel hardware or allS02/S03 tasks. The copied historical fixture has `drmd:advanced_column` enabled alongside actual BCC; this does not establish auto-activation in newly created default worlds. Diagnostics include `droppedWrites=1718` and a worldgen unavailable-column warning; neither has verified semantics establishing data loss or gameplay parity in this light case.
