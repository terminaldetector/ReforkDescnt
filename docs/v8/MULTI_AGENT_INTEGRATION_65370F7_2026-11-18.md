# Continued implementation of both specifications — 18 November 2026

Canonical tested source **65370f7266cea705ebf2f9e86217031370fa2a3d** passes **998 unit tests /502 ordinary GameTests /84 entrypoints**, without failures, errors or skips, and **two actual same-JAR BCC/Sodium clients**. The unique-case comparison retains all previous951/480 cases and adds47/22. This is a development milestone; whole specifications, packets and a release are not accepted.

MASTER v8 and ENGINE B /PS1 remain active, together with the direct Dynamic Boarding supplement and Create-style separate weapon/bearing/drive requirements. The [live packet queue](IMPLEMENTATION_PACKET_PROGRESS_2026-11-17.md) retains all21 packets and seven supplements; the historical748-clause crosswalk remains available. The99-task ledger now has3 completed /33 in progress /63 pending reverification. No task was newly declared complete.

## Implemented and integrated

* **Generic ControlCore** (`4c058a4b`): control of an existing arbitrary BlockBody through the existing connection/epoch system, real motor cells and bounded force frames. Manual lasers/rockets reuse the mounted-weapon scheduler, cooldowns and AA refusal policy. Assembly transfers support-sensitive blocks quietly, then publishes changes; partial failure rolls back without leaving a duplicate body. The legacy UFO protocol remains intact. The18 new unit and19 GameTest cases execute in the combined run. Actual seven-scene generic client control/fire/release remains a separate pending gate.
* **JET ragdoll limits** (`99d2cfca`): optional rest-frame swing/twist constraints on the existing solver, version2 JET wings, legacy version1 compatibility and opaque preservation/refusal of unsupported physics metadata. Thirteen new unit cases and three new ordinary-tick save/restore scenarios execute alongside the old methods. Same-process restoration retains the UUID. Native and separate-process cold restoration, other families, sleeping and mass-death acceptance remain open. Positional stabilization can dissipate angular momentum; the unilateral stop impulse has its separately tested conservation scope.
* **PS1 body geometry** (`5f01a3fa`): optional vertex snapping and affine-like textures for supported hard textured BlockBody faces, with independent persisted controls and zero/off defaults. Native/Debug, overlays and unsupported layers retain their previous path. Locale additions preserve existing recon/Core keys. Fifteen remapped classes and three shader resources exactly match the tested component; this does not claim near-chunk, smooth/translucent or whole-world PS1 coverage.
* **Climber command ownership** (`65370f72`): a genuinely accepted entity-move command can receive its pending READY path. Coordinate retargeting, null/foreign paths, stop and field changes cannot resurrect an obsolete command. The per-search delivery marker is scoped to the producer's actual native call. Eleven new unit cases execute in the full run. Separate actual Spider/CaveSpider cancellation and original-length120-tick delivery scopes pass on component50e884/caf14772; eleven current remapped navigation/geometry classes exactly match that component. Four older broad navigation failures and safe-release coverage remain open.

## Exact artifacts and evidence

| Artifact | SHA-256 |
| --- | --- |
| Game,2,936,179B | `4335188a11fb2e0e9328a7e51bce669d086cbf0431ca86c7a3e5bde6534aa16f` |
| Test observer,650,537B | `c7a7602a526ad1fc870a4682b2af6db0625509e673f0a13984978ffa033395a4` |
| [Combined evidence ZIP](EVIDENCE/integrated-65370f7.zip),10,942,581B | `e540c66e4d5e39af73098f4b393aee98e1c2325d4e2ac7dba16e170aef07a5d3` |

Frozen installable bytes are retained under `work/multi-agent/integrated-65370f7/artifacts/`; the observer is test-only. The ZIP has213 hashed payloads plus its manifest,46 exact changed source postimages, all current test XML/stamps/logs, both real-client receipts, actual network payloads, closed native-save archives and40 PNGs. The [independent reader](EVIDENCE/verify-integrated-65370f7.py) verifies all hashes/CRC, actual old/new case sets, JAR identities, source Git blobs, real saved cache bytes,12 gzip/NBT footers and presentation observations; [result](EVIDENCE/verify-integrated-65370f7.json). No Java is launched by that reader.

The first broad byte-parity expectation correctly reported a difference in shared `DescentConfig.class`. It is preserved as NONPASS. The classified result retains11 identical GUI classes and16 identical cloud implementation classes; the shared config has only three additive PS1 declaration/load/save lines plus a comment and exactly matches the new tested geometry component. It is not described as an unchanged17-class historical closure.

## Actual client observations

* Native arrival repaired the actual saved black cell to GOLD atY2301 through ordinary ingestion/disk/packets, with zero fixture edits, post-baseline mutations or dropped events. Server high FULL137, both server volumes FULL142, final163. Client high FULL is true and low FULL is false; no client-low claim or first-ever generation/smoothing claim is inferred.
* Six presentation scenes pass Native/PS1/Debug, actual window resize/restore and exceptional-target restoration. All world nearest-grid ratios are1.0 and GL errors0. Five palette states have RGB agreement1.0. Eight adjacent mirror panes render as one surface. The static same-camera/module viewmodel proof has166 native/PS1 pixels and zero projected-vertex difference. This is not the entire1/2/4-mirror failure or all moving weapon matrix.
* Separate component6dc geometry passes nine states on Sodium/GL3.3 and on actual GL3.2 Core /GLSL1.50 Mesa llvmpipe with Sodium absent. The latter uses observerd1bba21e and real draw-time context/registry receipts. Its earlier missing-sidecar NONPASS remains preserved. RGB agreement is at least0.9999859, depth agreement1.0; outliers remain recorded. This is software compatibility, not actual Intel hardware certification or a nine-state run of the whole433 JAR.

The closed ordinary world (1648files/342,817,508B) is preserved as `work/ufo-body/integrated-65370f7.zip`, SHA`f05dbe195f09e49904e8c46c0bdf6699ff7abb4a1e84ad17b15166b3ca3ef739`. All file hashes, ZIP CRC,481 gzip footers, fresh identities and exclusive locks were checked before reclaim. Both native saves likewise remain lossless archives with24 verified gzip footers. Source/JAR/log/PNG evidence and protected reusable donors remain.

## Continued work on unclosed clauses

Shapes remains isolated and partial. The original26/27 and passive103-attempt/zero-applied crossfire failure remain real. A separate actual41-cell Scout-LOS enclosure preserves its state and existing projectile, then proves Wingman movement/acquisition/fire and two actual9-damage events (HP1024→1006), with the genuine60-tick observation expiry. It is not renamed27/27 or merged through wrappers that omit the original failed method. Independent review identifies normal equal-damage hurt-window starvation, rather than absent relay AI; broader crossfire efficiency remains open.

New U07 source implements a distinct flying-teleport prototype in its own tree, including a matching rig, locked warning/counterplay state and bounded deferred admission using the existing route pool. It is uncompiled/unrun and unintegrated. U14 has observer-only32→128 ordinary-death baseline source; no sleep implementation or optimization result is claimed. These previously unused clauses now have explicit source owners and gates.

The cubic-light diagnostic retained all129 actual receipts and a same-object readiness counterexample: an actual FULL section with33 nonempty cells receives the old captured`empty=true` callback and becomes LIGHT_ONLY; a genuinely empty section remains correct. A minimal one-call production correction is under isolated development. This does not establish the cause of the earlier400-tick client-zero/server-lit failure. The genuine600-wall Survival writer, complete3D AI, arbitrary occupied Boarding/functional blocks, connected portal groups, low-poly/fog/pacing/volumetric LOD, native generic control and broad bestiary/mission/large-destruction acceptance remain in the queue.

Development is local. Nothing was pushed or published.
