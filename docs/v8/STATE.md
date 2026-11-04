# v8 intake and ongoing UFO work

Tested source22f3c10; development JAR SHA25633028ec6b4aa18b18d95763a7b0aa1b7149c37994f1829a9bef07f288d045655.
679 unit/238 GameTests passed previously on this source. Recovered raw native evidence now archived: five external boarding scenes each on Mesa and Sodium, ten frames total. These are recovered runs, not fresh reruns. Fresh full-rotation regression also passed all13 scenes on the same game JAR; its source metadata is recorded separately in EVIDENCE/index.json. Total23 frames preserved.

No supplied task is marked completed: three inventory tasks are in_progress and the other96 remain pending_reverification until their complete acceptance is mapped and checked. S00 inventory is in progress: tracked patches preserved, untracked runtime paths inventoried only. Old source/PR9/other branches are retained.

Current focus: finish UFO/BlockBody continuity; add mandatory newer-version physics gates; then ControlCore/module gameplay. Offline singleplayer is the current acceptance target; dedicated/multiplayer testing is deferred per v8, not claimed passed. No accepted1.1.35 binary.

Release evidence gates now inherit their required physics stages into future versions;47 Python checks passed. This changes packaging validation only, not game code.
