# Manual-city increment: pure-first contract

Base: full-tested `699b749045edde2722f0ae9685b2a3674c1a1041`; new isolated branch
`experiment/city-authoring-20261117`. This first checkpoint contains test source
and contracts only. Tests are **uncompiled/unrun**; no Minecraft/runtime writes,
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

The first byte reader quarantines incomplete/corrupt journal tails and preserves
their bytes; it does not quietly truncate or claim recovery. Explicit corrupt-tail
repair/migration is a separate review gate. Same identity acknowledgments are
idempotent; another owner/world/content hash cannot rewrite a journal. The real
adapter must never synchronously fsync from a server tick: one bounded immutable
I/O request at a time, polled on the server, with no World references on a worker.

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

No whole-city physics, actors, siege, faction canon or S13 completion is claimed.
Java and native/full-city acceptance require separate root leases/review.
