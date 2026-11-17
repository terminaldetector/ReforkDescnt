# S12 economy: first actual negative

Tested source: **`2568f8d737d597b35a13591637cf737b6907afe2`**, based on
`5839062`. This report/evidence checkpoint is separate from the tested source.

Main/GameTest compilation passed, process exit 0. The first focused run completed
all six cases with **1 PASS / 5 FAIL**, game exit 5 and Gradle exit 1. No source,
recipes, thresholds or assertions changed during this lease. All 1,472 captured
tracked build inputs matched after the run. No installable JAR, complete suite,
unit, native/client or performance run was requested or claimed.

| Case | First observed result | Classification |
|---|---|---|
| Real three-component crafting | Laser, rocket and thruster attempts all yielded `drmd:ship_thruster` through the ordinary result slot | Confirmed identical-recipe acquisition defect; material consumption checks completed |
| Crafted placement/assembly/bearing/drive | PASS | Actual Survival crafting, item consumption, world placement, wrench assembly/link/control and real physical motor work on the same bodies |
| Crafted laser/rocket firing | FAIL while acquiring `ship_laser`, actual output `ship_thruster` | Acquisition prerequisite blocked; firing behavior was not evaluated |
| Mining/pickup/replacement salvage | FAIL while acquiring `ship_laser`, actual output `ship_thruster` | Acquisition prerequisite blocked; recovery behavior was not evaluated |
| Local moving-mechanism repair | FAIL because the actual crafted MG did not establish partial damage on the selected drive within the fixture interval | Aim/projectile/damage prerequisite unresolved; **not evidence of a reached BlockBody repair failure** |
| Cracked-material repair | Stone → stone, deepslate → stone, nether → stone | Confirmed material-family conversion defect after actual crafted tool use |

All three ordinary crafting attempts were performed before the acquisition
assertion; the original XML preserves every requested/actual output. The family
case similarly preserves all three observed repaired states. No `recipe.craft()`
grant, manual entity tick, target assignment, direct damage injection or automatic
production chain was used. The pending repair-damage predicate must still be
reached with an actual projectile before a production BlockBody repair change.

Evidence: [first-negative](SURVIVAL_ECONOMY_EVIDENCE_2026-11-17/first-negative/).
It includes the untouched first compile/runtime logs and exits, XML, actual
focused runtime manifest, init script, commands, before/after inputs, exact case
messages and a complete closed disposable world archive. Its 84 world files were
ZIP-CRC and individually SHA-256 verified against the originals before removing
only `build/gametest/world`. No user saves or dependency caches were removed.
`closed-world-manifest.json`, `verification.json` and `SHA256SUMS` record the checks.
The Java lease was returned after full process close; evidence preparation used
no additional Java launch.

After this immutable negative, root authorized only narrow production changes:
distinct full 3×3 ship recipes using the same eight alloy plates and one energy
cell, and correct stone/deepslate/nether world repair mappings. No ammunition cost
policy, new technology tier or BlockBody repair implementation is authorized by
this result. Separate fixture-only real-projectile diagnostics must establish why
the local damage prerequisite was not reached. S12 remains incomplete.
