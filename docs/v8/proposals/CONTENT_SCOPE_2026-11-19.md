# Authored world/faction/mission increment — three exact IDs

Authored against canonical f1cdb19153fd70050531d166000f0e5017e84951.
Primary references are MASTER v8 and ENGINE B / PS1, retaining the direct
Dynamic Boarding and Create-style independent weapon/bearing/drive constraints.
This packet contains original prose/data; no proprietary maps/models/assets.

| ID | New concrete deliverable | Exact coverage and remaining boundary |
| --- | --- | --- |
| S13-T01 | `PlanetaryZones` derives seven contiguous signed half-open metadata bands from running dimension bounds and an explicit sea-level surface reference. The existing `WorldLayer.display` → `DescentHud` path uses it live. | Core/mantle/underground/surface/sky/upper-atmosphere/orbit are deterministic, finite, immutable and do not depend on retired Advanced constants. Bounds are **registry metadata**, not FULL/ticking/packing or physical-traversal authority. No generated core/labs/stations or world migration is claimed. |
| S14-T01 | `five_tech_candidates_v1.json` gives five distinct versioned capability proposals with all requested economy/weapons/transport/patrols/structures/AA/threat/materials/missions/visual fields. | The requested five-level **design proposal** is concrete. Every row stays CANDIDATE_ONLY/authorApproved=false; no canonical names, chronology, faction assignment, legacy tech0..3 mapping, recipes, unlocks, stats or live pressure changes. Author approval and gameplay balance are separate. |
| S14-T05 | `mission_contracts_v1.json` writes original Lab Infiltration, UFO Reactor Boarding and Convoy Defense contracts, compact room/route blockouts, ordered real-event identities, owner/permission/failure/extraction/reward/cold criteria and counterplay. | Authored **mission contract** coverage across all three scenarios. Existing lab implementation is reused, not counted as newly implemented. Moving-body/convoy adapters are explicitly UNAVAILABLE and these documents are outside runtime discovery. No fake ENTER_VOLUME/BREAK_BLOCK substitution or nonexistent playable UFO/convoy mission is claimed. |

The live S13 display is intentionally observational. `WorldLayer.at`, LayerBridge,
spawn/raid logic, atmosphere/flight physics, block generation, portals, storage,
server registration and shared ledgers are unchanged. Small or invalid dimensions
cannot invent missing zones; display returns Unmapped/Outside. Relative span caps
keep metadata landmarks near the surface reference instead of scaling mission
distances to an enormous native dimension's total height. No level teleport or
interior dimension is introduced.

Eight new meaningful units cover complete signed bounds, every half-open edge,
integer extremes, translation invariance, unmappable inputs, nonfinite positions,
immutable determinism and the exact live HUD resolver. One ordinary
GameTest checks all seven labels through `WorldLayer.display` on its actual
ServerWorld plus real exclusive boundaries; it writes/spawns/loads nothing.
The eight unit tests and one ordinary GameTest passed in the [integrated run](../TASK20_RESULT_2026-11-19.md).

CPU-only checks have parsed both JSON files, verified exactly five distinct
unapproved candidates and exactly three contracts with honest adapter statuses.
Read-only named MC1.21.1 class inspection confirms World.getSeaLevel()I exists;
this does not replace a fresh compile or runtime proof.

S13-T02/T03/T04/T06 remain separate authored-geometry/navigation work. City CAS,
old layer scaffolds, these metadata zones and written mission routes do not
establish physical labs, volumetric near/mid/far station LOD, a boardable ring,
Citadel or cross-layer pacing. S14-T01 approval and S14-T05 full runtime remain
open beyond the exact design deliverables above.
