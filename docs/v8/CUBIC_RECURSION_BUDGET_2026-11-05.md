# Recursive native generation budget and interrupted-client recovery

Production sourcef2348221abf64ebe167726f29cca130c74bc53c1 adds an optional compatibility mixin for Better Cubic Chunks0.1.1+mc1.21.1. The upstream schedule(tick, allowanceNanos) already supplies a time budget, but its recursive advance calls previously did not inspect that deadline. One per-instance budget now carries the same allowance into the dependency walk. Once expired, a nested request returns false as an unfinished dependency; its queue/status/geometry/entity data are not deleted or marked complete. The next scheduling pass renews the allowance. No new world backend, generation implementation or upstream source is copied.

The helper is server-thread confined and per native scheduler instance. Five tests cover a shared recursive deadline, subsequent-pass resumption, nanoTime wrap, calls outside an ended pass and independent world budgets.706 unit/269 GameTests passed locally. Vanilla has no native target, so the pseudo mixin remains absent. Actual compatibility was then verified on the production BCC JAR; test-only callbacks observe its real scheduler instances through a diagnostics interface.

Game JAR SHA256dc996a301d096e7d2d698bbaaeb623285edbeeb1bd97f312bd4d55e84f1bdc65. Normal native observer sourceb4c879b617ee74904209cb96b1a2c503351ffe7a, JAR SHA2560b1f4644eed3d64aab2f6aa5b038173216efe84c08f2663659dc08ba1066e96a. EVIDENCE/cubic-recursive-budget-b4c879b.zip SHA256acd91095040499f238089fe39f534a962b1d6cfcb09b864d2c96b6c066fd104d preserves the exact launch/source/JAR inputs, six fresh frames, actual world snapshots/NBT and passing server evidence.

Fresh mixed43 ORB/43 ROCKET/42 JET scenes pass at Y207/Y2156 on both renderers. All128 actors are active/moving/naturally targeting and shooting in each writer scene; all128 exact type identities synchronize, with real models and missiles. This demonstrates continued generation progress and gameplay while deferrals occur, not merely a counter increment.

| Renderer | Deferred requests after207 /2156 | Reader active / moving / natural targets / shooters | Reader shot roles |
| --- | --- | --- | --- |
| Mesa GL3.2 |16752 /25113|128 /127 /122 /118|Assault769 /Artillery52 /Interceptor1122|
| Sodium GL3.3 |14273 /22743|128 /128 /128 /128|Assault877 /Artillery64 /Interceptor1678|

Each reader opens its writer's exact closed world, compares actual components and registered classes before first tick, then performs normal combat. All four client exits are0. No manual actor ticks, target assignment or replacement NBT/entities are inserted. Metrics are actual cumulative scheduler deferrals, not a count of discarded cubes.

This is not a hard wall-clock deadline for the entire pass: already-entered caller loops, other native work, GC and OS scheduling can continue. Observed maximum whole-pass durations remain76.9/131.5ms for writers and117.6/88.1ms for readers. Full streaming/performance acceptance remains OPEN; no claim that every lag source or arbitrary generation graph is fixed.

## Deliberate renderer fault and actual read-back

Observer sourcef01996ad1e5b99c268e0476d5898fefb4b9752c3 adds a test-only renderer exception after the real high-altitude128-agent scene and after observing the subsequent actual world save. The game JAR remains byte-identical. The client throws the labelled deliberate exception at18:20:22, saves all dimensions by18:20:23 and exits with expected fatal code255. Native inFlight=0 and pendingWrites=0; the server-stopped callback runs. This exercises the shutdown path implicated by the retained19539-CPU-second livelock, rather than only a normal logout.

The original new wrapper nevertheless FAILED: it incorrectly also required pending new-generation requests=0.2375 requests for not-yet-generated cubes remained. That failed wrapper/result is retained without relabelling it a pass. A nonempty generation-request queue is separate from pending disk writes. An independent reader then reopened the actual closed crash world, without changing its NBT, and compared all128 saved UUID/type/component sets before first tick. It passed128 active/moving,123 natural targets,122 shooters and three shot roles (Assault770 /Artillery61 /Interceptor1377), with no duplicate swarm. Reader exit0 and all dimensions saved confirm this specific persisted-state recovery.

EVIDENCE/cubic-abort-recovery-f01996a.zip SHA2561611650a758695b0a569e285372b9b3848aa4992103faba24c7fae4c4df17481 contains the expected crash report/log, the failed original pending-zero assertion, the explicit classification, real saved-world snapshot/raw components and successful independent reader. This does not certify all crash, storage-corruption, arbitrary incomplete-cube or general streaming cases. The earlier long-running failure remains in TYPED_SWARM_2026-11-05.md's archive. Test launchers also retain kill-after as a diagnostic resource guard; it is not substituted for a successful shutdown.

## CI admission follow-up

Sourcef01996a Actions42083412968 again failed the existing Artillery range fixture at its80-tick admission assertion, before measuring movement;268 other GameTests passed. Thus318d535's synchronous chunk materialization was not sufficient to establish remote stability, despite two earlier passing remote server runs. The new raw failure artifact13440918449 is preserved and verified againstsha256:b96994f153ca63f29a653e69c6e52b4b6aac57f7d366d749e406d99116aafed2.

Test-only source1001f1b uses unique per-fixture entity-ticking tickets and waits for ServerWorld.shouldTickEntity at both actor positions before spawning. It no longer releases a shared global forced-chunk flag. The80-tick admission limit, five real initial entity ticks,40 measured movement ticks and every distance/velocity threshold remain.269 local GameTests pass; remote verification is pending. This changes fixtures only, not the production JAR.

Saved source artifact13440524153/run42083412968 is available/notexpired, digestsha256:9484b66b93c607547d16ee1e88be8b74d7dd06e35f7ea4cd8b8709a997185d80. Its workflow is not green because of the admission failure. Main release acceptance/full v8, type-specific wrecks, dense shared routes,256+ scaling and comprehensive profiling remain open. No accepted1.1.35 distribution is declared.
