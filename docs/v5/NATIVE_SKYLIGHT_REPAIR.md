# Native cubic skylight interoperability — candidate, 27 October 2026

This work addresses the reproducible roof-removal case, not the full v5 world migration.
Original upstream binaries and original user saves are untouched. BCC implementation code is not
copied into DRMD; interoperability uses public signatures from the unchanged 0.1.1 artifact.

## Reproducer and failed attempts

- Original 1.1.17 runtime: a 3x3 roof at Y=2161 shadows the air below it; removing it leaves sky
  light 13 on both server and client after 400 ticks. Initial native source refresh/check/propagate
  calls alone do not correct the column's stale cached occlusion height 2162 (loaded source is 87).
- `b1d5fd5`: correcting the cache makes the server reach 15, but client stays at 13. Retained under
  `build/cubic-client-server-only-light-failure` and `client-production-native-fix.log`.
- `7d07074`: announcing native cube light changes after the queued light work completes makes the
  client subset pass, including the two lower cubes. Evidence: `client-production-native-sync.log`.
- `61d3c4c`: adding arrival/reopen scans exposed work starvation: every unknown column was queued,
  and background arrivals could evict actual block changes. The unchanged sky assertion failed.
  Retained as `build/cubic-client-reload-queue-failure` / `client-production-native-reload.log`.
- `6494541`: scans enqueue only demonstrably stale loaded columns; actual block changes have a
  separate urgent queue within the same per-tick work limit. Strict run passes eleven checks,
  including high/negative native state and sign NBT, water, blocklight addition/removal, skylight
  occlusion/restoration through two lower cubes, independent unload and skylight after reload.

## Authority, work and memory bounds

The optional integration does nothing without BCC. A normal block removal schedules its X/Z
column. It reads only an already available FULL vanilla column and native FULL cube statuses.
It never acquires a ticket, calls a generating chunk getter, edits light arrays or assumes an
unavailable cube is air. If the old cached top block is still opaque, it is not lowered.

When the cached top block is now air in a FULL cube, native source metadata is refreshed. The
replacement height is at least the native loaded source height and at least the bottom of the
contiguous FULL cube range below the old top. The downward scan stops after 64 cubes. Unknown
lower cubes remain conservative occluders. Later native cube arrivals and column reopen rescan
candidate columns, so this temporary floor can move down as authoritative data becomes available.

Per world there are at most 4096 watched X/Z columns, 4096 deduplicated urgent entries and 128
deduplicated native-column scan requests. One column scan and at most 32 repairs run per tick.
Background scans cannot evict urgent removals. Native light propagation and completion notifications
are combined per X/Z chunk. After the light task completes, native `onSectionLightChanged` is called
for the affected available cube range, allowing the upstream sender to update its clients.

The optional `@Pseudo` callback observes the public `CubeApplier.applyLoaded` method. There are no
shadows of private upstream fields. Ordinary server column-load events cover initial/reopened
columns; actual block removals use DRMD's existing vanilla WorldChunk update callback.

## Limits

This targeted case covers opaque-block removal to air. Other transparent replacements and the
complete cubic lighting regression matrix remain to be measured. The BCC beta API is not a promise
of compatibility with future releases. Native `droppedWrites` telemetry remains unexplained, and
the corrected roof case does not certify structured world generation, corruption recovery, the
user's old 193-second stalls or the full Intel HD 4400/Sodium/NoCubes/3D LOD matrix.

No candidate JAR is certified for distribution until the fresh mandatory regression chain passes.
