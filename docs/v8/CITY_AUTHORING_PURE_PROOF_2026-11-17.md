# Manual city: first pure Java proof

Tested source: `77dc8a108587eb6bdcb31c6bbec8d48be744839c`.
Base: full-tested `699b749045edde2722f0ae9685b2a3674c1a1041`.
Branch: `experiment/city-authoring-20261117` in its own sparse worktree.

The first standalone offline compile exited0 in8seconds. The first focused
JUnit run exited0 in8seconds: **26/26 passed**, zero failures/errors/skips.
No first failure occurred; no source edits or retries were made during the lease.
This is **pure model/journal/controller proof**, not a Minecraft build, cubic-world
GameTest, native rendering check or full S13 acceptance.

| Suite | Passed | What actually ran |
| --- | ---: | --- |
| CityBlueprintTest | 7 | Deterministic real sparse geometry; eight archetypes twice; air entry/exit; separate solid/AIR-cut caps; actual bounds; duplicates/order/hash/material denials |
| CityBatchJournalTest | 8 | Real temporary files, force/close/reopen, durable prefix/pending state, identity denials, before/after/mixed/foreign/unavailable decisions, truncated/corrupt bytes preserved, missing-parent rejection |
| CityAuthoringJobTest | 11 | Explicit owner controls, page/64-cell bound, deferred disk acknowledgment, owning-thread world access, pause/cancel/recovery, CAS conflicts, I/O failure quarantine, notification after-image verification, terminal completion |

The executing project was exactly `verification/city-pure`, without Loom,
repositories, Minecraft dependencies or downloads. It compiled only the three
city production classes and their three tests. Actual input capture binds12
source/helper/wrapper files, seven cached JUnit artifacts, JDK release/java/javac
hashes and28 compiled classes. All before/after input and class hashes match.
Fresh output contained exactly three XML suites. `/proc` showed no active Java
when the lease was returned; old zombie processes were excluded.

Evidence: `CITY_AUTHORING_PURE_77DC8A1_EVIDENCE_2026-11-17.zip`,33,097bytes,
15members. SHA-256:

```text
e1d159e71fcad885ec0c4ad08af92320d93d5d3771ad8de440b53915c3024a0d
```

ZIP CRC and every member's SHA were independently reread and verified. It retains
the frozen source tar, actual commands, compile/test raw logs and explicit exits,
all three input captures, compiled-class hashes, fresh XML and close summary.
Raw originals remain under
`work/city-pure/first-77dc8a1-20261117T120330Z/` in this isolated tree. No disposable
Minecraft world existed; no world/cache reclaim or source/build duplication was
needed. Only the small standalone Java build was produced.

Known boundaries remain explicit:

- A journal parent must already be durably provisioned. File/header and parent
  directory are forced before open acknowledges; ancestor provisioning and actual
  power-cut survival were not tested.
- The durable journal records PREPARED and COMMITTED. Notification-cause and
  cancellation disposition are currently session-local. Reopen is manual and
  reconciles durable pending state; durable runtime manifest/stop disposition needs
  its own implementation and acceptance.
- Storage/CAS in controller tests is a bounded in-memory port. No actual Minecraft
  registry/WorldAccess/permission/border/actor/neighbor preflight or save/reload was
  established by those tests.
- The first runtime adapter must reject BE/reactive/unstable before-states and
  six-neighbor envelopes as well as restrict target materials. Stable targets alone
  do not prevent an existing adjacent falling block from creating an actor after
  neighbor publication. Missing or unsafe loaded volume must pause without tickets.
- No runtime registration, startup world write, legacy generator, StructureMover,
  new dimension, AI/actors, whole-city physics, siege or approved canon was added.

Actual mod compilation, real loaded-only cubic edits, persistent manual controls,
fresh-process reload, actual destruction/performance and client/native city checks
remain separate gates. This evidence does not change the central task ledger or
the existing full-mod test counts.
