# Authored missions: bounded protocol and live-event adapter

Source **fc54c416fdf1ba77339a909162cad21a32e122dd** compiles and passes **18 fresh
unit tests**, zero failures/errors/skips, with all frozen source inputs unchanged.
This is an isolated source candidate. Actual mission gameplay and a separate cold
disk reopen are still unverified; no complete mission family, lab or full-task
acceptance follows from these tests.

The genuine earlier ordinary three-use baseline produced0→3→6→9 reputation without
objectives. The candidate replaces unconditional acceptance rewards with an
explicit authored-site binding, Survival-owner acceptance, ordered volume entry
and successful owner mining, then extraction at the original live herald. The
claim receipt and clamped+3 reputation are mutated together in existing
FactionMemory and serialized in the same PersistentState. No new world-event
startup, automatic generator, reward command or separate persistence engine is used.

Two bounded JSON definitions use the same executor. Identity includes full signed
XYZ, site/owner UUIDs, seed, dimension, backend/bounds and definition hash. Reserved
claims cannot be evicted or reset by rebinding. Entry requires a fresh outside→inside
edge; disconnect/mode/context interruption and reload disarm transient entry.
Unknown, future, duplicate, malformed or excessive mission state is preserved
opaquely and blocks mission mutation, while legacy reputation remains usable.
Notification failure does not retry the state transition.

The18 controls cover ordered attributed events, early/duplicate claim refusal,
owner/context/definition mismatch, reload edge semantics, permanent tombstones,
invalidation, signed-height aliases/overflow/bounds, admission caps, opaque corrupt
state, same-state claim/reputation clamping, legacy data and strict resource codec.
NBT roundtrips here are deliberately not called a real cold-world persistence test.
The server adapter uses the ordinary initializer, actual loaded-only geometry,
Creative OP2 bind/inspect commands, real Herald interactions, normal END_WORLD_TICK
and successful PlayerBlockBreakEvents.AFTER. Six actual gameplay cases are next.

The first compile at9e3dfe failed on five ambiguous WorldAccess imports and executed
zero tests. That [first outcome](EVIDENCE/mission-ledger-9e3dfe-first-compile.zip)
is retained unchanged (70,271bytes, SHA-256
`7bd98c9e1c43660d6864789f718111a025598c893886a2e421c1269a72770726`).
The correction changes imports only. The subsequent26-second compile/unit gate
passed. Its [compact proof](EVIDENCE/mission-ledger-fc54c41-units.zip) contains43payloads,
23actual compiled class files, source/resources, helper/input freeze, logs and both
fresh XML reports:168,237bytes, SHA-256
`a6b8960e5edf90f671a2b0fc7940ec1fc5c079bb4c6b99c8cfef194ba19f17b0`.
Every payload hash and ZIP CRC was independently checked; [metrics](EVIDENCE/mission-ledger-fc54c41-units.json)
retain the narrow scope. No remapped installable JAR, client, actual six-case
cycle or cold-save acceptance is claimed. Both primary specifications, PS1,
Dynamic Boarding and the remaining mission adapters remain open. No publication.
