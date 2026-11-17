# Non-autostart siege intent: scoped first22unit/13server proof

Contract/protocol: `39c2517` / `dca31b2`. Implementation is isolated on
`experiment/siege-plan-intent-20261117`, based on accepted `6b50273`. Exact
tested source **`83a12773253ca01fa2f9518bc4297a25fde8c675`** passed the first
22 unit and13 stock GameTests with no failures/errors/skips. This later report
does not change the tested revision or claim live siege/full integration.

The packet adds pure `SiegePlan` intent/order/snapshot types and an optional
bounded `SiegePlans` codec within `SurvivalThreatState`. It changes no existing
phase/director/actor/goal/projectile/mechanism/recipe policy. No production
caller advances a plan, obtains a fire permit or consumes its output. There
are no new registrations, ray/entity queries, notices, chunk requests or I/O
tasks. Only the existing PersistentState save path serializes the extension.

The data protocol keeps at most8 records, one decoded intent per participant,
two future identities and10 accepted projectile IDs. Those identities never
reserve/consume live recon slots. Per-slot proposed allowance is5, pending
permits are instance-bound/current-tick/transient, ACKs are append-only and
late/duplicate/foreign acknowledgements cannot refill/replay them. All pure
phase deadlines use the existing grace/warning/search/loss/cooldown constants;
these proposed order values do not establish live siege balance.

Missing extension leaves legacy schema1 defaults. Known valid neighbours and
raw corrupt records coexist without erasure; unsupported/oversized subtrees
are owned opaque read-only copies. Legacy player progress, actor slots,
cooldowns and victim dedup retain their old codec semantics. New plan updates
cannot change identity/origin, restart a fixed warning/encounter, drop ACK
history, rebind an existing future identity or impersonate a live recon actor.

The optional unchanged-source `dca31b2` four-case baseline was **not executed**.
Its declared3 feature-absence failures/1 control pass are expectations only,
not observed failures or a chronological pre-code runtime claim. All four raw
semantic cases remain unchanged in the proposed positive gate. No disabled
hook, fake combat negative or stale marker substitutes for an actual run.

The first gate ran **22 unit tests** (13 new protocol cases +9 retained threat
cases), then **13 stock GameTests** (four raw codec cases, four new state/
isolation cases and five unchanged real threat regressions). Both actual child
exits were0, with unchanged1596 tracked build-input hashes throughout. Actual
compressed file write/reopen, opaque/oversized compatibility, finite ACK/no-
permit restoration, cap and nonreservation/rollback controls passed. Generated
`.dat` files, their hashes and expected managed state were copied into fresh
test-only proof directories before temporary input files were cleaned.

The no-activation fixture used a normal connected Survival player, non-Peaceful
world and enabled mob spawning. It waited real ticks through PLANNED, a full
400-tick WARNING and a supplied SIEGE DTO; the production director did not
advance a stored snapshot, add a rocket/roster slot or leave ordinary QUIET.
DTO observations/ACK IDs are protocol fixtures, not actual LOS/weapon-emission
proof. Setup pose/no-gravity is declared state-test infrastructure, not player
escape or novice survival balance. The retained threat cases exercise actual
alarms, recon, search/loss, warning, saved legacy state and unavailable spawn.

The first source/input/helper hashes, compiler logs, fresh unit/GameTest XML,
actual exits and saved-file/world artifacts are preserved below. Dedicated
client/native, ordinary full integration and installable JAR acceptance are
pending. Full
autonomous SIEGE, ground rocket infantry, authenticated observation producer,
priority/fire adapter, friendly modular AA and complete S12 remain open.

## Preserved exact first outcome

No source/helper/assertion change or retry occurred during the lease. It was
returned immediately after close with no executing JVM. The first unit worker
classpath/properties remain in the raw XML. Original GameTest task/build inputs,
bootstrap logs, focused manifest and actual compiled classes are retained; an
exact separate JVM-classpath property was not printed by that first runner.
No stale/current-cache reconstruction or installable-JAR claim replaces it.

Actual enabled-Survival receipt: PLANNED worldTime649, WARNING669 with full
deadline1069, explicit SIEGE DTO1069, completed1089. That is **440 actual world
ticks**, normal20HP, live roster0 and unchanged stored intent at every check.
These are non-activation observations, not automatic sight/attack transitions.

Three original GZIP PersistentState files were independently decoded after
close, with GZIP CRC and exact NBT consumption:

| Actual file | SHA-256 | Preserved state |
| --- | --- | --- |
| history,761bytes | `2890ae56ad1022bf2bde348519e43b56a43da4d7e88654cb85c50e5f6375a518` | One intent, one ACK, two future IDs; legacy player1/actor1/victim1 and spare recon allowance unchanged |
| future-opaque,642bytes | `3165025eab4b87de0e6c18a5b0f66106d1a5f7fdc420abb153f803d278d402c6` | Extension schema37 retained opaquely; same legacy1/1/1 |
| oversized,904bytes | `b4e7cf172022fa8b7914d63a2306399c7295b7fc3011a1f16ad15ca4b080f0c7` | All9 raw records preserved; read-only/no decoded authority; same legacy1/1/1 |

Bundle: `EVIDENCE/siege-plan-intent-first-positive-83a1277.zip`.
SHA-256: `78e7073f018e13874d81a372a1daa1a25a1ef39f90f1aa2260a4aa7aebced890`.
All132 payload SHA-256 and ZIP CRC passed: exact source/diff,1596 input hashes,
initial snapshots/frozen helpers, original logs/XML/first exits, actual compiled
classes, all original ledger/tick proofs and49 closed-world files
(9,912,304 bytes). The raw original optional negative remains **unexecuted**.

Only two new ordinary GameTest entrypoints are required:
`SiegePlanCodecBaselineGameTests` and `SiegePlanStateGameTests`, eight new cases.
The five retained `SurvivalThreatGameTests` and nine retained threat units
already belong to the ordinary suite; do not duplicate them. New automatic
unit discovery adds13. Root review and a new integrated source/full gate are
still required. This packet is not present in root's accepted20ef game binary.
