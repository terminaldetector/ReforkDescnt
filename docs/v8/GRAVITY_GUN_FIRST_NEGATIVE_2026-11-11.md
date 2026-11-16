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
