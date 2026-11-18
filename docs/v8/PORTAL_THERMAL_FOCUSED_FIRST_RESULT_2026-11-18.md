# First focused portal / thermal integration result — 18 November 2026

Exact integration source `8650bf80eda41e60ef6bead3f8862a6fe923730f` passed **16 unit tests and 44 ordinary server GameTests**, with zero failures, errors or skips. The actual Gradle process exited `0`, without timeout, on 18 November at `12:48:55.285648 UTC`; it started at `12:47:22.427490 UTC`. These are focused results, not a full regression or a client acceptance result.

| Actual selected class | Passed cases |
|---|---:|
| BodyPortalDisplayTest | 6 unit |
| RagdollPortalTransferTest | 5 unit |
| ThermalBoreTest | 5 unit |
| PortalTransferGameTests | 8 GameTests |
| RagdollPortalTransferGameTests | 2 GameTests |
| PortalExitInterestGameTests | 3 GameTests |
| ThermalDrillGameTests | 9 GameTests |
| PortalExitSafetyGameTests | 8 GameTests |
| PortalProjectorGameTests | 14 GameTests |

The ordinary source manifest has **94 entrypoints**. The retained focus initializer selected only the six GameTest classes above and three unit classes. XML was independently parsed: all 44 GameTest names are distinct and all fresh result files agree with the recorded outcome. The older `prior-full-game-results.xml` in the run directory was excluded and contributes no tests to these totals.

The scope covers atomic portal transfers of existing physical/riding groups, articulated ragdolls, final movement-phase ordering, loaded destination refusal and portal ownership regressions; it also covers asynchronous exit ticket progress and unlink/removal lifecycle, plus bounded thermal drilling/cooling and drops. BodyPortalDisplayTest verifies display-state arithmetic, not rendered pixels. The actual server log identifies Minecraft `1.21.1`, Fabric Loader `0.16.14`, Java `21`, `STOCK` / `VANILLA` world settings, and absence of native BetterCubicChunks, Immersive Portals, Sodium and Iris. Native XYZ interest, client visuals, hardware performance and full-spec acceptance remain open.

`remapJar` completed in the same successful invocation. The captured JAR is `drmd-6dof-1.1.35-ufo-dev.1.jar`, **3,087,133 bytes**, SHA-256 `69f474f401e7decc478737426ee89d7af11de53327029bd78cc40009d90d4e45`. Its 1,907 ZIP members passed full CRC verification. Ordinary GameTests executed named development classes; this remapped JAR has **not** been exercised by a same-JAR client in this checkpoint. It contains no embedded source stamp: source association is the explicit original start/outcome receipt plus exact Git postimages, not a claim of embedded provenance or a complete pre-run classpath receipt.

Evidence is retained in [portal-thermal-focused-8650bf80-first-20261118.zip](EVIDENCE/portal-thermal-focused-8650bf80-first-20261118.zip): **6,691,948 bytes**, SHA-256 `2155fc3c7a2bd5358f1766681deadda6fbcabb17911aa76ebeef7f034cbbc66c`. Its 53 payloads plus `MANIFEST.json` contain the original start/exit/outcome, full first log, focus initializer, fresh XML, JAR, closed-world archive and preservation receipt. Source snapshots contain the 38 unique changed postimages since `b8f24500` (27 portal-integration paths plus 12 thermal paths share the GameTest manifest), and the two unchanged selected legacy regression classes, each once. No old `0107` proof is copied.

The nested closed-world archive SHA is `a9487bb19fdc48bd42cb31aee9878c588c8beee8f018b3e3da846ec40729d8e3`. This independent review verified every one of its 196 file lengths, SHA-256 values and ZIP CRCs, and all 58 gzip footers. Root's original exclusive-lock/fresh-inode preservation and raw-reclaim receipt is included; this review neither reruns that deletion nor claims a new lock observation on an already reclaimed raw world. All 12 original retained run files kept identical bytes, inode and modification time during packaging; all outer payload hashes and CRCs were checked once after writing.

Root's later source `812e5e94` contains the pending-crossing follow-up and was **unrun at this checkpoint**. Remembering a crossing while the exit warms, subsequent drone gameplay changes, native/client acceptance and the full suite do not inherit this PASS. No JVM, world launch, gameplay source change or central-ledger update was performed during this evidence review.
