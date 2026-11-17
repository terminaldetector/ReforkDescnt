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

Compilation/first tests are pending. Root will preserve the first outcome. A real
rider traversing a moving child platform, two-process cold restart, generic world
anchoring, powered doors/airlocks and complete Dynamic Boarding acceptance remain
separate gates. This increment does not close the whole §6 or master taskS09-T03.
