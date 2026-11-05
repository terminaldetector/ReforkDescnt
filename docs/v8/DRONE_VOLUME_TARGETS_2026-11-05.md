# Natural vertical target acquisition

Fix source8fd19bc31778eb4899e4264885092f122fa26534 replaces vanilla's four-block vertical search band with the full existing follow-range volume for DroneEntity target goals. Ground actors retain their vanilla search geometry. Existing target validity, hostility priorities, sight checks and Creative/Spectator exclusions are retained. This adds target acquisition, not a route planner.

The two new actual-entity tests failed on ebd60db with actual=null for a cow20 blocks above/below, while the other253 GameTests passed. After the fix,679 unit/255 GameTests pass. Native game JAR SHA2566141868e01effa5233f1593704c3b28041fab89c9e79213947fa26a8731ae2ee; test JAR SHA2563e596f509ad170e8855106ac29da5233f0566d21a46756da8f5dc4d8ac3546bb.

Four fresh native scenes use seed271026 and the inherited128-agent mixed-role layout: target20 blocks above at Y207,20 below at Y2156, repeated with Mesa GL3.2 and Sodium GL3.3. Targets are visible, targetable cows with Resistance V; no test setTarget calls or manual drone ticks occur. Every scene passed128 alive/active/moving/registered/indexed/naturally targeting/synchronized/rendered; fixture cleanup left0 actors. Real shots/shooters: Mesa1311/85 and2852/96, Sodium1256/87 and2678/96. Boid candidate examinations68/73/75/57 remain within the actual configured96-candidate limit (32 returned neighbours); the earlier baseline's observed <=64 was not the configured cap.

Server p95 values82.86/72.78/108.76/73.41ms are NOT a performance pass. These short functional scenes use software rendering on shared cloud CPUs and a different target arrangement from the previous forced-target baseline. FPS percentiles remain UNKNOWN. No high-count scaling or user's GPU performance claim follows.

Evidence EVIDENCE/drone-volume-target-8fd19bc.zip SHA256b0f2a7e662d21ba074a31a7374ed29ce2a99aff6bb0d7d634db5c36fe6eba2f6 contains raw logs/inputs/CSV, four fresh frames and both failing/passing server runs. Source artifact13393597943/run41980416324 verified saved/notexpired, digestsha256:59a62f4480eac6408a9871a00bbdf0b7521a941da4b59324d185203247eadaaf; full workflow conclusion remains separate.

Previous actual-restart evidence remains tied to its different game JAR affc2408 (DRONE_STATE_2026-11-05.md). It is not relabelled as a current-main rerun. Full 3D obstacle navigation, separate ORB/ROCKET/JET identities, Survival/factions, universal ragdolls and remaining v8 acceptance stay open. No accepted1.1.35 package.
