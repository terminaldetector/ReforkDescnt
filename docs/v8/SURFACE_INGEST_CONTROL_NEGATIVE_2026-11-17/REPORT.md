# SurfaceIngest loaded-contract negative, 17 November 2026

**Confirmed CONTROL_FAILURE in the actual native BCC runtime. The unavailable-Y stage was not reached.** Exact test source `ba2dbb929c7f15ad67dcb4176f53ad3582a2f9b3`, based on canonical `9d51865`, has no production change. Remap/build/stamp passed37s; the sole fresh `cubic-surface-baseline-sodium` native run and wrapper exited255. No expected metadata/color was changed to match production.

Main JAR SHA-256: `08c4b776ee7b497b6e003193414a00eef749bb0f39dea9782747193d6571400b`. Observer JAR: `5f9501946d0d3003726696032b7c2218e7cb979dd7e39d4c8642b5357e1c4ddc`. Full candidate source/helper hashes and actual staged runtime artifact inputs are preserved.

| Actual measurement | Value |
|---|---|
| Authored FULL source block | GOLD at(8,2300,8) |
| Native sampleHeightmap at that coordinate | 2300 |
| Actual production colourAt selection | (8,2299,8), FULL AIR |
| Actual returned color | 0 |
| Server SurfaceStore cell | height2300,color0 |
| Flushed/read-back disk cell | height2300,color0 |
| Actual accepted client packet/observed cell | height2300,color0 |
| Expected GOLD physical top surface/color | height2301,color16445005(#FAEE4D) |

The ingest event records FULL GOLD already present at its entry, so an earlier LIME setup callback cannot be mistaken for this result. The real native sampled number is2300, matching the cached vanilla API caveat, and the production method subtracts another1 before choosing a material. Selecting actual AIR below the highest FULL GOLD produces the zero color. Independent decoding of the original received packet3 and saved compressed SurfaceSection bytes confirms the same target cell; this is not merely a server log counter or an assumed API contract.

Normal player interest authored the separate CYAN cube64 witness first, then GOLD/LIME in cube143. No heightmap/availability metadata, SurfaceStore cell, client packet or native ticket was fabricated. Only deliberate ordinary block edits inside checked FULL volumes and explicit fixture player repositioning were used. The loaded control failed before loaded demolition/restoration or natural high-cube unload; this does **not** prove the initially suspected unavailable-winner defect.

The [fresh failure frame](surface-ingest-frames/loaded-contract-failure.png) is context only. It was not gated on the authored GOLD mesh becoming visible and is not rendered-LOD/smoothing acceptance. Persistent sky/finite-plane artifacts are being investigated independently by the presentation lane; no claim links those artifacts to this loaded palette defect.

Original outcome/failure JSON, crash/log/exits, source/JAR/runtime manifests, raw accepted packets2/3, saved compressed section and independent byte readback are retained. Packet1 arrived before the observer runtime path was assigned: its metadata/hash is in the outcome JSON, but no raw packet1 file is claimed. The defect's actual packet3 is fully preserved and hash-verified.

After full process closure and immutable evidence copy, this lane archived its97 disposable save files to the path recorded in `closed-world-receipt.json`. Every file byte size/SHA matched the original and the gzip stream passed full CRC/ISIZE read-to-EOF before only those closed save copies were reclaimed. Runtime frames/logs/inputs/packets/mod JAR paths remain. No user save, shared cache or original historical gate world was changed.

No production fix or retry is part of this checkpoint. The next narrow question is the physical top-block/top-face convention in SurfaceIngest; any later unavailable-Y test must use the **measured** native metadata convention and keep its own conditional premise separate.
