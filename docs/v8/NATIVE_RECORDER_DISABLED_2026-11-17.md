# Recorder disabled: first actual component control

The first `9426-disabled-v1` run passed its **disabled-output control** on17November.
This is the historical recorder component, not a run of the latest combined game.
Exact Java source: `9426a6549fc91abc8c7e060a2eef9f184742dc3d`; docs/host HEAD:
`4d997e509f81b9cc3bf51853ee59bda8b2aa85eb`.

| Matched artifact | SHA-256 |
| --- | --- |
| Main | `cf3701b529b21633a8a3e910e6755d006d97d6349e8938d7701c82671027138d` |
| Separate recorder observer | `cb1056d89d1435a5211c5a1c1728934fde2caf92c3c85e28016e2c5ff4bce70a` |

No rebuild or game/physics/recorder-source change occurred. All1438 Java/build
inputs still match the original9426 stamp. The actual copied host wrapper was
`b2f734bb22f70df34a1fce61683f5d49fc3a3fbd4dd261d63d4344d08604f17c`,
the native executor `1946e64f61de6d676f1cf4e09e519e59174fad3356de3eea56e13ed5ab56fc64`,
and the external observer `6352a0991733890e35dbd6c51985cd3601887778649d6fd4a47972b938032fc9`.
All actual snapshot/configuration hashes remained unchanged. Original helper
bytes, launch and early child-exit receipt are preserved.

## Actual result

The external observer was ready at **14:49:26.809982Z**, before Java. Native child
69298 closed **14:50:55.192832Z**, exit0, timeout=false, forced_kill=false.
Native-host, external-observer, old v1 verifier and combined-host exits were0.
The Java lease was returned on that first close; there was no retry or capacity run.

- **441 bounded polls**, no observed output member of any extension, zero `armed`,
  zero `wrote`, zero recorder failure log lines. Recorder properties were entirely
  absent, rather than set to an explicitfalse value.
- The unchanged mounted gate performed real key/use/sneak interactions and all
  five scenes: connected-off, laser-fire, rocket-fire, fire-off-reversed,
  bearing-detached. Five fresh complete PNGs and later render-thread activity
  show an active client after the observer started. Fixture-supplied joint/shots
  and operator-follow teleport remainedfalse.
- Free disk before staging760643584bytes; before Java729874432bytes and private
  allocated runtime29732864bytes. Post-close private runtime55283712bytes. The
 256MiB/160MiB preflight guards passed. No save/cache/source was removed.

These observations establish disabled **emission behavior**, not zero allocation
or absence of every theoretical sub-poll filesystem event. No manual frame review,
new full-suite run, latest-game acceptance, performance threshold, physical GPU,
dedicated server or fullS16/release certificate is implied. Capacity16 remains open.
The earlier enabled instrumentation proof and its original wrapper failure/native
exitUNKNOWN remain distinct, unchanged evidence.

## Retained proof

[Parsed receipt](EVIDENCE/native-recorder-9426-disabled-v1.json) and
[compact original proof](EVIDENCE/native-recorder-9426-disabled-v1.zip) retain all
actual launch/source/stamp/exit/timeline/observer/native logs, immutable copied
helpers, five PNGs and the Python-only readiness receipt. ZIP CRC and every
payload SHA were independently re-read after packaging. No duplicated JAR or
world bytes are hidden in this archive: exact previous9426 binary/source archive
is referenced with its hash, and the closed disposable save remains preserved
in place. Preserving that save is not a new cold-restore acceptance.

No central status or all99 task completion was changed. Development remains local.
