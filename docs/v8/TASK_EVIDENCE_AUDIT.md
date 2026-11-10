# Task evidence inventory — pinned 4b2e243

Inventory prepared 9 November 2026; independent verification completed **2026-11-10T19:04:40.986027+00:00**. This report describes source `4b2e243010d0bba21b322d1999b559efcbaac270`, not an assertion that all v8 work is complete. The live coordination ledger and parallel branches may advance independently. No task status, canonical ledger or release gate was changed by this audit.

The machine-readable [99-row inventory](TASK_EVIDENCE_AUDIT.json) contains each exact task title/gate, execution owner, source/test/report paths, 444 SHA-256 anchors, remaining acceptance and the three source-specific recorded atomic acceptances. The verifier reads **immutable Git objects**, including the pinned ledger and evidence index; a dirty/sparse checkout or a newer live ledger cannot substitute for those objects.

## Inventory and acceptance are separate

| Implementation inventory | Rows | Meaning |
| --- | ---: | --- |
| Present | 30 | Relevant implemented behavior located; remaining acceptance still applies. |
| Partial | 46 | Parts/prototypes exist; required breadth or integration is incomplete. |
| Metadata/process | 13 | Recovery, evidence, integration or release work rather than a standalone gameplay feature. |
| Not located in this snapshot | 8 | Specific implementation not located in pinned 4b; does not describe later branches. |
| Design only | 2 | Proposed content/design, without implemented gameplay acceptance. |

| Verification assessment | Rows | Meaning |
| --- | ---: | --- |
| Recorded atomic acceptance | 3 | Retain only the existing task-specific acceptance at its recorded source. |
| Current scoped regression | 22 | Relevant behavior is covered by the source-matched core run; not whole-task acceptance. |
| Partial recorded evidence | 33 | Recorded subsets do not cover the complete task criterion. |
| Validation missing | 29 | No complete matching acceptance supplied by the retained evidence. |
| Specific evidence not located | 10 | Specific required proof/design approval not located in the audited snapshot. |
| Known unresolved regression | 2 | Two task rows retain the same observed sky/LOD issue; not two invented independent bugs. |

Pinned ledger states remain **3 completed / 9 in progress / 87 pending reverification**. These counts are historical source states, not the newer coordinator's live assignment counts. `pending_reverification` does not mean that implementation is absent. Neither the implementation counts nor aggregate tests are a completion percentage.

The three retained atomic records are S09-T02 (`fd8950fa4be0aad8a97daa87220226b44c02311e`, reference UFO engines), S11-T01 (`388eb845099ff4d400759f0e01e42bee1181322e`, distinct flight-enemy classes), and S11-T03 (`d9d0fd82cbd060dbe95d432e501042eb6de44f4d`, 128 active / at least 96 moving / two damaging roles). Their sources are checked against the pinned evidence index, including nested `runs` records. This audit does not replay their clients or accept their entire stages.

## Independent core-bundle verification

| Identity | Exact value |
| --- | --- |
| Full server source | `4fede1d762dc0619c9f894518afcedef6bd2a4c1` |
| Native observer source | `8e5111b2fbe80d7fa8a99ee7b8884188bb0211f1` |
| Game SHA-256 | `328307bc4b8db3b41d47f4b1b2bce8de9eb03eb0ccde78d46b9d33dcda0c02a7` |
| Observer SHA-256 | `61050740a522323fd34394aea7086bf2977ec5b27a87b369213cbbbe4e65db7f` |
| [Archive](EVIDENCE/player-weapons-8e5111b.zip) SHA-256 | `493a17c77edac806ef940adaa7e7ef0e4fb4c8faa98bf3510959623a81e4f734` |
| Fabric metadata | `drmd`, `1.1.35-ufo-dev.1`; separate observer `drmd_cubiccheck` |

The verified ZIP has **290 members: 289 hashed payload files plus `MANIFEST.json`**. The auditor checks the outer hash, canonical unique member paths, ZIP CRCs, exact manifest coverage and every payload digest. It checks actual JAR bytes and Fabric metadata, all **1414 committed build-input hashes in each of the two JSON build stamps**, the source-clean native launch receipt, and the game/observer destinations under `mods/`.

The full-to-native source delta is exactly `src/cubiccheck/java/com/terminaldetector/drmd/gate/NativeMountedWeaponCheck.java`. Native-to-4b changes are seven documentation/evidence paths only. The verifier rejects production changes between either scope and the run being cited. The full-run observer can legitimately differ from the later native observer; game bytes must match both stamps.

Recorded XML contains **758 actual unique unit test cases and 376 actual GameTest cases**, with no failed/error/skipped cases. The built GameTest manifest matches all **60** committed entrypoints, rather than a focused suite. These are independently read historical results, **not new game executions**.

The native bundle records five scenes: `connected-off`, `laser-fire`, `rocket-fire`, `fire-off-reversed`, `bearing-detached`. Checks require actual use/sneak keys, ordinary physical-gun ownership, preserved released spin, no fixture-supplied joints/shots and no operator-follow teleport. Recorded laser/rocket/off-tick/draw/pixel minima and finite muzzle/launch/anchor error bounds remain intact. Frame hashes match the recorded five-frame review; PNG chunk CRCs, dimensions, bounded decompression, row lengths and filters are validated. This is not a new visual review or a substitute for one. Fresh core frames still report sky/LOD black wedges in [PLAYER_BUILT_WEAPONS.md](PLAYER_BUILT_WEAPONS.md).

The first verifier attempt failed with:

```text
AUDIT FAIL: required evidence member missing: work/mounted-mechanisms/current-game.sha256
```

That first log is retained under `work/audit/first-proof-check.log` in the audit worktree. The original collector omitted the redundant `.sha256` text receipt. The archive does include the full `current-build.json` and `native-final-build.json` input/JAR receipts; those remain mandatory and are checked against Git objects and artifact bytes. The text receipt is explicitly reported absent and checked if supplied. No marker assertion, expected test count, source requirement or artifact digest was weakened to repair that unsupported receipt assumption.

## Reproduce without launching Java

Run in a local repository containing the pinned Git objects. `--evidence-root` can point to the canonical checkout so the large archive is not duplicated into a sparse worktree. This needs Python 3.9+ and local Git, with no Python packages, network, Gradle or Minecraft execution.

```sh
python3 -m unittest discover -s scripts/v8 -p 'test_audit_task_evidence.py' -v
python3 scripts/v8/audit_task_evidence.py --repo . --matrix docs/v8/TASK_EVIDENCE_AUDIT.json --evidence-root /workspace/ungravity-v5/ufo-body-physics --out work/audit/task-evidence-result.json
```

**Result: 19 Python tests passed; the real archive audit passed.** Negative fixtures cover missing/duplicate rows and owners, changed anchors/ledger/index, invented atomic sources, acceptance promotion, gameplay deltas reusing old test runs, outer/member hash disagreement, ZIP traversal/duplicates/unmanifested files, wrong build inputs/receipts/classpath, skipped or duplicated XML cases, false suite totals, focused manifests, stale launch source, native bypass/NaN/missing scenes, corrupt PNGs and invalid compressed rows. Synthetic fixture results never count as gameplay tests.

The verifier output records `gameplay_tests_rerun=false`, `performance_accepted=false`, `release_accepted=false`, `ledger_transitions_performed=false` and `task_acceptance_inferred=false`. Local outputs are `work/audit/task-evidence-result.json`, `work/audit/proof-check-final.log` and `work/audit/python-tests-final.log`.

## Cleanup, performance and concurrent work

A read-only run of the existing `scripts/audit_resources.py` found **290 models, 129 item-resource files, 0 static graph errors and 9 duplicate-content groups**. This verifies static resource syntax/references only. It does not identify the user's six red-marked slots, establish registry/atlas/render correctness or justify deleting a duplicate. No asset/code deletion, save migration or evidence cleanup was performed. S00-T04/S15-T01/S15-T02 still require an actual registration/use/save-origin map and parity before removal.

[BENCHMARKS.csv](BENCHMARKS.csv) contains short scoped MSPT observations and `UNKNOWN` FPS values; its eight-column format lacks the full seed, exact modlist/JAR, coordinates, heap, active cubes, draw calls and complete hardware/profiling protocol required by S16. Representative GL 3.2 Mesa/llvmpipe runs are not physical Intel HD 4400 measurements. This environment did not expose `/dev/dri`; no physical-GPU benchmark or 256/512/1024 swarm/long endurance acceptance is claimed.

The PS1 lane is outside this pinned tree. At audit handoff the parent reported a separate combined candidate `a923442fc3ef04b0b1c089a79883666670bb8146` with 763 unit / 376 GameTests passed and its first native world-grid check failed at 0.9697916667 against 0.97; a full-resolution cockpit crossbar intersected the sample region. That is a separate candidate observation, not a PS1 acceptance or a finding that later PS1 code is absent. The render owner/coordinator maintains its current state and rerun. Combat, cubic, physics and content increments are likewise reviewed and integrated separately; this inventory cannot promote later candidates.

Execution owners below are review/implementation responsibility, not acceptance. `audit`, `world`, `render`, `physics`, `combat`, `content` refer to the respective isolated agent lanes; `root` owns integration and generic mechanisms. Per-task explicit exceptions are in the JSON. Runtime/build slots and canonical ledger transitions remain centrally coordinated.

## All 99 task scopes

Codes: implementation **P** present, **p** partial, **M** metadata/process, **N** not located in pinned source, **D** design only. Verification **A** recorded atomic acceptance, **C** current scoped regression, **R** partial recorded evidence, **V** validation missing, **N** specific evidence not located, **F** known unresolved regression. Detailed source/test/report hashes and full task titles remain in the JSON.

### S00 — History & executable truth

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S00-T01 | audit | M / R | Live worktrees are active; historical captures exist but a new synchronized all-tree dirty-source preservation pass is still required. |
| S00-T02 | audit | M / C | The8e core archive has exact full/native source and game bytes; the historical user1.1.14 runtime remains unlinked and active experimental processes are separate. |
| S00-T03 | audit | M / R | Known PR9/recovery refs and selected regressions are preserved; not every pre-Oct26 effect has a current before/after parity mapping. |
| S00-T04 | audit | p / V | A low-reference candidate is not dead code; dynamic registration and save migration still need class-by-class KEEP/ADAPT/REMOVE_AFTER_PARITY decisions. |
| S00-T05 | audit | M / C | Ledger and hashed bundles exist; continue exact integration ownership and do not infer all99 acceptances from aggregate tests. |
| S00-T06 | audit | M / R | Current scope and replay commands exist but full-stage next-agent instructions and reproducible inputs remain distributed across reports and archives. |

### S01 — Reproduce and instrument baseline

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S01-T01 | physics | P / R | Several real mount/death/release lifecycles are tested; the exact original user lava/proxy world and all ownership paths are not current full acceptance. |
| S01-T02 | world | p / R | Native high/low volume and restart subsets exist; the complete fixed-seed original break/fast-flight route remains a dedicated repro gate. |
| S01-T03 | render | P / R | Real mirror geometry/FBO paths exist; exact original slab diagnostics and current1/2/4-mirror compatibility matrix are not a complete accepted repro. |
| S01-T04 | physics | P / R | Camera/pose adapters and real-client harness exist; all required torch axes and full hand rotation sweeps need matched current captures. |
| S01-T05 | world | p / R | Safer edit/BE paths exist; reproduce the exact negative-Y spawner-on-air/DUMMY origin rather than assuming aggregate no-crash tests identify its cause. |
| S01-T06 | audit | p / V | Short software-renderer MSPT windows exist; matched sim8/24 JFR, heap and complete percentile baseline are not supplied. |

### S02 — Choose actual CubicWorld backend

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S02-T01 | world | P / R | Exact external BCC dependency is pinned in native inputs; retain ARR licensing boundary and record its full current metadata/license audit. |
| S02-T02 | world | P / R | Real native Cubic runs are established, but copied lab-world runs do not automatically prove every clean new-save/preset path. |
| S02-T03 | world | P / R | Current real Y2200 demonstrates non-vanilla backend; complete signed16cube bounds and old snapshot comparison still require the world owner acceptance map. |
| S02-T04 | world | P / C | Signed loaded-only adapters have current server coverage; owner must map every required operation and backend boundary before atomic closure. |
| S02-T05 | world | p / R | Real closed-world persistence subsets exist; complete fresh cubic fluid/light/BlockEntity parity is not inferred from body or drone restart proof. |
| S02-T06 | world | p / R | GL3.2 llvmpipe is representative API fallback, not the user's physical IntelHD4400; beta/Sodium/driver combinations remain bounded observations. |

### S03 — Migrate world gameplay and structures

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S03-T01 | world | p / C | Current edit tests cover guarded paths; remaining direct legacy world edits and fluid/explosion call sites need complete migration audit. |
| S03-T02 | world | p / V | Height-aware modules exist but all biome/structure legacy column assumptions are not exhaustively ported or parity-accepted. |
| S03-T03 | world | p / R | Historical DUMMY/spawner logs identify real corruption; safe adapter presence is not complete origin tracing and clean cold-world proof. |
| S03-T04 | world | p / R | High/low and true cold-return subsets exist; the exact negativeY/1024/1640/2150 matrix remains broader than current mechanism proof. |
| S03-T05 | world | p / V | Legacy activation is guarded; old supplemental stores can be intentional. Remove only after an explicit use/parity/migration map. |

### S04 — Hybrid/NoCubes meshing and LOD

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S04-T01 | render | P / C | Material policies have source/current server coverage; all specified tree/fracture/transition classifications need visual/collision acceptance. |
| S04-T02 | render | p / V | Smoothing prototypes exist; feature-aware natural tree/terrain silhouette and cost must be demonstrated rather than equated with full NoCubes parity. |
| S04-T03 | render | p / V | Round/microdamage and bounded mesh code exists; watertight adjacent native cube seams and rounded mixed cavities remain acceptance work. |
| S04-T04 | render | p / C | Selected visible/collision geometry shares math; all nav/fluid/tunnel passage cases and native mesher combinations are not fully validated. |
| S04-T05 | render | p / V | Existing heightfield/surface LOD is not proof of true3D BetterDistantHorizons integration; isolated external compatibility remains open. |
| S04-T06 | render | p / F | Fresh core frames still show sky/LOD black wedges; ground continuity and smooth decay remain unresolved, cause not identified by this audit. |

### S05 — PS1 Hybrid presentation profile

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S05-T01 | render | N / N | No presentation target in pinned4b tree; separate PS1 branch now implements it, but that candidate and its native gate are distinct from this snapshot. |
| S05-T02 | render | N / N | Configurable PS1 vertex snapping/color quantization/dither/affine warp not located in audited tree; initial resolution-only external work does not close this. |
| S05-T03 | render | p / V | Generic altitude sky/atmosphere exists; PS1 fog plus ground/asteroid fade continuity is not a completed profile feature. |
| S05-T04 | render | p / V | Native UI/viewmodel code exists; low-resolution-world/full-resolution-overlay split is under separate PS1 branch verification. |
| S05-T05 | render | N / N | No profile switch in pinned4b source. Separate candidate's wall hash/flight flag is representative, not universal physics/cache/world invariance. |
| S05-T06 | render | N / N | Full Native/PS1/Debug plus GL3.2 visual matrix is not accepted in audited source; external branch's current client result must be recorded independently. |

### S06 — Core gravity and player 6DoF

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S06-T01 | physics | P / C | Shared math/adapters and server checks exist; universal consolidated coordinate ownership still needs full cross-subsystem acceptance. |
| S06-T02 | physics | P / R | Recorded real capture/death/release proof is scoped; original user proxy/lava repro and all normal exit conditions remain a distinct gate. |
| S06-T03 | physics | P / R | Camera/viewmodel fixes have subsets of proof; full pitch/yaw/roll all-angle torch/mount/reload image regression remains open. |
| S06-T04 | physics | P / C | Deterministic identity/index tests are current; dimension lifecycle and overlapping-field native acceptance need explicit owner mapping. |
| S06-T05 | physics | P / C | State-transition gate is implemented/tested; actual repeated player feedback under every field transition has not been independently runtime-audited here. |
| S06-T06 | physics | P / R | External-transfer and saved-world subsets are proven at recorded SHAs; all death/teleport/unload/local-world combinations remain wider acceptance. |

### S07 — Universal 6DoF for all mob families

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S07-T01 | physics | p / C | Explicit supported families and fallbacks exist; C1-C7, aquatic/bee/villager/spider/boss breadth is not universal current parity. |
| S07-T02 | physics | p / C | Selected surface pursuit/ground adapters work; complete local-frame wall/ceiling pathfinding for all families is not implemented acceptance. |
| S07-T03 | physics | p / C | Some look/attack adapters have current server coverage; full family-specific sight/raycast/attack and complex boss behavior remain. |
| S07-T04 | physics | p / C | Spatial indexes/budgets are present; universal gravity clusters and distant simulation/interest tiers need explicit integration and measurements. |
| S07-T05 | physics | p / V | Representative real-client harness exists; cross-family torch rotation/contact/cold save-load matrix is not established for all C1-C7 classes. |

### S08 — Articulated ragdoll and destructible bodies

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S08-T01 | physics | P / R | Solver lineage is documented; reconcile each historic collision effect against current source before declaring the entire old ragdoll work valid. |
| S08-T02 | physics | P / C | Current numeric/contact tests cover several joint/effective-mass cases; complete articulated multi-family limits and real impacts remain broader acceptance. |
| S08-T03 | physics | p / R | Authored typed wreck/death/restart proof exists; universal ragdoll wall CCD and player/drone multi-impact duplication/stress remain open. |
| S08-T04 | physics | p / V | Basic life/sleep/removal code is not a measured128-simultaneous-death aggregation/cleanup acceptance. |
| S08-T05 | physics | p / V | Related physics and weapon tests exist; combined local-gravity articulated rail-hit/block-contact fixture is not located as complete current runtime proof. |

### S09 — BlockBody / UFO boarding and reactor

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S09-T01 | root | M / R | Active UFO lineage is preserved; full Sol/local-work reconciliation remains audit scope, not inferred from the latest gameplay increment. |
| S09-T02 | root | P / A | Retain recorded atomic reference-UFO engine/six-axis acceptance; arbitrary assemblies, wholeS09/G2 and release remain open. |
| S09-T03 | root | P / C | Current generic lever-fired blocks pass exact scoped proof; automatic generic aiming, world anchoring, economy and full module lifecycle remain open. |
| S09-T04 | root | P / R | Boarding/capture/release and recorded hot restart work; complete module/control/portal-group/multiplayer lifecycle is not closed. |
| S09-T05 | root | p / C | Damage and bounded fracture exist; a complete playable sabotage/reactor/escape-window loop is not proven. |
| S09-T06 | root | P / R | Actual world reload/island cold-return subsets are proven; generic hot mechanisms, damage/legacy/portal migration and optional isolated VS gate remain open. |

### S10 — Weapons, cluster, drill and building

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S10-T01 | combat | P / R | Native weapon/atlas fixes exist; full held/inventory all-angle/normal/6DoF/current profile visual matrix remains. |
| S10-T02 | combat | P / C | Current server tests cover related behaviors; all three distinct gun modes and ballistic rail accuracy need explicit matched native acceptance. |
| S10-T03 | combat | P / C | Structured bounded ordnance code/current lifecycle tests exist; exact first-person staged carrier/split/impact visual workflow is not fully accepted. |
| S10-T04 | combat | p / C | Strong/weak drilling and drop limits exist; full visible thermal rim/circular accessible tunnel/light/fluid parity remains open. |
| S10-T05 | combat | P / C | Actual Survival/resource/permission tests are current; every construction mode and batch invalidation path still needs complete mapping. |

### S11 — ORB/ROCKET/JET swarms

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S11-T01 | combat | P / A | Retain exact recorded independent-class atomic acceptance; whole bestiary/performance/AI lifecycle remains separate. |
| S11-T02 | combat | P / R | Current source and recorded mixed128/passages work; dense/bidirectional/mixed-size/moving-hull behavior and group performance remain open. |
| S11-T03 | combat | P / A | Retain recorded128-active96-moving/two-role atomic proof; current native rerun and full stage/performance are not implied. |
| S11-T04 | combat | p / V | Existing bounded128 work is not measured256/512/1024 physics/AI/render LOD with profiler and memory. |
| S11-T05 | combat | P / R | Real damaging paths and aperture recovery exist; complete destruction/moving-hull/dense-role projectile/path/target handling remains open. |
| S11-T06 | combat | p / V | Destruction/debris/restart pieces exist; a settlement siege with bounded cleanup after actual save/reload is not established. |

### S12 — Survival threat and minimal armament economy

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S12-T01 | content | N / N | Specific persisted search-hunt-siege-cooldown faction threat director not located; existing aggro/bombardment goals are not this state machine. |
| S12-T02 | content | p / V | Rocket actors exist; coherent two-infantry/base encounter with telegraph/defense/escape balance is not accepted. |
| S12-T03 | content | p / V | Energy/items/loadouts exist; a complete minimal player/plasma/anti-missile resource progression is not located as an accepted gameplay loop. |
| S12-T04 | content | N / N | Specific base repair/loss/anti-softlock rules and persisted recoverability system not located; generic resource checks do not substitute for it. |
| S12-T05 | content | N / N | No complete measured day-night hunt event with player decision branches located in the audited source/evidence. |

### S13 — World architecture: core to Citadel

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S13-T01 | content | p / V | Deterministic level/band pieces exist; complete physical planetary zones under actual cubic backend remain world-design parity work. |
| S13-T02 | content | p / V | Old lab/complex generators are prototypes, not accepted permission-safe cubic authored laboratories/shafts. |
| S13-T03 | content | p / V | Legacy station/island and surface LOD code is not verified near/mid/far volumetric asteroid/station gameplay. |
| S13-T04 | content | p / V | Ring/Citadel-related scaffolds exist; an approved coherent ruined-ring/Citadel blockout and flight-route proof is not complete. |
| S13-T05 | content | p / F | Fresh sky/LOD wedges remain and legacy junk requires origin mapping; atmosphere/ground continuity is not accepted. |
| S13-T06 | content | p / V | Layer helpers do not prove compact mission pacing and physical traversal across the complete authored world. |

### S14 — Factions, Shapes and mission framework

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S14-T01 | content | D / N | Five tech tiers remain proposed candidates requiring author review; no approved gameplay tier assignment is inferred. |
| S14-T02 | content | N / N | Distinct armed physical Shapes infantry/squad implementation without vanilla teleport is not located. |
| S14-T03 | content | p / V | A flying Seeker exists, but a distinct teleport archetype with counter-play and reassigned infantry teleport is not verified. |
| S14-T04 | content | p / V | Mounted physical weapons exist; authored heavy infantry/operator with shoulder rockets on machinery is not a complete combat archetype. |
| S14-T05 | content | D / N | Mission concepts are specified; executable Strife-style infiltration/boarding/convoy contracts and branch acceptance are not located. |

### S15 — Cleanup / asset and UI integration

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S15-T01 | audit | p / V | Static IDs/use candidates exist; six red-marked screenshot slots are not mapped with enough evidence for deletion. |
| S15-T02 | audit | p / V | Save-aware migrations exist for selected systems; no blanket approved dead-item/model/texture removal map is established. |
| S15-T03 | render | P / R | Separate offscreen passes/frame fallback exist; current1/2/4 mirror and portal/PS1/Fabulous matrix remains open in render ownership. |
| S15-T04 | content | p / V | Activation guards do not classify every visible sky object; retain approved asteroid/ring concepts and remove only proven origins. |
| S15-T05 | render | P / R | UI exists and Native core frames are inspected; tooltip/scale/PS1 readability matrix and debug-line audit remain incomplete. |

### S16 — Full performance and regression testing

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S16-T01 | audit | M / C | Exact4f/full8e Native game equivalence is independently checkable; full all-subsystem rendercheck/release gate is not satisfied by that scoped mechanism pass. |
| S16-T02 | audit | p / V | Existing CSV has short MSPT subsets and UNKNOWN FPS; required context/heap/cubes/draw calls and32-1024 matrix are incomplete. |
| S16-T03 | audit | p / R | Recorded high-altitude motion exists; dedicated full-mesh/camera/LOD pressure under roll/fast vehicle remains open and visible wedges remain. |
| S16-T04 | audit | p / V | Short bounded fixtures are not hours-long city/dungeon explosion/ragdoll128/debris/cold-save endurance proof. |
| S16-T05 | audit | M / R | Known beta/shutdown/software-renderer limits are retained; full platform-specific rollback and real hardware matrix remain incomplete. |

### S17 — Integration, handoff and release candidate

| Task | Owner | Impl / verification | Remaining acceptance |
| --- | --- | --- | --- |
| S17-T01 | audit | M / R | Isolated branches preserve work; latest parallel candidates are not all reviewed/integrated/accepted in pinned4b source. |
| S17-T02 | audit | M / C | Latest core ledger and source/JAR evidence are coherent; final all-stream SHA/ledger reconciliation is still coordinator work. |
| S17-T03 | audit | M / V | No accepted1.1.35 release; same-candidate all-gate modlist/test-save package remains blocked by open gameplay/visual/performance work. |
| S17-T04 | audit | M / R | Reports state scoped passes/failures honestly; this99-row audit adds coverage but final P0/P1 acceptance report is not automatically complete. |
| S17-T05 | audit | M / R | Core archive includes patches/replay helpers; full integrated reproducible candidate/backlog remains to be finalized after all required gates. |
