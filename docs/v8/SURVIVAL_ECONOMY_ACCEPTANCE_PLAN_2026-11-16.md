# S12 economy: actual Survival acceptance candidate

Base: `5839062c3af8a57687b7e39f2f6d79ce5badf8a3`. Status: test-only source
candidate, **uncompiled and unrun** until root grants the shared Java lease.
No production, recipes, shared entrypoint or central ledger has changed.

New package: `src/gametest/java/com/terminaldetector/drmd/world/economy`.
`SurvivalEconomyGameTests` contains six actual GameTests. `EconomyFixture` owns an
ordinary Survival ServerPlayer/EmbeddedChannel connection, scoped ticking tickets,
a Vanilla crafting table and real input/result-slot clicks. A focused runtime test
manifest must select the test class; it is intentionally not added to the shared
source manifest by this lane.

## Concrete source findings before the negative

All three existing recipes `drmd:ship_laser`, `drmd:ship_rocket` and
`drmd:ship_thruster` have the exact same `AAA / AEA / AAA` shape with alloy plates
and an energy cell. Vanilla's ordinary result slot chooses one matching output;
calling each recipe's Java `craft()` directly would hide the acquisition problem.
The first case therefore performs all three UI attempts before reporting their
actual results, with material conservation checked on every take.

`EngineerTools.RepairLaserItem` only casts a world-block ray and refreshes world
states; it does not repair BlockBody local partial damage. Its cracked deepslate
and nether cases also write ordinary stone bricks. The two repair cases acquire
the independently craftable repair tool and avoid depending on the colliding ship
recipes. Mounted weapons currently have no specified per-shot resource policy;
the harness does not invent ammunition, fuel costs or approved technology tiers.

## Six planned runtime cases

1. Craft all three ship components through the actual table, gathering every
   output before requiring three individually reachable identities. Also remove a
   required energy input from the grid: no result, full input refund on closing.
2. Craft bearing/drive/wrench; place real BlockItems in Survival; assemble the
   placed material through actual wrench clicks; use local material controls to
   join and drive the same physical bodies. No static duplicate or refunded part.
3. Craft and place a laser and rocket, consuming one inventory item apiece; place
   and use actual levers; observe body-owned projectiles from the production
   post-contact schedule. No direct emitter calls or assigned targets.
4. Craft bearing/drive/laser, then perform timed Survival mining with repeated
   legitimate server pulses. Verify one real loot entity, actual item pickup,
   and one-item replacement into the local grid. No recovery grants.
5. Craft the existing handheld MG and repair tool; a real fired projectile must
   first cause partial drive damage and physical motion. Actual repair use must
   reduce local damage without replacing the body, changing mass or resetting
   motion. Expected to expose the current world-only repair implementation.
6. Use the crafted repair tool on real cracked stone, deepslate and nether brick
   states. Collect all outcomes before checking source-family preservation.

Downstream mounted-fire/recovery cases may fail at acquisition before exercising
their later predicates because of the genuine recipe collision. Such a failure is
not evidence about later firing/recovery behavior. The original negative must be
preserved before root's authorized narrow recipe disambiguation or repair work.

## Fixture boundaries and remaining acceptance

The fixture declares iron/copper/redstone/amethyst/gold/nugget/stick/cobblestone
starting stock. Its table, neutral iron carrier and one mining pickaxe are test
infrastructure. This proves the recipe/installation/use/recovery path from
available material, not ore distribution, mining progression or a full Survival
playthrough. Products are obtained only from the loaded Vanilla result slot.

All actions use ordinary Survival mode. There are no manual entity ticks,
production time jumps, cosmetic success flags, direct target assignments, new
factory chains or fabricated per-shot costs. Inputs and result stacks transfer
through `CraftingScreenHandler.onSlotClick`, allowing `CraftingResultSlot` to
perform real ingredient consumption. Timing steps use a test-owned queue inside
one repeating GameTest callback, avoiding mutation of the engine callback map.

Before any runtime, root must grant compile/negative admission and capture exact
inputs. Preserve the first compile/runtime failure without rewriting it as a
gameplay result. Only then evaluate narrowly scoped fixes and rerun the same
assertions. Full economy balance, novice defenses, repair costs/permissions,
resource-loss rules, full siege, client/native and performance acceptance remain
open; preparing these six cases does not close S12.
