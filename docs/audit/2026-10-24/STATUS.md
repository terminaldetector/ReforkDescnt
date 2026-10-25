# Video audit work record

The user requests concrete fixes following the supplied video audit. The installed video
JAR version is unknown (user confirmed 25 October). This report is contextual evidence,
not proof that every hypothesis affects the current recovered branch. Baseline under review:
93baf2a / published 1.1.13. Preserve the original recovery branch and PR9.

| IDs | Investigation / acceptance | Current finding |
|---|---|---|
| UG-001,009,021 | H, dismount, death/respawn, unload and reconnect restore control | CONFIRMED source defects: H does not dismount; ship tick re-enables disabled flight. Ship assigns blocks/second to per-tick velocity. Fix + regression tests in progress. |
| UG-002,007 | Finite lower-world traversal and recovery | INVESTIGATE actual dimension (-784..1888) and streamed lower geometry; snapshots are not unbounded cubic gameplay. |
| UG-003,005,006 | Horizon ON, DH ownership, altitude/roll/speed matrix | INVESTIGATE projection, depth and settings; white plates are not automatically terrain holes. |
| UG-004 | Explicit outside-world HUD classification | CONFIRMED fallback classifies every Y above the column as Oblivion. |
| UG-008 | Fire/lava obscuration and safe control recovery | INVESTIGATE; vanilla fire is not itself a mod rendering defect. |
| UG-010,011 | Gravity-frame recovery and transition-only notification | Existing !wasActive guard is insufficient for repeated field-edge capture/release. Investigate debounce and state cleanup. |
| UG-012,013 | Modal HUD and scaled settings layout | INVESTIGATE 720p / GUI scales with actual client frames. |
| UG-014 | Distinct item/egg icons | INVESTIGATE existing item models and textures; preserve item behavior. |
| UG-015 | Player-facing combat messages | INVESTIGATE raw ordnance identifiers; developer details belong behind diagnostic flag. |
| UG-016,017 | Bounded effects and clear central aim | Existing 1.1.13 checks cover weapon FOV/bank/vertical; add video-specific FX and menu cases. |
| UG-018,019,020 | Real swarm movement, bounded work, cleanup and measured timings | Existing 128-drone proof is not a 1024-drone performance claim. Add actual load/cleanup measurements. |

No item is closed by a source change alone. Record fresh unit/GameTest/client outcomes for
its exact revision. Preserve the first failure and mark infrastructure blockers separately.

On resume, ordinary git push lacks a credential helper, and the standard gh helper with the
injected GH_TOKEN is rejected as Invalid username or token. The user is checking the cloud
GitHub connection. Local commits/patches are not claimed as GHA checkpoints until pushed.

## Package A implementation (local, not yet certified)

- H-off clears authority before dismounting, clears held thrust/dash/hook/roll/velocity,
  restores world-up and Creative flight permission, and cannot be rearmed by a parked hull.
- Native H-on uses player 6DoF without manufacturing a Pyro entity. Existing physical
  vehicles remain explicit interactions; no replacement flight model or new Pyro asset.
- Ship velocity is converted from blocks/second to blocks/tick at the entity assignment.
- Dead/spectator pilots cannot retain/arm flight; death, respawn and disconnect clear control.
- In-flight input received while disabled/dead is ignored rather than rebuilding stale state.
- Three GameTests exercise actual H transitions, mount graph and ship velocity assignment.

First local check: `gradle test compileGametestJava --offline` stopped before compilation:
Fabric Loom 1.10-SNAPSHOT is absent from the local cache. See
`/workspace/audit-evidence-2026-10-25/first-local-check.log`. No Java/GameTest/client pass
is claimed. GHA remains pending restoration of the cloud GitHub credential.
