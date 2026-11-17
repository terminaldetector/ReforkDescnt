# Drone equipment preview — first compile and six focused tests

Exact frozen source `e0478ce8d4e79cc73678582bafa4f539978e9a26` passes
**compileJava, compileTestJava and all6 focused DronePreviewProjectionTest cases**.
Fresh XML has0 failures/errors/skips. This verifies Java/API compilation and the
pure geometry contracts, **not an actual GUI image or complete inventory task**.

The first attempted launcher failed before compilation: Gradle's default
`/home/agent/.gradle` wrapper lock parent could not be created. Its actual
child105906 closed1 at17:39:29.321105UTC, without timeout/kill or test XML.
The original immutable helper, complete output, actual exit and before/after
source/runtime inputs are retained. This is a tooling failure, not a failed
preview/unit assertion, and it is not omitted from the record.

Root then explicitly authorized two environment setup corrections before the
first actual compilation: existing writable `GRADLE_USER_HOME=/workspace/.gradle`
and complete `JAVA_HOME=/workspace/jdk21-complete` with its bin prepended to PATH.
The initially recorded system Java had no javac/jmods. Both original/current
Java paths, java/javac executable hashes and release/libjvm metadata are in the
receipts. An intermediate cache-only helper was prepared **but never executed**.
No candidate code, test or criterion changed.

The actual compile helper SHA-256 is
`38c70156ddf03c2ccd008be3bf3a8e7787aeab3b677c640e13cc28a33e5ce139`.
It ran offline, no-daemon/no-parallel, one worker, Xmx1G:

```text
./gradlew compileJava compileTestJava test --tests com.terminaldetector.drmd.DronePreviewProjectionTest
```

Exact additional options/environment and named Minecraft JAR hash are retained
in runtime-inputs. All4 actionable tasks executed; actual child106313 closed0
at17:42:21.604086UTC with no timeout/forced kill. Fresh six-case XML SHA-256 is
`3e29efc948a408fb38d14964be35821c8fbe925fc29aabc1cb81aaa62e13009a`.
The runner and all source/resource/build inputs remained byte-identical before
and after. No live Java remained when the lease was returned.

The six cases cover shared model/muzzle axes without a second `/16`,625 orbit
distance-preservation samples,9 asymmetric/negative viewport fits and relative
scale, flat/point geometry, non-finite input rejection and unusable viewports.
They do not render the menu or prove cache/synchronization behavior.

The [compact proof receipt](EVIDENCE/drone-preview-e0478ce-first-compile.json)
binds the [archive](EVIDENCE/drone-preview-e0478ce-first-compile.zip), both executing
helpers/logs/exits/input manifests, actual fresh XML, four exact Java inputs and
their selected compiled classes. All archive member hashes and ZIP CRCs pass.
The original [uncompiled-candidate report](DRONE_LOADOUT_PREVIEW_CANDIDATE_2026-11-17.md)
remains an honest earlier checkpoint; this report supplies the later compile scope.

No remapping, installable JAR, native client, full unit/GameTest suite, hardware or
performance acceptance was run. Actual synchronized slot→rendered equipment,
empty state, unrelated held stack, free/mounted inventory, ordinary/6DoF and
Native/PS1, resize/reopen and GL/FBO/scissor/shadow/following-frame restoration
remain OPEN under the [native contract](DRONE_LOADOUT_PREVIEW_CONTRACT_2026-11-17.md).
No central ledger was changed and no task was promoted.
