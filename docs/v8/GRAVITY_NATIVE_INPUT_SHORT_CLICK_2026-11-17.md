# Native gravity-tool input: decoded short-click loss

Tested source: `07b2fa8a999c66bfb38893e276592c0dd959a4ca`. This is the
separately granted first run of the corrected required observer mapping. The
production JAR is unchanged from the preceding `c76` run.

## First result

Offline compilation/remapping passed. The generated observer refmap resolves
the full `Item.use(World, PlayerEntity, Hand): TypedActionResult` selector to
`DescentWeaponItem.method_7836`, which exists in the actual remapped main JAR.
Both injections remain required. Dedicated fresh runtime staging passed.

The one GL 3.2 native process opened the real Cubic world, acknowledged normal
hotbar selection and reached the first scene, `ordinary-swarm-lmb`. Its actual
one-client-tick LMB pulse produced these server receiver observations:

| Sequence | World time | Observation |
| --- | --- | --- |
| 1 | 9066 | Vanilla hotbar packet selected slot 8 |
| 2 | 9069 | Vanilla hotbar packet selected slot 0 |
| 3 | 9077 | Real trigger receiver: mask 1, eligible, SWARM in main hand |
| 4 | 9077 | Real trigger receiver: mask 0, eligible, same selected SWARM |

No actual item-use callback followed. At the unchanged transaction deadline,
server tick 299/world time 9237, the capacitor was still 100, cooldown was zero,
the grab set was empty, and all four original one-cell physical targets remained
present and stationary. The full 24-block collision halo was loaded. The real
client and server both retained slot 0 and the registered SWARM item; no GUI was
open, and 6DoF was off as required by this scene.

`DroneArsenal.receive` currently overwrites the previous mask, and the weapon
simulation samples that latest held mask. A press and release received together
can therefore disappear before a firing tick. This is evidence of **lost short
clicks**, not evidence that sustained firing is broken. The first transaction
failed and the remaining five scenes were not executed. Full ordinary/6DoF
button, hold/throw and energy acceptance remains **OPEN**.

The observer saved one fresh world-frame PNG showing the four actual material
targets and wrote the failure marker before stopping. Its intentional exception
at `rendered` line 320 rethrows the original CAPTURE deadline to terminate the
test; it is not an additional screenshot or mixin failure. Native exit was
**255**, without external timeout. The lease was returned immediately on close,
before analysis. No retry, timeout change, production fix or sustained-button
driver substitution occurred in this run.

## Preserved artifacts

- Main candidate SHA-256:
  `3f1e116c198d0341f0c81a400198fa0b1755be62afcaa2e60370c7be6920a7fd`.
- Required observer candidate SHA-256:
  `02a31ad4181e2e8a9d1c9fc99a444c39904013fcb1ec911cd50b061065291e84`.
- Bundle: `EVIDENCE/native-gravity-input-short-click-07b.zip`.
- Bundle SHA-256:
  `a0efa7a7d24f75715961e46acd2daf7d4ec0abef99a09cbccd0141f2e919cab2`.

All 136 payload SHA-256 values and ZIP CRC were verified. The bundle preserves
the tested source archive/diff, exact two JARs, read-only executing helpers,
compile/staging/controller/native receipts and logs, resolved mapping receipt,
actual launch/input manifests, decoded trace/failure JSON, fresh failure frame,
crash report and runtime configuration. All 95 closed-world files (36,952,883
bytes) are preserved. These are post-failure artifacts, not restart acceptance.

The original `c76` pre-world mapping failure remains separately preserved and
must not be reclassified as a gameplay negative. The next authorized work is a
test-first bounded rising-edge latch in the actual server arsenal, followed by
the **unchanged** native one-client-tick driver on a separately granted run.
