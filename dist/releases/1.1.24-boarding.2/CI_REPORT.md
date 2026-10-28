# DRMD checkpoint report

- Source SHA: `a1b045cde046d2a9f3be932eae960ed28bd4e083`
- Version: `1.1.24-boarding.2`
- Run: https://github.com/terminaldetector/ReforkDescnt/actions/runs/40683605278
- Target: Minecraft 1.21.1 / Fabric / Java 21

| Stage | Outcome |
|---|---|
| unit | success |
| gametest | success |
| build | success |
| rendercheck | success |
| shootcheck | success |
| verify | success |

## Test reports

- unit: {'tests': 598, 'failures': 0, 'errors': 0, 'skipped': 0} (`build/test-results/test`); zero tests means NOT VERIFIED.
- gametest: {'tests': 147, 'failures': 0, 'errors': 0, 'skipped': 0} (`build/gametest/reports`); zero tests means NOT VERIFIED.

## Verified distribution

- JAR: `distribution/drmd-6dof-1.1.24-boarding.2-fabric-1.21.1.jar`
- SHA-256: `19dbbeb2eacde9d7d587bf77780b6342c2394147d2631a88614c5e3007eb7c6a`
- Rendercheck: 5/5 fresh markers.
- Live swarm synchronized 128 drones; indexed=128; moving=128; maximum candidates/query=96.

### Fresh frames

- hybrid-before.png
- hybrid-after.png
- collapse-before.png
- collapse-flight.png
- collapse-after.png
- ragdoll-flight.png
- ragdoll-after.png
- gravity-swarm-held.png
- gravity-swarm-flight.png
- gravity-rail-held.png
- gravity-rail-flight.png
- gravity-shift-held.png
- gravity-shift-flight.png
- drone-swarm-dense.png
- drone-swarm-flight.png

### Hybrid samples

- {'models': 3, 'presetRoundTrips': 3, 'serverSaves': 3, 'muzzlePairs': 3, 'fovs': [70, 80, 90], 'banked': 3, 'vertical': 3, 'realUseAnimated': 3, 'loadSynced': True, 'external': 3, 'frames': 40}

- hybrid-0-workshop.png
- hybrid-0-fov70.png
- hybrid-0-fov80.png
- hybrid-0-fov90.png
- hybrid-0-banked.png
- hybrid-0-vertical.png
- hybrid-0-external.png
- hybrid-1-workshop.png
- hybrid-1-fov70.png
- hybrid-1-fov80.png
- hybrid-1-fov90.png
- hybrid-1-banked.png
- hybrid-1-vertical.png
- hybrid-1-external.png
- hybrid-2-workshop.png
- hybrid-2-fov70.png
- hybrid-2-fov80.png
- hybrid-2-fov90.png
- hybrid-2-banked.png
- hybrid-2-vertical.png
- hybrid-2-external.png
- hybrid-2-loaded.png

### Weapon verification

- {'scenes': 10, 'fovs': [70, 80, 90], 'rangePixelsChecked': True, 'fastSpeed': 8, 'fastTravel': 24.0, 'fastFrames': 4, 'fastPixelsChecked': True, 'fastTickStepped': True, 'fastServerTicks': 4, 'fastServerTravel': 32.0, 'muzzleEvents': 27, 'impactEvents': 2}

- bolt-dark-ranges.png
- bolt-light-ranges.png
- orb-dark-ranges.png
- orb-light-ranges.png
- rocket-dark-ranges.png
- rocket-light-ranges.png
- banked-fire.png
- vertical-fire.png
- muzzle-impact.png
- fast-motion.png
- mounts-fov-70.png
- mounts-fov-80.png
- mounts-fov-90.png

## Limits

- Historical 1.1.12 results in the supplied MHT are not results of this run.
- Manual playability smoke test: NOT RUN.
- Audit camera sweeps up to 628 m/s stress the renderer; these are not production player-speed claims.
- Swarm profiles use invulnerable controlled actors, capped client FPS and software graphics in GHA. Larger counts report actual limits, not a general 1000+ performance guarantee.
- Workshop Apply Build publishes construction/muzzles; Stats/Projectile/Flak/Guidance drafts are saved/exported, not runtime behavior overrides.
- Three hybrid samples: laser_pulse (impulse), plasma, gravy_railgun. Models tab in the existing Workshop; Creative + server operator for publishing.
- Local named presets: config/drmd/weapon-presets/*.nbt; authoritative layouts: world/data/drmd/visual/*.nbt.
- Source archives, reports and artifacts are retained by GHA for 90 days.
