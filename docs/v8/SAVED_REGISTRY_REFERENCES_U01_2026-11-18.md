# U01: copied saved-registry reference index

The new `scripts/v8/saved_registry_references.py` reads explicit files into a
fresh audit directory and indexes references from those copies. It never opens
an original for writing, migrates a save, executes commands or proposes deletion.
`scripts/v8/test_saved_registry_references.py` provides20 passing Python tests.
This tooling is separate from the951 Java unit /480 GameTest canonical result.

Supported input paths are compound NBT (raw/gzip), ordinary Anvil `.mca` regions
(gzip/zlib/uncompressed chunks), JSON, command-function text and plain-text
candidates. Numeric NBT arrays are bounds-checked without expanding them into
objects. External region chunks, LZ4, databases, archives, malformed or ambiguous
formats remain UNKNOWN. Modified UTF encodings not decoded by this tool likewise
remain UNKNOWN; no lossy decoding is used.

The index binds a supplied registered-root graph to its own revision and exact
SHA-256. A known literal in a registry field or command is KEEP: retain the saved
reference. General text mentions and external/unresolved IDs are UNKNOWN. ADAPT
requires an explicit separate source/target/reason/evidence proposal whose target
is in that catalog; it is a review record, not a save change. An absent catalog
entry never means that the game lacks that registry ID.

```bash
python3 scripts/v8/saved_registry_references.py \
  --catalog /tmp/pinned-registered-graph.json \
  --input /tmp/copied-world/playerdata/example.dat \
  --input /tmp/copied-world/region/r.0.0.mca \
  --out /tmp/fresh-saved-reference-audit

python3 -m unittest discover -s scripts/v8 -p test_saved_registry_references.py -v
```

The catalog format is the existing `registered-graph.json` in the sealed
`EVIDENCE/resource-usegraph-9b7cf9b.zip`: `revision`, and `roots` rows with `kind`
and `id`. This source catalog has178 item/block definitions,145 unique IDs;
it is not a runtime entity/component/biome registry enumeration. Other explicit
catalogs may include entity roots, with their own truthful provenance.

Exit0 means the selected inputs were indexed without unresolved references;
exit1 preserves UNKNOWN references or input issues for review; exit2 rejects
arguments/catalog/proposal/output setup. The fresh `REPORT.json` records exact
paths, member and NBT/JSON paths, sizes, hashes, copied bytes and final original
readback. It does not certify a coherent snapshot of a concurrently running
world. Inputs must be explicit bounded regular files; symlinks and special files
are refused without waiting for a FIFO writer.

## Actual checks and first failures

Exact tool source `b8c0f23a47f149ddc7548b133cb71bc1de04c110` passes20 tests,
including real gzip/Anvil decoding, corrupt footers, duplicate compound keys,
truncation, overlapping regions, external/LZ4 refusal, depth/inflation/reference
limits, original-byte/inode preservation, changed-input detection and FIFO safety.

An independent review of prior `c3a3dccc` reproduced three omissions. With the
new18-test set,15 pass, two fail and one errors because no expected issue existed:
duplicate JSON keys could hide an earlier reference, decompression began after
the shared byte budget was exhausted, and numeric JSON nodes escaped the node
counter. The first log is preserved. The corrected reader rejects duplicate JSON
keys, admits and caps actual inflation against the remaining budget before doing
work, and counts every visited JSON node. All original tests remain present.

A subsequent independent review found that rejected oversized documents could
repeatedly inflate without charging the shared budget. Its real three-chunk
negative passes18/19, failing the expected single-inflater bound. The final fix
charges returned bytes before limit/stream validation and conservatively refuses
further inflation after any oversized or invalid stream. The same independent
reproduction now observes one inflater /five returned bytes, with all three
members UNKNOWN. All20 tests also pass in independent review. Malformed scalar
adaptation files are rejected before output creation.

The actual15-file replay reads12 retained AA NBT proof files, two genuine saved
entity regions and `level.dat` from closed canonical505 test saves. It finds
36 references: two KEEP entries for `drmd:ship_laser`,34 UNKNOWN external or
uncatalogued entries, and zero parse issues. CLI exit1 is expected. Every original
and catalog byte remains unchanged. `drmd:block_body` being UNKNOWN in this
item/block-only source catalog does not make that registered entity missing.
The full GameTest save also records its historical `drmd:advanced_column` test
profile; that observation does not describe the ordinary native world preset.

All five retired weapon IDs remain real registered recipe roots in the bound
source graph. No saved instance of them was found in this selected15-file sample;
no claim is made about uninspected saves or the six historical marked slots.

[Evidence](EVIDENCE/saved-registry-u01-b8c0f23a.zip) retains28 payloads, copied
inputs/catalog, first negative and corrected logs, actual commands/exit, source
and tests:124,865 bytes, SHA-256
`a647ee396b2856c08e979784956afd6d24d04529c2b6e2b7ffe80caf02ad87db`.
Every payload hash/size and ZIP CRC was reread. Unsupported stores, runtime ID
enumeration, full source lifecycle consumers and user-save compatibility remain
open. U01 and whole tasks are not marked complete.

The earlier9c evidence remains preserved as an intermediate result. The canonical
tool commit `fd5e4a78` has byte-identical script/test postimages to testedb8; no
production game file changed in this tooling integration.
