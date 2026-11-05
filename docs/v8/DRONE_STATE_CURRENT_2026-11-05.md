# Current flight-route build: exact128-drone restart

Production route source5623e0ad7fb8f2ce8aa8c4b1ca545a23457868e9 and observer source dbc5a0cdb0a44832f408a1c6d9e10d79ba8664d7 share the exact game JAR SHA256c911bd5d57cb9dccfd0c346b5d92c9280e9c7bad30a7c99b6f13cd725422d302. Native test JAR SHA256aaad79ca1c48c9177934b2aa445e42b352af21e7cccdaaacc88d4558c38ef5f8.690 unit/257 GameTests pass on this main code. The route proof from off-grid starts is in DRONE_ROUTES_WORLD_GRID_2026-11-05.md.

Each renderer launches two separate clients against the same saved cubic world, with closed-world ZIP/per-file hash verification between them. The existing four-role128 fixture runs at Y207/Y2156 before save; final128 actors remain at the high scene. Damage and bank are seeded before save, cooldowns come from real shots. Every loaded actor is compared to its saved components before its first tick. The reader assigns no targets, spawns no substitutes and requires actual tick-age advancement, movement, natural targets, two damaging roles, real projectile creation, matching client roles/UUIDs and real render calls. Actor/grid/geometry changes are not used to fake a restart. See DRONE_STATE_2026-11-05.md for the original persistence fix and full fixture description.

| Renderer | Restored / active / moving / client synced | Natural targets | Shooters | Shots | Assault / MG shots | Rendered identities |
| --- | --- | ---: | ---: | ---: | --- | ---: |
| Mesa GL3.2 |128 /128 /128 /128|128|64|1321|461 /860|110|
| Sodium GL3.3 |128 /128 /128 /128|128|62|1090|406 /684|107|

Counters now freeze in one server-thread acceptance snapshot; the client publishes immutable role totals. The fixture also asserts their sum equals the shot count. Actual gameplay firing continues while client visibility catches up, but late shots do not contaminate the measured window. Earlier live-count output showed a one-shot skew and is retained as a diagnostic, not the final counter measurement. Its Sodium inputs also retain a docs-only dirty note; final runs have clean tracked inputs and exact matching source hashes in both processes.

Evidence EVIDENCE/drone-state-atomic-grid-dbc5a0c.zip SHA25691bcc51e469b139504052f2d0059aa8e01f607a84a047ea8adefd08fa97b0f17 contains six fresh frames, both exact saved-world snapshots, raw actor SNBT, logs/launches/input hashes and server test proof. All four writer/reader exits are0. Source artifact13396892564/run41988014377 verified saved/notexpired, digestsha256:7c32ccf8096956c7785258dc13dde61a152b2e943d1c54fe6bf984b9ea2840f9. Source-artifact preservation is separate from full Actions workflow success.

This verifies a generic mixed-role drone fixture. Cow targets deliberately survive with Resistance V; real projectiles and damaging-role configurations do not establish damage balance. Full ORB/ROCKET/JET identities, every combat role, shared passages, moving hulls, Survival/factions, full ragdoll acceptance and high-count/performance gates remain open. Native runs use software rendering on shared cloud CPUs. No new accepted1.1.35 release or complete v8 claim.
