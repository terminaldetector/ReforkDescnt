# Regressions

Late travel deferral: previous tick stamp stranded accepted commands; current-phase refresh and asynchronous GameTest guard it. External bay acceptance also checks lateral bounds.

Identified release gate issue: literal version-prefix lists stop at1.1.34 and could silently omit inherited physics checks for1.1.35+. Fixed in f6fa6e4;47 release-gate tests pass.

Actual UFO restart:1c200fd native run restored the serialized hull and physical state but failed to reacquire physical walking (contact0 timeout). Reproduction and first attempts in UFO_RELOAD_WIP.md; remains OPEN.

Restart follow-up: source9c461cc fixes forced suit rearming; dc13381 fixes initial body-load admission for saved context-bearing passengers. Native08d61c3 passes actual restart/Ready-delay/grounded contact/keyboard walk on Mesa+Sodium. Pre-context legacy admission and modules/unload transitions remain OPEN; see UFO_RELOAD_2026-11-04.md.

Drone state:6cb317e fixes bank/energy/cooldown/phase and hidden corpse ownership; current actual restart proof is preserved per exact source/JAR. Navigation: b7f8ff0 reproduces blocked wall/ceiling,4b22163 reproduces off-grid one-cell failure; world-aligned velocity-followed routes guard both. Observer counters are frozen on the server after a retained non-atomic report race. Combat rings: c7c828b reproduces7 actual movement failures; d9d0fd8 corrects radial signs and removes duplicate target attraction from flock/retreat. Current690 unit/264 GameTests plus two actual128 restart pairs pass. Full dynamic-hull/group navigation and G4/G6 remain open.

Flight enemy identity:4db8403 reproduces three absent registry IDs. Source388eb84 passes696 unit/267 GameTests and two actual typed-world restart pairs with models/icons and natural combat. First mesh-basis failure is retained. JET acceleration, type-specific death wrecks and full typed swarm/Survival acceptance remain OPEN.
