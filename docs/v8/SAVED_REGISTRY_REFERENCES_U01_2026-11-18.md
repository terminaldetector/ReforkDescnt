# U01: copied saved-registry reference index

The new `scripts/v8/saved_registry_references.py` reads explicit files into a
fresh audit directory and indexes references from those copies. It never opens
an original for writing, migrates a save, executes commands or proposes deletion.
`scripts/v8/test_saved_registry_references.py` provides18 passing Python tests.
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

Exact tool source `9c9d2b07165e0d4e8dbe293c57f9520dcd63a86e` passes18 tests,
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

[Evidence](EVIDENCE/saved-registry-u01-9c9d2b07.zip) retains25 payloads, copied
inputs/catalog, first negative and corrected logs, actual commands/exit, source
and tests:121,734 bytes, SHA-256
`e22352adf6357e49149b5ca5ce24f87a5d42c574ec552ba191fcce10216f339b`.
Every payload hash/size and ZIP CRC was reread. Unsupported stores, runtime ID
enumeration, full source lifecycle consumers and user-save compatibility remain
open. U01 and whole tasks are not marked complete.
