# Actual gravity items: loaded queries and scoped mode acceptance

This S10-T02 increment preserves the existing distinct SWARM/RAIL/SHIFT physics implementation and default roles, speeds, mass/target budgets, energy and intended ranges. It fixes reproduced synchronous terrain generation in gravity-item target queries. It does not close S10-T02, the broader weapons stage, native Cubic, client presentation or gravity-compensated ballistic acceptance.

## Production change and actual use

Exact tested production/test source **`9441318b7dedc7aa198baa59afee046ee47598bc`** includes the narrow `40f7b6f` production change in `DescentWeaponItem.java`:

- Single-target acquisition and SHIFT surface projection use existing `WorldEditSafety.loadedEnd` before querying collision shapes. Known loaded prefixes retain their useful range; unknown tails are neither terrain hits nor invented walls.
- SWARM candidate visibility uses existing `WorldEditSafety.loadedSegment` before each independent occlusion ray. A clear known prefix does not establish visibility of a target in unknown volume.
- No RAIL impulse, physical solver, range, energy or target/mass profile was changed. Pivot-independent long-body target acquisition is still separate open work.

The six new `GravityGunBehaviorGameTests` invoke registered `ModItems.*.use` over real connected server players and real BlockBody targets. They never directly call `tryGrab`/`fling`, inject launch velocity, manually tick bodies or follow targets by teleport. Fixture admission waits for actual entity-ticking columns with UUID-owned tickets. Normal server updates perform holds, movement and field tracking.

## Exact first outcomes and correction

| Scenario | Original `1ecca08` | Corrected `9441318` |
|---|---|---|
| SWARM captures four bodies, ordinary hold movement, distinct volley and energy | PASS | PASS |
| RAIL hundred-cell capture, actual launch and tilted short-range COM trajectory | FAIL before trajectory; later diagnosed fixture | PASS |
| SHIFT actual throw, directional anchor field and removal cleanup | PASS | PASS |
| SWARM-to-RAIL actual tool handoff without inherited volley | PASS | PASS |
| All three actual items reject zero-energy capture without retained grab/impulse | PASS | PASS |
| Actual RAIL target query does not generate an unavailable remote column | FAIL: column materialized | PASS |

The original exact `1ecca0865432083668466ed686fb7edf3da1094f` stock Fabric run is retained as **2 FAIL / 4 PASS**, Gradle exit 1 / game exit 2. Heavy capture passed, but its terse launch failure was initially unclassified; velocity and trajectory assertions were not accepted.

The first diagnostic/fix source `40f7b6f9c19f6880a32e886aeb4a3811a8c325e8` compiled, then crashed during its first RAIL batch. **No fresh XML and no six-case result exist for that run.** Detailed state shows the same callback invoked twice at world time 97: the first elapsed-eight-tick use launched the live 100-mass body at exactly 42 m/s, consumed 12 energy and released the grab; the duplicate same-tick use correctly failed cooldown. In-callback GameTest task registration rehashed the iterated fastutil map and was followed by `GameTestState.tickTests` iterator NPE. This diagnoses a fixture fault without relabelling the original raw outcome.

Test-only `9441318` replaces nested GameTest registrations with one driver registered at test entry and owned due-action/observer queues. The eight normal-world-tick delay, exact 42 m/s launch assertion, twelve-block plane and 1.2-block bound are unchanged. Owned connections and exceptional constructor paths also close cleanly. No gameplay RAIL fix was introduced to make these tests green.

The corrected exact **`9441318`** stock Fabric run completed **6 PASS / 0 FAIL**, Gradle/game exit 0. Its trace contains one launch callback: elapsed 8, held RAIL count 1 / mass 100, capacitor 88→76, followed by the unchanged trajectory assertion. The loaded-only unknown-column regression now passes.

## Scope and evidence

The heavy-body scene uses the default **gravity-disabled** BlockBody. Its passing flight assertion is a tilted **zero-G COM crossing** within 1.2 blocks at twelve blocks, not a material impact or gravity-compensated/all-mass/long-range rail proof. SWARM requires at least one held body to move before all four physical launches, rather than full per-body formation convergence. Zero-energy coverage concerns capture rollback, not insufficient-energy fling. SHIFT tracks its real field and retires it on anchor removal; a full-duration actual-gun field/restart scenario remains open. Direct item use does not prove client button/packet routing or visual presentation.

All three runs used the included focused generated-manifest init with Java 21, shared immutable dependency cache and:

```sh
./gradlew runGametest --offline --no-daemon -I work/gravity-gun/focus.init.gradle
```

Source `fabric.mod.json` remains unchanged for root-owned full-suite registration. No full ordinary regression or native/client run is claimed on this branch, and no installable JAR is certified or published.

Complete scoped bundle: `EVIDENCE/gravity-gun-loaded-queries-9441318.zip`.

SHA-256: `5234ee6db25061924db5df10616aa325f3275d5ad67ada0483dd148fa1aae4a2`.

The archive includes original/diagnostic/corrected logs, fresh XML where it exists, explicit no-XML receipt for the crash, exits, exact input/source snapshots, common focused init, bounded RAIL state traces, source diffs and all three preserved disposable worlds. The archive passed CRC and every payload file's SHA-256 check. Each original world was locked, archived and independently CRC/per-file-hash verified before reclaiming only that generated build directory. The clean positive world contains 118 files / 24,163,349 original bytes, nested archive SHA-256 `d3cd2af5be5ccab461e7742207357d131b60cb7e73a766abca69ca8728db6eaa`. World preservation is not save/reload gameplay acceptance; the crashed world is explicitly labelled as such.

Source chain after the already integrated cover checkpoint: `1ecca08` actual-use tests → `436a3ca` first evidence → `3796e16` state/cleanup → `40f7b6f` loaded rays → `405b781` first crash evidence → `9441318` owned fixture driver. The current evidence/report commit is distinct from the source actually tested.
