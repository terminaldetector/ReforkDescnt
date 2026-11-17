# Seven requirements without a distinct packet in the 21-packet map

This small supplement reviews **production3095999**, with current metadata
HEAD72ed2bf (no `src/main` delta), against all99 ledger IDs and the prior
[21-packet map](UNTOUCHED_IMPLEMENTATION_PACKETS_2026-11-17.md). It does not change
statuses, repeat the full catalogue or declare new runtime acceptance. The ledger
hash3258cbd8… remains99 rows:3 completed/28 in_progress/68 pending_reverification.
The21 packets explicitly reference44 task IDs; the other55 are **not55 absent
features**. Many are history, existing active gates, regression and delivery work.

The [compact source-bound receipt](SUPPLEMENTAL_CLAUSE_AUDIT_3095999_2026-11-17.json)
retains7 supplements,20 exact Git-blob/line/quote anchors, exact task links and
selected direct-user clause quotes. Current server evidence309/898unit/450GT is
server-only: its19-payload archive hashes/CRC were checked, tests were not rerun,
and no client acceptance is inherited. Older scoped reports retain their own
source/JAR identities. The separate component GUI/XTest failures close no task.

## Authority retained at equal coverage

[Master v8](reference/00_MASTER_SPEC.md) is edition04.11.2026 (SHAc98fe04f…);
[ENGINE B / PS1](reference/02_ENGINE/RENDER_NOCUBES_PS1.md) remains the parallel
render contract (SHA169972c7…). The direct-user **Dynamic Boarding / BlockBody**
text has10 sections/12 criteria, no stated edition date. Its normalized
transcriptionfb81c358… and exact DB clause IDs remain in the
[earlier crosswalk](TWO_SPEC_COVERAGE_2026-11-17.md#direct-dynamic-boarding-all-ten-sections);
the dated27October UFO-demo upload is distinct. No later standalone PS1 edition
is invented or rendering downgraded by interpreting “both” narrowly.

The later user override requires separate reusable gun/bearing/drive/moving blocks
on the existing solver. It supersedes the old UFO-only assembly priority, not the
rest of the physics contract. No Create dependency/factory chain, separate engine,
interior dimension, follow teleport, mandatory gas/decompression or publication
is implied. Collision proxies are expressly allowed; no triangle-solver rewrite
follows from their use.

## Supplemental packets and gates

| ID / existing task / exact specification | Existing source and evidence boundary | Missing distinct work in the packet map |
| --- | --- | --- |
| **R1** — S04-T01/T02/T03/T04; [ENGINE B material policy + algorithms](reference/02_ENGINE/RENDER_NOCUBES_PS1.md#terrain-material-policy), lines7–15 | [HybridTerrain:14](../../src/main/java/com/terminaldetector/drmd/world/geometry/HybridTerrain.java) stores source/mask/organic; real [HybridMesh:35](../../src/main/java/com/terminaldetector/drmd/world/geometry/HybridMesh.java) shares warped corners and engineered anchors. This is a working baseline. | U18 covers logs, U19 drill heat, U21 distant tiers. Add an explicit general material/stage decision and measured near-mesher comparison for engineered/natural/fracture/cavity interfaces. Preserve current baseline if it wins. Acceptance: signed-XYZ seams, normals/UV/visibility/proxy passage agreement and bounded dirty rebuild—not a mandatory algorithm replacement. **Owner: render+world.** |
| **R2** — S05-T01/T05,S08-T05; [v8§4](reference/00_MASTER_SPEC.md#4-hybridnocubesps1-render-p1), line58; ENGINE B line25 | [ProfileDefaults:9](../../src/main/java/com/terminaldetector/drmd/config/ProfileDefaults.java) preserves explicit hybrid off; [DescentConfig:151](../../src/main/java/com/terminaldetector/drmd/client/config/DescentConfig.java) saves it; [DrmdServerConfig:103](../../src/main/java/com/terminaldetector/drmd/world/DrmdServerConfig.java) independently defaults physics to EXPANDED. **Not missing toggles.** | U17's selector/debug/pacing does not name the full independent persisted-default matrix. Add copied fresh/existing config/world controls through render profiles, renderer fallback, STANDARD/EXPANDED and restart. Fix only observed loss/coupling. Acceptance: choices survive independently, render-only changes do not mutate contacts/world/cache. **Owner: render+audit.** |
| **R3** — S01-T03,S05-T06,S15-T03; [ENGINE B mirrors + compatibility](reference/02_ENGINE/RENDER_NOCUBES_PS1.md#mirror-vs-portal), lines27–32 | [MirrorReflectionRenderer:34](../../src/main/java/com/terminaldetector/drmd/client/portal/MirrorReflectionRenderer.java) deliberately budgets2 reflections; [MirrorFramebuffer:46](../../src/main/java/com/terminaldetector/drmd/client/portal/MirrorFramebuffer.java) refuses unavailable raster; [ScenePresentation:64](../../src/main/java/com/terminaldetector/drmd/client/presentation/ScenePresentation.java) has restoration/fallback. Merged-pane proof is real. | Beyond generic U16/U17 matrix wording, schedule1/2/**4 requested** surfaces, no-target/minimize/resize and failure restoration with a portal. Four surfaces do not require four expensive reflections when budgeted; every slab must still draw correctly. Preserve exact Native/PS1/Sodium/softwareGL scopes; physical Intel remains separately unverified. **Owner: render.** |
| **B1** — S09-T04,S06-T06,S08-T03; direct Boarding§2–3, DB-017…024,DB-029/032 | [DynamicBodyAttachment:114](../../src/main/java/com/terminaldetector/drmd/world/contraption/boarding/DynamicBodyAttachment.java) already applies friction/reaction and field acceleration; relative sweeps exist at148. [BodyContactConstraintTest:33](../../src/test/java/com/terminaldetector/drmd/BodyContactConstraintTest.java) covers momentum/friction. [Moving-deck native proof](BOARDING_SUPPORT_TRANSFER_2026-11-06.md) must be preserved. | U11 adds non-player adapters; it does not distinctly schedule the original player's **changing acceleration/friction/two-moving-side** matrix. Extend the occupied-body scenario with spin, inner/outer narrow surfaces, jump/slide and independent carrier transfer. Correct only demonstrated failures; no second contact engine. **Owner: root+gravity.** |
| **B2** — S09-T03/T04/T06; direct Boarding§7, DB-059…065 | [UfoControlSystem:58](../../src/main/java/com/terminaldetector/drmd/world/mega/UfoControlSystem.java) has real validity/reach/unique-owner/epoch and AI handoff, but its entry is typed to `SkyUfoEntity`. [Control lifecycle proof](CONTROL_CORE_LIFECYCLE_2026-11-04.md) accepts real capture/release/death subsets on its earlier bytes. | U08 modules/U12 functional blocks do not name a **generic player-built BlockBody ControlCore lease adapter**. Reuse existing input/lease authority, separate engine/gun/mechanism blocks and saved identity. Accept core use, invalid/stale ownership negatives, routing, release/reclaim/fracture/reload without recreating the hull or resetting velocity. Do not invent faction canon. **Owner: root.** |
| **B3** — S09-T04/T06,S10-T05; direct Boarding§5/8/9, DB-051/070/071/074/077/082 | [BlockBodyEntity:340](../../src/main/java/com/terminaldetector/drmd/world/contraption/BlockBodyEntity.java) already batches edits and at466 rebases COM/point velocity/dirty geometry. [BoardingClient:109](../../src/main/java/com/terminaldetector/drmd/client/flight/BoardingClient.java) waits for shape revision and replays bounded inputs. **Not absent prediction or full-mesh-only rebuilding.** | U12 does not isolate the combined occupied **asymmetric edit→COM/collider revision→pose/contact packet→prediction** transaction. Add moving occupied placement/removal with interleaved shape/pose arrival and cold restore; implement only failure-driven transaction changes. Material-point continuity, real contact and no duplicate fragment/refund remain mandatory. Remote multiplayer is outside v8; integrated-server reconciliation remains required. **Owner: root+audit.** |
| **B4** — S09-T05/T04,S11-T06; [v8§6](reference/00_MASTER_SPEC.md#6-blockbody--ufo--ships-p1-эпизод-уже-в-работе), line75; [physical episode](reference/03_GAMEPLAY/BLOCKBODY_UFO_AND_MOTHERSHIP.md#пять-целевых-боевых-эпизодов), lines12–27 | [SkyUfoEntity:160](../../src/main/java/com/terminaldetector/drmd/world/mega/SkyUfoEntity.java) already retires control, changes gravity and enters a saved100-tick crash/shockwave/wreck path. Reactor damage/destruction is **not absent**. | U04 base siege/U08 heavy modules do not replace **aboard→sabotage→visible warning→safe emergency departure→fragmented wreck**. Complete explicit feedback/departure integration at this entry, then a moving playable episode and cold return. Preserve world/contact-point momentum, bounded cleanup and exactly-once detonation; no automatic atmosphere subsystem. **Owner: root+content.** |

R1/B2/B4 identify bounded extensions or missing bridges at the inspected entries;
R2/R3/B1/B3 identify distinct combined gates over code that already exists. They
are not seven automatically missing subsystems. Generic ControlCore and reactor
feedback are concrete next-code candidates; renderer material decisions should
follow current owner coordination. Other rows start with original controls and
need production edits only after an actual defect.

## Scope checks

No central STATE/TASKS/DECISIONS/index, reference document, old4b audit or21-packet
map was modified. Every new task/packet reference was checked; all20 selected
quotes/hashes match exact Git blobs. The ledger snapshot matches its309 Git blob.
Latest metadata72ed changes no production source. Existing inventories, COM
updates, player prediction, friction/CCD, ControlCore and crash timer remain
present; pending_reverification is not absence. Full12-criterion arbitrary-body
Boarding acceptance, renderer matrix and hardware/performance results remain open.
