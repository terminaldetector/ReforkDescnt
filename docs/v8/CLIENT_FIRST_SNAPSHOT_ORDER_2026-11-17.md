# Preserve surface packets delivered before the first planet snapshot

The first native pair on exact `362613f51ba8d6c18cef9e2c75aadb5cef3f8849`
does **not** pass as a pair: ordinary 851 unit/442 GameTests pass and the actual
presentation client passes, but the no-edit arrival client reports
`PREMISE_NOT_OBSERVED` after its unchanged 600-tick low-phase limit.

The accepted raw packet contains height2301/black, SHA
`2214c13e203e6d6c172a9e7a4452019960935564e57f51162c2d546ec34d32d3`.
Server/disk retain that cell, low cubes are FULL, high cubes remain unknown;
the client cache instead reads NO_HEIGHT. No high-phase arrival occurs and no
terrain edit or dropped observation is recorded. This is not a failed high-cube
resampling result. The observer does not directly trace first-seed application
order; that specific native ordering remains unobserved.

Source review identifies a deterministic defect: `PlanetClientState.apply`
replaced the observed store even when establishing its first seed. A valid
section accepted before that snapshot was therefore discarded. On test-only
source `cb3a8da`, the first targeted run has **2 failures/1 pass**: early data and
early data after a same-seed reconnect disappear, while the old known-seed
replacement control passes. Exact first XML/log/exits are preserved.

Production `20ef2c4c8fd551e4326d2841601f9c56fa4a3a0a` retains observations while
establishing the first seed. A known seed replacement still drops them, and
disconnect still clears the session. Both relevant network receivers capture
their connection and reject delayed application after that connection changes.
There is no replay, synthetic section, server resend, fixture delay or weakened
600-tick criterion. The same three targeted tests now pass, first invocation.

[Targeted source and proof](EVIDENCE/planet-first-snapshot-order.zip) preserves
both exact source versions, immutable executing helpers, fresh XML, original logs,
child exits and input hashes. [The prior combined result](EVIDENCE/integrated-362613f.zip)
retains 133 payload files, 39 fresh PNGs and 10 depth arrays; SHA-256
`f7dfa1ab50fd29b3278d7489babd587acf5edbfc62f7f0491dc0f6c4e4b21a86`.
Its arrival premise failure remains a failure; the newer targeted pass does not
retroactively promote that client result.

The subsequent complete853-unit/442-GameTest regression and both unchanged
no-edit arrival/presentation clients pass on the exact20ef remapped pair. The
[combined report](MULTI_AGENT_INTEGRATION_20EF2C4_2026-11-17.md) records those
separate full/native results. No release or whole-spec acceptance is claimed.
