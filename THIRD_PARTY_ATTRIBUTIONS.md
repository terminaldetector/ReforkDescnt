# Third-party attributions

DRMD 6DOF is MIT (see `LICENSE`). This file lists third-party material distributed with it or
adapted into it, and the notices those licences require.

Required by the plan's section 20, and not written earlier than it was needed: an attributions table
with nothing in it reads as "this question has been settled" when it has not. It became due the
moment the tree started distributing someone else's binary.

## Distributed with DRMD

| Project | Version | Licence | What is distributed | Modified? |
|---|---|---|---|---|
| [Immersive Portals](https://github.com/iPortalTeam/ImmersivePortalsMod) | 6.0.6 (MC 1.21.1) | Apache-2.0 | `libs/immersiveportals-6.0.6-mc1.21.1-fabric.jar`, unmodified, used as a compile-time dependency | No |

### Immersive Portals

Copyright the Immersive Portals authors (qouteall and contributors).

Licensed under the Apache License, Version 2.0. You may obtain a copy of the licence at
<http://www.apache.org/licenses/LICENSE-2.0>, and the full text ships in the jar's own `LICENSE`
entry.

The jar is redistributed unmodified. DRMD calls into it through a single file,
`world/portal/mirror/ImmPtlMirrorBridge`, and works without it — see `docs/IMMPTL_STACK.md`.

**On which licence applies.** Immersive Portals' licence differs by branch: the `1.21` and `1.20.1`
lines are Apache-2.0, while `master` is GPL-3.0. DRMD targets Minecraft 1.21.1 and uses 6.0.6 from
the `1.21` line, so Apache-2.0 is the one that governs here. This is recorded because the difference
is easy to miss and was in fact missed once — see `docs/source-audit/license-map.md`.

## Vendored into DRMD's own source

| File | From | Licence | Modified? |
|---|---|---|---|
| `src/main/java/com/terminaldetector/drmd/vendor/immptl/ImmPtlQuaternions.java` | Immersive Portals 6.0.6, `qouteall.q_misc_util.my_util.DQuaternion` | Apache-2.0 | Yes — see the header |
| `src/main/java/com/terminaldetector/drmd/vendor/immptl/ImmPtlPlane.java` | Immersive Portals 6.0.6, `qouteall.q_misc_util.my_util.Plane` | Apache-2.0 | Yes — see the header |

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
