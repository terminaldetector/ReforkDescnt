# S12 economy: actual guarded repair passes, two causes isolated

Exact tested source: **`3d4058108b1c19060eb8b04c708499c476f94e86`**.
Both tracked sources (1,472 files) and runner/helper hashes stayed unchanged for
the complete compile/runtime lease. Compilation passed, exit 0. The fresh focused
six-case run completed **4 PASS / 2 FAIL**, game exit 2 / Gradle exit 1.

Passing cases now include:

- Ordinary UI acquisition of three distinct ship components with exact existing
  ingredient consumption, incomplete-grid denial and refund.
- Correct stone/deepslate/nether repair families.
- Actual Survival bearing/drive crafting, placement, assembly and physical work.
- **Actual moving-body partial repair:** a real crafted MG causes local damage
  and motion; the crafted repair tool reduces damage while world terrain cover,
  an intact nearer body, Spectator mode and absent-cell controls deny invalid
  repair. Body UUID, material mass, shape revision, collider identity, linear
  velocity and angular momentum remain unchanged by the valid partial repair.

The repair operation clears existing canonical loaded local partial damage only;
it does not create missing material or rebuild geometry/physics. The tool retains
its prior resource costs, uses its existing 16m range and requires authorized
loaded rays with terrain/nearest-body occlusion. These are scoped checks, not a
full server restart, network/client, economy-balance or world-storage acceptance.

## Remaining negative diagnoses

The salvage case now reaches an explicit real eligibility check instead of
silently waiting for an unharvestable component. For the actual crafted bearing,
with the declared diamond pickaxe, runtime reports:

```text
canHarvest=false requiresTool=true miningSpeed=0.2 onGround=false pickaxeTag=false
```

The metadata source agrees: ship components have mining tags, whereas bearing
and drive do not. No speed, hardness, drop or timeout change was made in this run.

The first lever placement selects the exact intended gun and upward face, with
an AIR destination and available volume. It is correctly rejected because the
test player's oriented collision box penetrates the destination cube by
**0.12000000476837158m**. The full real ray, intended cube, player position/frame
and contact are recorded externally. This is a fixture approach-position defect,
not evidence that production collision should be relaxed.

After this immutable evidence checkpoint, root approved only bearing/drive
additions to the existing metal-component mining-tag policy and a fixture UP
approach of 3.0m instead of 2.5m. Reach 4.5m, the 400-tick watchdog, resource costs,
hardness/drop rules, exact ray/AIR/availability and collision assertions remain.

## Evidence

[local-repair evidence](SURVIVAL_ECONOMY_EVIDENCE_2026-11-17/local-repair/) preserves
fresh logs/exits/XML, before/after inputs and helper hashes, the focused runtime
manifest, ten actual diagnostic reports and the complete closed disposable world.
All 86 archived world files passed ZIP CRC and individual SHA-256 checks before
only that fixture world was removed. No user saves or dependency caches changed.

Full diagnostics are in report files and assertion messages are short; the prior
1,024-character failure-lectern save error did not recur. Earlier 1/5 and 3/3
evidence remains separate and unchanged. No full regression suite, unit suite,
native/client run or installable JAR was produced by this lease. S12 remains open
pending the final focused checks and wider integration/acceptance.
