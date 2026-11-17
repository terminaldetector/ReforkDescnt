# Linear stage: previously unimplemented Dynamic Boarding §6 component

Primary requirements are master v8§6 and the direct-user Dynamic Boarding§6:
physical sliders/platforms/airlocks must reuse BlockBody/joints with their own
collisions. The later user direction requires separate reusable blocks, rather
than another fixed UFO kit or a Create dependency.

Source1a859 had a real low-level SLIDER solver and persistent joint codec, but
MechanicalBearingSystem could create only MOTOR links from rotary bearing blocks.
There was no player-built linear actuator. The new increment adds two material
blocks, `sliding_bearing` and `linear_drive`, through the existing local-grid wrench
route. Opposing guides must already meet; assembly sets no poses or velocities.
Their local axis is transformed by the current body quaternion. Stroke is0–2m,
speed up to.6m/s and one drive supplies at most40N through the existing solver.
Zero power/drive loss coasts; sneak-use reverses the separate drive. Two competing
sources coast; a rotary source does not power a slider. Existing rotary controls
and legacy/operator joints retain their original behavior.

The same registry owns finite limits, collision participation, material anchors
and save data. Both bodies remain real free physical bodies. There is no new
rigid body per static block, follow teleport, separate interior dimension or
static ship copy. No new contact exemption is added for sliders; ordinary body,
actor and terrain contacts remain active.

First source increment includes recipes, drops, harvest tags, six-direction
models/item icons, English/Russian names and wrench guidance. Five actual
GameTests cover player connection/codec/state preservation, rolled physical
extension/stops/retraction, wrong/ambiguous drive rejection, momentum-preserving
coast/unlink and material replacement. Fixture-owned manual solver steps are
explicit; they are not a native rider or natural-tick persistence proof.

The first compilation, focused GameTests and remap passed on exact source
`3920768301320fa35af5b178a85ebf92bed99ef6`: **17 tests, zero failures/errors/skips**
(five new linear cases plus twelve retained rotary cases). The processed manifest
contains only those two declared classes; no fixture dimension override is added.
The rolled case takes180 extension and180 retraction physical steps with finite
stroke0–2m and verifies reaction against a free parent, transverse drift below.03m,
relative quaternion agreement above.999 and retraction below.2m. Actual wrench
use, saved registry equality, competing/wrong sources, actuator destruction and
material replacement also pass. These fixture-owned solver steps do not establish
natural-tick rider behavior.

The fresh XML, actual child/wrapper exits0, source/input/helper hashes, exact
executing runner, full log, processed manifest and source patch are retained in
[`EVIDENCE/linear-stage-3920768.zip`](EVIDENCE/linear-stage-3920768.zip),36payload
files plus SHA manifest,182383bytes, SHA-256
`30bf2934c495aa5485cd37a7f4026c5945e23860924d54701d3086c8b5430297`.
Every entry SHA and ZIP CRC was verified. The newly remapped game JAR is preserved
locally under `work/multi-agent/linear-first-3920768/artifacts/`, SHA-256
`1d1786bdaee1e8c5b159aa7068b4356563da64774abf26dadf3d3106784508a8`.
Other old build-directory JAR hashes in the raw receipt are inventory, not new
observer acceptance; this gate did not run a client or the full unit suite.

A real rider traversing a moving child platform, two-process cold restart, generic
world anchoring, powered doors/airlocks and complete Dynamic Boarding acceptance
remain separate gates. This increment does not close the whole §6 or master task
S09-T03. The five cases are now added to the ordinary manifest for the next combined
run; the recorded11513 baseline remains824unit/418GameTests until that run occurs.
