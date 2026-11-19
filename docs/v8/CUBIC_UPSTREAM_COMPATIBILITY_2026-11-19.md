# S02-T06 — upstream and graphics limits

This records verified combinations and explicit unknowns. It does not certify the current build on Intel hardware or close S02-T05. The original failures remain preserved.

Better Cubic Chunks is the unchanged Fabric **0.1.1+mc1.21.1** artifact, 806,910 bytes, SHA-256 `8660f6e18fafaaa1dea2ef344e86188a943b0f3661c65fdcd294f326f3c6e8b6`. Its actual metadata and license resource are **All-Rights-Reserved**; integration uses the installed dependency/public APIs, without copying its implementation. Dependencies require Minecraft `~1.21.1`, Java `>=21`, Fabric Loader `>=0.19.5`, and Fabric API. Its build manifest records Loom **1.17.21**. The retained first Loom **1.10.5** dependency-remapping attempt fails with the explicit newer-Loom exception. The working production Knot runtime retains the upstream artifact unchanged; stripping metadata is not a compatibility fix.

| Combination | Actual evidence and limit |
| --- | --- |
| Native BCC + Sodium **0.8.13**, strict Mesa **GL3.2/GLSL150** | **Blocked**: actual renderer log identifies llvmpipe/GL3.2 and fails compiling Sodium's `block_layer_opaque.vsh`, “GLSL 3.30 is not supported”. Sodium SHA-256 `3d43c14985a4deb19c654aad3393b454cf57e1ebcbba86c4ae6a98dfc60eca2d`. DRMD game in that failure: `c7cf1bfbc5f02d2f752abb89a74b85040034b237c9d47103e053fe1b867ac550`. |
| Native BCC + same Sodium, Mesa **GL3.3/GLSL330** | Historical finite native baseline **PASS**, actual log identifies llvmpipe/GL3.3, fourteen checks and exit0. Source `0f76a0789fe8cb62ff98c3e08d6232f222d72cb8`, game `3df5cfcbd41c03af0df44c28e2f11fd18f9754c0571ca7895bea0e4d38319298`. This is not a performance or all-render-path guarantee. |
| Native BCC, **Sodium absent**, requested Mesa **GL3.2/GLSL150** | Same historical source/game passes seven mirror/portal scene pairs, fourteen frames and exit0. The successful run's inputs bind the staged3.2/150 profile; its log does not print an independent GL-version string. Do not fabricate that missing string or extend the result to every current shader. |
| Physical **Intel HD4400/OpenGL3.2** | **Current backend/build untested**. The original user's stock/Advanced1.1.14 log identifies that hardware, but does not run BCC or the present game. Mesa version overrides are not Intel hardware emulation. |
| BetterDistantHorizons, ordinary DistantHorizons, third-party NoCubes combinations | No general co-installation acceptance is established by these runs. DRMD's own LOD/geometry evidence is separate. |

Native bounds are finite: this installed preset declares `min_y=-16384`, height32768. Default TALL runtime packing was actually observed as Y−32768…32767/XZ±8388607; registry height, active packing, FULL availability and BLOCK_TICKING eligibility remain different. `WorldAccess` checks current owner-thread/native FULL metadata and coordinate round trips; it never treats a supplemental snapshot as authority.

Other retained limitations: upstream telemetry's `droppedWrites` has no proven general data-loss interpretation; a historical busy `save-all flush` inside an END_SERVER_TICK callback stalled, while a settled console save succeeded. Neither zero warnings nor a successful marker round trip proves unrestricted save/content parity. Do not convert original player saves or delete optional snapshot files to hide these gaps. The original later400-tick client-light symptom and the full cold-light matrix remain open despite the separately qualified stale-section-readiness repair and water/lava saved-UNKNOWN proofs.

The CPU checker rereads the original logs, exact artifact bindings, all archive CRCs and original child exits:

```bash
python3 scripts/v8/verify_cubic_compatibility.py --checkpoints /workspace/ungravity-v5/checkpoints --output /tmp/cubic-compatibility.json
```

Inputs are the preserved `gate0-server/evidence.zip`, `cubic-lighting-1.1.18/native-evidence.zip`, `world-access-1.1.19/native-evidence.zip`, and original world-access game JAR. No Java is launched. The output retains original source/JAR identities; CPU reverification does not relabel historical runs as current-build execution.
