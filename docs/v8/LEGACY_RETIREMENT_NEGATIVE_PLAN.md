# Retired Advanced callbacks — isolated negative preparation, 16 November 2026

This is **test-only, not compiled/run yet**. Production files `EnderDragonFightMixin.java` and `PortalComplexity.java` remain unchanged and byte-identical to current canonical source. No content-generator authoring or central task ledger is edited.

The old/manual Advanced-save callback scope remains reproducible even though the normal UI/default automatic builders are retired. Both dragon callbacks and the portal catalyst callback gate only on `WorldLevels.isAdvancedColumn`, bypassing `LegacyGeneration.retired()`. The following negative must execute before adding the narrow retirement guards.

`scripts/v8/legacy_retirement_focus.init.gradle` follows the existing focused-resource helper pattern: it changes only this lane's generated GameTest manifest, selects `LegacyRetirementGameTests`, enables two test-only mixins and copies the existing Advanced dimension-type JSON into generated test resources. This supplies actual loaded registry values, not a mocked height predicate or new authoring in main resources. The real Overworld must have bottomY=-784, height2672, stock backend and retired flagtrue in every test. The real End must also be loaded for the two dragon cases. Source build configuration and ordinary source manifest remain untouched. The helper refuses a pre-existing `build/gametest/world/level.dat` rather than overwriting a save. Its generated resource output is a focused fixture and must not be mistaken for ordinary full-suite configuration.

Eight focused cases are prepared:

1. Actual old tall-world/retired-feature fixture control.
2. End eye without catalyst: actual `UseBlockCallback` must PASS; ordinary vanilla eye insertion must remain functional and consume only its eye.
3. End eye with three catalysts: callback must PASS and leave all three catalysts, followed by ordinary eye insertion.
4. Fire starter without catalyst: callback must PASS; ordinary vanilla ignition over obsidian must remain functional.
5. Fire starter with three catalysts: callback must PASS and leave all three catalysts, followed by ordinary ignition.
6. Unrelated item callback/inventory control.
7. Actual `EnderDragonFight.tick()` in the real End with a real dragon and nearby player: no automatic Citadel entry, arena generation or dragon discard while retired.
8. Actual `EnderDragonFight.respawnDragon()` with dragonKilled=false must reach the real vanilla field read and return normally, without the old mod cancelling the request. This is routing proof, not a complete crystal/portal/dragon respawn simulation.

Instrumentation is explicit and bounded. `LegacyArenaEntryProbe` observes `EndReactorSession.generateBase` at HEAD, increments an entry counter and throws a test sentinel before the forbidden bulk writes. Existing `ensureBase` catches that sentinel; the GameTest fails unless the entry counter was zero. This proves the guard/callback route and prevents a known unwanted ~60k-block baseline generator from running in the disposable negative. It does **not** prove complete generated-arena parity or actual arena-write contents. `LegacyDragonRespawnProbe` observes the real dragonKilled field read, avoiding the ambiguity of a synthetic RETURN inserted by a cancelled HEAD mixin. Observation/support/test classes are outside the dedicated mixin package to preserve Mixin class-loading rules. Neither probe is enabled by the ordinary source manifest or included in the installable main JAR.

After an explicit root JVM lease, record exact source/hash and run only:

```sh
JAVA_HOME=/workspace/jdk21-complete PATH=/workspace/jdk21-complete/bin:$PATH GRADLE_USER_HOME=/workspace/.gradle ./gradlew runGametest --offline --no-daemon -I scripts/v8/legacy_retirement_focus.init.gradle
```

Preserve the first compile/setup/runtime failure, exact generated manifest/dimension JSON, real bounds/callback/probe logs, XML, process exit and disposable world. Source stays frozen throughout that one run. A fixture failure is not a confirmed production failure; unexpected PASS does not justify changing production. Only after the genuine negative closes may the two production retirement guards be added and tested against the same assertions in a fresh world.
