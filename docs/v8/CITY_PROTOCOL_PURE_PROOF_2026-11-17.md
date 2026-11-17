# City save protocol: first47 pure checks

Tested source `62c976dbea81969eb34ba7a6d5838127eaf6c34b`, isolated branch
`experiment/city-authoring-20261117`. Original26 proof77dc8a1/bd8acc6 is unchanged.

First offline standalone compilation exited0 in8seconds. First focused test run
exited0 in10seconds: **47/47 passed**, no failures/errors/skips. No first failure
occurred; no source/helper changes or retry occurred during the lease. Fresh unique
output contained exactly five XML suites:

| Suite | Passed |
| --- | ---: |
| CityBlueprintTest | 7 |
| CityBatchJournalTest | 13 |
| CityAuthoringJobTest | 14 |
| CitySaveIdentityTest | 6 |
| CityManifestTest | 7 |

Actual executing scope is JDK-only: six production models/utilities and five tests.
Seventeen source/helper/wrapper input hashes, seven cached JUnit artifacts and JDK
inputs match before/after; all37 compiled-class hashes match too. No Java remained
active at lease return. Old zombie processes were excluded. No Minecraft client,
server, Loom/full-mod compilation or actual WorldAccess adapter was executed.

The real temporary-file checks establish close/reopen save-ID and manifest data,
cross-save/job denial, exclusive same-process file ownership, strict unknown-version/
bounds/hash rejection, corrupt/partial metadata preservation, durable cancel and
after-image-fence controls. Controlled futures establish acknowledgment ordering,
cancel priority behind an earlier prepare/commit, immediate world-port stop, refusal
to resume ahead of a control acknowledgment and quarantine on lost acknowledgments.
These are protocol contracts, not actual filesystem-failure/power-cut or game tests.

Archive `CITY_PROTOCOL_PURE_62C976D_EVIDENCE_2026-11-17.zip`,56,466bytes,27members:

```text
SHA-256 c436f68302bd26b24ce0e7e4a64ca7e414bff8a0b4922eaa1c387c90caa2013a
```

ZIP CRC and every manifest member SHA were independently reread and verified.
Archive includes frozen source tar, actual commands, first compile/test raw logs and
explicit exits, all input captures,37-class hashes, fresh XML/HTML and close summary.
Raw originals remain at
`work/city-pure/protocol-first-62c976d-20261117T123554Z/` in the isolated tree.
No real Minecraft save was created or reclaimed; JUnit used its own temporary files.

Remaining gates: serial runtime I/O worker/session with actual shutdown/drain,
Minecraft loaded registry/cube/six-neighbor/permission/actor preflight, fixed-slot
manual commands and visible sterile-palette diagnostics, owner disconnect/stop
handling, actual mod compilation, focused real-world tests and fresh-process native
save restoration. No registration/startup writes, retired generation, automatic
resume, physical city/actors/siege/canon or S13 completion is claimed. Central
ledgers/full-mod test counts were not edited.
