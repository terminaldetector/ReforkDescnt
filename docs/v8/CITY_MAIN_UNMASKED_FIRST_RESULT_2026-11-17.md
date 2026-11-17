# Ordinary city registration: first unmasked command PASS

Exact isolated source `a81ebef546074db5599b53b2d530c31646070c9b`.
First compileGametestJava PASS0, launcher26.829s; first same actual command case
PASS4.502s, actual game/Gradle0, launcher39.721534531s. No deadline, TERM/KILL,
retry or source correction. The Java lease was returned at first close before
packaging; no non-zombie Java remained from this run.

## Real activation and unchanged command path

The only production registration is `CityAuthoringSystem.register()` in
DescentMod after existing registries/commands. It delegates once to CityCommands;
the actual literal is **`/ungravitycity`**. The processed test mod has **no main
entrypoint**, all source test registration calls are absent, and the actual log
contains neither active nor passive bootstrap markers. Thus a test initializer
could not supply the dispatcher or production END_SERVER_TICK callback.

All41 actual commands and all31 original command criteria passed:

- Startup created zero city sessions, production workers or city/save-ID files.
- All24 baseline commands from non-OP Creative, Survival/Spectator OP and console
  left world/session/workers/files unchanged. Eligible owner plan reached READY;
  inspect and three inert ticks left world writes/CAS at zero.
- A foreign actual Creative OP attempted all six children without changing the
  current owner/session or manifest/journal/save-ID byte witnesses.
- Actual apply followed by pause prevented CAS. Actual resume allowed exactly
  one64-cell CAS with bounded snapshots and exact target after-images. Actual
  pause prevented a second CAS, and cancel persisted terminal cancellation.
- Actual inspect reopened the same persisted cancelled slot with cursor64;
  apply/resume could not reactivate it. Final cancel drained, actual production
  workers exited, and the JDK witness reacquired the same persisted save UUID.

Only registered production END_SERVER_TICK callbacks advanced jobs. No direct
test call to runtime plan/open/apply/tick/pause/resume/cancel replaced a command.
Final phase11:45runtime game/server ticks,2793472262nanos, zero production workers.
The actual plan metadata used world seed0, city seed717, STOCK backend, current
bounds, owner/save/job UUIDs and anchor(4676985,64,-10633568). Its full85520-cell
blueprint was not built: only the first64 cells were exercised, then fixture
terrain/players were restored.

## Actual normal flush and pacing receipts

Inspect issued game39/server67; original raw GameMessage queue stayed31→31
immediately. The next existing callback game40/server68 had exact deltas1/1 and
raw31→32, and the same Anchor/sterile-limit predicate passed. No manual flush,
event-loop pump, fake packet, extra phase or timeout was introduced. All31 original
criteria are unchanged7cd/463/3b1/a81, SHA256
`ad99cc5c6cfdf8c3b1daa1ba50650ddeedaf8fbbcc3f1b4421cc9346c8d3f93a`.
The unchanged first callback of the original3inert-tick window checks delivery.

62actual clock receipts have consecutive game/server ticks, no early target and
all61 intervals at least50070631nanos. At success packet diagnostics had35events,
22740encoded bytes, no cap/fault. Original setup10s/runtime300/abort5s,
framework30350 and launcher180+10 bounds remain. This is an explicitly paced
GameTest, not a native client or performance result.

## Immutable source, world and evidence

All1490 source/helper files, cached JDK/sevenJUnit artifacts remained unchanged.
All1082 compiled files stayed unchanged during execution. Versus3b1 only the
DescentMod main class changed;970other main classes were identical. The six pure
city models already on ROOT ae1bd82 are unchanged. Main activation adds one call
and updates an unwired-comment; the five runtime classes and eight IoSession
unit methods retain their earlier source/proof. No unit suite was rerun here.

Closed world66files, ZIP845443bytes SHA256
`377aaf6c1737ad55776f802d7dd2153858faab38a93c2e48fb2997bd40b8b0b6`.
Java-compatible exclusive fcntl.lockf(r+b), archive CRC, every member/raw SHA and
the exact file set were verified. All NBT, PersistentState, actual manifest,
journal, save-ID and ownership lock are preserved. Raw retained at sealing;
any later authorized raw-only reclaim is a separate receipt.

Evidence `CITY_MAIN_A81EBEF_UNMASKED_EVIDENCE_2026-11-17.zip`,37files,
2894222bytes SHA256
`c47cacc154af48c82786add7918ee6fb5c92a6d1eab335ce736ee0e47f23845d`.
CRC/every SHA manifest entry reread and verified. It includes first logs/XML/
exits/launcher commands, actual source/helper TAR/classpaths, source authority,
clock/packet/phase/WAL/CAS observations and the closed world archive.

## Minimal handoff and remaining review

Use the already supplied patches, not this older-base entire branch:

1. `CITY_RUNTIME_FINAL_FIVE_REVIEW_FROM_AE1BD82_2026-11-17.patch`: add CityIoSession,
   CityWorldStorage, CityRuntimeSession, CityAuthoringSystem and CityCommands.
2. `CITY_RUNTIME_EIGHT_UNITS_REVIEW_FROM_AE1BD82_2026-11-17.patch`: only the eight
   CityIoSessionTest methods; first scoped55/55JDK proof remains027.
3. `CITY_MAIN_ACTIVATION_ONLY_FROM_E4DD979_2026-11-17.patch`: ROOT coordinates the
   single DescentMod registration call in its current shared initializer.

Production components are c041e8d five-runtime/+8units →027685a essential fixed-slot
preflight →a81ebef comment/one-call registration. Runtime/command/diagnostic clock
classes stay out of the ordinary test manifest until separately approved.

ROOT review subsequently identified that the per-cell actor query materializes an
unbounded entity result list. This PASS is preserved unchanged; a separately
authorized bounded first-overlap query and actual actor guard are the next source
increment. Do not integrate these five classes as already passing that new gate.

Scope remains isolated manual sterile-city prototype and unmasked command wiring.
No ROOT merge/integrated count increase, full city/destruction/save-reload,
Survival siege/factions/missions or S12–S14 completion is claimed. Earlier genuine
failure and diagnostic checkpoints7cd/463 and phased admission failures remain
immutable. No startup generation, default authoring or automatic resume is added.
