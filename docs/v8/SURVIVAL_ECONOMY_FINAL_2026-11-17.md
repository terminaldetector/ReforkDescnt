# S12 economy: six actual Survival checks pass

Exact tested source: **`16a25a96e2d3b51c425a04534e15fe81f6881f95`**.
Main/GameTest compilation passed, exit 0. The fresh focused run passed **6/6**
GameTests with game and Gradle exit 0 and normal server shutdown. All 1,472 tracked
build inputs and runner/helper hashes remained unchanged throughout the lease.
This later report/evidence commit is not the tested source revision.

## Verified behavior

| Actual Survival path | Result |
|---|---|
| Crafting table UI | Laser, rocket and thruster are individually craftable; each still uses eight alloy plates and one energy cell. Actual result-slot consumption, missing-input denial and refund pass. |
| Mechanism construction | Crafted bearing/drive/wrench are placed, assembled and connected through actual item/material interactions; the same physical bodies perform real motor work. |
| Mounted combat | Crafted laser/rocket placement consumes items once. Real placed levers and the production post-contact schedule emit actual body-owned projectiles. |
| Recovery | Bearing, drive and laser are mined through real timed Survival pulses; one actual loot entity is picked up and the recovered item is placed once. All three report `canHarvest=true`, mining speed 1.6 while airborne. |
| Moving-body repair | Actual crafted MG projectiles cause local partial damage and physical motion; the crafted tool repairs it while preserving UUID, grid revision, collider identity, mass, linear velocity and angular momentum. World cover, nearer intact body, Spectator and absent-cell controls deny invalid repair. |
| Material repair | Cracked stone, deepslate and nether bricks retain their respective material families. |

Production changes are narrow: mirror-safe full 3×3 ship recipes with unchanged
ingredients/IDs; correct world brick mappings; server-owned repair of existing
loaded local partial damage without topology/physics rebuild; the existing repair
tool's authorized loaded 16m ray with nearest-body/terrain occlusion; bearing and
drive added to the existing pickaxe/iron-tool metadata policy. There is no new
per-shot cost, factory chain or approved technology tier.

## Preserved causal evidence

| Tested source | Original result | What it established |
|---|---|---|
| `2568f8d` | 1 PASS / 5 FAIL | All three actual crafting attempts yielded thruster; brick repair converted deepslate/nether to stone. The MG had not reached its damage prerequisite. |
| `a738b34` | 3 PASS / 3 FAIL | Recipe/family fixes pass; actual offset MG barrel reaches the drive and damage 11→11 confirms the physical repair failure. Salvage timeout and lever rejection remain. |
| `3d40581` | 4 PASS / 2 FAIL | Guarded physical repair passes. Real diamond pickaxe cannot harvest an untagged bearing. Correct lever ray/AIR/loaded volume is rejected for the fixture player's **0.120000004768m** destination penetration. |
| `16a25a9` | 6 PASS / 0 FAIL | Limited mining tags and fixture-only UP approach 3.0m resolve those causes. Production placement, reach 4.5m, watchdog 400, hardness, loot, costs and collision assertions remain unchanged. |

All original logs/XML and diagnostics remain separate and immutable under
[the evidence directory](SURVIVAL_ECONOMY_EVIDENCE_2026-11-17/). The earlier a738
failure-lectern chunk-save error is preserved: its 3,805-character diagnostic
exceeded Vanilla's 1,024-character page limit. Full diagnostics now go to files,
with short assertion messages; the error did not recur. No failed run was relabeled
as a pass, no direct damage/recovery/product grant replaced actual gameplay, and
no timeout/reach/collision threshold was weakened.

## Final evidence and limits

[final6](SURVIVAL_ECONOMY_EVIDENCE_2026-11-17/final6/) contains original compile/run
logs and explicit exits, fresh XML, runtime focused manifest, source/helper
manifests, 1,586 compiled/resource hashes and actual placement/shot/harvest reports.
The complete closed disposable world is archived: 81 files, 8,779,230 bytes,
1,107,046 ZIP bytes. ZIP CRC and every file's SHA-256 matched the original before
only that fixture world was removed. `closed-world-manifest.json`,
`verification.json` and `SHA256SUMS` preserve checks; user saves/caches were untouched.

Starting material stock, table, neutral iron carrier and one mining pickaxe are
declared test infrastructure. These checks prove acquisition/use/recovery/repair
from available material, not ore availability or a full novice playthrough.
No integrated full suite (including root's forthcoming 404-case run), new unit
suite, native/client, network/two-JVM reload, performance benchmark or installable
JAR was produced by this lane. Plasma/AA balance, repair/resource-loss policy,
unprovoked hunt/full siege and the remaining S12/S13/S14 criteria remain open.
