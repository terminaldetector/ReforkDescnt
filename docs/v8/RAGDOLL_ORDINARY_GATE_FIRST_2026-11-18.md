# U13 — first ordinary ragdoll GameTest gate

**First attempt: 9/9 PASS.** Both actual Java children exited 0, without timeout or kill.
No retry or assertion change occurred. The exclusive Java lease was returned immediately
after the ordinary TestServer child closed.

- Production component: `5acde8915ee940b8d2c8086511810f112bbe21bf`.
- Updated test source: `faf732ca11ffbbf295c286fefd74845d393cc939`.
- Frozen launcher/proposal checkpoint: `5fec51d26439f26a07bab3b0e7e7eb5205886180`.
- Runner SHA-256: `cac099a5609f65aa2a29ed77054b56572c46bf3f19d67258fe7917298a33ac79`.
- Proposal SHA-256: `c24a7a08ca8086e15dfb726e99291f45aae824ffe9b04909989c119488fb1c4b`.

Only `RagdollGameTests.java` was compiled: the test class and its `CodecClock` helper.
The five production source files and all unit sources remained unchanged. The accepted
17/17 selected-unit component result was not rerun or relabelled as whole-suite proof.
The 14 accepted production class files were reused as an isolated overlay on the verified
canonical baseline, followed by the ordinary production resources and named dependencies.
This is named-class TestServer proof, not a newly remapped or installable mod JAR.

## Actual ordinary clock and codec observations

All nine methods ran through the ordinary Fabric DLI Main → KnotServer → TestServer
path, using exactly one selected GameTest entrypoint, a fresh own world and fresh XML.
The six pre-existing tests retain their original direct-tick regression behavior inside
that server environment. The **three new codec cases** use registered ServerWorld
entities and ordinary callbacks, with identity membership, entity-age and world-time
barriers; they do not manually tick the entity or solver.

1. **New limited JET, actual lethal damage, save/read and resumed ordinary ticks.**
   A live AI-disabled JET first received at least two ordinary registered ticks, then
   lethal damage created exactly one typed version-2 wreck. Both authored wing limits
   were checked after six ordinary wreck ticks. Its position, velocity, angular momentum,
   quaternion, identity and rig data survived the in-memory NBT comparison. After the old
   world identity was removed, the same UUID was registered again and received six more
   ordinary ticks. The final receipt records JET age 15, original wreck age 6, restored
   age 6, physical lifetime **6 → 12**, and world-time delta 6.
2. **Missing and explicit version 1.** Both valid three-part legacy JET fixtures spawned
   with unique identities, received their impulse and advanced six ordinary ticks and
   six world ticks. Both moved in the impulse direction, retained the legacy free joints
   and saved version/lifetime correctly. No version-2 migration was introduced.
3. **Unsupported and wrong-type metadata.** INT 99, INT 0, STRING `"2"` and DOUBLE 2.0
   each spawned with a valid unique identity. All four received six real ticks while
   staying fixed, retaining their centre of mass and zero physical velocity/angular
   velocity despite impulse and torque attempts. Raw version, physics payload, nested
   future field, hull identity and lifetime 37 remained equal. All four were removed,
   loaded/saved and registered again under the same identities, then received another
   six ordinary ticks. The second receipt gives ages **6,6,6,6**, lifetime 37 and world
   delta 6. Exactly eight refusal warnings were logged: one per load, twice per UUID.

The three `U13_CODEC_*_REAL_TICKS` markers each occur exactly once. Existing scout
fall/joint/floor, wall-contact, impulse/NBT, single death-wreck, sampled six-axis field
strength and field-removal regressions also passed. No earlier Spider/AI failure is
retired by this scoped result.

## Immutable runtime and preserved first evidence

The runtime classpath had exactly 109 entries and the proposal's two mod groups.
All 103 named runtime JAR hashes matched their qualified captured input values. All
**2,084 frozen input files** remained unchanged across the run, as did both freshly
compiled fixture outputs. The frozen runner was checked again when sealing evidence.
Minecraft expanded its runtime-owned `server.properties`; its separate before/after
hashes are retained. No Gradle, Loom, remap, native client or broad 951-unit run occurred.

Fresh fixture class hashes:

- `RagdollGameTests.class`: `7f680f24eca675e83cbc026faa774ec372571a4faf53d76537ad732e47a4a9f4`.
- `RagdollGameTests$CodecClock.class`: `7b401cd4dd409116304d20e787851607559ff1bf642bebdc22cd60763e5f614f`.

First XML SHA-256:
`3c9501dbec11135ba3e6828d18d1504295e74c677e96c72fa35f0af337651fd6`.
First raw game log SHA-256:
`5bfbf8445899c49eb01fb427a052d89bc6f37c2fd24de7d08abe27793ce3c310`.

`RAGDOLL_ORDINARY_FAF732CA_FIRST_EVIDENCE_2026-11-18.zip` preserves all first-child
commands, starts, actual exits, compile/game logs, fresh XML, unchanged-input proposal,
both fresh fixture outputs, 14 accepted overlay classes, source/launcher, startup
configuration and the closed world. Its 39 world files total 7,646,052 bytes; every
archived world file was read back and checked against its original SHA-256, with the
raw closed world retained unchanged. The artifact has 76 members, 1,154,982 bytes and
SHA-256:
`3730962ab3dba32c839a2771ab8be2bc389cc79cafdfa9fbd303c10e955341c3`.

This establishes a bounded ordinary-server gate for the new JET family and codec plus
the existing scout regressions. The save/read is in the same process. Separate-process
cold restart, native BCC/two-client family restoration, all-family coverage, moving-deck
ragdoll contact/gravity, swept CCD and sleep remain unaccepted. The positional angular
stabilization's documented energy/momentum dissipation limitation is unchanged. The
five production files have not been merged into the canonical working tree.
