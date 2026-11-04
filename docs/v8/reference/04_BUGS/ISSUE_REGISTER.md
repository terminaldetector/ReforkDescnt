# CURRENT BUG REGISTER / USER EVIDENCE CROSSWALK v8
**Sources:** Bugs and shit (19:29), original screenshots 26.10, latest.log, latest(1).log; older issue register preserved under `09_LEGACY_V5/`. Severity based on gameplay failure, not count of lines.

| ID | P | Source | Symptom | Owner/Gate | Evidence needed |
|---|---|---|---|---|---|
| UG-P0-01 | P0 | Video ~02:25–02:30 + USER | Forced destruction of intermediary entity with lava to exit 6DoF | physics / G2 | exact proxy mount graph; normal dismount / death / reload |
| UG-P0-02 | P0 | Video ~09:40–10:26 | `fell out of the world` while exploring layered volume | cubic / G1 | bounds, worldgen, collision tracing |
| UG-P0-03 | P0 | latest(1).log | Server thread stalled 193148ms (3862 ticks behind) | world/AI perf / G0–G6 | JFR/spark/async-profiler; per-stage MSPT |
| UG-P1-04 | P1 | LOG latest(1) 16:29:50 | mob_spawner BE attempted on `air` | worldgen / G1 | position 334,-21,496 and generator trace |
| UG-P1-05 | P1 | logs | 33/100 invalid DUMMY BE by session (all negative Y) | worldgen / G1 | full NBT source and state transition |
| UG-P1-06 | P1 | screenshot #1 + mirror logs 16:53 | Mirror slab missing / no render target | render / G3 | surface rendered even if reflection unavailable |
| UG-P1-07 | P1 | screenshot #2 | landscape geometry seams/holes | cubic/LOD / G1–G3 | repeat same seed, compare loaded cube boundaries |
| UG-P1-08 | P1 | screenshot #3 | tall rectangular geometry / towers in world | existing owner / G1 | ALREADY IN PROGRESS; do not duplicate work |
| UG-P1-09 | P1 | screenshot #4 | gravity torch results in incorrect camera visibility | physics/render / G2 | localGravity + eye + collision traces |
| UG-P2-10 | P2 | screenshot #5 | junk objects in sky (not intended asteroid art) | worldgen cleanup | identify origin before deletion |
| UG-P1-11 | P1 | screenshot #6 | arm/held model drifts across screen during 6DoF roll | viewmodel / G3 | roll/pitch/yaw sweep; no double transform |
| UG-P1-12 | P1 | VIDEO + USER | upper ground forcibly disabled / voxel horizon off | Cubic+LOD / G1–G3 | high-altitude terrain rendered, no hidden workaround |
| UG-P1-13 | P1 | video HUD Y≈2150 | Layer resolver `OBLIVION` out of expected area | world bounds/labels | compare actual preset/classification |
| UG-P2-14 | P2 | video chat | `Local gravity active` spam | gravity field | edge-trigger and debounce |
| UG-P1-15 | P1 | USER + VIDEO | Guns invisible in some control modes | weapon/viewmodel | first-person native/6DoF correct |
| UG-P2-16 | P2 | USER + screenshot #8/9 | inventory models repeated/incorrect icons | asset cleanup | registry/model/texture map |
| UG-P2-17 | P2 | screenshot red circles | six unwanted registered items | cleanup | map IDs first, save compatibility |
| UG-P1-18 | P1 | USER | cluster projectile spray incorrect | weapons | first-person structured split visible |
| UG-P1-19 | P1 | USER | drill melt geometry missing/incorrect | smooth mesh/physics | weak cubic vs strong circular working |
| UG-P1-20 | P1 | USER | construction tool resource cost/batch issues | building | survival accounting and no ghost placements |
| UG-P1-21 | P1 | USER | default hybrid render not enabled | settings | default ON, preserve overrides |
| UG-P1-22 | P1 | USER | expanded physics not default | settings | default ON, preserve overrides |
| UG-P1-23 | P1 | USER | all mobs lack universal 6DoF under local gravity | entity gravity | ground/flying/aquatic/anchored baseline tests |
| UG-P1-24 | P1 | USER | ragdoll contact and collision correctness unknown | solver | actual wall/joints/impulse fixtures |
| UG-P1-25 | P1 | VIDEO + USER | UFO/BlockBody current status fragmented | agent lineage | no claim complete without latest JAR |
| UG-P1-26 | P1 | USER | many drones/AI separation/performance not proven | AI perf | 128 active/96 moving; tiered benchmarks |
| UG-P2-27 | P2 | video | HUD cropped/too dense/debug strings | GUI | 720p and higher, GUI scales |
| UG-P2-28 | P2 | video | fire overlay may obscure gameplay | UX/physics | distinguish vanilla overlay from stuck field |
| UG-P1-29 | P1 | design | ship/portals transforms and safe teleport not proven | portal/physics | teleport/velocity/rotation save/load |
| UG-P1-30 | P1 | design | collision mismatch smooth caves and cubic blocks | noCubes/physics | genuine round tunnel traversable |
| UG-P1-31 | P1 | all logs | `simulation distance 24` heavy, workload unprofiled | perf | baseline 6/8/12/24 same world |

## One screenshot ≠ one root cause
Mirror missing may be block model, FBO, scissor/clip, or culling. Tall poles could be intended unmasked structures or generator bug. Sky debris may be deliberate orbit junk or a legacy duplicate. Treat user classification as authoritative for WHAT needs correction, instrumentation as authoritative for WHY.

## Source notes
- Two copies of `2026-10-26-1.log.gz` byte-identical; `2026-10-26-2.log.gz` uncompressed equals `latest.log`. Do not count a duplicate as an independent event.
- `latest(1).log` 41 stalls, 100 DUMMY, 21 mirror messages total (includes one `none in range`). Main runtime has no Immersive Portals and no Distant Horizons in that session.
- The third screenshot is already in progress per user's statement; this task is **RECONCILE**, not automatic duplicate implementation.
- Original reports under `09_LEGACY_V5/` still apply unless explicitly superseded by user or a verified test.
