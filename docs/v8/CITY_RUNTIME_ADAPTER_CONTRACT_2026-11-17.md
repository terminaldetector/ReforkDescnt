# Manual city runtime adapter: proposal for API review

**Design only; no runtime production source is authorized by this document.**
The preceding proof is source77dc8a1,26 pure tests; evidence tipbd8acc6. No actual
Minecraft city, mod compilation, persistence reload or native acceptance is claimed.

## Save identity and immutable plan context

Read-only search found no reusable persistent save UUID in DRMD world/access,
world/cubic, world/store or current canonical source. The cubic owner confirms its
known APIs provide no such contract. Dimension key, world seed, operator UUID and
`session.lock` are not save identities.

Propose `CitySaveIdentity`, JDK-only, under the explicit save-root path
`drmd/city/save-id.bin`: bounded magic/schema/UUID/CRC. It is provisioned **only**
after an explicit manual plan, on the serial I/O worker. Every independently
created save receives a different UUID even with identical seed/dimension/owner/
anchor. Importing only a manifest/journal from another save is refused. A complete
backup/copy inherits its original save UUID and history; this is intentional
portable save identity, not an assertion that cloned directories are physically
different universes. Automatic clone reprovisioning/path binding is excluded.

The worker provisions only fixed `drmd`/`city` descendants of an existing captured
save root. For each created directory it forces its parent entry and the directory;
then a new save-ID file is forced and its parent forced before acknowledgment.
Existing malformed/foreign/unsupported data is preserved and refused. Atomic
file publication requires `ATOMIC_MOVE` plus directory force; no silent weaker
fallback or claim about a failed sync. No fsync, directory listing, file existence
test or path canonicalization occurs on the world thread.

Initial scope uses **one persistent current-job slot per save**:
`current.manifest` and `current.journal`. It never overwrites/deletes a previous
slot, including a cancelled/completed one. A second distinct city plan in that
save is refused until a separately reviewed explicit archive/multi-job facility
exists. This avoids an unreviewed unlimited history/catalog/cleanup policy in the
first adapter; it is a prototype limit, not S13 completion.

Immutable `CityManifest` fields:

```text
manifestSchema, journalSchema, blueprintVersion
saveId, jobId, ownerUuid
dimensionKey, actualWorldSeed, requestedCitySeed
anchor, actualBounds(bottomY, exclusiveTopY, inclusiveBorderBounds)
backendKind, planHash
solidCount, cutCount, maxBatchCells(64), zeroAuthoredBlockEntities, zeroAuthoredActors
```

Limits: manifest≤16KiB, save-ID≤128bytes, journal≤8MiB; bounded strings and cells
remain the existing pure limits. Paths contain no arbitrary command filenames.
Manifest publication and the complete identity-bound journal header must both
acknowledge before a plan is exposed as ready. A crash in provisioning may leave
incomplete files; explicit reopen validates and quarantines them without rewriting
or deleting anything. No automatic startup scan/open/creation/recovery occurs.

On every explicit reopen, regenerate the plan from its immutable manifest on the
worker and verify exact version/hash/geometry/counts. Capture actual runtime facts
on the server thread and require **exact equality** for dimension, world seed,
backend and bounds, as well as save-ID. Changed height/border bounds or unsupported
versions refuse resume, even if an expanded border would look harmless. No implicit
rehash, migration or backend fallback. The same check pauses a running job when its
runtime context changes. The live registry is separately checked on the world thread.

## Proposed files and APIs

New `world/fixture/city` files:

| File | Responsibility/API |
| --- | --- |
| CityManifest | Pure immutable context, bounded versioned codec; no World/Player references |
| CitySaveIdentity | Worker-only explicit provisioning/open and directory durability |
| CityIoSession | Pure serial worker, exclusive filesystem ownership, one pending immutable request, typed durable control, asynchronous drain/close |
| CityWorldStorage | Implements existing job Storage via WorldAccess and ServerBlockEdits; server-thread-only registry/loaded/permission/actor/neighbor guards |
| CityRuntimeSession | Server-owned context, one current plan/job, intent/backpressure and immutable I/O results polled on the server |
| CityCommands | Player operator command registration only; no initialization writes |
| CityAuthoringSystem | Inert tick/disconnect/shutdown event registration; creates a runtime session only after a manual command |

Reviewed existing pure files need explicit extensions, not silent runtime workarounds:

- `CityBatchJournal.Identity(saveId,jobId,owner,dimension,planHash)`; bump byte format
  toversion2 and explicitly refuse prototypeversion1 without overwriting it.
- Durable typed control records (`PAUSED`, terminal `CANCELLED`, `VERIFY_AFTER_ONLY`)
  and Snapshot disposition. Known notification failure remains PREPARED and must
  persist its verification disposition. It never authorizes CAS from an all-before
  image. After a crash before that disposition acknowledges, the earlier generic
  PREPARED ambiguity remains honest: explicit before/after/mixed reconciliation,
  without assuming a known notification outcome survived.
- `CityAuthoringJob` restores terminal cancellation and verification disposition;
  all other nonterminal restored jobs are PAUSED. No persisted RUN flag ever
  authorizes startup movement. The existing owner/page/cursor/future controls stay.

The single coordinated `DescentMod` call would be
`CityAuthoringSystem.register()`, only after source/API review and test readiness.
It registers commands and inert event handlers; it neither opens storage nor writes
world cells at registration/server startup.

## Manual commands and owner lifecycle

Proposed command root: `/ungravitycity`.

```text
plan <citySeed> <x> <y> <z>   immutable plan preparation, no world edits
inspect                     read cached plan/route/budget/context/status
apply                       explicit initial authoring
pause                       stop future CAS immediately, persist pause
resume                      explicit loaded/conflict/context revalidation
cancel                      stop future CAS immediately, persist terminal cancellation
```

First adapter follows the existing operator-build policy: connected living player,
Creative, permissionlevel≥2, allowModifyWorld, matching owner UUID and same world.
Each cell additionally checks `world.canPlayerModifyAt` and the real world border.
No ownership transfer, console impersonation or routine admin override is added.
Inspect during an active session never triggers filesystem I/O. A manual inspect/
resume after a fresh process can queue one bounded read/open operation; its result
is always paused and never creates a missing plan or save identity.

Disconnect, death, spectator/mode/permission change, dimension departure or server
shutdown stop future CAS **before** polling an acknowledgment that could otherwise
enable a write. Persist PAUSED after the one outstanding disk operation finishes;
reconnect does not auto-resume. Cancel is session-immediate and acknowledged as
durable only after its terminal record forces. An I/O failure remains stopped/
quarantined; it must not report durable success or silently drop cancellation.
No rollback or automatic deletion of the committed prefix is promised.

## Actual loaded-only storage boundary

Use only `WorldAccess.of(world).isVolumeAvailable`, `readIfLoaded`,
`snapshotIfLoaded` and `modifyServerAuthoritative`; do not call a generating chunk
getter, add a ticket, touch a legacy generator or use StructureMover.

At most64 planned cells from one16³ page and one CAS per authoring tick. Check the
page plus its one-cube halo (at most27 metadata queries). Plan runtime preflight
also requires that halo fit the actual world/packing/border envelope; unsupported
edge plans are rejected rather than requesting impossible cubes. Missing volumes
pause. No full27-cube block scan: at most64 cell plus384 face-neighbor positions,
deduplicated to≤448 snapshots in that phase. No cached before-image survives a
tick; recheck availability/state/guards before CAS after a disk acknowledgment.

Target registry IDs remain exactly the five tested pure palette entries:
air, stone_bricks, deepslate_tiles, polished_andesite, iron_block. Actual states
must be the default property-free `AirBlock.class` or full-cube `Block.class`,
with no fluid, block entity or special callback. First before-state/face-neighbor
allowlist is equally strict: exact inert full `Block.class` or `AirBlock.class`,
property-free, no fluid/BE. Do **not** inherit the backend's broader GrassBlock,
PillarBlock or container exceptions. Glass/emissives/foliage/attached blocks,
doors/panes/redstone, liquids and gravity blocks need separate explicit support.

The six neighbors matter even outside the batch: `ServerBlockEdits` publishes
neighbor notifications after committing. A pre-existing sand/attached block could
otherwise react and spawn debris despite a safe target palette. Unsafe/missing
neighbors pause before WAL preparation and again before CAS. No actor intersects
new solid geometry; reject that batch rather than move/kill the actor. These are
authored actor/BE budgets, not a claim to disable ordinary Survival spawning.

Snapshot expected cells have null BE data. Reconstruct exact registry states from
their canonical property-free IDs; pass≤64 `ServerBlockEdits.Edit` values to the
existing transaction. No ordinary `setBlockState`, drop generation or placement
fallback. Successful state/no-change results write the durable COMMITTED marker;
notification failure is physically committed but paused in PREPARED until actual
after-image verification. Conflict/unsupported/unavailable pauses; ambiguous
rollback failure quarantines. Never relax backend allowlists or assertion limits.

## I/O and shutdown ordering

The server thread captures only immutable UUIDs/paths/strings/longs/bounds and plan
DTOs. The I/O worker imports no Minecraft code, owns no World/Player/server object
and completes no callback into game state. Pure plan generation happens off the
world thread too. Results are polled; no `join/get/force` from a world tick.

One serial worker and one pending request; never an unbounded executor queue.
Pause/cancel/stop has one coalescing terminal-intent latch rather than another work
queue. Restrictive intent cannot be overwritten by a later resume while persistence
is pending. World mutation stops immediately; once the current prepare/commit
acknowledges, the worker forces the stop/control record before more authoring can
be admitted. Repeated identical stop intent is idempotent.

On server stopping, close admissions and world mutation synchronously in memory,
publish one immutable terminal request, and request asynchronous worker shutdown.
The worker drains that one operation, forces terminal intent, closes the journal
and releases its exclusive lock; only it performs file I/O/close. It is non-daemon
and starts only after an explicit command. A stalled filesystem may delay process
exit; no false guarantee of both forced durability and hard shutdown timeout.
A separate diagnostic timer may report a slow drain, never kill/abandon it and
claim success. Game-thread shutdown never waits for it. Server-owned physical
references are cleared at stop; the worker's remaining inputs are pure immutable
data. Explicit next-process opens refuse a still-owned lock.

## Acceptance gates to propose after API review

First extend pure tests for two same-seed save roots with distinct IDs, transplanted
manifest denial, schema/bounds/hash mismatch, durable cancellation/verify controls,
partial provisioning preservation, one-request backpressure and drain/lock release.
Retain the original26 proof and actual first outcomes on the new source.

Then no more than five focused actual Minecraft cases initially:

1. Manual inert startup/plan/inspect, real≤64page CAS and exact cursor/blocks.
2. Missing cube/halo and six-neighbor denial without new loaded volumes/tickets.
3. Real falling/attached/BE before-state or neighbor rejection, no induced actor.
4. Owner/permission/mode/disconnect pause and durable cancel while I/O is pending.
5. Actual world-context mismatch refusal and safe loaded readback after reopen.

A separate fresh-process native gate must restore the same real save, confirm its
identity/manifest/prefix, remain paused, then manually resume or honor cancellation.
Only afterward propose the full16-building real city, actual destruction, native
client visibility, save integrity and performance. No actor/siege activation,
whole-city physics, approved faction/technology canon or full S13 claim enters this
first manual adapter.
