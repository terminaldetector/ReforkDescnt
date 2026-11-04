# ControlCore lifecycle increment — 2026-11-04

Development source: `ea9c9eb6c2e8a004ccbe016ea4221b4cf183fbd9` on `feature/ufo-body-after-1.1.34`.
Game JAR SHA256: `8155bfc90682679d550fee7c11a891e008b0460dac2f44e7d27c7e5e71c0effe`.
Native test JAR SHA256: `b67ec599cc664e79a7845cb38b0536c6be3db749e6f8c5e04d987e7cd02ea8b2`.

The client now releases mouse and input ownership when its player dies, becomes a spectator, or its dimension/world is replaced, independently of a delayed old-world release packet. Existing server checks remain authoritative. Two added GameTests verify leaving the hull without resetting its momentum, recapture with a fresh authority epoch, stale command rejection, suit-flight transition and actual hull removal.679 unit and245 required GameTests passed. Their main code is df8f67b; the subsequent ea9c9eb change only corrects the native stand's console aim point.

Fresh production-remapped singleplayer clients passed9 scenes each on software Mesa GL3.2 and Sodium GL3.3: external flight approach, moving bay entry, H-off boarding, walking, real RMB capture, eight-second pause/resume with thrust and roll, H release, real RMB recapture of the same hull, and actual player death releasing both client and server control.18 raw frames and complete logs/inputs are in `EVIDENCE/ufo-control-lifecycle-ea9c9eb.zip`. Both markers have `pauseResumed=true` and `deathReleased=true`; both helpers exit0. Approach prediction maxima:0.031924m(Mesa),0.025243m(Sodium). These are approach samples, not control-motor latency, FPS or hardware GPU benchmarks. This scenario omits the separate roof test and reports roofContacts=0.

First attempts are retained, not counted as successful runs:

- The earlier stand aimed at the buried console centre. From the farther-back boarding endpoint, the ray hit the neighbouring deck before reaching the console. The corrected stand aims through its visible top face; interaction still uses the real production use key and authoritative ray/reach/occlusion checks. The first runtime's full `logs/latest.log` is preserved. The runner's stdout was overwritten after an in-place runner edit while it was executing; its exit127 is a runner failure and is also retained. Subsequent launches execute separate unchanged runner copies.
- The first Sodium lifecycle launch stopped at Minecraft's low-disk confirmation and reached the stand startup timeout (phase0, no player/world). It is an environment failure, not a gameplay pass. Verified identical immutable JAR copies in closed disposable runtimes were hardlinked, retaining every path and byte and recovering roughly1.6GB. A fresh runtime then completed the scenario. The initial crash report and log remain in the evidence archive.

Native launch now checks a successful-build stamp covering source inputs and both remapped JAR hashes/ZIP integrity; changes require a rebuild. This prevents stale test artifacts from following a failed compilation. The helper and successful build stamp/log are preserved with evidence.

GitHub Actions source artifact13362489923 for run41914150548 was verified saved and not expired (digest `sha256:e3e1584bbffc43cd8cf12eb9a1f042ce5bace41071a3210ff139acf9e7f2096b`). This proves source preservation; the full workflow was still running at that check and is not claimed green.

S09-T04 remains in progress. Native dimension/portal transitions, actual save/reload and wider actor/ship handoffs still need acceptance; weapons, reactor, modules and full UFO9 are open. No verified public1.1.35 release or full v8 completion is claimed. Last accepted public package remains1.1.34-joints.1.
