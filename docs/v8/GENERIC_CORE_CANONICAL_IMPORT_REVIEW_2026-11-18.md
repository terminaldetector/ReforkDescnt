# Independent generic-core canonical import review

Source/CPU-only **PASS** for exact canonical `4c058a4bc6d18e561a2c3d65d778a374ffc5a7ab`, parent `e24372756aca051cad4abd98bec28e808b2078ec`, against qualified producer `0bb280a8d27e4c1a0e75fd09a79ee6a2e05232ce` (production d492). No Java, canonical working-tree edit or generated-output mutation occurred.

- Exact24-path import:21 non-language producer paths are byte-identical; the two language objects preserve every preimport key/value and add only `block.drmd.ship_control_core`; the remaining path is the exact ordinary manifest union. Existing U16/505 keys are retained, with duplicate-key rejection in the audit parser.
- The GameTest manifest SHA-256 is `7683042e587a96de8a1f9cab79dfaaf2d888754b6e0f259e119f523810cae342`: all81 original entries in original order plus the three new classes,84 total, corrected test-only mixin config. The main manifest is unchanged and the isolated Gradle init is absent.
- Full `Input`, `Fire`, `TurretInput` and legacy four-field `State` record declarations, including their CODECs/constructor signatures, remain byte-identical505. All14 prior public static UfoControlSystem method signatures remain present;21 are now declared. This is a source API/wire comparison, not remapped binary ABI proof.
- Exactly18 new unit methods (8frame/7admission/3codec) and19 new GameTest methods (7core/7fire/5assembly) are declared. The unmodified final-AA claim decoder, emitter and interception source files remain exact505; modified shared core/scheduler files match the reviewed producer.
- AssemblyWriteProbe still has no custom VM property. Its scoped fixture arm and inactive guards match the qualified source; normal full-suite mixin inertness is to be exercised by the coordinated combined gate.

The prior isolated18unit and actual19GT passes remain their own results. This review does **not** report a current combined full-suite/native pass or close all B2 clauses. Other lanes may add disjoint changes after4c; they are outside this pinned review.

[Per-file hashes and receipt](EVIDENCE/generic-core-canonical-4c058a4-import-review.json). [Exact independent audit script and raw receipt](EVIDENCE/generic-core-canonical-4c058a4-import-review.zip).
