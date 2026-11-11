# PS1 scene target — scoped checkpoint, 11 November 2026

The initial scene-target/profile increment is verified on isolated branch `experiment/render-ps1-20261109`. This supports the bounded S05-T01 target/toggle requirement; S04, the rest of S05, and complete master v8 remain open. The required S04 dependency is not declared closed by this proof. Development and evidence remain local; there is no release or publication.

## Exact source and artifacts

- Full regression source: `a923442fc3ef04b0b1c089a79883666670bb8146`, including the locally merged mechanism/input fixes from `8e5111b`.
- Both accepted native scenarios: `703ea8565dd7f7d37e821e5b0050041225857b90`. Later commits through this checkpoint only correct the independent observer; `src/main`, `src/test`, `src/gametest`, and build inputs are identical to the full-regression source.
- Installable `drmd-6dof-1.1.35-ufo-dev.1.jar`: SHA-256 `4f78433814c707b1199cde406dc8ea4469613a3ad93923ce29ae2428b0d859ad`, byte-identical in the full and both native runs.
- Separate native observer `drmd-6dof-1.1.35-ufo-dev.1-cubiccheck.jar`: SHA-256 `8623ed991381c304a1d67a8e5c42bfad014bb46598458bea31fd68cca39a8737` for both accepted native runs. The observer is not part of the installable mod.

The compact [evidence archive](EVIDENCE/ps1-target-703ea85.zip) contains exact stamps, input manifests, logs, XML, 16 accepted images, retained first failures and per-file hashes. Candidate JARs and disposable worlds are excluded from the archive; exact JARs remain in the isolated worktree's `build/libs`. This checkpoint does not claim the later canonical branch's test count.

## Implemented behavior

`HYBRID_NATIVE` remains the default for existing and new configurations. Settings switch Native, PS1 Hybrid, and Debug at runtime and persist a separately configurable internal scene height. The default 16:9 PS1 target is a real 640×360 framebuffer, nearest-upscaled to the window. Aspect ratio, small/minimized windows, GPU texture limits and an allocation ceiling are bounded. Independent hybrid-render and physics settings retain their values.

PS1 wraps the existing world pass, with private low-resolution scene, Fabulous transparency and outline targets. It borrows and restores the original target references, then nearest-blits colour and depth to the original framebuffer. Original auxiliary targets are never resized or closed. The low-resolution outline composite runs once; the later ordinary outline composite is skipped only for that already-composited frame. Mirror/portal passes retain their separate rendering and use the current outer framebuffer's raster/scissor dimensions.

Vanilla hand, crosshair, menus and HUD remain after world rendering at native resolution. Custom 6DoF weapon, cockpit and Mega Beam world-event callbacks are deferred until after the upscale. Their context copies Fabric's reusable context, matrix stack and projection/model-view matrices; it does not retain matrices later popped or reused by nested reflections. Same-frame consumers remain live and are flushed on the restored native target. Each callback preserves its depth/write/cull/blend policy and restores the incoming render state in `finally`.

The scoped world pass restores framebuffer references and viewport on normal completion and exceptions. Profile, resize, resource and world changes release only private targets. Allocation failures retain the saved choice and report a Native fallback until toggle/reload; native acceptance rejects any fallback. Presentation does not call blockstore, collider or mesher mutations. The native check only verifies an authored front-wall hash and an enabled-flight assertion; these representative checks do not establish universal world/physics/cache invariance.

## Verification and first failures

On 9 November the combined source passed **763 unit tests and all 376 registered GameTests**, with zero failures/errors/skips. The manifest proof checks all 60 GameTest entrypoints against the built metadata. Earlier five targeted presentation tests and both first Java/harness compilations passed. An initial shell launch failed because sparse checkout omitted the launcher directory; the unchanged source built after restoring that directory. This was not a compiler/gameplay failure.

The first Sodium native case failed its final-frame nearest-grid assertion: `0.9697916667 < 0.97`. The supposed world-only region intersected the intentionally native-resolution cockpit crossbar. Its original log and baseline image are retained; that first case did not save its failing PS1 frame.

A separate diagnostic on `4cf5c0c234cea32c782b1d93bbc8c5436b4e440d` retained the actual failing PS1-180 frame, matching pre-overlay world frame and mismatch map. For the same frame epoch 354, the pre-overlay world had **1.0 grid ratio / zero mismatches**, while the final image had **0.9475 / 126 mismatches** concentrated on cockpit edges. The guessed mirror region also sampled a glowing husk instead of the mirror: 162 legacy gold pixels versus 15,349 inside the same-frame projected actual mirror apertures.

The corrected observer samples immediately after the real world blit and before the first deferred overlay, and separately checks the final HUD/viewport/GL state. It projects the real eight-pane mirror from the main-world camera, excluding recursive offscreen views, and measures the inset aperture only. Thresholds remain **world grid >0.97** and **reflected gold ≥30**; cockpit/HUD/husk remain enabled. Production bytes did not change for these observer corrections.

Both corrected native cases passed with six fresh final scenes and two matching pre-overlay frames each:

| Scenario | Date UTC | PS1 dimensions | Pure-world grid | Projected mirror gold |
| --- | --- | --- | --- | --- |
| Sodium, GL3.3/GLSL330 override | 10 November | 640×360→1280×720; 320×180→960×540 | 1.0; 1.0 | 27,772; 15,140 |
| No Sodium, Fabulous, GL3.2/GLSL150 override | 11 November | 640×360→1280×720; 320×180→960×540 | 1.0; 1.0 | 27,792; 14,831 |

Each run switches Native→PS1→Native→Debug→PS1 with an actual window resize→Native/window restoration. All six scenes check restored output framebuffer/viewport, a one-physical-pixel HUD witness and zero GL errors/fallbacks. Both PS1 scenes execute two deferred custom overlays, observe one merged mirror formed from eight actual panes, and render an outlined real husk. A deliberate scoped exception verifies restoration of all eight original auxiliary target references. All 16 fresh images were inspected, including the pure-world/final layer pairs. These are software-Mesa compatibility checks, not physical Intel/modern-GPU or performance acceptance.

## Known negative and remaining work

**Fabulous Debug is not a useful world-wireframe visualization:** the fresh GL3.2 Debug image is mostly black with a fullscreen diagonal. Applying polygon-line mode across Fabulous postprocessing likely affects fullscreen triangles as well as 3D geometry. Native restoration succeeds, but a diagnostic assertion on useful authored world edges and a narrow production fix are still required. S05-T06 is not declared complete.

Visible existing geometry cracks and natural-terrain/LOD deficiencies are not repaired by changing presentation resolution. NoCubes material policy, watertight seams/cavities, collision/navigation parity, high-altitude ground and smooth LOD remain S04 work. Vertex snapping, colour quantization, ordered dithering, affine-like UV controls, altitude fog/fade, low-poly assets and frame pacing remain S05 work. Full yaw/pitch/roll readability, ordinary hand/UI interaction, broader portals/mirror counts and universal state invariance still need separate acceptance. This checkpoint does not claim the complete PS1 look or full specification completion.
