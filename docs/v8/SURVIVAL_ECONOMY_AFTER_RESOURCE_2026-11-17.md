# S12 economy: resource fixes and reached physical repair negative

Exact tested source: **`a738b34f14f946ecb6b86988c7ab22316668d10d`**.
Fresh world, unchanged focused helper, 1,472 before/after input hashes match.
Compilation passed (exit 0). Six actual GameTests completed **3 PASS / 3 FAIL**,
game exit 3 / Gradle exit 1. The lease closed and was returned before further edits.

## Reached results

- **PASS:** ordinary crafting UI individually acquires laser, rocket and thruster
  using their distinct full 3×3 patterns. Each still costs eight alloy plates and
  one energy cell. Real consumption and incomplete-grid refund checks pass.
- **PASS:** actual crafted repair use preserves stone/deepslate/nether families.
- **PASS:** actual Survival bearing/drive placement, assembly, linking and physical
  motor work remains valid on the same bodies.
- **Reached repair FAIL:** the actual crafted MG emits four real owned projectiles,
  spends energy 100→98.5 and causes partial drive damage **11.0** plus physical
  motion. The existing repair item leaves that same local damage **11.0→11.0**.
  The target cell remains a real rotary drive. This is the first reached physical
  repair failure, unlike the earlier camera-versus-offset-barrel prerequisite miss.
- **Salvage FAIL:** the real mining/pickup/replacement workflow reaches the existing
  400-tick fixture watchdog. Actual harvest eligibility/speed still needs a focused
  diagnostic; no increased timeout or injected recovery is a substitute.
- **Mounted fire FAIL:** after successful real weapon acquisition and placement,
  the first lever placement on the laser's upper face is rejected by the actual
  validated local-block interaction. Exact ray/occupancy diagnostics remain open;
  later firing assertions were not reached.

The fixture compensates the test shooter's position for the actual MG primary
mount (one metre sideways, 0.625m down). It validates that the real barrel ray hits
the selected drive and then uses the crafted item normally. It does not change
weapon data, projectile state, damage, velocity, targets or the mechanism pose.
The original XML retains actual muzzle, aim, owner, energy, projectile positions,
velocities and damaged local cells. Repair identity/mass/motion preservation
assertions follow the reduction assertion and were not yet evaluated on this red.

## Original evidence and shutdown limit

[after-resource evidence](SURVIVAL_ECONOMY_EVIDENCE_2026-11-17/after-resource/) preserves
first compile/runtime logs and exits, original XML, runtime focused manifest,
exact commands/helper, before/after hashes and the chunk debug report. Original
first-negative evidence remains separate and unchanged.

The long repair failure message exceeded Vanilla's 1,024-character written-book
page limit when GameTest created its failure lectern. This produced a chunk-save
serialization exception during shutdown, preserved verbatim in the runtime log
and chunk debug. The test server subsequently closed. The archive is an exact
byte preservation of all 82 on-disk fixture-world files, with ZIP CRC and every
file's SHA-256 checked before removing only that closed disposable world. It is
**not** a successful world-reload or complete failure-marker serialization proof.
Next fixture diagnostics must retain full detail in a report file and keep the
in-world assertion message short; assertions and limits remain unchanged.

After this reached negative, root authorized narrow server-owned local partial
damage repair and the existing repair tool's loaded-only, permission-checked,
terrain-occluded nearest-body ray. No solver, mass/collider rebuild, invented cost,
missing-cell restoration or broad placement/harvest behavior change is implied.
S12, full integration/client/native, balance and performance acceptance remain open.
