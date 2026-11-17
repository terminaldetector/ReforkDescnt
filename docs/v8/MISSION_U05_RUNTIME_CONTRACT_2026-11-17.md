# U05 mission runtime: source audit and proposed first contract

Review source **522ee0765fe1a439b0a079120d5282f498c64533**, isolated worktree
`/workspace/ungravity-v5/mission-runtime-20261117`, branch
`experiment/mission-runtime-20261117`. No main edits or Java execution. The accepted
20ef same-JAR snapshot remains separate from this newer audit source.

Authority: masterv8 and its SWARM_FACTIONS_SURVIVAL/PLANET_RING_CITADEL_LABS files,
direct Dynamic Boarding supplement, retained PS1 requirements and later Create-style
reusable-mechanism instruction. Scope is U05/S14-T05 and the compact pacing part of
S13-T06; it does not complete physical labs, Boarding, convoys or faction tiers.

## Actual source findings

| Existing integration | Located behavior and consequence |
| --- | --- |
| EnclaveQuest.offer(EnclaveSite) | Deterministic six existing kinds and titles only; no instance, objectives, extraction or difficulty. Keep these IDs/origins. |
| EnclaveHeraldBlock.onUse | Every sneak-use calls FactionMemory.addRep(+3) immediately, including Creative; repeated acceptance farms reputation. Ordinary use displays a title. |
| FactionMemory | Existing overworld PersistentState `drmd_faction_memory`, player/origin reputation, clamp[-100,100], broke-control flag and legacy NBT. Put mission progress/claim and its reputation effect in this same state. |
| WorldEventSystem/WorldEventState | Existing world-event spine; tick is disabled by LegacyGeneration.retired()==true. Its D6WorldEvent has no mission owner. Do not reactivate or mistake diagnostic event completion for an attributed objective. |
| Active Fabric hooks | END_WORLD_TICK, AFTER_DEATH, player disconnect/world-change hooks already run independently for threat/physics. Use ordinary active callbacks for the missing mission adapter, not another world-event director. |
| FacilityReactorFight | Existing reactor escape subsystem, static ACTIVE/BY_CORE, ownerless world effects and packed-position identity. Not a saved mission engine or a completion/attribution source to trust unchanged. |
| EnclaveSite.generate | Site selection uses X/Z384 cells; generated anchor Y72 is presentation/legacy generation metadata. Mission positions must use the actual clicked/authored herald and signed X/Y/Z, not this fake altitude. |
| WorldAccess/WorldEditSafety | Existing backend-aware loaded reads, FULL-volume availability, actual bounds/border and modification guards. No new tickets, generation, dimension or world writer is needed. |

No new faction names, five-tier canon, reward items/costs, raids, enemy spawns,
factory chains, imported maps or physics/render changes follow from this increment.

## Minimal playable first slice, subject to ROOT review

One explicitly bound **Lab Infiltration prototype**, mapped to existing
`EXPLORE_SHAFT` at a compatible existing MINERS herald:

1. Actual owner sneak-uses the bound herald in Survival: ACCEPTED, reputation
   unchanged, no objective completed and no material/actor/world mutation.
2. After acceptance the same real owner enters the authored loaded entry volume.
3. The same real owner successfully mines the bound `minecraft:iron_block` panel
   through ordinary server mining. The actual AFTER callback must carry the
   matching previous state/site/world and follow step2. All objectives are now
   ready, **reputation is still unchanged**.
4. Owner physically returns and sneak-uses that same live bound herald. This is
   explicit extraction; the single state transition records extraction, a claimed
   receipt and the existing +3 reputation delta toward the bound existing origin.
   Existing[-100,100] clamp remains. Repeated use/read/reload never grants again.

The iron panel is an existing authored-block target, not a new canonical machine.
An unstable reactor is deliberately not used: its replacement already invokes the
legacy facility breach/carve path. The first prototype does not claim the later
mines/gravity route, complete lab layout, real UFO or convoy mission.

Only a compatible, explicitly authored/bound site can offer this playable contract.
Other existing titles remain visible but unbound/unsupported acceptance reports
unavailable and grants nothing; it must not pretend to start a working mission.
Existing reputation and engineer/cult help thresholds are preserved. Missing
playable contracts for those origins remain an explicit partial boundary.

## Identity, authority and bounded site admission

The existing FactionMemory instance is resolved only from the actual event world's
server overworld. It remains save-scoped. A ledger-scope UUID is first minted on
explicit bind/accept mutation and persisted inside its new mission compound;
this is a mission namespace, **not another global world-identity service** or a
dependency on the city save-ID file. Fresh saves differ; whole-save copies inherit
their saved mission namespace/progress as normal copies. Raw manual transplantation
of an entire FactionMemory file is not authenticated or claimed safe import.

Site identity: immutable site UUID + ledger scope + actual dimension ID + separately
stored signed int herald X/Y/Z + contract ID/hash/version + actual seed/backend/
bounds captured at bind. Instance key reserves `(owner UUID, site UUID, contract ID)`
across definition revisions. New definition versions never reset a claimed key.
No BlockPos.asLong/2D cell/seed-only identity. Offset arithmetic uses checked long/
int operations; regions must stay inside actual backend Y limits and world border.
No WorldLevels/LayerBridge/EnclaveSite.Y72 substitution.

Normal acceptance/progress/extraction requires the actual connected, alive Survival
ServerPlayerEntity, PlayerManager UUID identity, matching live ServerWorld/dimension,
finite position and allowModifyWorld. 6DoF suit use is independent of that mode.
Creative/Spectator/dead/foreign/disconnected/wrong-world actors cannot advance or
claim. Normal block interaction uses its real hit/interaction path, actual current
block-interaction reach and loaded segment; no client-supplied objective/reward DTO.

An explicit Creative OP2 player-only authoring command may **bind/inspect only** an
already built site, e.g. `/ungravitymission bind <contract> <herald x y z> <anchor x y z>`.
Contract-relative entry/target offsets resolve from that actual anchor. It verifies
the exact live herald and target state, compatible offered kind/origin, separate
entry/extraction, current permissions, bounds and loaded volumes before one state
admission. It places nothing. Console/unprivileged modes refuse; no complete,
advance, extraction or reward command exists. Rebind/overwrite/ownership transfer
is not in the first slice. Reloaded/bad contexts refuse rather than relocating a site.

Herald.onStateReplaced invalidates its binding when the actual block type changes,
including explosion/world-edit replacement; replacement at the same coordinates
cannot silently inherit a live site. This is state invalidation only, no terrain
write. Missing/unavailable target/herald never auto-completes a step. Unknown
backend/context/definition or invalidated site reports a blocked reason.

## Existing state, exactly-once claim and cold-save boundary

Extend FactionMemory, not a second mission PersistentState. Mission instance,
objective cursor, owner/site/world identity, extraction/claim receipt and reputation
change serialize into the **same** NBT state as current players/rep/broke flags.
The server-thread claim operation validates everything before mutation, then commits
the claim bit and clamped rep delta together without a callback/network yield.
There are no inventory/currency rewards crossing player.dat, no extra WAL or fsync
on the world thread, and no raw public reward operation exposed to commands.

Exactly-once means one reward in live state and after a successful ordinary save/
close/cold reopen of that same state. Before an unacknowledged vanilla save, a crash
may roll the whole state back; no hard-power-loss durability or item/state distributed
transaction is promised. Cold proof must use real PersistentStateManager disk output
and a separate reader process, not only writeNbt/fromNbt DTO roundtrip.

Legacy saves lacking the mission compound retain all reputation/broke behavior and
start with no missions. Unknown/future mission schema is quarantined and its raw
NBT compound preserved on write, with mission mutation/reward refused; do not erase
it by treating it as empty. Corrupt instances/keys retain reserved IDs/tombstones
and opaque data; never drop a claimed record and allow another reward. Over-limit
or malformed load fails closed for missions while leaving legacy faction fields
usable. Unknown origin IDs are strict errors here; EnclaveOrigin.byId's legacy
SCAVENGERS fallback is not a mission reward policy.

Disconnect/death/mode/world mismatch stops observation/acceptance/claim without
offline progress. Persisted objective cursor stays; only fresh real owner events
after reconnection can advance. No replay inferred from a now-absent target.
Current context is rechecked before each step/extraction. No mission launches on
world start/load; data reload only validates definitions.

Feedback follows the authoritative state operation. A dropped/failed notification
cannot undo the claim, trigger a second reward or convert a refusal into acceptance.
Actual interaction can query status again. Packet acceptance tests allow the normal
end-of-tick flush and inspect the next existing tick; no manual flush/injected text.

## Data and work contract

Proposed schema1 data: ID/revision, existing offered kind, title key, difficulty
label1, ordered objectives, local entry box/target block offset+expected block ID,
extraction-at-origin-herald, existing reputation reward3. No arbitrary Java names,
commands, entity spawn instructions or scripts. Difficulty is mission metadata,
not approval of faction technology tiers or a hidden damage/resource modifier.

Initially supported generic hooks: ENTER_VOLUME and successful OWNER_BREAK_BLOCK;
extraction is explicit herald use. Another validated data contract with different
bounds/order/target uses the same adapter, not mission-ID Java branches. Reactor
Boarding and Convoy Ambush remain subsequent contracts through this engine only
after actual body-control/target/convoy adapters exist; unsupported types fail closed,
not simulated success. Do not count title/data presence as those missions working.

Proposed limits, operational rather than balance:32 definitions/64KiB each,
8objectives,64bound sites,64owners,256total reserved instances,1active per owner,
at most27FULL cubes in a compact entry region and64owner observations per world
tick. Direct herald/break events look up owner/site without scanning world entities
or blocks. Refuse new admission at caps; never evict reward tombstones to make room.
No chunk getter, ticket, force-load, actor creation or filesystem worker with world
references. New model/catalog state is server-owned; no renderer/camera mutation.

## Proposed files and concrete first checks

Existing files to change only after ROOT review:

- `world/enclave/FactionMemory.java`: same-file ledger/claim/rep and versioned NBT.
- `world/enclave/EnclaveHeraldBlock.java`: actual accept/status/extract instead of
  +3-on-accept; site invalidation on actual block replacement.
- `DescentMod.java`: one ROOT-coordinated EnclaveMissionSystem.register call.

Small new components in the **existing** `world/enclave/` package:
`EnclaveMissionContract`, `EnclaveMissionCatalog` (bounded SERVER_DATA reload),
pure `EnclaveMissionLedger`, and `EnclaveMissionSystem` (existing-hook adapter and
bind/inspect command). Internal FactionMemory mutation can remain package-private;
there is no external raw-DTO progress/claim endpoint. One JSON under
`data/drmd/missions/` plus a second data-only variant verifies that execution is
not hardcoded by mission ID. No
changes to EnclaveOrigin/tech tiers/AI director/worldgen/physics/client render.

First acceptance order, each with exact source and preserved first failure:

1. Baseline real Survival ordinary herald interaction: three sneak-uses leave rep
   unchanged. Existing source is expected to fail with +9; no new API is needed
   to expose the bug. Preserve the real interaction result before a production fix.
2. Pure protocol/schema controls: ordered steps, dedup, owner/site/world/version
   mismatch, signed Y outside packed-coordinate range, bounds/overflow/caps,
   corrupt/future/legacy NBT and same-compound claim+rep semantics.
3. Actual dispatcher authoring plus real owner interaction/mining: bind is inert;
   accept is not completion; other owner/wrong site/out-of-order break is ignored;
   fresh real owner entry then successful mining reaches extraction-ready with rep0.
4. Actual early/foreign/Creative/Spectator extraction refuses; eligible owner use
   grants once, repeat use/status/duplicate callbacks grant zero. Real notification
   receipt is checked after normal flush; failed feedback cannot duplicate a claim.
5. Actual unavailable/out-of-bounds/invalidated site refuses without chunk demand,
   world writes or reward. Signed below-zero and supported upper-Y site identity
   persists without aliasing. Definition change/corrupt/future state stays blocked.
6. Real cold writer/reader: save an in-progress instance, close, reopen and finish
   via real events/extraction; save/close/reopen again and verify same claimed ID,
   rep exactly3 and no second reward. Ordinary fast GameTests are not this cold proof.

Pure/actual/cold counts and exact isolated manifests will be frozen only after
review. No Java lease is assumed, no test-only bootstrap may mask production
registration, and no task status or full-three-mission completion is promoted here.
