# Profile-correct STOCK retry — relative-path fixture failure

Source `668e9514c0fe1b445d70ca177d9684d364170226` compiled and ran the dedicated fresh STOCK fixture. **The actual pre-registry profile selection worked:** only `drmd:advanced_column` moved from enabled to disabled, with other profiles/order retained. Both test logs directly report actual ServerWorld bottom-64/top320.

Both cases then stopped at the instrumentation check for the dedicated run directory, before material/callback assertions. FabricLoader.getGameDir returned the valid relative path`.`; the observer incorrectly compared its raw final path component with `surface-stock-control`. The actual new world/report files are under the dedicated `build/surface-stock-control` directory. This is a path-comparison error in the test, not a production height failure or a callback PASS. Gradle exited1/GameTest2 in36s. The unchanged five numeric unit tests were not rerun or relabelled.

The next test-only correction normalizes the actual path to absolute form and compares it with the explicitly supplied dedicated directory. The strict backend/profile, bottom-64/top320 and loaded GOLD/material/top-face/demolition/restore assertions remain unchanged. No production, runtime dimension or ordinary full-suite configuration changes are justified by this fixture outcome.

Original logs, source/helper input hashes, exact XML, generated focused manifest/mixin JSON and original level.dat are immutable here. The29-file closed dedicated world was archived after full-file SHA/size comparison and gzip CRC/ISIZE read-to-EOF; receipt preserves archive hash/path. No user save/shared cache was touched. No actual corrected-source STOCK callback result is claimed at this checkpoint.
