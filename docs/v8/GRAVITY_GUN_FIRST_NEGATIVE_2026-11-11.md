# First actual gravity-item use run (S10-T02, incomplete)

Exact source `1ecca0865432083668466ed686fb7edf3da1094f`, unchanged before and after execution. The Nov 11 stock Fabric GameTest run compiled and completed six cases with **2 FAIL / 4 PASS**, Gradle exit 1 / game process exit 2. Better Cubic Chunks classes were absent; this is neither native Cubic nor client evidence.

The actual registered SWARM item captured four physical bodies, normal hold movement and the distinct physical volley passed, including synchronous capture/launch energy debits. Actual SHIFT capture/throw, attached directional field movement and anchor-removal cleanup passed. Actual SWARM-to-RAIL handoff and zero-energy capture rollback for all three items also passed.

Two failures are retained:

1. The RAIL hundred-cell mass capture succeeded, but the second normal use returned failure before velocity or ballistic-plane assertions were reached. **Cause remains unclassified**; it is not a demonstrated ballistic miss. Fixture diagnostics must record real cooldown, stack, held-mode/count, capacitor, player/frame and target state before changing gameplay or assertions.
2. Actual RAIL use in unavailable remote terrain synchronously materialized a column while finding a target. This reproduces a caller-safety bug in the raw terrain ray. A correction must use existing loaded-volume safety without treating an unknown boundary as a wall or reducing the intended loaded range.

Source `fabric.mod.json` was unchanged. Generated test resources were focused through the included init script. The run began with no disposable world; the prior generated XML was preserved separately and removed before execution to prevent stale evidence. Logs, fresh XML, exit, source and input receipt are retained.

`EVIDENCE/gravity-gun-first-negative-1ecca08.zip` SHA-256: `e1e21735e341137f8eb08ca5694549d58bfe9852021d54ac1d4bd5b5a4c45a3c`.

The bundle passed CRC and each-file SHA-256 verification. It contains the closed original world (131 files, original 26,348,611 bytes), archived under an exclusive session lock, CRC/per-file-hash verified before reclaiming only that generated world directory. Its nested world archive SHA-256 is `a050e659a1760ef05d0ee3df5747e9705a0a72f80e6bc521f36269dae4561626`. Preservation is not save/reload gameplay acceptance.

Scope limits remain: one short-range tilted heavy-body COM trajectory, not all-mass/long-range material-hit accuracy; zero-energy capture rollback, not insufficient-energy fling; at least one ordinary held body moves before all four launches; no full per-body formation proof. Initial connected mock players use random UUIDs. Explicit connection and exceptional-construction cleanup can be hardened in a later test commit without relabelling these original outcomes. S10-T02 and the broader stage remain open.


## Nov 16 diagnostic: test scheduling, no ballistic result

Exact combined source `40f7b6f9c19f6880a32e886aeb4a3811a8c325e8` compiled, then crashed in the first RAIL batch, Gradle/game exit 1. There is **no fresh XML and no completed six-case outcome**. Its loaded-ray fix is not yet accepted by this aborted run.

The bounded state trace records the same launch callback twice at world time 97. The first call had elapsed 8 / cooldown 6, held mode RAIL, one real body of mass 100, energy 88 and a live indexed target. It returned true, consumed 12 energy, released the grab and applied velocity `(11.2249718921, 22.4499437842, 33.6749169280)`, exactly the tested 42 m/s ship aim. A second invocation in the same tick had elapsed 0, no grab and energy 76; it correctly returned false on cooldown. A fastutil map iterator NPE followed in `GameTestState.tickTests`.

The fixture adds GameTest tasks while the task-map iterator is executing (`c.runAtEveryTick` during the launch callback, and nested delayed callbacks). Rehash can revisit a running callback or invalidate the iterator. This diagnostic supports a fixture explanation for the earlier terse RAIL launch failure; it does not rewrite the original 2 FAIL / 4 PASS record, and neither ballistic material impact nor the twelve-block plane was accepted. The default body in this scene has gravity disabled, so even a future successful plane crossing is a zero-gravity COM trajectory, not gravity-compensated ballistic accuracy.

Correction is confined to test scheduling: one per-tick driver registered before execution, with test-owned due-action and observer queues. The eight-tick launch delay, body mass, aim, 42 m/s velocity assertion and twelve-block/1.2-block trajectory bound must remain unchanged. No RAIL gameplay correction is indicated by these state events.

First crash evidence: `EVIDENCE/gravity-gun-fixture-crash-40f7b6f.zip`, SHA-256 `4ef80e7850b0a0fe6e4f885c847ed670fe83172a0a54854a73c6ff07aecd199a`. Raw log/crash, no-XML receipt, exact source/inputs, both rail state events and the crashed disposable world are preserved. CRC and every-file hashes were verified before reclaiming its generated directory. A crashed-world archive is not a clean save/reload result.
