# Oriented hull contacts — 1.1.31-contacts.1

This stage extends the existing BlockBody/D6 engine with actual hull/hull contacts.
The complete master specification and Dynamic Boarding acceptance remain OPEN.
This source document describes implementation and limits; the later distribution report
must bind fresh results, frames and the remapped JAR to this exact source commit.

## Implemented

- A server world contact phase runs after hull free trajectories and before passenger
  constraints. Normal world ticks invoke it; tests also exercise explicit numerical cases.
- A temporary balanced spatial index covers the remaining swept material bounds. Every
  potential pair is tested. Local sectional compound BVHs and the existing OBB SAT/CCD
  perform narrowphase; neither the hollow envelope nor one rigid body per block is used.
- Translation accounts for movement of both hulls, including clear endpoints that already
  crossed. Actual quaternion poses verify angular contacts and preserve real openings.
- Clipped face polygons and closest support edges provide contact patches. Planar reduction
  retains the projected convex hull and deepest point. Merely retaining world-axis extrema
  can discard a closing corner and is explicitly regression-tested.
- Relative material velocity differentiates the quaternion interpolation. It does not
  subtract almost identical world positions at millions of blocks from the origin.
- Simultaneously touching bodies share two-sided unilateral/friction constraints, including
  a third resting hull. Collision changes re-query the remaining trajectory. The rest of
  the 50ms tick is integrated with the new contact velocity, without reapplying frame forces.
- Paused/unavailable hulls are immovable contact supports for that tick and retain their
  saved resume momentum. Terrain/volume availability still guards pose corrections and
  remaining integration. No player-follow teleport is introduced.
- Explicit placement and portal discontinuities reset the swept trajectory while retaining
  physical momentum. Corrected poses are published through the existing entity state.
- `runHullcontactcheck` uses the same eight-scene renderer/network harness in an ordinary
  disposable CI world that the installed native cubic client uses at Y2156. Packaging
  requires its fresh marker, all eight PNGs, actual hull-pair flags, synchronized positions
  and stable rendered rotations, along with all prior boarding/ship/edit/scale gates.

## Regression coverage

Pure tests cover fast opposed motion, openings, full three-axis transforms, rebased centres,
rotating geometry, reversed participants, large-coordinate material velocity, resting islands,
clipped patches and retention of all projected convex corners.

Real server entities exercise registered world updates, fast crossing, remaining tick time,
sequential three-body impacts, an initially resting chain, offset torque and angular momentum
below the existing speed limiter, rotated openings, explicit placement and paused supports.
First failed logs/XML, including time loss and contact-patch corner failures, are retained.

Client scenes: diagonal corner; terrain stop; hollow doorway; compound relative contact;
gravity support; opposed physical hulls; three-hull chain; a fully rotated offset strike.
The scene freezes its successfully solved sample only for the subsequent network/render
comparison. Frames certify the actual renderer and post-contact synchronization, not a
live multiplayer battle, a city destruction benchmark or player boarding during an impact.

## Explicit limits and unfinished requirements

- Existing production limits remain 50m/s translation and 2rad/s rotation. The integrator's
  limiter dissipates momentum above its bound; angular conservation is certified only below
  that limiter. Removing it safely requires extending world/terrain/prediction step budgets.
- Translational CCD is exact for fixed orientations. Rotation uses bounded corner-travel
  subdivision (0.15m); this is not a proof for arbitrarily thin geometry or unbounded spin.
- Contact iteration is bounded proportionally to loaded hull count. If exhausted, unresolved
  trajectories pause at the last checked pose with momentum retained; `Stats.budgetPaused`
  exposes this. Dense persistent contact islands still require a dedicated stress acceptance.
- Positional overlap projection is a small numerical correction, not a physical impact impulse.
- No damage/fracture law, connected-component separation, joints, functional block simulation,
  ControlCore handoff, physical conversion of SkyUfoEntity, complete 3D AI, dedicated two-client
  multiplayer proof or full-world ship/module save/reload acceptance is closed by this stage.
- Existing 32768-cell/128-edge limits and full-volume native leases remain as verified in1.1.30.
  This stage does not claim a larger hull limit or completion of all12 Boarding criteria.
