# City protocol stage: prepared source, not tested

The original26 pure checks remain immutable source77dc8a1/evidencebd8acc6.
Runtime API contract1cff40e was reviewed and approved for staged source work.
Test-contract checkpoint93aa405 preceded this implementation. No Java, Minecraft
world, startup registration or real-city write was run in this stage.

Prepared JDK-only source:

- `CityDurableFiles`: bounded CRC reads, forced temporary metadata and atomic new
  publication, forced directory entries, no replace/cleanup fallback.
- `CitySaveIdentity`: explicit create versus openExisting; random persisted save
  UUID and lifetime exclusive lease under the fixed save-root path. Missing existing
  data is never provisioned by inspection. Corrupt/partial/missing-ID-beside-city-data
  conditions are refused without identity replacement. Directory ancestors are
  provisioned/forced only by explicit create. Full backup copies retain identity.
- `CityManifest`: schema1 with blueprint1/journal2, immutable save/job/owner/dimension/
  actual seed/requested city seed/anchor/bounds/backend/hash/material counts. Bounded
  read/write verifies CRC, exact supported versions, actual regenerated geometry
  and caps. Context mismatch refuses; even enlarged height/border bounds are changed
  context. Existing/pending files are not overwritten.
- `CityBatchJournal`: schema2 requires saveId and jobId in addition to owner,
  dimension and plan hash. CREATE_NEW and existing-only opens are separate. Real
  prototypeversion1, even with valid CRC, is refused without migration/rewrite.
  Typed persistent PAUSED/CANCELLED/VERIFY_AFTER_ONLY controls preserve cancellation
  and known after-image fences through close/reopen. Cancellation is terminal and
  cannot be weakened; pause cannot clear verification. COMMITTED clears the verified
  pending fence and advances the durable prefix.
- `CityAuthoringJob`: one outstanding I/O future plus a coalescing restrictive
  control latch. Pause/cancel stops new world CAS immediately; inspect separately
  reports acknowledged durable disposition. Resume cannot bypass an outstanding
  stop/control acknowledgment. Cancel dominates pause/verify, and waits for an
  earlier prepare/commit acknowledgment before its terminal record. I/O loss
  quarantines without retry; known notification failure persists its after-image
  fence before manual resume can check it. Restore honors durable cancel/verify
  and never activates automatically.

Forty-seven prepared tests, **uncompiled/unrun**:

| Suite | Cases |
| --- | ---: |
| CityBlueprintTest | 7 |
| CityBatchJournalTest | 13 |
| CityAuthoringJobTest | 14 |
| CitySaveIdentityTest | 6 |
| CityManifestTest | 7 |

The standalone offline helper now compiles exactly six pure production files and
five test files; input capture binds those plus three helper and three wrapper files
(17files), the same seven cached JUnit artifacts and JDK inputs. There are no new
dependencies or Loom/Minecraft compile tasks. Optional `-PcityEvidenceDir=<absolute
run directory>` selects fresh XML/HTML locations without altering the helper during
the gate. Preserve each actual first compile/test outcome; no source edit or retry
inside a lease. Requested commands after the exact-source grant:

```sh
JAVA_HOME=/workspace/jdk21-complete GRADLE_USER_HOME=/workspace/.gradle python3 verification/city-pure/capture_inputs.py --output <run>/inputs-before.json
JAVA_HOME=/workspace/jdk21-complete GRADLE_USER_HOME=/workspace/.gradle ./gradlew -p verification/city-pure --offline --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx256m compileJava compileTestJava
# Only after compile0, same frozen inputs and fresh output directory:
JAVA_HOME=/workspace/jdk21-complete GRADLE_USER_HOME=/workspace/.gradle ./gradlew -p verification/city-pure --offline --no-daemon --max-workers=1 -Dorg.gradle.jvmargs=-Xmx256m -PcityEvidenceDir=<absolute-run> test
```

The new directory/file APIs are worker-only by contract, not an implemented Minecraft
thread integration. A lost outcome before a control record acknowledges remains an
honest PREPARED ambiguity; terminal intent is never falsely reported as durable.
The future serial worker/session must drain/close its lease without waiting on the
world thread and must preserve the single fixed-slot rule. That worker, actual loaded
registry/neighbor/actor preflight, sterile-palette inspect/failure text, commands,
disconnect/stop integration and one inert registration hook are subsequent source
increments. None is claimed here, nor any runtime/native/full-mod/S13 completion.
