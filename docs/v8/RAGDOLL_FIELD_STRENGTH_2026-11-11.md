# Ragdoll sampled field strength — 11 November 2026

Ragdoll acceleration now follows the sampled strength of a local gravity field. The one-line correction preserves the existing world-down default, physical velocities, weapon impulses, damping, constraints and collision adapter.

## Reproduction and correction

On test-only source `0c136925729949eeb8791596b9e224a8db588774`, the focused six-case GameTest run has one failure and five passes, with Gradle exit1. A real five-part scout rig starts at rest in clear loaded air. With a quarter-strength downward field, its measured centre-of-mass speed after one tick is `0.48951899999999987` blocks/second, identical to the full-strength baseline. The first DOWN-axis comparison fails before the test reaches the remaining five directions. The four existing cases and the separate no-field/removal control pass.

`RagdollEntity.gravityVector()` previously used the field's direction but always returned magnitude9.81 for an active field. Production source `9c5b6e1e44d76f50bcd8015daf12893b7034871c` changes only that return expression to `9.81 * field.strength()`. It uses the blended, distance-dependent sample with its existing cap2, rather than raw emitter power. The null, below-threshold and invalid-direction default remains world-down9.81.

The D6 core owns velocity in blocks/second. The entity adapter converts an incoming blocks/tick velocity by multiplying20 and divides20 when publishing ordinary Entity velocity. The simulation applies force `mass * acceleration` over50ms, split into four substeps; the existing0.998 linear damping runs once afterward. `PhysicsTarget.physicsVelocity()` remains in blocks/second, as required by gravity weapons. No conversion, momentum reset, joint, collision or shared D6 implementation changes were made.

## Verified result

On exact production source `9c5b6e1`, all six focused GameTests pass with zero failures/errors and Gradle exit0 in44seconds. Test source, source manifest and focused-init hashes are unchanged from the negative run. The two added cases verify:

- A real scout rig, initially at rest, on each of six field axes with sampled strengths0.25,0.5,1 and2. Emitter power4 exercises the existing sample cap. Measured COM velocity scales with sampled strength, stays on the field axis and matches Entity velocity through the20:1 boundary.
- The existing no-field world-down acceleration, followed separately by sideways-field removal that restores world-down force while retaining horizontal momentum.

The four retained cases cover floor settling and connected parts, a local-gravity wall contact, NBT/impulse state, and one physical wreck after drone death. They pass before and after the correction. Source GameTest registration remains unchanged; the init filters only built resources to the existing `RagdollGameTests` entrypoint.

## Evidence and replay

[ragdoll-field-strength-9c5b6e1.zip](EVIDENCE/ragdoll-field-strength-9c5b6e1.zip) retains both original logs/XML/exits, source identities, clean input hashes, summaries, the exact init, test source, production source before/after and the two-file patch. Its manifest hashes and ZIP CRC are verified independently. Existing C3 archives remain unchanged.

```sh
JAVA_HOME=/workspace/jdk21-complete PATH=/workspace/jdk21-complete/bin:$PATH GRADLE_USER_HOME=/workspace/.gradle ./gradlew runGametest --no-daemon --offline -I work/gravity-roster/ragdoll-strength.init.gradle
```

This is focused stock-Fabric server evidence. The complete new suite and native client have not been run for this delta, and no JAR is certified. It does not close full S08-T05: heavy rail interactions, authored rig orientation under impact, player impacts, native cubic loaded-volume parity, compound/thin terrain contacts, broader family rigs, angular-limit descriptors and mass-death budgets remain separate acceptance. Existing NBT coverage is not a two-process world restart. All work remains local.
