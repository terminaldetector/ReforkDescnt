# Two-spec coverage crosswalk — 17 November 2026

**Primary pair: MASTER v8 (4 November) + the direct user Dynamic Boarding / BlockBody supplement (10 sections /12 acceptance criteria).** Both remain open. ENGINE B render/NoCubes/PS1, the27October UFO demo and earlier retained supplements remain in scope. This is a proposed coverage table, not a new task ledger or completion report.

Source review: `1a8593846bed3b02d03d68236c6698d26a5116d4`. Latest recorded combined runtime: `11513cdd26fe4d3d291858d91efab1b21007c1a8`,824unit/418ordinaryGameTests/66entrypoints plus one same-JAR6scene softwareMesa/Sodium client. Inspected source/build paths have no delta between those commits, but the actual executed source remains11513. No Java/native run was made for this crosswalk. Ledger stays **3completed /24in_progress /72pending**. The prior4b all99 audit is unchanged.

The [JSON crosswalk](TWO_SPEC_COVERAGE_2026-11-17.json) retains every extracted source block, section/line anchors, source identities, owner/task references, current code/test/report hashes and99 concrete next-step/acceptance plans. Inventory is not acceptance. A clause may map to several tasks; a completed atomic task does not certify the whole clause.

## Authority and retained requirements

| Source | Identity / priority |
| --- | --- |
| MASTERv8 | `reference/00_MASTER_SPEC.md`, title MASTER ENGINEERING SPECIFICATIONv8.0, revision04.11.2026; SHA `c98fe04f5592d5afcc9ddc34414c4d3032287944905718cf2fd17b4e2770d082`. |
| Direct Dynamic Boarding | User message headed Minecraft Ungravity / Техническое дополнение: Dynamic Boarding / BlockBody. Undated direct text; its normalized transcription is explicitly embedded in JSON. It is not falsely identified as the bytes of the separate UFO upload. |
| ENGINEB | `reference/02_ENGINE/RENDER_NOCUBES_PS1.md`; undated subdocument of4November package. No later standalonePS1 or G2 edition located; G2 is a physics gate. |
| UFO27 | Dated27.10 UFO-as-BlockBody demo, repository copy and retained original-upload hash bound separately. |
| Earlier v5 | Fourteen preserved `08_PREVIOUS_SPECS` members mapped in JSON, including weapons/cluster/drills/building/physics/render/models/mirrors/portals/cleanup/cubic/video. Their technical requirements survive where compatible with the latest pair. |

Later user direction takes priority over old narrow-UFO assembly framing: turrets use **separate player-built gun/bearing/drive/moving blocks on existing joints**. No Create dependency, manufacturing chain or separate physics engine. Current local-only/offline queue supersedes historical publication/remote-multiplayer gates; local server authority and correction still apply. Five tech tiers remain unapproved design candidates. Pressure/gas/decompression remain optional.

## Direct Dynamic Boarding: all ten sections

| Section | Existing scope to preserve | Unclosed clauses / next acceptance | Tasks / owner |
| --- | --- | --- |
| §1 local frames | Local grid, quaternion, COM/velocities and transforms already exist. | Cross-subsystem transformations, point velocity and exact restored state for arbitrary assemblies; no replacement coordinate engine. | S06-T01,S09-T02 /gravity+root |
| §2 contact attachment | Real player boarding, point-velocity release and moving-deck seam subsets exist. | Attachment is player-only; extend other physical actors. Complete acceleration/friction, walk/jump/slide, arbitrary6DoF and free body transfer controls. | S09-T04,S06-T06,S07-T01 /root+gravity |
| §3 collisions | Compound cells, spatial queries, oriented contact and bounds guards exist. | Both-body motion/inner-outer normals and actual fast-object CCD matrix; no per-inert-block rigid body. | S09-T02/T03,S08-T03 /root+gravity |
| §4 moving interior | Scoped deck/interior/local gravity proof exists. | Rotated ladders, doors, moving child platforms, zeroG handoff, mobs/free items and independent local gravity are not universal acceptance. No separate dimension or forced atmosphere subsystem. | S09-T04,S07-T01 /root+gravity |
| §5 local block API | BlockBodyWorldAccess/read/ray/place/break and supported mechanical use exist. | General functional-block adapters, moving use/aim, damage→mass/collider and all orientations/lifecycle; world-block calls cannot target local grid directly. | S09-T03/T04,S10-T05 /root+combat |
| §6 joints/boarding | Existing hinged gun/bearing/drive and child-body mechanics have scoped proof. | Generic SLIDER platform/airlock construction is missing at this pin; root implementing later. Complete FIXED/HINGE/MOTOR/SLIDER child collision, standing/transfer and detached momentum. | S09-T03/T04/T05 /root |
| §7 ControlCore | Actual control capture/release and scoped saved restoration exist. | Complete access checks/AI takeover/drive+weapon routing without body recreation or velocity reset across death/reload/fragmentation. | S09-T04 /root |
| §8 authority/sync | Server state, packets, local interpolation and actual island load/restart subsets exist. | Unified body→children→occupants order, correction/attachment state, full rotated occupied volume and uninterrupted chunk crossing; remote multiplayer deferred byv8 queue, not silently accepted. | S09-T04/T06,S02-T04 /root+world |
| §9 optimization | Compound/index/dirty geometry mechanisms exist; bounded jobs do not prove final budgets. | SimulationLOD/interest must preserve active occupants; incremental damage/rebuild, bounded CCD and full spatial volume require measured cases. | S09-T03/T06,S16-T02 /root+audit |
| §10 twelve criteria | Reference engine acceptance and several earlier moving-deck/control/restore proofs remain real. | All12 criteria still need a coherent current-source arbitrary-mechanism acceptance matrix; see separate rows below. | S09-T02–T06 /root; cross-lanes as referenced |

### Twelve acceptance criteria crosswalk

| Criterion | Remaining exact gate | Task refs |
| --- | --- | --- |
| 1 landing/walking | Actual moving UFO landing and walked surface with admitted contact; broader than spawn/pose marker. | S09-T04 |
| 2 full6DoF contacts | Full maneuver with occupied surface/interior and normal/collider continuity; reference engine alone is insufficient. | S09-T02,T04 |
| 3 jump world momentum | Actual release inherits linear+angular point velocity and relative jump with controls. | S09-T04,S06-T06 |
| 4 enter via passage | Traverse existing and destructively opened route during flight with no invisible wall/dimension swap. | S09-T04,S10-T04 |
| 5 local gravity | Inside rotated ship, switch field/zeroG for required actors without world-UP fallback errors. | S09-T04,S07-T01 |
| 6 moving block edits | Actual player break/place/use with resources, ownership, collider/mass update and restore. | S09-T04,S10-T05 |
| 7 moving mechanisms | Real independently colliding airlock/turret/platform works while hull maneuvers; latest Create-style direction applies. | S09-T03,T04 |
| 8 ship-to-ship transfer | Actual moving parent/child and independent-body transfer preserves contact/release state. | S09-T04,S06-T06 |
| 9 fragment separation | Damage detaches bounded material/child with mass, world velocity and angular momentum, no duplicates. | S09-T05,S08-T02 |
| 10 ControlCore | Access/AI→player transfer controls without rebuilding/resetting existing moving hull. | S09-T04 |
| 11 client agreement | Actual local client interpolation/prediction/correction and occupants agree with authoritative integrated server; remote2JVM deferred. | S09-T04,S16-T01 |
| 12 actual save/reload | Fresh-process restore of hull+modules+constraints+occupants retains physical state once, not only serialization call. | S09-T06,S16-T04 |

##99-task proposed implementation and acceptance table

Owners below come from the pinned current assignments, not a reassignment. The JSON binds the actual code/test/report paths and hashes per row. Older report scopes retain their exact source; this table does not relabel them as11513 runs.

### S00 — History & executable truth

Exact11513 combined proof is retained; all-worktree and historical lineage completeness remains process work.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S00-T01 / audit_integration / in_progress | Capture active worktrees/dirty patches and inputs without disturbing running lanes. | All active trees have exact refs, dirty-byte receipts and recoverable local checkpoints; no overwrite. |
| S00-T02 / audit_integration / in_progress | Retain exact combined11513 source/main/observer binding; reconcile each later candidate separately. | A candidate's actual launched bytes, mod metadata and source inputs agree; never relabel an older runtime as current HEAD. |
| S00-T03 / audit_integration / pending_reverification | Complete pre-Oct26 commit-by-commit PR9/recovery/local-artifact lineage matrix. | Each claimed recovered mechanic links to real source/artifact and current scoped regression or explicit UNKNOWN. |
| S00-T04 / audit_integration / pending_reverification | Finish real use/save graph and KEEP/ADAPT/REMOVE_AFTER_PARITY mapping before removals. | Each proposed removal has consumers, registry/save compatibility, parity test and reviewed scope. |
| S00-T05 / audit_integration / in_progress | Reconcile final all-lane evidence metadata after each accepted integration. | STATE/DECISIONS/REGRESSIONS/index identify actual source/JAR, first failures and open gates consistently. |
| S00-T06 / audit_integration / pending_reverification | Version unresolved assumptions and exact replay paths, including absent old audit4 source/save. | Another agent can replay recorded gates from retained inputs; missing inputs stay explicit rather than inferred. |

### S01 — Reproduce and instrument baseline

Original audit4 DUMMY source/save not recovered; mirror/viewmodel/surface corrections have scoped current and older evidence, not original-scenario closure.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S01-T01 / gravity_ragdoll / pending_reverification | Replay original trapped-proxy scenario through actual toggle/mount/death/unload paths. | Normal exit works without lava, preserves intended velocity/ownership and leaves no hidden orphan proxy. |
| S01-T02 / world_cubic / pending_reverification | Capture fixed-seed multi-Y/void/fast-flight failure and actual native availability. | No false AIR/hard plane/void; out-of-bounds denied safely, with original before/after source-specific evidence. |
| S01-T03 / ps1_render / pending_reverification | Extend merged-pane mirror proof to 1/2/4 mirrors and missing-target/slab fallback. | Real reflected content and slab remain correct across partial visibility/resize/fallback, independent of portals. |
| S01-T04 / gravity_ragdoll / pending_reverification | Exercise torch-zone eye/collision/viewmodel through full yaw/pitch/roll and body changes. | No clipped eye or swept arm; actual aim/muzzle stay coherent under all declared poses and reload. |
| S01-T05 / world_cubic / in_progress | Add non-mutating pending-NBT first-transition observation and valid cold-generation controls. | Identify actual first incompatible state/NBT writer or explicitly scoped negative; historical133 warnings are not closed by unrelated logs. |
| S01-T06 / audit_integration / pending_reverification | Collect actual sim8/sim24 timing context using bounded observation and honest missing fields. | Fixed-seed profiler/MSPT/JFR run provenance and p50/p95/p99 retained; lag warnings alone do not attribute a subsystem. |

### S02 — Choose actual CubicWorld backend

Real native BCC and prior provider/load/save evidence exists. Complete clean-noDRMD/preset/multiY/actual-hardware matrix remains partial.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S02-T01 / world_cubic / pending_reverification | Retain verified BCC artifact/license and complete alternatives/API dependency comparison. | Exact native artifact/metadata/license and unsupported combinations documented; no ARR source copy or invented compatibility. |
| S02-T02 / world_cubic / pending_reverification | Run two truly clean Fabric+BCC new saves before DRMD-specific preset content. | Actual provider/cube persistence/bounds/vertical distances confirmed; tall registry STOCK is not substituted for native cubic. |
| S02-T03 / world_cubic / pending_reverification | Compare actual stock/native bounds and FULL16-cube load states to supplemental snapshots. | Unknown/presentAIR/full metadata differ correctly across signed XYZ limits; no shadow snapshot treated as authority. |
| S02-T04 / world_cubic / pending_reverification | Reconcile existing loaded-only API with required bounds/result/dirty-volume contracts at real consumers. | Signed addressing, availability, lifecycle and bounded mesh/light/fluid invalidation have explicit callers and negative controls. |
| S02-T05 / world_cubic / in_progress | Extend actual native parity to scheduled fluid ticks, multiple BEs/light and cold seam reopen. | Requested multi-Y and XYZ seams retain state/NBT/light/flow after genuine unload and fresh-process restore. |
| S02-T06 / world_cubic / pending_reverification | Document verified software profiles separately from actual Intel/modern GPU tests. | Real supported/blocked driver/upstream matrix with rollback; software GLSL150 is not physical Intel acceptance. |

### S03 — Migrate world gameplay and structures

Loaded winner guard actual negative→positive and known return/edit re-ingest proven; natural no-edit arrival remains unrun. AllBE/flow/consumer migration unclosed.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S03-T01 / world_cubic / pending_reverification | Migrate remaining raw gameplay consumers one at a time; keep reactive block adapters explicit. | Actual edits/explosions/fluid/navigation respect unavailable volumes, permissions and bounded invalidation without force loading. |
| S03-T02 / world_cubic / pending_reverification | Port one authored biome/structure subsystem to native bounds/loaded authority without old autostart. | Actual generated content at signed/cubic boundaries preserves layout, lifecycle and BE ownership; routing sentinel is not content parity. |
| S03-T03 / world_cubic / pending_reverification | Reproduce generation-to-state/NBT inconsistency with original writer observation before correction. | Valid generated DUMMY/spawner data survives replacement/save/cold return without stale ownership; no blanket NBT deletion. |
| S03-T04 / world_cubic / pending_reverification | Execute negativeY/1024/1640/2150 flight/collision/light/fluid and genuine cold-return matrix. | Every declared coordinate has actual loaded state and same-world restored behavior; unsupported presets reject safely. |
| S03-T05 / world_cubic / pending_reverification | Complete old-emulation use/parity/migration map; test opt-in supplemental unknownY separately. | Delete only proved obsolete paths after parity; preserve artistic layers, local hull grids, existing saves and legitimate snapshots. |

### S04 — Hybrid/NoCubes meshing and LOD

Near organic geometry, shared conservative collision, seam math and simple falling-tree paths exist. True3D farLOD/log-axis branching/thermal-front/cloud morphology are missing or incomplete.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S04-T01 / ps1_render / pending_reverification | Extend boolean organic policy with explicit engineered/transition/cavity/thermal masks where required. | Material boundaries preserve built edges and intended natural/fracture silhouettes with server/client agreement. |
| S04-T02 / ps1_render / pending_reverification | Extend existing geometry with log-axis-aware low-poly trunk/branch morphology; compare candidate mesh algorithms. | Authored vertical/horizontal logs, branch joint, canopy and engineered neighbor demonstrate intended silhouette/support and bounded cost. |
| S04-T03 / ps1_render / pending_reverification | Test oblique rounded cavities across negativeXYZ/native seams and establish watertight topology/LOD transitions. | No cracks/nonmanifold faces or invalid normals; supported passage, damage and cold restore agree across cells/volumes. |
| S04-T04 / ps1_render / pending_reverification | Refine existing conservative collision proxies where actual passage/nav/fluid tests expose mismatch. | Visible slopes/tunnels are traversable with no invisible wall or ghost support; server/client/camera/nav/flow stay coherent. |
| S04-T05 / ps1_render / pending_reverification | Prototype true volumetric farLOD/BetterDH in isolated licensed compatibility stand. | Multiple surfaces at oneXZ, overhangs and floating volumes remain represented near/mid/far; heightfield is not called full3DLOD. |
| S04-T06 / ps1_render / in_progress | Preserve accepted sky-depth fix, diagnose remaining blue edge and add smooth ground/altitude transitions. | Actual roll/climb/fast flight retains visible ground without wedges/holes; no sky/LOD/ground disable workaround. |

### S05 — PS1 Hybrid presentation profile

11513 same-JAR6scenes/166Native166PS1gun/nearest1.0/8panes merged pass on softwareMesa+Sodium. Later separate8f palette Sodium positive does not add vertexsnap/affine/fog or certify this pinned profile; GL3.2 control remains queued at handoff.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S05-T01 / ps1_render / in_progress | Retain real640x360/nearest target and runtime profile/resize restoration evidence. | Exact candidate changes presentation only, renders stable overlay and restores targets after exceptions; broader matrix remains separate. |
| S05-T02 / ps1_render / pending_reverification | Finish independently measured palette/dither, then add vertex snap and affine-like UV at geometry stage. | Known authored colours/patterns and textured slanted primitives meet declared quantization/snap/warp; GUI/aim stay stable. |
| S05-T03 / ps1_render / pending_reverification | Implement actual altitude-aware atmospheric clouds and configurable PS1 fog/ground-asteroid fade. | Below/in/above-cloud climb and roll preserve horizon silhouettes/ground and continuous density within bounded draw budget. |
| S05-T04 / ps1_render / in_progress | Extend existing full-resolution HUD/viewmodel split to tooltip/scales/all actual model banks. | Readable UI/crosshair and correctly projected weapon across profiles, resolution, FOV and full roll without aim drift. |
| S05-T05 / ps1_render / in_progress | Compare active physical/contact/world state through profile changes; retain independent hybrid choice. | No simulation/grid/collider/cache ownership mutation or reload; saved user override remains intact. |
| S05-T06 / ps1_render / in_progress | Complete native/PS1/debug with normals/LOD/collision overlays and full compatibility matrix. | Mirror/portal/YPR/Sodium/native-cubic cases pass at exact bytes; actual unavailable hardware is BLOCKED, not fabricated. |

### S06 — Core gravity and player 6DoF

Real player lifecycle, local frame and seam-transfer subsets exist; original proxy/camera full-axis/lifecycle matrix remains open.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S06-T01 / gravity_ragdoll / pending_reverification | Audit shared quaternion/local-gravity state ownership across player/mobs/bodies and render boundaries. | No duplicate roll/gimbal discontinuity; transforms/momentum remain authoritative through frame transitions. |
| S06-T02 / gravity_ragdoll / pending_reverification | Complete ordinary exit/dismount/death/restore ownership cases without proxy resurrection. | Player exits safely without lava or speed reset, no orphan or repeated remount across each lifecycle path. |
| S06-T03 / gravity_ragdoll / pending_reverification | Add full torch/body/ordinary-camera eye and hand pose matrix. | World hitbox/eye and displayed aim remain aligned through0/90/180/270/360 yaw/pitch/roll and saved restore. |
| S06-T04 / gravity_ragdoll / pending_reverification | Test dimension/index lifecycle and deterministic overlapping-field removal in real scenes. | Same descriptors/blend order reproduce local up and vanilla restoration with no stale owner/field. |
| S06-T05 / gravity_ragdoll / pending_reverification | Verify actual once-per-transition feedback under overlaps, reload and removing torch. | No per-tick LocalGravity chat/log flood; meaningful enter/exit changes remain visible. |
| S06-T06 / gravity_ragdoll / pending_reverification | Complete world/local contact reset under death, external teleport, unload/reload and portal admission. | No stale frame/contact/duplicated owner and continuous intended velocity after each valid transition. |

### S07 — Universal 6DoF for all mob families

Flyer force-only, Fish8 and Spider/CaveSpider24entry+floor proofs exist. Separate corrected-observer250 first4 remains2FAIL2PASS: actual wall failure, ceiling timeout before full observation; no local-up navigation acceptance.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S07-T01 / gravity_ragdoll / in_progress | Extend supported family adapters/fallback while retaining fish force-only and flyer species movement. | C1-C7 representative families including multipart/anchored actors behave safely inside/outside gravity fields. |
| S07-T02 / gravity_ragdoll / pending_reverification | Fix proved wall-support/navigation failure after observer controls; extend ceiling/local-up routes. | Actual admitted Spider/CaveSpider progress and supported contact on walls/ceilings; valid idle controls do not create false failures. |
| S07-T03 / gravity_ragdoll / pending_reverification | Complete actual local-frame LOS/raycast/attacks across species and complex bodies. | Eyes, aim, projectiles and hit volumes agree on arbitrary local orientation with occlusion and unavailable-volume denial. |
| S07-T04 / gravity_ragdoll / pending_reverification | Add measured actor interest/sleep/wake clusters to existing field/spatial indexes. | No whole-world per-field scan; nearby interacting actors retain integration/contact while far tiers save measured work. |
| S07-T05 / gravity_ragdoll / pending_reverification | Run cross-family torch contact/orientation and actual cold restore, separate from entry-only tests. | Rotation/contact/species AI survive lifecycle for declared C1-C7 set; fish/spider entry proof is not universal navigation. |

### S08 — Articulated ragdoll and destructible bodies

Field-strength6 and actual occupied-native-volume guard exist; no swept CCD/universal rigs/angular limits/sleeping/mass128death closure.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S08-T01 / gravity_ragdoll / pending_reverification | Reconcile historical solver/contact changes with actual current constraint behavior. | Each inherited claim is KEEP/ADAPT/UNKNOWN with relevant current regression; old PR description is not acceptance. |
| S08-T02 / gravity_ragdoll / pending_reverification | Define and implement missing articulated angular limits/family rig contracts within existing solver. | Off-centre impulses, limits/friction/rest and contact solving remain stable under actual limb/rig interactions. |
| S08-T03 / gravity_ragdoll / in_progress | Extend direct occupied-volume guard to swept wall/high-speed impact and duplicate-death controls. | Rigs do not tunnel or duplicate under declared projectile/player/drone/body collisions; direct atomic relocation proof stays scoped. |
| S08-T04 / gravity_ragdoll / pending_reverification | Add explicit ragdoll sleeping/wakeup and bounded aggregation for128/256/1024 deaths. | Measured simultaneous deaths/debris decay leave no permanent tickers; lifetime1200 alone is not sleeping/aggregation. |
| S08-T05 / gravity_ragdoll / in_progress | Exercise gravity-compensated rail hit plus articulated local-gravity and moving-body contact. | Actual rig orientation/momentum/contacts respond correctly; zeroG100mass plane proof is not ballistic/material-hit acceptance. |

### S09 — BlockBody / UFO boarding and reactor

Reference engine/six-axis atomic acceptance plus generic hinged gun/bearing/drive/restart proofs exist. Player-built slider root work is later/uncompiled outside this source pin.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S09-T01 / root / pending_reverification | Finish preserved Sol/UFO lineage reconciliation before shared core changes. | No active source lost; exact selected implementation and excluded candidates documented with real evidence. |
| S09-T02 / root / completed | Preserve accepted reference-UFO engine/six-axis proof as regression only. | Atomic authored reference accepted; arbitrary player assemblies and allDynamicBoarding clauses require their own gates. |
| S09-T03 / root / in_progress | Build generic separate gun/bearing/drive and sliding platforms on existing joints; complete moving LOS/FF. | Real player-built mechanisms move/collide/fire/detach under6DoF without ship-specific shortcut or new engine. |
| S09-T04 / root / in_progress | Extend player-only boarding to required physical actors/interior devices and full control lifecycle. | Contact-based walking/jump/slide/transfers, doors/ladders/zeroG and AI↔player ownership work without teleport follow. |
| S09-T05 / root / pending_reverification | Finish playable sabotage/reactor-warning/escape/fracture loop with independent powered modules. | Actual moving UFO boarding→weapons/spawners→reactor→escape is playable, bounded and preserves fragment momentum. |
| S09-T06 / root / in_progress | Restore arbitrary child mechanisms/occupants/modules in flight; keep optional VS experiment separate. | Fresh-process save restores exact states/constraints/ownership and no duplicates; old UFO or island subset is not universal restore. |

### S10 — Weapons, cluster, drill and building

5f8 actual receiver10 and six real ordinary/6DoFtoolinput scenes pass; costs/caps/quarter-cell drill paths exist. Structured FP carrier/thermal topology/ballistic gravity material hit remain open.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S10-T01 / combat_ai / pending_reverification | Complete actual registered weapon/item inventory and normal/6DoF all-pose model bank matrix. | Every declared item/tool is visible/usable with correct primary/secondary mounts, no fallback placeholder hiding errors. |
| S10-T02 / combat_ai / in_progress | Preserve realthree-mode input proof; add gravity-compensated RAIL material-hit accuracy. | SWARM/RAIL/SHIFT stay distinct; real mass/field/energy/occlusion and declared ballistic trajectory meet source-specific gates. |
| S10-T03 / combat_ai / pending_reverification | Replace random one-step cassette drop with carrier/staged structured directional spread path. | First-person carrier→arming/split→ring/stagger→actual impact→cleanup observed with bounded fan-out/save/FF/protection. |
| S10-T04 / combat_ai / pending_reverification | Extend quarter-cell capsule/drop caps with real material thermal front and temporary melt rim. | Weak chunky vs strong circular passage, time-course, collision/light/fluid/save parity pass; particles are not thermal topology. |
| S10-T05 / combat_ai / pending_reverification | Complete actual player construction permissions/resources/undo/batch invalidation across remaining modes. | No free Survival placement, unloaded edit, duplicated refund or stale mesh/light/fluid; existing six economy paths remain scoped. |

### S11 — ORB/ROCKET/JET swarms

Distinct ORB/ROCKET/JET128active/moving/restart, loaded3D routes and rotated hull cover6 proven. Explicit squad assignments/far tiers/256+perf remain open.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S11-T01 / combat_ai / completed | Preserve independently registered ORB/ROCKET/JET class identities. | Recorded atomic identity proof retained; other bestiary classes or full AI performance not implied. |
| S11-T02 / combat_ai / in_progress | Add explicit bounded squad tasks/target assignment atop existing3D boids/spatial index. | Groups investigate/coordinate and avoid dense mixed-size/moving hulls with measured bounded neighbors, not independent decorative steering. |
| S11-T03 / combat_ai / completed | Retain active128/moving96/two-role evidence without calling spawn count combat. | Exact recorded atomic proof remains; later integrated checks report their own bytes and counts. |
| S11-T04 / combat_ai / pending_reverification | Implement explicit far aggregate/reduced tiers and run32/256/512/1024 scaling before5000research. | Record actual full-physics vs aggregate counts/FPS/MSPT/heap/cubes/draws; no10000-full-physics claim. |
| S11-T05 / combat_ai / in_progress | Complete moving-hull/dense-role target/path/projectile/obstacle-destruction acceptance. | Actual damaging attacks and recovery work on arbitrary loaded3D routes; preserve existing rotated-cover/path proofs. |
| S11-T06 / combat_ai / pending_reverification | Run actual settlement siege/destruction, bounded debris and fresh-process restore. | Targets/building loss, survivors, state and cleanup remain meaningful after reload without permanent entities. |

### S12 — Survival threat and minimal armament economy

Provoked kill→warning/recon/search/hunt/cooldown9unit5GT and actual economy acquisition/use/recovery/repair6GT pass. Unprovoked/fullsiege/AAinterception/novice fullplaythrough not proven.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S12-T01 / world_content / in_progress | Extend proven provoked threat states to unprovoked hunt and actual fair siege/defense. | Natural warning→recon/search→hunt→siege→recovery has faction ownership, counters and persisted cooldown. |
| S12-T02 / world_content / pending_reverification | Author and balance two rocket infantry vs defended/undefended novice base. | Telegraphs, concealment/escape/repair and recoverable loss prevent unavoidable instant softlock while real damage occurs. |
| S12-T03 / world_content / pending_reverification | Implement real projectile/bomb interception for existing point-defense and minimal plasma/AA progression. | Actual hostile ordnance intercepted with ownership/LOS/range/budget; LivingEntity-only fast fire is not missile defense. |
| S12-T04 / world_content / pending_reverification | Define persisted repair/material-loss/recovery rules beyond existing crafted-tool checks. | A novice can recover resources/equipment/base after failure without factory chain, dupes or irreversible lockout. |
| S12-T05 / world_content / pending_reverification | Complete one coherent day/night hunt with player decision branches. | Actual conceal/fight/escape/capture outcomes, faction state and reload behavior demonstrated, not only director unit transitions. |

### S13 — World architecture: core to Citadel

Current source contains47pure city geometry/journal/protocol controls, no production runtime registration. Isolated runtime first5cases all timeout; no city-world acceptance.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S13-T01 / world_content / pending_reverification | Author deterministic native-preset spatial zones rather than revive retired altitude autogen. | Core/surface/upper atmosphere are physically traversable with real bounds, tickets and safe generation budgets. |
| S13-T02 / world_content / pending_reverification | Build permission/licence-safe labs/shafts from versioned native-world templates. | Actual ground→cave/lab approach, smooth/rough passages and destructible access survive native save/reload. |
| S13-T03 / world_content / pending_reverification | Implement near/mid/far volumetric stations/asteroids with active-near physics only. | Same spatial silhouettes and boarding/navigation targets remain coherent at distance without random sky particles. |
| S13-T04 / world_content / pending_reverification | Block out ruined ring/Citadel and flight-route graph with original assets. | Playable routes/hub/landmark silhouettes persist; legacy scaffolds or sentinel tests do not count as authored zone. |
| S13-T05 / world_content / pending_reverification | Add atmospheric clouds/continuous transmittance and remove only proven junk origins. | Surface→sky→ring transition retains ground/landmarks without discontinuity; approved asteroids/ring remain. |
| S13-T06 / world_content / pending_reverification | Author compact physical mission routes through shafts/surface/orbit/ring. | Traversal and pacing are playable without teleport-only layer substitutes; route breakage and return are tested. |

### S14 — Factions, Shapes and mission framework

Versioned unapproved tier/mission candidates and legacy EnclaveSite tech0..3/Herald quests exist. These are not five approved tiers or current data-driven missions; no distinct Shapes/HeavyOperator/teleport-flyer role found in bounded roster review.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S14-T01 / world_content / pending_reverification | Review existing five capability-tier candidates; do not invent approved canon. | Versioned author-approved content before recipes/spawn/balance activation; current candidate table is real but unapproved. |
| S14-T02 / world_content / pending_reverification | Add distinct armed physical Shapes infantry/squad role while preserving existing roster. | No vanilla teleport; actual local-frame movement, cover, team fire and encounter counterplay, not renamed Enderman reference. |
| S14-T03 / world_content / pending_reverification | Implement separate flying teleporter role with readable counterplay. | Identity/ability/telegraph/cooldown/occlusion/save state demonstrated; existing flying Seeker alone is not that contract. |
| S14-T04 / world_content / pending_reverification | Author compact heavy laser operator with independent shoulder missile machinery. | Real separate modules, aim/damage/repair/destruction and constrained motion; existing turret block is not this enemy identity. |
| S14-T05 / world_content / pending_reverification | Turn proposed mission schemas into bounded data-driven lab/boarding/convoy execution. | Triggers/objectives/extraction/idempotent faction rewards and fail/abandon/reload branches work in actual world. |

### S15 — Cleanup / asset and UI integration

Merged8pane mirror and separate portal render paths exist; six screenshot IDs/use graph, safe dead-asset migration, broad UI/junk classification remain open.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S15-T01 / audit_integration / pending_reverification | Identify six exact marked screenshot itemIDs and real command/recipe/save use graph. | Every slot has evidence-backed registry mapping; ambiguous circles are not deletion authority. |
| S15-T02 / audit_integration / pending_reverification | Remove only proved dead assets/IDs through explicit save-compatible migration. | Existing worlds/items/recipes remain readable; unresolved or used resources retained, no broad cleanup by filename. |
| S15-T03 / ps1_render / pending_reverification | Complete independent mirror/portal render and traversal matrix after merged-pane proof. | Slab/FBO/clip/culling/velocity/basis/cycles work across1/2/4 mirrors and portal combinations with fallback. |
| S15-T04 / audit_integration / pending_reverification | Trace each unintended sky object to generator/registry before narrow removal. | Confirmed junk gone while authored asteroids/stations/ring and necessary legacy save readers survive. |
| S15-T05 / audit_integration / pending_reverification | Audit actual tooltips/debug lines/HUD and UI scales in native/PS1 modes. | Readable text/crosshair at supported scales, no battle spam or implementation debug leaking into user flow. |

### S16 — Full performance and regression testing

11513 combined824unit/418ordinaryGT/66entrypoints+same-JARclient retained. Recorder enabled1s smoke observes6/12/6 only; no hardware/performance/fullcity-endurance certificate.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S16-T01 / audit_integration / in_progress | Retain latest exact11513 combined proof; extend remaining gameplay/physics/render gates on new final candidate. | Same actual source/JAR passes declared scopes;824/418/6scenes is not whole-spec or release acceptance. |
| S16-T02 / audit_integration / in_progress | Finish disabled/cap recorder controls, then real32→1024 scenario metrics with provenance. | Actual timing/heap and observed cubes/draws/seed or null+reason; softwareGPU and instrumentation-only smoke never become hardware benchmarks. |
| S16-T03 / audit_integration / pending_reverification | Run high-altitude mesh/camera/LOD pressure under roll and fast vehicle. | No hidden ground/seam holes or runaway queue; declare source, full world geometry and measured resource load. |
| S16-T04 / audit_integration / pending_reverification | Run actual city/dungeon explosions,128+ragdoll/debris and cold save/reload for declared hours. | Time-stamped bounded memory/queues/entities and repeated restoration; short pure/control tests do not substitute. |
| S16-T05 / audit_integration / pending_reverification | Complete actual unsupported-beta/driver matrix and reproducible rollback pointers. | Known failures remain reproducible, original evidence retained, unsafe default promotion prevented without fabricated hardware results. |

### S17 — Integration, handoff and release candidate

Reviewed local integrations preserve failures; final all-required gates remain open, no release or publication authorized.

| Task / owner / ledger | Next concrete implementation or verification | Acceptance boundary |
| --- | --- | --- |
| S17-T01 / root / in_progress | Review/merge isolated candidates without overwriting active mechanism/renderer/world lanes. | Exact source deltas and first evidence preserved; integration regressions are attributed to actual candidate. |
| S17-T02 / root / in_progress | Reconcile final-source ledgers and proof index through coordinator only. | All99 owners/statuses/evidence agree; this coverage proposal performs no automatic promotion or ledger rewrite. |
| S17-T03 / root / pending_reverification | Prepare local release candidate only after required integration/save/visual/performance gates. | Installable JAR/modlist/test save have exact hashes and truthful acceptance; publication stays paused by current user instruction. |
| S17-T04 / root / pending_reverification | Produce honest task/clause PASS/FAIL/OPEN/BLOCKED final status from actual evidence. | No file-presence or unrelated green run closes a clause; first failures and hardware/missing-input limitations explicit. |
| S17-T05 / root / pending_reverification | Ship reproducible local diffs/replay steps and retained worldbuilding backlog. | A reviewer can restore inputs and rerun accepted scopes; no GitHub/Actions or complete-spec claim implied. |

## Important current gaps that do not fit a single green marker

- Existing near mesh/seam tests, falling simple trees, quarter-cell drilling, loaded3D AI/cover, threat director and six real Survival economy paths are present. Do not call these whole systems absent.

- Still missing/incomplete: specialized round log-axis branches; typed thermal/transition masks and advancing melt rim; true volumetric farLOD; actual atmospheric cloud/fog fade; vertex snap/affineUV/lowpoly selector/frame pacing/debug normals; explicit bounded squad assignment; actor interest/sleep clusters; ragdoll angular limits/sleep/128death aggregation; mobs/items boarding; generic sliding airlocks/platforms; first-person programmed cluster stages; actual point-defense projectile/bomb interception; distinct Shapes/HeavyOperator and executable mission contracts. Source/data foundations do not close these clauses.

- Existing POINT_DEFENSE_TURRET targets LivingEntity; rapid gunfire is not actual anti-missile interception. Existing ORB/ROCKET/JET and CyberMob roster remain real and must be preserved.

- Latest isolated evidence is not automatically integrated: palette8f has a later reported real Sodium positive after preserved074negative, while GL3.2 remains queued; corrected-observer250 spider-wall failure persists and ceiling ends on observer timeout; no-edit surface-arrival native unrun; city runtime first5cases all timeout despite compile/pure55PASS. Root slider source follows this pin and is not accepted here. Later outcomes retain their own source identities.

- Performance/hardware/endurance remain actual gates. SoftwareMesa,6frame/12tick/6heap recorder observations, pure city geometry and short GameTests are not Intel benchmarks,32→1024 scaling or hours-long destruction evidence.

## Evidence and coverage verification

The generator retained 30 document identities and 748 complete nonempty source blocks, with explicit many-to-many links to all99 task plans. 14 v8 reference documents matched the original package SHA/byte manifest. Direct-user text is labelled a normalized conversation transcription, not original attachment bytes. All tracked source/test/report bindings are pinned to1a859. No central ledger/reference or implementation code was edited.

Read-only coverage verification passed with **10 deliberate negative controls** (omitted task/block, forged owner/status/source quote/hash, invented task/date, wrong primary pair and false runtime claim). It checks all99 plans, all748 source blocks, all299 source/test/report bindings, all10 direct sections and12criteria, plus14 original-v8 manifest identities. The exact validator source/hash is embedded in JSON; a local receipt is retained under `work/two-spec-coverage/verification.json`. These are document/data checks, not gameplay acceptance.

This is advisory coverage and prioritization. It neither claims every compound clause has an atomic passing test nor marks pending_reverification as absent. Concrete task plans distinguish known source gaps, current failures, design decisions, evidence gaps and recorded atomic acceptances. Root coordinates implementation/merges and any future task promotion.
