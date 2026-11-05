# S11-T03 acceptance mapping

Task: Run active128 moving96 proof with exact AI measures and two attack roles.
Status: completed for this atomic generic-drone proof. GateG4 and stageS11 are NOT closed. DependenciesS06/S08 remain in the task graph; their broader acceptance is not inferred from this test.

| Required observation | Current exact-source evidence |
| --- | --- |
|128 real active actors, not a spawn counter|Writer age advances>=20 during sampling;128 server lookups/indexed; both heights/renderers pass. Reader independently checks128 actual loaded registered objects and advancing ages.|
|>=96 genuinely moving|Writer128 in all four scenes; after actual restart125(Mesa)/128(Sodium), measured displacement>.2m.|
|At least two actual attacking roles|Three separately counted roles: Assault, MG, Interceptor. All96 combat-role actors fire; WeaponCore's real projectile return is observed. No manual drone ticks.|
|Actual save/reload and AI response|Two client processes per renderer use one exact saved world.128 UUID/component comparisons before first tick;128 natural valid targets after load; no substitute actors or post-load target assignments.|
|Actual client models and metadata|128 saved UUIDs and roles synchronized; actual renderer identities98/96 after restart; six fresh frames across writer/reader runs.|
|Exact code/JAR and reproducible environment|Source d9d0fd82cbd060dbe95d432e501042eb6de44f4d; game4214bda36aac4935215fcc788db4beda9eccd382b9ec35fee08bb5ff909bcd4d; seed271026; normal difficulty, doMobSpawning=true; coordinates/rules/settings/logs/hashes in the archive. No video recording is claimed.|
|Before/after regression|Seven old role-motion failures reproduced; narrow range/flock fix passes690 unit/264 GameTests. Original PR9/recovery generic128 fixture remains independently preserved in earlier evidence.|

Report and raw proof: DRONE_COMBAT_RINGS_2026-11-05.md and EVIDENCE/drone-combat-rings-d9d0fd8.zip. Performance is OPEN (p95 exceeds50ms in these short software-renderer windows). Independent ORB/ROCKET/JET identities, dense passage navigation, hostile factions/Survival, all-role damage balance and full ragdoll/UFO acceptance belong to other still-open tasks. This atomic completion is not a release certificate.
