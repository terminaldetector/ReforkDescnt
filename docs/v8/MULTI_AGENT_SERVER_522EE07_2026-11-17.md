# Integrated non-autostart siege state: 866 units / 450 server tests

Exact source `522ee0765fe1a439b0a079120d5282f498c64533` passed its first full
**866 unit tests and 450 ordinary GameTests / 73 entrypoints**, with zero
failures, errors or skips. The full Gradle task took 5m05s. Both native launch
protocols were bound to the resulting source/JAR pair, but **no client was run
on this candidate**. The last two-client installable package remains20ef2c4.

The only production delta from20ef is `SiegePlan`, `SiegePlans` and the optional
extension in `SurvivalThreatState`. Plans have bounded identities, warning and
encounter deadlines, ten append-only acknowledgements, transient current-tick
permits and saved compatibility checks. Unsupported extensions are preserved
without runtime authority. Existing actor slots and ordinary recon behavior
stay under the original director. No production caller activates a siege.

Root reviewed the code and imported13 new units and eight new server cases;
existing nine threat units/five threat GameTests were not duplicated. The
[isolated first gate](SIEGE_PLAN_INTENT_2026-11-17.md) separately preserves22/13,
three actual saved ledgers and440 observed Survival ticks without activation.
The proposed old-source four-case negative was not executed and is not evidence.

| Remapped artifact | SHA-256 |
| --- | --- |
| Game | `9e53eae734a7c4a0ac6ce42c0a4124c348c7d62e11a71eb3ed34316fdb107417` |
| Observer | `c7a7602a526ad1fc870a4682b2af6db0625509e673f0a13984978ffa033395a4` |

[Evidence](EVIDENCE/integrated-522ee07-server.zip), SHA-256
`a4193662747980c9811e508ce3535d517e9e473c250c9b0567a3ba42676f720d`,
contains26 payloads: fresh XML/unit tar, full logs, exact helpers, input/source
bindings, both actual remapped JARs and the closed-world preservation receipt.
Every payload SHA and ZIP CRC was independently checked. A first launcher
invocation rejected an incorrectly transcribed full source hash before any
build/JVM; its precondition receipt is retained separately from the successful
actual test invocation.

After close,1265 disposable GameTest world files /240200858 bytes were preserved
with every-file SHA/ZIP CRC and an exclusive session `lockf`, then reclaimed.
The retained world ZIP is `work/ufo-body/integrated-522ee07.zip`,28497972 bytes,
SHA-256 `5c8915746b00d6c9eac4701f30a9ce224645a63d1af853c2eee6eda27a2b481a`.

This is local server integration, not full specification/release acceptance.
The ordinary TestServer enables discoverable registry packs, including Advanced;
this does not re-enable retired generators or certify native-Cubic behavior.
Automatic siege, ground rocket infantry, friendly modular AA, mission objectives,
native fluid continuation, high-altitude clouds and complete Boarding remain open.
Current unrelated failures and their original archives remain in the repository.
