# Survival scouts and player-built anti-missile defense: combined verification

Exact production source **`5057931efce99156ffdc8a195e90b1a926907ed8`** passes
**951 unit tests, 480 ordinary GameTests / 81 entrypoints and two actual clients**.
All test reports have zero failures, errors or skips. The fresh full Gradle run
exits 0 in 7m32s. The two clients use identical game and observer JARs, with source,
helpers and artifacts unchanged throughout the pair.

| Artifact | SHA-256 |
| --- | --- |
| Game, 2,886,856 bytes | `917942f866ae00968bbb736c926f7e14eaad1dad3dfb92663c4cdfd48ecd5d2b` |
| Observer, 650,537 bytes | `c7a7602a526ad1fc870a4682b2af6db0625509e673f0a13984978ffa033395a4` |

The exact tested game is retained locally under
`work/multi-agent/integrated-5057931/artifacts/`. This is development verification;
no release or publication is performed.

## Integrated behavior

The existing Survival director can admit one nonlethal SUPPORT scout after
12,000 actual online Survival ticks. Its own natural target and loaded line of
sight must observe the player before a full 400-tick warning and at most one
ASSAULT allowance. Interception without detection retires the encounter into
cooldown. Existing player, candidate, sight, spawn and roster budgets remain
bounded. Saved unsupported or corrupt authority stays opaque. The corrected
post-commit cooldown notification is included; the original missed notification
remains in the component's retained first failure.

Player-built directional guns can be armed through the real revisioned wrench
interaction for Survival anti-missile defense. The authoritative scheduler keeps
its existing firing cadence, world budgets, muzzle obstruction and loaded-volume
checks. Ordinary unclaimed weapons retain their behavior. A claimed gun with an
invalid principal cannot fall through to ordinary fire. Actual projectiles retain
their motion, damage, energy and recoil behavior.

Future/corrupt ownership refuses all automatic banks. When opaque material is
actually fractured, descendants retain a compact immutable refusal marker rather
than gaining ordinary automatic firing. The original detached-gun failure is
preserved. Valid ordinary fragments and independent neighboring bodies still
fire. Full schema-1 unknown-field retention remains a separate open issue.

The integration changes 27 source/resource/test paths. All 27 postimages match
the reviewed inputs. The original 75 manifest entries retain order; six new
entries add 21 AA cases and three real recon cases. These 24 cases execute in the
actual 480-case ordinary manifest, with 16 additional recon unit cases in the
951-unit run. The component's separate 82- and seven-case results are not
misrepresented as a separately executed combined 89-case run.

## Actual clients and retained data

The saved-surface arrival repairs the actual black observation to GOLD at height
2301 through live ingestion, disk and fresh client packets, with zero fixture
edits, post-baseline external mutations or dropped events. Server FULL appears
at tick118, both server volumes at121, and the final observation at161. The final
client has the high volume FULL and the low volume unloaded. This verifies saved
observation repair, without claiming first-ever ingestion or general migration.

Six presentation scenes pass Native/PS1/Debug, window resize, restoration and
exception paths, all with GL error0 and world nearest-grid ratio1.0. Five actual
palette states match the independent RGB oracle at1.0. Eight mirror panes form
one reflected surface. The weapon has166 actual pixels in Native and PS1 with
identical camera/modules and zero projected-vertex delta. The resized PS1 frame
was also viewed. All11 menu and17 cloud remapped classes remain byte-identical
to their tested components and previous canonical7c; the
[class receipt](EVIDENCE/render-preview-class-parity-5057931.json) records that
boundary without transferring additional component scenarios into this run.

[Combined evidence](EVIDENCE/integrated-5057931.zip) contains161 payloads,
40 fresh PNGs and10 depth arrays, 4,847,632 bytes, SHA-256
`faa4c5e1fb95f3e3947418f1ea6b17d785fdf39ceb88cc0db8226efb63546cd5`.
It includes28 fresh AA proof files, including12 compressed compound-NBT files.
The [independent readback](EVIDENCE/verify-integrated-5057931.json) checks exact
source postimages, every payload hash/size, ZIP CRC, all12 gzip footers, raw
network/disk cells, new gameplay cases and presentation outcomes.

The complete closed GameTest save is separately preserved as
`work/ufo-body/integrated-5057931.zip`:1556 files /336,849,595 bytes,
41,771,199-byte archive, SHA-256
`5d28aea735905e47f0da063b218cd96624bedf194919a79a5ba5f0001ac58b23`.
All448 nested gzip streams were fully decoded before reclaiming only that closed
disposable raw save. Both native saves are likewise preserved with per-file
hashes, CRC, decoded gzip streams and exclusive session locks.

## Remaining scope

Both latest specifications, all99 task IDs, all21 implementation packets and
the seven supplemental gates remain in scope. This run does not establish a
real-time 600-second scout event or its ordinary-loader cold continuation, a
complete day/night siege/recovery loop, native wrench arming, the full drone
inventory matrix, generic ControlCore, complete 3D AI, ragdoll limits, all
Dynamic Boarding criteria or PS1 vertex/UV geometry. The latter features retain
separate source and runtime lanes. No whole task or specification is newly
marked complete.
