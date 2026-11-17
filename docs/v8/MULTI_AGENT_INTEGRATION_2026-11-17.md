# Combined local checkpoint — 17 November 2026

Exact source `699b749045edde2722f0ae9685b2a3674c1a1041` passes **772 unit tests,
404 ordinary GameTests and the complete 64-entrypoint manifest**, with no failures,
errors or skips. Both fresh BCC/Sodium real-client scenarios use the same remapped
game and observer JARs from that full run. This is partial implementation evidence;
neither master specification nor Dynamic Boarding is complete.

The integrated changes add loaded-only acquisition/projection queries for the three
gravity tools, retained captures on an unfunded throw, real Survival crafting and
mechanism placement/fire/salvage/repair checks, occupied XYZ admission for ragdolls,
Debug compositor polygon restoration, and deferred weapon-buffer ordering after the
cockpit. Retired legacy portal/dragon callbacks are guarded; their separate eight-case
tall-registry fixture passes, but those eight are **not** part of the ordinary 404.
All original failures remain separately identified in the worker reports and archives.

Game JAR SHA-256: `cce6a9bb60a275542ab64b8f347757880718e2b76f609578e9562f70567baad2`.
Observer SHA-256: `da6789f14f2076918ed9fdc0a18064b5e042637bc6a5dbe2e9005c6c7afb4994`.
The actual staged files in both clients match these hashes and pass ZIP CRC checks.
Later documentation commits do not change the tested source or artifact identity.

`ps1-viewmodel-integrated-699b749-sodium` exits zero and completes the six profile
transitions without a world reload. Both pure-world nearest-grid ratios are 1.0;
the HUD witness remains one physical pixel, the mirror contains eight merged panes,
and every scene reports zero GL errors. The projected weapon mask observes
**166 Native / 166 PS1 pixels**, exceeding the fixed 125-pixel gate, with identical
camera/modules and zero projected-vertex displacement. Nine presentation images and
fourteen intermediate weapon-stage images are retained. Presentation and final
weapon images were inspected; intermediate images also retain their pixel metrics.
This confirms the measured camera/model only, not arbitrary 6DoF poses or all items.

`mounted-integrated-699b749-keys-sodium` exits zero after actual use/sneak-key
linking, laser/rocket fire, both OFF, reversal and physical bearing release. Five
fresh images were inspected. The ordinary fixture supplies no joint or shots and
does not teleport-follow the operator. It observes three lasers/eight rockets,
zero muzzle error, 6.305e-16 maximum launch-velocity error, .000064882m maximum
anchor error, 74 OFF ticks and preserved separated spin. Shot counts depend on
timing and do not constitute weapon-balance changes.

The [compact archive](EVIDENCE/integrated-699b749.zip) contains 69 payloads plus
a per-file SHA-256 manifest: original full logs/XML, unit XML bundle, processed
manifest, source/build/input receipts, both native logs/markers, all **28 PNGs**
and local helpers. All hashes and ZIP CRCs were verified; the archive checksum is
`4412ea6da9b281bc1da36e4c43a1c90b2efc77d88f2a18d725b9681c0215e324`.
The [parsed receipt](EVIDENCE/integrated-699b749.json) preserves the exact results.
Closed disposable worlds were separately archived and verified before reclaiming
space. This does not establish a fresh two-process persistence acceptance.

These clients use **software Mesa GL3.3 with Sodium**. Separate worker proofs cover
the Debug and weapon-order fixes under GL3.2/Fabulous; those different source/JAR
identities remain distinct. No hardware performance, larger-cohort benchmark,
multiplayer, arbitrary-angle viewmodel or full visual-style acceptance is claimed.

Sky/LOD wedges remain visible in the mounted frames. A separate actual BCC probe
has now reproduced a loaded surface colour sample one block below its heightmap
winner; that first failure is preserved before a production fix. Another separate
negative reproduces submerged Cod entering the standing-surface gravity path.
Neither negative belongs to, or changes the passing counts of, this checkpoint.
Actual button/network gravity-tool routing, further mob-family adapters, sky draw
attribution and a bounded manual city fixture continue in isolated worktrees.

The 99-task ledger remains **3 completed / 21 in progress / 75 pending revalidation**;
no full task is promoted from these partial proofs. The independent historical audit
remains pinned to `4b2e243`. Development and checkpoints are local; no GitHub push,
Actions, release acceptance or publication occurred.
