# Bounded city actor guard: first focused two-case PASS

Exact frozen source `03c26a2eb46ffeb53ad94d421a134e407f544515`.
First compileGametestJava PASS0/25.584568222s. First actual commandactor20hz run
PASS2/2, actual game/Gradle0, launcher42.859819997s. XML actor2.057s and
command2.574s. No retry, source/helper/test change, deadline, TERM/KILL or budget
extension. No executing Java remained at close; the lease was returned then.

## Actual late overlap prevents all partial writes

The only production source change since a81 is CityWorldStorage: collect at most
one existing actor per solid through ServerWorld.collectEntitiesByType with
TypeFilter.instanceOf(Entity.class), reused capacity1 scratch, limit1. The same
cell Box and exact nonremoved/nonspectator predicate remain. No new isAlive
condition, category exemption or enlarged collision tolerance.

Actual registered no-gravity ItemEntity cecb70fc-55b4-457d-a759-26dc6122774f
overlapped the **last solid** at(1118108,42,2437079), with30earlier solids in the
64-cell batch. Its actual bounds were
`[1118108.375,42.5,2437079.375]→[1118108.625,42.75,2437079.625]`.

- At runtime3/252.130365ms actual guard was ACTOR_BLOCKED, snapshots164/batch64/
  CAS0, jobPAUSED/cursor0 with durable PREPARED at READY. The actual actor remained
  intact and **all64 AIR predicates passed**, including the earlier solids.
- Remove only the owned blocker and explicitly resume the same prepared page:
  runtime4/289.813578ms actual backend CAS1, snapshots164, all64 exact target
  predicates passed. Pause prevented a second page.
- Actual cancel and asynchronous close reached runtime9/527.590405ms: CLOSED
  with durable CANCELLED/cursor64, no pending batch, preparation and I/O workers
  exited. The four real actor-slot files were copied and rehashed; its actual
  Java-compatible ownership lock could be reacquired after close.

This is actual actor/whole-page safety behavior, not a query-call-count assertion.
The cap is directly evidenced in production source; the behavior case preserves
the existing refusal semantics under the new bounded query. Only fixture-owned
actor setup/cleanup spawns/discards an actor; authoring creates none.

## Unmasked command companion and unchanged controls

Vanilla ran the actor batch first and command batch only after it completed.
The actual processed test manifest contains only the two fabric-gametest classes,
no main initializer; actual log has neither CITY_COMMAND_TEST_BOOTSTRAP nor
CITY_COMMAND_TEST_PASSIVE_MARKER. The single DescentMod register call supplies
the real **`/ungravitycity`** dispatcher and production END tick behavior.

All41 actual commands and all31 original criteria passed: startup zero city jobs/
workers/files; non-OP Creative, actual Survival/Spectator OP and console denial;
foreign Creative OP denied all six actions without state/file changes; inert
plan/inspect; explicit apply+pause/CAS0; resume/exact64CAS; pause/cancel; actual
persisted terminal reopen, apply/resume refusal and worker/lease close.

Inspect issued game18/server151, received next existing callback19/152, exact
deltas1/1 and normal raw packet predicate. No manual flush/pump/new wait. Command
completion runtime34/1928350037nanos, zero production workers. All31 criteria
remain SHA256 `ad99cc5c6cfdf8c3b1daa1ba50650ddeedaf8fbbcc3f1b4421cc9346c8d3f93a`.
Original five runtime predicate bodies and accepted cadence are also unchanged.

Actor26 and command42 actual clock receipts are consecutive game/server; no early
target or clock fault. Minimum intervals50072105/50081008nanos. Same setup10s,
runtime300, abort5s, framework30350 and launcher180+10 controls. Actual backend was
STOCK with FULL27 halo; no city NATIVE_CUBIC, native-client or performance claim.

## Honest observer failure and immutable evidence

A separate read-only prelaunch class-inventory observer wrongly expected only the
top-level CityWorldStorage.class to change; javac regenerated its three nested
classes too. That observer assertion and exact four-file delta are retained.
The shell continued to the one already-authorized runtime after actual compile0
and successful frozen-input check. It did not reach its old non-world output
rename. Prior a81 raw world had already been archived/reclaimed; the run init
independently refused any existing level.dat, and fresh XML names both new cases.
No compilation/gameplay/clock criterion was changed to accommodate the observer.

All1492 source/helper files, actual JDK/seven cachedJUnit inputs remained frozen.
All1083 compiled-directory files stayed unchanged during execution. Main has
971classes: four CityWorldStorage/nested class files changed and967others match
a81; the separate refmap JSON was also unchanged. No unit suite was rerun; the
eight IoSession units retain their earlier standalone55/55 scoped proof.

Closed disposable world77files, ZIP909475bytes SHA256
`9ad35f6d97d81b177a2fae9e762534bd047a5d7e1f5789c816b394ced1917894`.
Exclusive fcntl.lockf(r+b), CRC/every member/raw SHA/exact file list verified.
Actual world NBT/PersistentState/command slot are inside; the independent actor
slot is also copied in evidence. Both original raw save and sidecar retained.

Evidence `CITY_BOUNDED_ACTOR_03C26A2_PASS_EVIDENCE_2026-11-17.zip`,42files,
3022016bytes SHA256
`16d9c735e17d549c577cd7a1b276fba27a1249b4c1f5513f57abf311105c2a87`.
CRC/every SHA manifest entry reread and verified. Includes first logs/XML/exits,
actual inputs/classpaths, frozen source/helper TAR, observer failure, exact
command/actor/clock/phase/CAS/WAL receipts, world archive and actor-sidecar hashes.

## Minimal ROOT review handoff

Final five-main patch is
`CITY_RUNTIME_BOUNDED_FIVE_REVIEW_FROM_AE1BD82_2026-11-17.patch`,40561bytes,
SHA256 `c62434ba645347f4e054f73fdd7cd4c71ea71a16866439a969866351bd5cd42a`.
Base `ae1bd821fcc0a7d7e16b0284bfa25355902c5077`; adds only CityIoSession,
CityWorldStorage, CityRuntimeSession, CityAuthoringSystem, CityCommands. The six
pure models on that ROOT checkpoint remain identical. Production components:
c041e8d five-runtime/+8units →027685a fixed-slot preflight →a81ebef one-call
activation/comment →03c26a2 bounded actor query.

Eight-unit patch and activation-only patch remain separate, unchanged from a81
readiness. ROOT coordinates its current DescentMod line. All runtime/command/
diagnostic clock helpers stay isolated from the ordinary manifest until approved.
Do not cherry-pick this older-base whole tree or infer an integrated count.

Scope is manual sterile-city prototype, first64cells and current-process actual
persisted cancellation reopen. No full-city build, whole-world cold reload,
destruction/battlefield acceptance, S12–S14 completion or ROOT integrated/native
claim. Earlier failures and scoped positive evidence remain immutable.
