# Existing UFO → physical BlockBody (work in progress)

This follows1.1.34 source5d5c2b8. It is not included in that certified JAR.
Registration `drmd:sky_ufo` and the existing `/d6 mega ufo` spawn route remain.
The current migration replaces its grid relocation, carried-entity shifts and solid envelope
with the existing BlockBody subclass/runtime. No parallel UFO entity or interior dimension.

Physical migration, actual enterable UFO client evidence, functional weapon/bay/reactor
modules, core capture, expanded interior, full world reload and UFO9 acceptance remain OPEN.
Source compilation or generic boarding evidence cannot certify these requirements.

The development version is1.1.35-ufo-dev.1. Its distribution gate rejects development
metadata; it cannot replace the accepted1.1.34 package. Initial local checks:671 unit,
229 server GameTests, including seven tests of the actual registered UFO. These cover one
physical identity/no terrain writes, a rotated real bay/deck, local mining, damaged-grid
entity NBT, retained material-point velocity and immediate reactor power loss/fragments.
The first reactor fixture incorrectly compared COM before/after removing material; its
failure is retained and the corrected check compares an unchanged material point and its
world velocity at1e-8 instead. A COM shift is physically required by this mass edit.

Native UFO mode reuses the real ship-gravity stand with the actual registered entity.
It does not place the small generic fixture's floor/ceiling in the world. A bounded physical
attitude controller drives the larger inertia, and the shared renderer must actually draw
the UFO. Completion of those scenes still does not establish functional turret/bay/reactor
sphere modules, expanded interior, core capture or full3D combat AI. The compatibility bay
currently retains unknown/unloaded escort IDs rather than refilling them without evidence
of death; durable escort death reconciliation remains open.

The first native fixture retained contact through full roll and most of pitch, then lost
support during the final pitch correction. Its larger mass-based torque allowance could
accelerate the surface away faster than the32m/s² field. The stand now bounds its commanded
angular acceleration to4rad/s² before applying torque through the actual inertia tensor.
Contact assertions remain unchanged; the runtime is not forced to keep impossible contact.

The bounded native pass retained contact through all full rotations and both jump modes.
Its final model assertion revealed a test-probe bug: every ModelOrientation call, including
launched escorts, was compared with the local player frame. The probe now samples the
actual appended matrix of the local player renderer, using that draw call’s tick delta.
The model/camera tolerances are unchanged. Full native rerun remains mandatory.
