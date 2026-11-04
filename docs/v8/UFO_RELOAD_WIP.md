# Actual UFO reload — open regression

Source1c200fdbd80978dce453a5a0be0764a3d41cf7dd, unchanged8155bfc9 game JAR: the first native process approached/boarded/captured the real UFO and stopped cleanly. The second process opened the same saved world. Raw saved physics data, restored physical components (tolerance1e-10), position, local grid1046cells and AI mode passed before simulation. However physical walking never reacquired: the stand timed out with restored=true/contact=0/draws=79. This is NOT an accepted save/reload run.

Raw attempts remain under build/cubic-client-failed-reload-contact-gl32 and work/ufo-body/native/final-flight-body-control-{save-reload2,reload2}-gl32.log. Closed world preserved with full-file hash/ZIP CRC verification. New diagnostics record player local position, suit/creative flight, protocol readiness and contact progress. Next runs also preserve the complete closed first-process world before reader launch so failures can be reproduced from the same state.

Earlier a9c19f1 first-save stand failed with NoSuchMethodError on an inherited Entity method. Corrected by calling inherited methods through a vanilla Entity reference, matching the existing native stand convention. Its logs and failed runtime remain separately preserved. This was a harness mapping failure, not save/load acceptance.

No production fix is asserted yet. Preserve both failures and reproduce the initial contact loss before choosing the implementation.

The instrumented reproduction found the cause: the loaded player was still inside the hull (local1.97,-0.02,-1.73) but suit=true because DescentSession unconditionally rearmed free flight on every login. Boarding correctly refuses suit flight, so no walking lease formed. Complete closed first-process world and raw diagnostics are preserved in EVIDENCE/ufo-reload-mode-repro.zip.

Source9c461cc preserves a saved enabled=false choice on join; new players still arm flight, and saved enabled=true still initializes noGravity/abilities/sync. Returning walkers retain world momentum.679 unit/247 GameTests passed, including new/returning flight and saved-walking cases. Actual native regression and fresh same-candidate restart pairs remain pending. Candidate JAR SHA256e61072c7ee39133f9d21cb949cde2396348fbd6684078d32e601c310722d0e91.

Follow-up: fresh e61072c7 pair exposed a second race, saved walking falling before the body entity loaded (initial localY=-2.12, downward velocity2.0258blocks/tick). Source dc13381 adds persisted physical context and bounded admission;250 GameTests passed. Strengthened native source08d61c3 then passed exact-candidate fresh restart pairs, intentional Ready delay, grounded contact and real keyboard walking on both renderers. See UFO_RELOAD_2026-11-04.md. Earlier OPEN descriptions remain historical; pre-context migration and the other documented limits remain open.
