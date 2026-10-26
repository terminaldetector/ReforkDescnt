# Third-party attributions

DRMD 6DOF is MIT (see `LICENSE`). This file lists third-party material distributed with it or
adapted into it, and the notices those licences require.

Required by the plan's section 20, and not written earlier than it was needed: an attributions table
with nothing in it reads as "this question has been settled" when it has not. It became due the
moment the tree started distributing someone else's binary.

## Native mirror/portal transition, 26 October 2026

The external Immersive Portals 6.0.6 JAR and direct bridge were removed in 1.1.17-mirrors.1.
DRMD builds and runs without that mod. The locally adapted pure math helpers below remain
Apache-2.0 source and compiled code; removing a dependency does not remove their attribution.

Copyright the Immersive Portals authors (qouteall and contributors).

Their upstream `1.21` line (6.0.6) uses Apache-2.0. Other upstream branches have different licences.
The Apache licence text is retained in `src/main/resources/META-INF/licenses/` and packaged in
DRMD's own JAR alongside this attribution. Per-file headers identify DRMD's modifications.

## Vendored into DRMD's own source

| File | From | Licence | Modified? |
|---|---|---|---|
| `src/main/java/com/terminaldetector/drmd/vendor/immptl/ImmPtlQuaternions.java` | Immersive Portals 6.0.6, `qouteall.q_misc_util.my_util.DQuaternion` | Apache-2.0 | Yes — see the header |
| `src/main/java/com/terminaldetector/drmd/vendor/immptl/ImmPtlPlane.java` | Immersive Portals 6.0.6, `qouteall.q_misc_util.my_util.Plane` | Apache-2.0 | Yes — see the header |
| `src/main/java/com/terminaldetector/drmd/vendor/immptl/ImmPtlIntMatrix3.java` | Immersive Portals 6.0.6, `qouteall.q_misc_util.my_util.IntMatrix3` | Apache-2.0 | Yes — see the header |
| `src/main/java/com/terminaldetector/drmd/vendor/immptl/ImmPtlAARotation.java` | Immersive Portals 6.0.6, `qouteall.q_misc_util.my_util.AARotation` | Apache-2.0 | Yes — see the header |
| `src/main/java/com/terminaldetector/drmd/vendor/immptl/ImmPtlIntBox.java` | Immersive Portals 6.0.6, `qouteall.q_misc_util.my_util.IntBox` | Apache-2.0 | Yes — see the header |

Vendored files live under `com.terminaldetector.drmd.vendor.<project>` and are Apache-2.0 whole, so
each file is its own licence boundary. Each carries the Apache header, the copyright, and a list of
the changes made, as Apache-2.0 section 4 requires. Nothing outside those packages is anything but
DRMD's own MIT code.

## Adapted, not copied

Ideas and algorithms read from other projects during the source audit are described in
`docs/source-audit/algorithm-map.md` and reimplemented; where a description leads to copied code
instead, the file moves into the table above with its notices.

Projects read under licences that forbid copying into an MIT project — NoCubes and LittleTiles
(LGPL-3.0), bobby and Immersive Aircraft (GPL-3.0) — are, and stay, description only.
