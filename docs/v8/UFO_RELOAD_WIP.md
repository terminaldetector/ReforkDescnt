# Actual UFO reload — open regression

Source1c200fdbd80978dce453a5a0be0764a3d41cf7dd, unchanged8155bfc9 game JAR: the first native process approached/boarded/captured the real UFO and stopped cleanly. The second process opened the same saved world. Raw saved physics data, restored physical components (tolerance1e-10), position, local grid1046cells and AI mode passed before simulation. However physical walking never reacquired: the stand timed out with restored=true/contact=0/draws=79. This is NOT an accepted save/reload run.

Raw attempts remain under build/cubic-client-failed-reload-contact-gl32 and work/ufo-body/native/final-flight-body-control-{save-reload2,reload2}-gl32.log. Closed world preserved with full-file hash/ZIP CRC verification. New diagnostics record player local position, suit/creative flight, protocol readiness and contact progress. Next runs also preserve the complete closed first-process world before reader launch so failures can be reproduced from the same state.

Earlier a9c19f1 first-save stand failed with NoSuchMethodError on an inherited Entity method. Corrected by calling inherited methods through a vanilla Entity reference, matching the existing native stand convention. Its logs and failed runtime remain separately preserved. This was a harness mapping failure, not save/load acceptance.

No production fix is asserted yet. Preserve both failures and reproduce the initial contact loss before choosing the implementation.
