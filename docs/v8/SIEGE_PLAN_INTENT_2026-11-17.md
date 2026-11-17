# Non-autostart siege intent: first source packet, verification pending

Contract/protocol: `39c2517` / `dca31b2`. Implementation is isolated on
`experiment/siege-plan-intent-20261117`, based on accepted `6b50273`. This
report currently records unverified source, not a gameplay or test positive.

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

Prepared gate: **22 unit tests** (13 new protocol cases +9 retained threat
cases), then only if they pass **13 stock GameTests** (four raw codec cases,
four new state/isolation cases and five unchanged real threat regressions).
Expected assertions include actual compressed file write/reopen, opaque and
oversized payload compatibility, finite ACK/no-permit restoration, cap and
nonreservation/rollback controls. Actual generated `.dat` files, their hashes
and expected managed state are copied into fresh test-only proof directories
before temporary input files are cleaned.

The no-activation fixture uses a normal connected Survival player, non-Peaceful
world and enabled mob spawning. It waits real ticks through PLANNED, a full
400-tick WARNING and a supplied SIEGE DTO; the production director must not
advance any stored snapshot, add a rocket/roster slot or leave ordinary QUIET.
DTO observations/ACK IDs are protocol fixtures, not actual LOS/weapon-emission
proof. Setup pose/no-gravity is declared state-test infrastructure, not player
escape or novice survival balance. The retained threat cases exercise actual
alarms, recon, search/loss, warning, saved legacy state and unavailable spawn.

No Java has run on this packet yet. The first source/input/helper hashes,
compiler logs, fresh unit/GameTest XML, actual exits and saved-file/world
artifacts must be preserved before any correction. Dedicated client/native,
ordinary full integration and installable JAR acceptance are pending. Full
autonomous SIEGE, ground rocket infantry, authenticated observation producer,
priority/fire adapter, friendly modular AA and complete S12 remain open.
