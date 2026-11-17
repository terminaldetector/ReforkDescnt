# Manual-city increment: pure-first contract

Base: full-tested `699b749045edde2722f0ae9685b2a3674c1a1041`; new isolated branch
`experiment/city-authoring-20261117`. The first checkpoint `b36a5c0` contained test
source and contracts only. Pure model/job implementations and standalone test
project are now prepared. All26 tests are still **uncompiled/unrun**; no Minecraft/runtime writes,
registration, world generation or Java launch has been made.

The first pure models are `CityBlueprint` (eight distinct original archetypes,
sixteen lots, deterministic immutable sparse cells/inspect/hash/bounds/caps) and
`CityBatchJournal` (JDK-only bounded identity/CRC frame persistence and conservative
CAS recovery). Tests require actual geometry variation, both air openings for each
building, all material/footprint/world limits, genuine disk close/reopen and exact
before/after/mixed/foreign/unavailable recovery decisions.

Durability is deliberately narrower than a world-wide transaction: an immutable
PREPARED batch of at most64 cells is durably acknowledged before server CAS; a
COMMITTED marker is durably acknowledged afterward. Between those operations a
crash may leave a pending batch. A restart never writes into the world. Explicit
owner/world/plan-bound resume can retry all-before or advance all-after; mixed,
foreign or unavailable state pauses. Publication failure is a committed state that
must be paused/verified, not blindly replayed.

Refinement: a known notification/publication failure leaves its physical after-image
in durable PREPARED state, with cursor unadvanced. Manual resume must observe every
actual after-image before requesting COMMITTED; same-session all-before is refused,
as are mixed/foreign/unavailable states. The notification-cause flag itself is
session-local in this pure prototype. After process loss only PREPARED is known,
and general explicit recovery still distinguishes all-before/retry from all-after/
advance. It never writes on startup. A durable runtime job manifest/stop disposition
is a separate adapter acceptance, not established by these models.

The first byte reader quarantines incomplete/corrupt journal tails and preserves
their bytes; it does not quietly truncate or claim recovery. Explicit corrupt-tail
repair/migration is a separate review gate. Same identity acknowledgments are
idempotent; another owner/world/content hash cannot rewrite a journal. The real
adapter must never synchronously fsync from a server tick: one bounded immutable
I/O request at a time, polled on the server, with no World references on a worker.
Opening a journal requires an already existing, durably provisioned parent;
it never creates ancestors. After file/header force it forces the parent directory
before returning on both initial creation and reopen. Unsupported directory syncing
fails closed. No claim is made about provisioning/durability of ancestor directories
or an actual power-cut test.

After the pure controls, `CityAuthoringJob` will own bounded CAS phase/pause/resume/
cancel controls against a storage port. `CityWorldStorage` later binds that port to
existing loaded-only WorldAccess/ServerBlockEdits (at most64 planned cells and one
CAS per tick; zeroBE, zero actors). `CityAuthoringSystem`/`CityCommands` may register
only a manual operator command and inert tick handler; no startup writes, automatic
resume, tickets, retired generator calls or StructureMover fallback.

Material identifiers are inert full blocks and explicit air openings. Actual
registry/type/shape and the existing strict backend allowlist remain a mandatory
runtime preflight: glass/emissive identifiers are not assumed compatible merely
because they look full-cube. No falling blocks, liquids, doors, panes, redstone or
attached/multiblock state may enter an independent batch before dependency-group
or notification-ownership semantics are explicitly reviewed. Neighbor rewrites
must not masquerade as committed after-images or create unbudgeted entities.

`CityAuthoringJob` is now JDK-only source: one owner thread, explicit apply/pause/
resume/cancel, at most64 planned cells from one16³ page, at most one CAS per tick,
one pending disk future and no disk waits or callbacks into world state. Cursor
advancement waits for durable COMMITTED acknowledgment. I/O loss or ambiguous
backend exception quarantines the session. Unavailable/conflicting storage pauses;
an all-after pending batch advances without CAS. Cancel stops future CAS without
rollback or deleting its journal; cancellation is session-terminal, not a claimed
durable tombstone. Any reopened session still needs an explicit owner command.

Plan construction validates target whitelist, unique/canonical cell order, actual
world/envelope bounds, independent solid/cut caps and content hash before a WAL
request. The future runtime preflight must also reject BE and unstable *before*
states, check actor/permissions/border and actual backend support. No runtime
adapter, command registration, world availability proof or rendered city exists yet.

The first focused gate uses `verification/city-pure`, a standalone plain-Java
offline project reading exactly three production and three test files. Cached
JUnit5.11.3/Platform1.11.3 artifacts are identified and hashed; no repositories,
Loom or Minecraft dependencies are resolved. Input capture hashes those sources,
runner/wrapper files and cached JUnit/JDK inputs. This verifies pure contracts only;
the full mod compilation and actual cubic-world/WorldAccess/native city gates remain
separate. Preserve the first compile/test failure without editing/rerunning in place.

After ROOT grants the exact-source Java lease, from the isolated branch:

```sh
JAVA_HOME=/workspace/jdk21-complete GRADLE_USER_HOME=/workspace/.gradle python3 verification/city-pure/capture_inputs.py --output work/city-pure/inputs-before.json
JAVA_HOME=/workspace/jdk21-complete GRADLE_USER_HOME=/workspace/.gradle ./gradlew -p verification/city-pure --offline --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx256m compileJava compileTestJava
# Record first compile exit; only if successful with frozen inputs:
JAVA_HOME=/workspace/jdk21-complete GRADLE_USER_HOME=/workspace/.gradle ./gradlew -p verification/city-pure --offline --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx256m test
```

Capture raw logs, explicit exits, fresh XML (or explicit noXML) and post-run inputs;
do not substitute the existing full mod's772/404 proof for these new26 controls.

No whole-city physics, actors, siege, faction canon or S13 completion is claimed.
Java and native/full-city acceptance require separate root leases/review.
