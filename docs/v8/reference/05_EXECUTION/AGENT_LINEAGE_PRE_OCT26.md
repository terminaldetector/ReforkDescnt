# CODE / AGENT LINEAGE AUDIT — PRIOR TO OCT 26 + CURRENT RECONCILIATION
**Requirement from author:** include modifications completed before conversation Oct 26, not just today's tickets. This is a **verified metadata** index from public connected GitHub read and historic v5. Runtime of exact `drmd 1.1.14-audit.4` still unlinked to SHA.

## Known Git refs (observed from GitHub 04.11)
| Ref | SHA | Evidence | Action |
| --- | --- | --- | --- |
| `codex/d6-spatial-physics-integration` PR #9 | `963375bf60830461f338ed440dac1aed1d6b9aae` | title `Unify DRMD flight, world settings and experimental terrain systems`; PR open | inspect/compare, not automatically cherry-pick |
| `codex/ungravity-cloud-recovery-20261024` | `93baf2a4926428e7a21f515c2927eb3a5a511413` | 24.10 Workshop and first-person gun passes | regression current JAR |
| default `claude/trusting-dijkstra-xuin48` | `74d500677a1008f42f159a6272258319d25ebe02` | old default, September commit | do not treat as latest production |
| `cursor/ordnance-cluster-rockets-02fc` | `9c8706de29650d45ab29dad19f1680b3b17fcf8a` | historical branch | compare cassette behavior |
| `cursor/megacity-complex-02fc` | `0ad53e2e8ef64d996851ee557e8b1b62e1271cbf` | historical branch | compare worldgen before deletion |
| `claude/reactor-rollback-world-loading-owszu7` | `60ad79892f3f64aca23f0f67b09dccd59462cb26` | historical branch | audit rollback history and reactor |

## Specific commits to audit (not verified as running)
- `9e15d8b` Oct 2: block bodies, portal frames, spatial systems.
- `0345128` Oct 7: hybrid mesher and chunk-batched microdamage.
- `82f3ec1` Oct 7: falling tree/columns block bodies.
- `30523be` Oct 7: gravity torches and anomalies.
- `8ef153d`, `b72c735` Oct 8: collapse + local gravity, dimension isolation.
- `0ac4ae0`, `c206342`, `d0361b1` Oct 8: articulated/networked ragdolls, client.
- `97142ff`, `550c87e` Oct 10: contact solver and point effective mass.
- `f1d7904`, `18feed8` Oct 10: bounded swarm neighbor work and 128-drone fixture.
- `4c7c696`, `b43d3c1`, `46d6433`, `2414fc1` Oct 24: visible fast shots and stable fixture.
- `7ac1005`, `be0be13`, `ca85f4e`, `dd9dea1`, `93baf2a` Oct 24: Workshop meshes, root NBT, two-sided preview and rail carriage.

## The significant PR admission
PR #9 states no complete CubicChunks engine; vanilla owns generation, chunk lifecycle, lighting, networking and rendering; cubic feature stores supplemental 16³ snapshots. NoCubes full world and volumetric LOD absent. Build claims in PR, including CI, cannot be translated into current JAR success.

## Required `HISTORICAL_FIX_RECONCILIATION.csv`
Columns: `agent,worktree,commit,component,claimed_effect,current_JAR_SHA,reproduced_before,reproduced_after,unit_result,gametest_result,client_result,regression_status,evidence_path,decision`.
States: `CONFIRMED_CURRENT`, `REGRESSED`, `PARTIALLY_VALID`, `OBSOLETE_AFTER_CUBIC`, `UNVERIFIED`, `BLOCKED_BY_UPSTREAM`. No mass code removal of a working piece without `reproduced_after` and test.

## Local and unindexed work
Look for local agent sessions, unpushed branches, build artifacts, alternate worktrees, patches and archived ZIPs. If unavailable, put **NOT PROVIDED**. Do not say missing work was never done. In particular UFO after Oct 29 was reported as near completion but is not attested by this GitHub inventory.

## GitHub reference links
- https://github.com/terminaldetector/ReforkDescnt/pull/9
- https://github.com/terminaldetector/ReforkDescnt/tree/codex/ungravity-cloud-recovery-20261024
- https://github.com/terminaldetector/ReforkDescnt/tree/codex/d6-spatial-physics-integration
