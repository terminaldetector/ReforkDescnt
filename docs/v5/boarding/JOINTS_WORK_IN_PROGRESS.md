# Joint runtime — work in progress

This development branch is based on the earlier1.1.33 source checkpoint. Its version metadata
is inherited; it is not the certified1.1.33 release. No new installable JAR is published by this
checkpoint. The verified release lives on codex/ungravity-cloud-recovery-20261024.

Implemented source: fixed/hinge/slider/motor constraints on existing D6 bodies, bounded
per-step motor impulse, break loads, force/pose integration with rollback, shared island
substeps before hull/player contacts, and a world-owned persistent joint registry. Material
anchors follow COM changes and real connected fragments; destroying an anchor removes the
connection. Unloaded endpoints pause the island with momentum retained. New links activate
on the next world tick rather than splitting an entity update phase.

Local previous checks:670 unit tests and218 GameTests passed, including long forced motion,
hinge orbit energy, slider stops, registry round-trip, fragment anchor transfer, actual world
motor/slider/fixed motion and overload release. Logs under work/body-joints retain the first
compile failure and subsequent runs. Fresh checks on resumed source follow this checkpoint.

OPEN: real-client joint/platform acceptance and its distribution gate; live editing/control
UI or commands; joint/collision stress and dense islands; full endpoint volume loading and
simulation LOD; whole-world/dedicated/multiple-client acceptance. The full master TZ, dynamic
boarding/UFO acceptance, functional blocks, ControlCore and full3D AI/bestiary remain open.
This checkpoint must not be presented as completed movable-mechanism acceptance.
