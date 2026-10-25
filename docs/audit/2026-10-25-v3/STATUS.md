# Video audit v3: source implementation ledger

Input ZIP SHA-256: `3fa35c5670e409f879ff6db7f7211270574f182e15a5a3abf7eed34b80025270`.
Received 25 October 2026; same 19:29 video, expanded requirements UG-022..031.
The recorded JAR version remains unknown (user confirmed); this source starts from
`a39e91d2874d6512a4507d364212cb0abf227b37`, version1.1.14-audit.1.
Attached requirements are context; the user's latest request controls the work.

The user now says GitHub limits are exhausted and asks to work here with source code.
Use local commits and downloadable source/bundle/patch/report backups. Do not push or
trigger Actions during this pause. These backups are not GHA checkpoints or tested JARs.

| ID | Scope | Source status / acceptance still required |
|---|---|---|
|022|Mirrors|OFF scans/target lifecycle patched; exact mirror clipping and client proof remain open.|
|023|Portals|Whole-body exit preflight patched; traversal/foot-frame/pair reload and see-through proof remain open.|
|024|Weapon visibility|Main-hand-only replacement and fallback patched; four native view contexts remain unverified.|
|025|Item models|180models/118itemresources pass static checks;9duplicate groups retained as warnings; native bindings/appearance pending.|
|026|Legacy|Reference candidate report produced; no blind deletions or saved ID removal.|
|027|Clusters|7/14whole-burst budgets, contact/arming/reload/lifetime source changes; native counts/scatter/detonation cost pending.|
|028|Drill/melt|Existing quarter-cell geometry retained; safety/progress/write bounds patched; accumulated heat and smooth SDF surface remain open.|
|029|Construction|Server transaction/consumption/lifecycle patched; native tests and complete preview/matrix remain pending.|
|030|Hybrid default|Independent true default and explicitOFF persistence implemented; optional far/sky composition wired; native combinations pending.|
|031|Expanded default|Independent persisted server/world profile and actual hooks implemented; migration tests pass; native/state/performance proof pending.|

PriorP0 fixes and365-frame harness remain as in docs/audit/2026-10-24/STATUS.md.
Fabric compilation is blocked locally by absent Loom1.10-SNAPSHOT; runtime evidence is
NOT RUN. Do not assert all31items fixed or use old1.1.13images as proof of this revision.

## M construction source changes (local; runtime NOT RUN)

Tool, surface, ray and batch construction share one server transaction: loaded/bordered/in-height
target, vanilla modification permission, valid replaceable/collision-free state, successful write,
then exactly one decrement from the selected offhand/hotbar stack. Survival has no invented iron
fallback; creative retains its fallback and does not consume. Batch actions examine at most256
cells (the existing purple tier cap). Full ship-forward and actual local-gravity up are shared.
Construction/draft state is cleared at death,respawn,dimension change,disconnect and server stop.
Block-entity/container item data cannot yet be transferred by this direct path; such stacks are
rejected intact to avoid silently losing their contents. No claim-mod integration exists.
Four real GameTests cover consumption,hotbar,rejected targets,batch duplicates,exhaustion and
creative collision. Added but NOT RUN: Fabric/Loom remains unavailable locally.

## H weapon fallback source change (local; runtime NOT RUN)

Vanilla hand suppression moved from the entire GameRenderer hand pass to the actual main-hand
item path, only when a visible cockpit cluster is ready. The offhand, engineer tools, weaponView
OFF and nonflight mode retain vanilla models. No gameplay IDs, muzzle coordinates or Workshop
presets changed. First/third-person, dropped-item and offhand client frames remain required;
this source finding is not inferred from which JAR was recorded.

## N/O profile implementation (local; game proof NOT RUN)

Hybrid is a persisted client option with a true default; explicit false/off/0 survives reload.
It gates optional distant terrain and level-sky composition without overwriting their subflags,
weapons, near block geometry, mirror/portal options or any server physics. Essential near/dynamic
geometry stays present when OFF. This is a bounded composition profile, not a claim of completed
NoCubes geometry or tested DH integration. Loaded before renderer registration.

Physics is a separate server-owned PersistentState, `data/drmd_physics.dat` in the save's
Overworld, shared across its dimensions. A save with a stored profile keeps it even if global
server defaults later change. Untagged saves use `drmd-server.properties:physicsProfile`, whose
missing/invalid default is EXPANDED (the branch previously always used these optional systems).
An explicit STANDARD server default applies at first migration of untagged saves; it never
rewrites an already recorded per-world choice. The settings button displays the server's actual
profile, waits for synchronization and is locked for players without operator permission.

STANDARD suppresses new staged combat microdamage, explosion-rim fracturing, automatic support
collapse and drone-wreck conversion. EXPANDED retains them. Native movement/local gravity,
projectile hits, existing carved-block collision and existing dynamic bodies remain valid in both;
these are not a new rigid-body engine or a claim of mass-body performance. Existing limits remain.

Five actual standalone JUnit regressions PASS for default/migration/round-trip/independence.
Two added NBT GameTests and four client profile combinations are NOT RUN. New client proof must
include real Hybrid ON/OFF VBO behavior, actual server profile and config reload; the gate now
requires369 fresh audit frames, retaining all earlier checks. UI localization is RU/EN.

## K cluster lifecycle source changes (local; runtime NOT RUN)

Ground contact now stops at the ray's surface before releasing children instead of using a
position already inside terrain. A persisted landed state gives children an arming interval,
and a saved spent parent is discarded without a second release. Expired/under-void rounds
discard without spawning another generation. Launch-down survives owner loss/reload, with
actual active foot gravity used while the owner is available. Payload multipliers and hazard
ages are bounded on load. Probes defer at unloaded chunks; no extra generation is requested.

A per-world admission budget preserves whole7/14bursts: max128children/tick,48/chunk/tick,
512live children counted from actual loaded entities. Pressure defers the parent until budget
is available or its finite lifetime ends. Four pure JUnit cases PASS. Three actual lifecycle/
count GameTests are prepared but NOT RUN. Detonation/block-update timing and scatter images
remain unmeasured; this is a spawn/lifecycle bound, not full explosion-cost certification.

## L drill safety/progress source changes (local; runtime NOT RUN)

The recovered branch already uses TunnelCarving→CarvedBlock quarter-cell masks and real matching
collision; the older branch findings in the attachment are not a description of this exact source.
Repeated identical boundary cuts previously rewrote/recounted the same shape; they now skip it.
Handheld engineer rays now use full ship aim and stop at the loaded-region boundary. Mining
checks loaded chunks,height,border,vanilla player permission and container presence before reads.
The cobblestone heat stage no longer returns the same block indefinitely; hot Nether/magma
processing no longer creates a lava source. Four prepared native GameTests are NOT RUN.

Still open: accumulated/persisted heat, complete drop/material economics, pooled tick budgets
and the requested smooth signed-distance tunnel surface. The existing quarter-cell edge is a
finer staircase, not a mathematically smooth NoCubes replacement. No new dependency was added.

## F mirrors: resource lifecycle source change (NOT RUN)

Disabled mirrors no longer scan nearby chunks. Turning them ON schedules a fresh scan. The
shared offscreen target is deleted on world change/disconnect, or when both mirror and portal
views are OFF; one still-active view keeps its target. Allocation is refused with both flags OFF.
Reflection transforms/traversal were not conflated: native mirror image compositing still has
the documented rectangular scissor/depth limitations. Exact clipping, reflections at roll and
GPU-resource behavior require real client evidence and are not labelled fixed.

## G portals: exit body preflight source change (NOT RUN)

Ordinary travellers now validate their complete current AABB before any attitude/velocity/
position/cooldown mutation: runtime height, world border, every intersected chunk and collision.
Previously only the destination cell was checked. BlockBody keeps its existing transform-aware
whole-hull check. Two prepared native GameTests cover clear feet/blocked head, valid body exit,
height,unloaded target and no partial move. Foot-gravity reorientation, high-speed traversal,
pair reload and actual see-through clipping remain independent acceptance work.

## I/J resource and conservative legacy audit

The static graph covers180modelJSONfiles and118item model resources: zero local missing-parent,
missing-texture,PNG-header,geometry-bound or display-vector errors. Vanilla-owned references
are recorded as external (not falsely verified from absent vanilla files). Nine identical-geometry
groups are warnings; different textures may intentionally share a shape. Six actual Python
regression cases PASS. The generated table/report is in resources/.

A native GameTest now enumerates all actual registered drmditems (including retired savedIDs),
checks model resources and writes item-resource-inventory.tsv with implementation/weaponDef.
It is NOT RUN; the static118resources are not asserted to be the complete runtime registry.
Real inventory/hand/drop appearance and broad category screenshots remain pending.

Low-reference candidates are recorded with source references and a review-only action. Example:
CarvedBlockRenderer is the old per-box BER with no registration in the present source, while
HybridTerrainModel supplies the active chunk model; retained until native regression. Do not
remove saved aliases or recovery ZIP/JARs. Portal stabilizer is a live recipe ingredient in six
crafts, not an invented interactive portal tool or dead item merely because it extends Item.

Final source review also clears view-scan caches immediately on world replacement. Capsule
API inputs are finite and limited to the existing tool dimensions; each call writes at most128
actually changed cells, and later calls skip unchanged masks so they continue through the bore.
This per-call ceiling is not a global server tick-time guarantee. Removing the final intersected
cells counts as a real break with the original breaker, rather than an uncounted replacement.

## Final local checks and distribution scope

Five profile JUnit cases and four cluster-budget JUnit cases PASS in the v3 stage. All26Python
regressions PASS (20evidence-gate +6resource graph). Workflow actionlint and whitespace checks
PASS. The distribution gate also requires all18new native GameTest identifiers and a fresh real
registry table, preventing unrelated passing tests from standing in for these changes.

Full Fabric compile/GameTests/client execution remain NOT RUN because Loom1.10-SNAPSHOT is
absent locally and its repository is unavailable in this managed network. GHA is paused by the
user's current limits instruction. No new JAR, native frames or all31closed claim is supplied.

Final drill review preserves the distinction between containers and CarvedBlockEntity: mining
continues through existing carved fragments, while container data stays protected. A dedicated
native regression is added and mandatory in the gate; NOT RUN.

Direct construction also rejects multi-cell doors/plants/beds until their complete footprint can
be validated atomically: onPlaced must not overwrite an unchecked neighbour. Vanilla command/
structure/jigsaw placement keeps its creative/operator restriction. Items remain intact on
rejection; ordinary vanilla block-item placement remains available. Added native regression:
NOT RUN. The18new native cases are mandatory before distribution.

## M follow-up — complete placement footprints (audit.3, source only)

The blanket rejection of ordinary vanilla doors,beds and non-age-dependent tall plants is
replaced by an immutable footprint shared by prediction and application. Every cell is checked
for border,height,reach,loaded neighbourhood,permission,replacement and entity collision before
any target write. Vertical uppers validate their planned lower support; door power/open and
per-cell waterlogging are resolved before writing. Existing vanilla direction limits still apply.

PlacementTransaction checks stale snapshots,duplicate cells and a256cell bound, performs quiet
writes, verifies the complete result and rolls back all attempted cells on denial/exception or
loss of material/authority. Listeners/neighbours are notified only after the complete placement.
Known pair callbacks are skipped because both parts already exist; exactly one item is charged
per complete object. Batch limits/counts now use actual cells. Failed rollback is a distinct
diagnostic, never a success. Container/blockentity item data and age-dependent crops remain
unsupported by this direct path. Operator requirements are retained.

Nine standalone transaction JUnit cases PASS, including second-write failure,exception after
mutation,stale snapshot,invalidated first part,resource loss and failed rollback. Seven more
native GameTests are prepared (doors,all bed facings,tall plants,second-part collision,batches,
native quiet rollback and upper world bound); NOT RUN. All25native audit methods are required
by the distribution gate. Full Fabric/API/Mixin/client confirmation remains blocked locally.

## M follow-up — shared build preview and effective mode (audit.3; client NOT RUN)

The basic build tool now uses one proposal method for server placement and client prediction:
selected mode,anchor,block orientation,full footprint and refusal reason. A bounded read-only
preview runs once per client tick, shows both cells in green/red,local axes,material and cost,
and hides beneath screens/F1/sneak or in nested mirror/portal views. World/player changes
invalidate the rendered snapshot. New strings are RU/EN; no world or inventory writes in preview.

Adaptive construction mode is explicitly mirrored in the existing scaffold payload even when
no locked draft exists; client state is separate from server authority and cleared on disconnect.
The frame decoder rejects negative/over384counts before allocation. Server join/mode changes
push the effective flag. Explicit saved LOOK/SURFACE/PLANE choices on the tool override only the
automatic surface default; existing scaffold mode stays intact. The blank fresh tool can still
inherit that surface default. This prevents mode cycling from being silently ignored.

Four additional native checks cover read-only plan/application equivalence,mode-only payload
round-trip,oversized wire frames and explicit persisted mode choices. All29native audit IDs
are now mandatory; these and visual preview/roll/GUI-scale checks remain NOT RUN. The earlier
369-frame suite remains mandatory and does not claim to certify the new preview's appearance.

Ordinary single-block insertions retain the normal vanilla callback/notification path rather
than being mistaken for a failed transaction when TNT immediately becomes an entity or another
block reacts. One successful native insertion is charged once. Quiet commit/rollback applies
to the explicitly handled multi-cell families. A powered-TNT native regression is added; all30
native audit IDs are required. NOT RUN locally.
