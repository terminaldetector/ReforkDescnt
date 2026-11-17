# Non-autostart SiegePlan v1: expected semantics before implementation

Review base `39c2517b891b29c31cb574b059943ae89293e0bf`; production remains
accepted `6b50273`. This freezes the first data/protocol packet, not live siege.

* No production path consumes a plan, reserves a recon slot, spawns, targets,
  emits notices, requests chunks or creates a new I/O task. Existing phases,
  director and role/weapon/mechanism behavior stay unchanged.
* The extension is optional `siegePlans = {schema:1, plans:{UUID:record}}`
  inside the existing top-level schema1 ledger. Missing extension writes no
  extension and means ordinary legacy defaults. At most8 raw record entries
  (valid plus quarantined), one decoded plan per participant. A single opaque
  unsupported/oversized subtree is preserved read-only rather than decoded
  into an unbounded map or silently erased. All legacy fields retain the
  existing codec's byte-semantic values. There is no downgrade reset to QUIET.
* A known-schema corrupt record is isolated and preserved raw; valid neighbours
  survive. A corrupt/unsupported record cannot grant execution authority, free
  an old actor slot or replace a valid plan. Unsupported extension mutation is
  refused. Caller input/snapshot objects are defensively copied.

Known record fields (required unless marked optional):

```
participant: UUID; faction: existing drmd:hostile_machines; world: namespaced ID
phase: PLANNED | WARNING | SEARCHING | SIEGE | RETREATING | COOLDOWN | CLOSED
proposedAt, clock, online, since, until, warningEnds, encounterEnds, cooldownEnds: long
origin: observation; known: observation
actor0?, actor1?: UUID              # future identity data, not live roster slots
acks: [{slot:int, actor:UUID, shot:UUID, at:long}]  # maximum10 entries total

observation = {
  site:UUID, observer:UUID, participant:UUID, world:namespaced ID,
  at:long, point:{x:double,y:double,z:double},
  body?:UUID, local?:{x:double,y:double,z:double}    # both present or both absent
}
```

One immutable observation provenance/site identity is retained from proposal.
Later known points may advance only from matching-world/site/participant
observation data with nondecreasing actual observation times. Re-reading a
stored point never refreshes its time. Pure data validation does not prove
real LOS; that producer/bridge is a later separately reviewed implementation.
Future actor IDs are distinct, cannot be rebound/reset, and cannot overlap
another decoded plan or an existing live recon actor. Offering/replacing a
plan never creates a legacy participant or changes `canDeploy`/actor count.

Pure transition/order contract:

1. PLANNED accumulates at most100 supplied online ticks per explicit update,
   bounded by existing12000 grace. Offline/zero input cannot spend grace.
   After grace, a fresh eligible explicit update starts a full400 warning;
   late processing never subtracts already elapsed proposal time from it.
2. Warning fixes `warningEnds`, and the whole encounter ends at most3600 ticks
   after that. SEARCH and SIEGE cannot extend it through hide/reacquire/load.
   An initial old site can guide search, but only fresh matching observation
   can enter SIEGE. Hidden/mismatched/future points never update known data.
3. Leaving the area, ineligible/dead subject, confirmed pair defeat, expiry or
   ten accepted emissions ends fire authority. RETREAT uses at most the
   existing200-tick lost-sight window; its cooldown end is fixed12000 ticks
   from retirement and never refreshed on reads. Eventually CLOSED cannot
   reopen/refill; a new encounter needs a new reviewed admission.
4. Each of two future identities has at most5 accepted emissions, the proposed
   encounter order bound from capacity100/cost20. This is not approved weapon
   balance or finite energy in existing actors. No identity means no fire.
5. A fire permit requires explicit SIEGE, matching slot/actor, same-tick own
   observation and explicit loaded/own-LOS/energy/cooldown/muzzle readiness.
   Denial queues nothing. Permits are private, instance-bound, current-tick
   capabilities; at most2 transient pending permits, never serialized.
6. ACK accepts only that issued permit/identity/time/sequence and a new actual
   projectile UUID supplied by the future real emission adapter. It consumes
   once. Duplicate/foreign/late/replayed ACK cannot spend, refill or affect a
   second slot. Persist all accepted IDs (at most10), deriving consumption
   from them; keep no unbounded seen-ID map. Restoring drops pending permits.
7. A state update cannot replace a plan with older observation/clock, different
   identity/origin/deadlines, rebound actors or a subset of its ACK history.
   The plan snapshot exposes no mutable internal list or live world reference.

First preimplementation codec baseline uses only the old public/package
PersistentState/NBT APIs and hand-authored **non-executable protocol data**.
Expected old-source results: missing-extension legacy controlPASS; supported
record roundtripFAIL; unsupported opaque roundtripFAIL; valid-neighbour/corrupt
record isolation roundtripFAIL. These would be feature-absence codec results,
not live combat bugs. Preserve the first exact run if it is leased; no disabled
hook/synthetic runtime negative. The unchanged baseline semantic assertions
must pass on the new codec.

Later first positive gates: meaningful pure warning/knowledge/slot/ACK/expiry/
snapshot protocol cases; the four raw baseline cases; actual compressed
PersistentState write/reopen with legacy recon and plan continuity; cap/owner/
rollback controls; actual real world ticks proving no new activation; retained
old threat tests. Record source, fresh XML, exits and scope. Whole SIEGE,
ground infantry, friendly modular AA and novice survival balance stay open.
