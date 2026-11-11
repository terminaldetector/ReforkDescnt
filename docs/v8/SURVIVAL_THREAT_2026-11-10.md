# S12-T01: persisted Survival recon — scoped proof

Tested source: **`24462277964ab81bd8d343267f5433234567fb38`**, on
`experiment/world-content-20261109`, based on `4b2e243`.
The report/evidence commit is later than that tested source and does not turn
these focused checks into a complete project, client or installable-JAR proof.

Result: main and GameTest compilation succeeded; **9/9 unit tests and 5/5 focused
GameTests passed** on their first positive run. No disabled-hook synthetic negative
was run. No production source changed between input capture and completion.

## Resulting behavior

A confirmed, canonical machine death caused by an actual Survival player can
provoke one encounter. Creative kills, environmental deaths, live aliases,
duplicate victim callbacks and disabled mob spawning do not create alarms.
The event records the actual victim location and the existing hostile-machine
coalition; it does not invent approved faction tiers or modify enclave reputation.

The server maintains online novice grace, a fresh warning, recon/search,
actual-observation hunt, loss-of-sight search and cooldown. Existing ORB identity,
roles, target selectors, navigation and weapons own combat. The director never
assigns a target or enables retired Advanced/world-generation systems.

- Grace: 12,000 online Survival ticks; warning: 400 ticks; loss of sight: 200.
- Search: 1,800 ticks; the whole search/hunt encounter is bounded to 3,600 ticks.
  Defeat or escape also leads to a saved 12,000-tick cooldown.
- Initial pair allowance: two lifetime ORBs per encounter, eight roster slots
  per world. Early detection permits the second initial slot, not replacement waves.
- Saved state: at most 1,024 participants, 128 deduplicated victim alarms and eight
  actor identities. Unloaded actors retain roster slots until their expiry.
- Tagged actors absent from the authoritative saved roster are discarded on load.
  Expired unloaded actors cannot reappear as uncounted additional patrols.
- Each fifth server tick checks at most four players, eight candidates and eight
  sight rays, and admits at most one actual actor. No tickets or generation calls
  occur in production spawning. Candidates require actual entity-ticking space,
  loaded cubic availability, world border/height clearance and hull clearance.
- Actors have a 4,800-tick lifetime. Actual visible combat can extend retirement
  in 200-tick steps, so an engaged enemy does not disappear in front of a player.
  This does not extend the director's finite encounter or mint another pair.

Implementation: `world/threat/ThreatProgress`, `SurvivalThreatState` and
`SurvivalThreatDirector`; one `DescentMod` registration hook and namespaced
English/Russian notices. No enemy mechanics or low-level world/backend changes.

## What was exercised

The unit XML contains nine pure tests covering novice time, full warning deadlines,
last actual observation, finite repeated hide/reacquire cycles, defeat/cooldown
reload, lifetime allowance, early detection, escape/expiry and invalid snapshots.

| Actual GameTest | Result and concrete control |
|---|---|
| Confirmed signal | Real lethal damage and registered AFTER_DEATH callback; real Survival versus Creative/environmental/disabled-rule deaths; live/UUID alias and duplicate rejection; orphan entity admission rejection |
| Persistence | Minecraft PersistentState writes a real compressed file; a fresh manager reopens it with exact cooldown, actor IDs/expiry and victim dedup; corrupt neighboring record is isolated |
| Warning | Real world ticks before the stored warning deadline do not create actors |
| Natural encounter | Real registered ORBs naturally acquire a normal Survival ServerPlayer through existing selectors; solid world cover for 200+ actual ticks causes search at the last observation; killing the pair starts cooldown without recursive waves |
| Unavailable candidates | Out-of-height candidates are rejected within the work cap; synchronous director call adds no loaded chunks |

The player fixture uses Vanilla TestContext's EmbeddedChannel/connection recipe
with a **normal ServerPlayerEntity**, avoiding its permanently-creative anonymous
mock subclass. No `setTarget` calls, manual entity ticks or production time jumps
were used. Test-owned entity-ticking tickets and the actual mob-spawn rule are
leased and restored. Vanilla TestServer disables mob spawning by default; the
production director continues to respect that rule.

The encounter fixture pre-ages only its own persisted novice/warning state.
The full 10-minute grace and 400-tick warning arithmetic are unit-checked, rather
than claimed as a live 10-minute playthrough. The natural cover interval is
measured through real server ticks.

## Evidence and provenance

Directory: [SURVIVAL_THREAT_EVIDENCE_2026-11-10](SURVIVAL_THREAT_EVIDENCE_2026-11-10/).
It preserves the original compile and GameTest logs, XML, focused init script and
actual runtime test manifest, exact commands, 1,445 before/after build inputs,
1,708 compiled/resource output hashes and the recorded GameTest process exit 0.
All source input hashes match the clean tested SHA.

The complete closed disposable fixture world is retained as
`closed-threat-world-2446227.zip`: 77 files, 9,465,519 uncompressed bytes,
1,105,834 archive bytes, SHA-256
`931d8ffd2dbcf1fc589f9b493cb3224d0094d93742ed98dea35f619d05cd223e`.
ZIP CRC and every archived file's SHA-256 were verified against the original
before removing only `build/gametest/world`. No user saves or dependency caches
were removed. `closed-world-manifest.json`, `cleanup.json` and `SHA256SUMS` record
that procedure. No new Java run was made while preparing this report.

Startup logs retain optional BetterCubicChunks-class warnings, first-run missing
`server.properties`/EULA messages and refused Yggdrasil public-key access. They did
not prevent the server from running all five tests and closing successfully.
An evidence housekeeping helper found the optional `/tmp` design draft missing
after environment reset; the candidate design was reconstructed in versioned
docs. This happened after archive verification and did not affect test results.

## Open acceptance

**S12-T01 is partial.** Time-driven unprovoked hunts, full siege, faction reputation
inputs, novice defense/resource/repair/loss balance and a coherent day/night event
remain open. Existing gun/plasma/AA recipes are not proof of a balanced resource
path. The feature does not authorize automatic rocket/village bombardment.

This lease did not build an installable JAR or run the full regression suite,
native/client presentation, two real network clients, full-server restart with
loaded/unloaded patrol rehydration, cubic-backend integration, physical BlockBody
cover, maximum-cap/multiplayer stress or performance benchmarks. The real file
roundtrip proves the ledger persistence, not those broader save/network gates.
The out-of-height case does not substitute for a missing in-range cubic-volume
integration test. These remain explicit limits for root integration/review.

S13 production remains deferred by its Cubic/LOD/BlockBody core gates. The separate
[candidate design v1](WORLD_CONTENT_CANDIDATES_v1.md) contains proposed technology
capabilities and mission contracts, with no runtime activation or canon approval.
