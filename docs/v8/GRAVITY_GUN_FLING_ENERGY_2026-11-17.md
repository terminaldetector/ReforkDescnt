# Held gravity items: insufficient-energy fling and immediate refill retry

One test-only S10-T02 acceptance increment, based on canonical `da3ece88df777e049ef9074e051beb068e6cfe71`. Exact source **`1934e26c340470e7b03c867fd624eb69b6b319d7`** adds one GameTest to the already accepted actual-use class. Production code and registration are unchanged; no gameplay fix was needed.

The new case iterates SWARM (four real bodies), RAIL (one hundred-cell aggregate body) and SHIFT (one body) using the accepted owned single driver. It captures through each registered item, waits eight normal world ticks, checks that the persisted use cooldown has elapsed, then explicitly sets the capacitor to zero. It requires actual use to fail while preserving grab mode/count/mass, live registered original target identities, immediate physical velocities, zero energy and unchanged stack/use cooldown. No owned target field may appear on rejection.

The case then refills the capacitor to 100 and retries the same item in the same tick, without another wait. Every original target must receive a real forward launch, the grab must release and exactly one launch cost must be spent. Owner-only bodies/fields are cleaned between modes. There are no direct service capture/fling calls, launch-velocity injection, manual body ticks or following teleport.

The first focused class run on exact `1934e26` completed **7 PASS / 0 FAIL**, Gradle/game exit 0. This includes the one new case plus the existing six actual-use regressions. The source was unchanged before and after the command; fresh inputs, XML and logs are retained. The three successful new phases recorded:

| Mode | Retained original targets / mass | Energy after funded retry |
|---|---:|---:|
| SWARM | 4 / 4 | 90 |
| RAIL | 1 / 100 | 88 |
| SHIFT | 1 / 1 | 82 |

Command used Java 21 and the root-leased sequential runtime:

```sh
./gradlew runGametest --offline --no-daemon -I work/gravity-gun/focus.init.gradle
```

This is **stock Fabric direct `Item.use` acceptance**. Fake-player cooldown-manager observations do not establish actual interaction-manager or button/packet cadence; the persisted-use timestamp retention and same-tick direct retry are the scoped cooldown checks. Native Cubic/client input, gravity-compensated/all-mass/material-hit rail accuracy and the full S10 stage remain open. No full ordinary-suite result on this source, installable JAR certification or publication is claimed.

Evidence: `EVIDENCE/gravity-gun-fling-energy-1934e26.zip`, SHA-256 `e8c59d48966c6608acd8ca365d0fd1d1c892706e8bdba47fe119d3af5b086999`.

The bundle includes clean source/input snapshots, first log/XML/exit, three-mode events, exact focused init, test-only source diff and the closed disposable world. CRC and every payload SHA-256 were verified. The original world (109 files, 25705148 bytes) was exclusively locked, archived and independently CRC/per-file-hash verified before reclaiming only the generated build directory. Nested world archive SHA-256: `0b934c90bf32a1b91510a8c43f845538f8a9d2648ffb8e12b8be079c55c1c468`. Archival preservation is not save/reload gameplay acceptance. The subsequent evidence/report commit is distinct from the source actually tested.
