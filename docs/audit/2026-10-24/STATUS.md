# Video audit work record

The user requests concrete fixes following the supplied video audit. The installed video
JAR version is unknown (user confirmed 25 October). This report is contextual evidence,
not proof that every hypothesis affects the current recovered branch. Baseline under review:
93baf2a / published 1.1.13. Preserve the original recovery branch and PR9.

| IDs | Investigation / acceptance | Current finding |
|---|---|---|
| UG-001,009,021 | H, dismount, death/respawn, unload and reconnect restore control | CONFIRMED source defects: H does not dismount; ship tick re-enables disabled flight. Ship assigns blocks/second to per-tick velocity. Fix + regression tests in progress. |
| UG-002,007 | Finite lower-world traversal and recovery | INVESTIGATE actual dimension (-784..1888) and streamed lower geometry; snapshots are not unbounded cubic gameplay. |
| UG-003,005,006 | Horizon ON, DH ownership, altitude/roll/speed matrix | INVESTIGATE projection, depth and settings; white plates are not automatically terrain holes. |
| UG-004 | Explicit outside-world HUD classification | CONFIRMED fallback classifies every Y above the column as Oblivion. |
| UG-008 | Fire/lava obscuration and safe control recovery | INVESTIGATE; vanilla fire is not itself a mod rendering defect. |
| UG-010,011 | Gravity-frame recovery and transition-only notification | Existing !wasActive guard is insufficient for repeated field-edge capture/release. Investigate debounce and state cleanup. |
| UG-012,013 | Modal HUD and scaled settings layout | INVESTIGATE 720p / GUI scales with actual client frames. |
| UG-014 | Distinct item/egg icons | INVESTIGATE existing item models and textures; preserve item behavior. |
| UG-015 | Player-facing combat messages | INVESTIGATE raw ordnance identifiers; developer details belong behind diagnostic flag. |
| UG-016,017 | Bounded effects and clear central aim | Existing 1.1.13 checks cover weapon FOV/bank/vertical; add video-specific FX and menu cases. |
| UG-018,019,020 | Real swarm movement, bounded work, cleanup and measured timings | Existing 128-drone proof is not a 1024-drone performance claim. Add actual load/cleanup measurements. |

No item is closed by a source change alone. Record fresh unit/GameTest/client outcomes for
its exact revision. Preserve the first failure and mark infrastructure blockers separately.

On resume, ordinary git push lacks a credential helper, and the standard gh helper with the
injected GH_TOKEN is rejected as Invalid username or token. The user is checking the cloud
GitHub connection. Local commits/patches are not claimed as GHA checkpoints until pushed.

## Package A implementation (local, not yet certified)

- H-off clears authority before dismounting, clears held thrust/dash/hook/roll/velocity,
  restores world-up and Creative flight permission, and cannot be rearmed by a parked hull.
- Native H-on uses player 6DoF without manufacturing a Pyro entity. Existing physical
  vehicles remain explicit interactions; no replacement flight model or new Pyro asset.
- Ship velocity is converted from blocks/second to blocks/tick at the entity assignment.
- Dead/spectator pilots cannot retain/arm flight; death, respawn and disconnect clear control.
- In-flight input received while disabled/dead is ignored rather than rebuilding stale state.
- Three GameTests exercise actual H transitions, mount graph and ship velocity assignment.

First local check: `gradle test compileGametestJava --offline` stopped before compilation:
Fabric Loom 1.10-SNAPSHOT is absent from the local cache. See
`/workspace/audit-evidence-2026-10-25/first-local-check.log`. No Java/GameTest/client pass
is claimed. GHA remains pending restoration of the cloud GitHub credential.

## Package C implementation (local)

Client gravity mirrors now have their own table; a late client off packet cannot erase the
integrated server's authoritative field. Collider and interaction/camera eyes now use the
same local-up frame, with upright geometry restored on release. Two GameTests exercise
state isolation and the actual player bounding box/eye methods; real-client confirmation
is still required before certification.

`GravityNoticeGate` emits only on source transitions and debounces field-edge/source jitter
for 100 ticks. Notifications use the action bar and RU/EN translations. Its four real JUnit
regressions were compiled with Eclipse ECJ 3.40.0 and run via JUnit console 1.11.3 locally:
4/4 PASS, XML at `/workspace/audit-evidence-2026-10-25/notice-junit/`.
This is not a substitute for compiling the Fabric module or testing rendered camera behavior.

## Package B — finite height / emergency recovery (local)

The current source declares -784..1888, unlike the older README cited by the video audit.
The cubic subsystem explicitly stores snapshots and does not implement unbounded physical
vertical travel. HUD classification now checks the running world's actual bounds and shows
Outside world above/below the Overworld instead of pretending Y=2150 is authored Oblivion.
Ordinary above-column flight is not capped.

A START_SERVER_TICK safeguard for the advanced Overworld remembers clear, fluid-free player
positions and restores one before the lower void, clearing outward momentum and enabling
existing free 6DoF. It is an emergency recovery path, not a claim that lower worldgen is fixed.
The Nether floor remains diggable granite and is still being investigated with streaming.
A GameTest exercises the actual recovery and HUD resolver. Four standalone JUnit boundary
regressions PASS locally (`bounds-junit/`); Fabric/GameTests/client evidence remains pending.

## Package D — modal UI and player messages (local)

Flight HUD and 3D cockpit overlays now stop beneath modal screens. Settings keyboard hints
wrap, button columns adapt to narrow scaled windows, single-column content remains scrollable,
and long toggle/slider labels have full tooltips. Real screenshots at GUI scales are pending.
Raw ordnance enum/Y output is moved to the logger's DEBUG level; localized action-bar launch
messages replace it. The same source review found another real blocks/second inheritance bug
in BombBayItem, fixed at the entity handoff and covered by an actual-use GameTest.

Asset inspection found four cluster payload items sharing one icon; several ancestor/super
weapon pairs also share models. Spawn egg/item UX remains open until actual model changes and
an inventory rendering check; none is silently relabelled as fixed by the UI changes above.

## UG-014 inventory assets (local)

Sixteen duplicate/indistinguishable inventory meshes were given distinct silhouettes or
class markers: five cluster bays/payloads, energy/shield pickups, portal stabilizer and eight
ancestor/super weapon pairs. These are JSON voxel models using vanilla block materials;
weapon behavior and world viewmodels are unchanged. `scripts/audit_item_models.py` regenerates
them. JSON and element bounds passed local validation. Existing spawn eggs already have
fourteen different model resources; actual perceptual differentiation still needs the fresh
inventory screenshot scene, so the audit item remains open until that evidence exists.

## UG-003/005 horizon projection (local)

Source inspection confirmed a projection error: the cached mesh compressed vertices relative
to its previous eye, then translated those compressed positions in uncompressed world units.
Those operations do not commute. The vertex buffer now retains true offsets and a registered
horizon shader rebases to the current camera before radial compression. Source coverage includes
the old inner disc; the current camera's terrain exclusion and near fade happen in the shader,
so movement cannot reveal a stale hole where the prior eye excluded geometry.

World epochs prevent an in-flight background build from publishing a previous world's mesh
following disconnect or seed change. Vertex storage remains bounded at the existing mesh budget.
This is a source fix requiring the ON/altitude/bank/motion real-client matrix; it is not certified
by shader JSON validation or by the previous 1.1.13 screenshots. Actual DH interop is untested.

## Further control authority and swarm findings (local)

`DescentPlayerData` also shared one UUID object between integrated server and client. A late
client SyncPayload could therefore overwrite authoritative enabled/resources state, not only
its own mirror. Server and client stores are now separate, with an actual player-state GameTest.
Dimension changes clear old foot/body/input/cache state while preserving deliberate portal flight;
non-death End respawn preserves flight intent, ordinary death respawn remains off.

Perfectly coincident boids were explicitly skipped by the separation loop, leaving no escape
vector. A deterministic antisymmetric pair direction now resolves zero-distance neighbours,
without widening the 32-neighbour / 96-candidate work budget or changing role exclusions.
Three standalone JUnit regressions pass over 128 identities, and an actual-compute GameTest
covers coincident drone entities. These are not measured 128/1024-client movement results.
