# Retired Advanced callback guard — positive, 16 November 2026

**PASS: all eight focused GameTests, Gradle exit0 in44s.** Exact production/test source `39ae637b18d17f6064ba2b8708839af4eed76b98`. Production change is restricted to `EnderDragonFightMixin.java` and `PortalComplexity.java`: retired automatic generation returns before the old tall-world override, leaving the ordinary vanilla method or PASS callback path active. No Citadel authoring, identifiers, save layout, dimension resources or default world generator changed.

The preserved pre-change `e930a8f` negative has six failures and two controls. Comparing full source-input maps proves that only those two approved main Java files changed; all eight test assertions, test-only probes, helper and generated focused registry/manifest/mixin resources are byte-identical between the two runs. Both original logs, exact XML and source/input hashes remain separately preserved.

| Actual focused case | Positive result |
|---|---|
| Real tall-world/retired fixture | PASS: loaded bottomY=-784,height2672, STOCK backend, retired flagtrue |
| End eye without catalyst | CallbackPASS, normal eye insertion succeeds and consumes only the eye |
| End eye with three catalysts | CallbackPASS, all3 catalysts retained; normal eye insertion succeeds |
| Fire starter without catalyst | CallbackPASS, normal ignition over obsidian succeeds |
| Fire starter with three catalysts | CallbackPASS, all3 catalysts retained; normal ignition succeeds |
| Unrelated item/inventory | PASS, normal callback and quantities unchanged |
| Real EnderDragonFight tick | Arena entry0, generated=false, actual witness dragon alive |
| Real respawn request | Reaches actual vanilla dragonKilled field read once and returns normally |

The same explicit test-only generator-entry sentinel remains installed. No forbidden entry occurred in this positive, so the sentinel did not fire; the test does not replace a failing generator with a fake successful arena. It proves retirement guard/callback routing, **not** generated Citadel geometry/content parity. The respawn case deliberately uses dragonKilled=false to observe normal vanilla delegation without triggering portal scans or a complete crystal respawn sequence. This is not full boss-fight acceptance.

This run uses DEV GameTest classes in an actual old-height STOCK world. It is not a native-Cubic client gate, installable-JAR check, new-world generator migration or full S01/S02/S03 completion. No JAR from an older or untested source is presented as this result. The closed disposable world remains intact under this worktree's `build/gametest-final-legacy-positive/world`; no original user save was opened or overwritten.

See `positive.log`, exact `gametest-results.xml`, `results.json`, generated focused resources, complete `source-inputs.json` and `SHA256.json`. The normal source GameTest manifest is unchanged; the dedicated tests are selected only by the focused init script.
