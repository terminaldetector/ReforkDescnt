# v8 intake and ongoing UFO work

Historical flight foundation: tested source22f3c10; development JAR SHA25633028ec6b4aa18b18d95763a7b0aa1b7149c37994f1829a9bef07f288d045655.
679 unit/238 GameTests passed previously on this source. Recovered raw native evidence now archived: five external boarding scenes each on Mesa and Sodium, ten frames total. These are recovered runs, not fresh reruns. Fresh full-rotation regression also passed all13 scenes on the same game JAR; its source metadata is recorded separately in EVIDENCE/index.json. Total23 frames preserved.

No supplied task is marked completed: three inventory tasks and S09-T04/S09-T06 are in_progress and the other94 remain pending_reverification until their complete acceptance is mapped and checked. S00 inventory is in progress: tracked patches preserved, untracked runtime paths inventoried only. Old source/PR9/other branches are retained.

Current focus: finish UFO/BlockBody continuity; add mandatory newer-version physics gates; then ControlCore/module gameplay. Offline singleplayer is the current acceptance target; dedicated/multiplayer testing is deferred per v8, not claimed passed. No accepted1.1.35 binary.

Release evidence gates now inherit their required physics stages into future versions;47 Python checks passed. This changes packaging validation only, not game code.

ControlCore increment has bounded local native proof: right-click capture, exclusive ownership, bounded thrust/torque, H release, lifecycle cleanup and nonpersistent pilot connection.679 unit/243 GameTests passed; native control capture/pause/thrust/roll/H-release passed seven scenes each on Mesa and Sodium (14 fresh frames), exact source0dff43b. This is partial S09-T04 evidence, not full task acceptance. See CONTROL_CORE_WIP.md.

Current increment: native sourceea9c9eb6c2e8a004ccbe016ea4221b4cf183fbd9, game JAR8155bfc90682679d550fee7c11a891e008b0460dac2f44e7d27c7e5e71c0effe.679 unit/245 GameTests pass on unchanged main codedf8f67b. Fresh lifecycle runs on Mesa and Sodium each passed9 scenes including actual re-capture and player death (18 frames); source artifact13362489923 verified saved/notexpired. See CONTROL_CORE_LIFECYCLE_2026-11-04.md. Dimension/portal/actual reload acceptance and weapon/reactor modules remain open. No public1.1.35 acceptance.

Actual same-world UFO reload is now an explicit open regression: exact data/physical restore passes, but walking contact fails after restart. See UFO_RELOAD_WIP.md. This prevents claiming S09-T06 or full save/reload acceptance.
