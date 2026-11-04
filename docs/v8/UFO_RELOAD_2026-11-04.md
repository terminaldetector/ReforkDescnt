# Actual physical UFO restart: verified increment

Native source08d61c3a2bdc758924252ed1e7d28e8f9c3ff826. Main code/tested server revisiondc1338112c0c01101883d36ee8907b0a05029034; the later change strengthens only the native walking assertion.

- Game JAR SHA256:4609134fc38f4d856507e4cba47743cdc84674a7674988a49571e547b33a1962.
- Native test JAR SHA256:fac68364a1f03a4f84f6a4d86c664595f4526956d4b650e7b0b863cc40888eba.
-679 unit/250 required GameTests passed. New tests cover saved/new movement modes, saved physical context, preserved momenta during initial admission, continued motion of an already active body, missing-body timeout, flight cancellation and dimension mismatch.

Two independent actual client-process pairs passed on Mesa GL3.2 and Sodium GL3.3. Each first process flies into the real1046-cell UFO, walks, captures its console through production RMB and closes the world cleanly. Each second process opens those saved files, checks exact raw stored physics and grid data, restored physical components within1e-10, position, pre-capture AI and UUID, then exercises the real player/physics/network/render path. No NBT-inserted clone, replacement hull, dimension transfer or teleport-follow attachment is used.

The reader deliberately delays the normal boarding Ready handshake by30 server ticks after body restoration. Observed admission:34ticks(Mesa),44ticks(Sodium). The saved passenger waits in world space; a newly restored hull retains its pose and both momenta until contact admission. An already simulated body keeps running. After readiness, ordinary D6 and the physical walking motor resume. Forty grounded contact ticks are required, followed by actual keyboard walking. Relative displacement:4.021809m(Mesa),2.964453m(Sodium). These are functional measurements, not FPS/MSPT benchmarks. No duplicate local hull or stale ControlCore owner was observed.

`EVIDENCE/ufo-reload-08d61c3.zip` contains12 raw frames, both process logs/exit codes/launches and inputs for each renderer, exact artifact hashes, complete closed first-process world snapshots with per-file hashes, the successful build stamp,679 unit XML results and250-GameTest log. Archive SHA256:02520e8748b9ca62126ea227b6cb03df52cdf01301054545a608a1470aa79a02.

## Causes and retained failures

1. Every login unconditionally enabled suit flight. Source9c461cc now respects an existing saved mode, initializes new/saved flying players through the actual flight path, and preserves a saved walker's momentum. The old failing saved-world regression then passed, but that alone did not close restart acceptance.
2. A fresh process pair exposed a timing-dependent gap: the walker could fall more than2m before the physical hull loaded. Physical boarding context (body UUID, dimension and frame) is now saved, with a bounded100-server-tick admission wait. It never stores console ownership. Missing/destroyed/invalid contacts release; enabling flight or changing dimension cancels the wait. At the normal takeover, existing local gravity/collision code acquires the physical body and ends admission.

`EVIDENCE/ufo-reload-first-failures.zip`, `ufo-reload-mode-repro.zip` and `ufo-reload-late-body-first-failure.zip` retain the mapping failure, first contact timeout and delayed-body failure. Closed pre-reader worlds are included where available. No failed or outdated stand is counted as a pass.

## Limits still open

This proves current-UFO saves containing the new physical context in offline singleplayer. Migration/admission for older passenger saves without that context remains open; the earlier mode-only legacy regression is narrower evidence. Connected movable modules, arbitrary-speed restored actors, mid-session unload/reload, cross-body/portal transitions and broader v8 integration still need their acceptance. The optional unofficial VS backend was not installed or made required. No full UFO9/Boarding12/G2/G6/v8 completion is claimed.

Source artifact13366438284 for GHA run41921156739 was verified saved/notexpired, digestsha256:e599063b8ef69a363b64963a08b439badc1498f9b6d9269781ba99121042efaa. Full workflow status is separate and was still running at that check. No public1.1.35 acceptance; last accepted package remains1.1.34-joints.1.
