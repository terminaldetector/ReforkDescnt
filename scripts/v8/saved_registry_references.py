#!/usr/bin/env python3
"""Bounded offline saved-reference index. Reads originals; writes only a fresh audit directory.

KEEP means a reference must be retained, not that a registry migration is safe.
ADAPT is an explicitly supplied review proposal, never a write to a save.
Unknown formats, ambiguous strings and absent catalog entries remain UNKNOWN.
"""
import argparse
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import re
import stat
import struct
import zlib

MAX_FILE = 32 * 1024 * 1024
MAX_TOTAL = 64 * 1024 * 1024
MAX_DOCUMENT = 8 * 1024 * 1024
MAX_NODES = 200_000
MAX_DEPTH = 64
MAX_FILES = 4096
MAX_REFERENCES = 20_000
IDENTIFIER = re.compile(r'(?<![a-z0-9_.:/-])([a-z0-9_.-]+:[a-z0-9_./-]+)(?![a-z0-9_.:/-])')
EXACT_ID = re.compile(r'[a-z0-9_.-]+:[a-z0-9_./-]+\Z')
REFERENCE_KEYS = {'id', 'Name', 'name', 'item', 'block', 'entity', 'entityId', 'weapon', 'Block', 'Item'}
TEXT_KEYS = {'Command', 'command'}
sha = lambda raw: hashlib.sha256(raw).hexdigest()


class Unknown(ValueError):
    pass


class Budget:
    def __init__(self):
        self.remaining = MAX_TOTAL
        self.references = MAX_REFERENCES
        self.inflation_blocked = False

    def take(self, size):
        if size > self.remaining:
            raise Unknown('total decoded-byte budget exceeded')
        self.remaining -= size

    def reference(self):
        if self.references <= 0:
            raise Unknown('reference-count budget exceeded')
        self.references -= 1


def decompress(raw, mode, budget):
    if budget.inflation_blocked or budget.remaining <= 0:
        raise Unknown('total decoded-byte budget exhausted before decompression')
    limit = min(MAX_DOCUMENT, budget.remaining)
    try:
        if mode == 1:
            with gzip.GzipFile(fileobj=io.BytesIO(raw)) as saved:
                data = saved.read(limit + 1)
        elif mode == 2:
            inflater = zlib.decompressobj()
            data = inflater.decompress(raw, limit + 1)
        elif mode == 3:
            data = raw
        else:
            raise Unknown('unsupported compression ' + str(mode))
    except (EOFError, OSError, zlib.error) as error:
        budget.inflation_blocked = True  # A failed read may already have produced bytes internally.
        raise Unknown('invalid compressed stream: ' + str(error)) from error
    budget.take(min(len(data), budget.remaining))
    if len(data) > limit:
        budget.inflation_blocked = True
        raise Unknown('decoded document exceeds document/remaining byte budget')
    if mode == 2 and (not inflater.eof or inflater.unused_data):
        budget.inflation_blocked = True
        raise Unknown('incomplete or trailing zlib stream')
    return data


class Nbt:
    """Only string leaves are retained; numeric arrays are bounds-checked and skipped."""
    def __init__(self, raw):
        self.raw, self.at, self.nodes, self.strings = raw, 0, 0, []

    def take(self, count):
        if count < 0 or self.at + count > len(self.raw):
            raise Unknown('truncated NBT payload')
        at = self.at
        self.at += count
        return self.raw[at:self.at]

    def number(self, format):
        return struct.unpack(format, self.take(struct.calcsize(format)))[0]

    def string(self):
        try:
            # Nonstandard/modified encodings are UNKNOWN; never decode lossily.
            return self.take(self.number('>H')).decode('utf-8')
        except UnicodeDecodeError as error:
            raise Unknown('unsupported NBT string encoding') from error

    def payload(self, tag, path, depth):
        self.nodes += 1
        if depth > MAX_DEPTH or self.nodes > MAX_NODES:
            raise Unknown('NBT depth/node budget exceeded')
        if tag in {1, 2, 3, 4, 5, 6}:
            self.take({1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}[tag])
        elif tag in {7, 11, 12}:
            count = self.number('>i')
            if count < 0:
                raise Unknown('negative NBT array length')
            self.take(count * {7: 1, 11: 4, 12: 8}[tag])
        elif tag == 8:
            self.strings.append((path, self.string()))
        elif tag == 9:
            child, count = self.number('>B'), self.number('>i')
            if count < 0 or count > MAX_NODES - self.nodes or child > 12 or (child == 0 and count):
                raise Unknown('invalid or excessive NBT list')
            for index in range(count):
                self.payload(child, path + (index,), depth + 1)
        elif tag == 10:
            names = set()
            while True:
                child = self.number('>B')
                if child == 0:
                    break
                name = self.string()
                if name in names:
                    raise Unknown('duplicate NBT compound key')
                names.add(name)
                self.payload(child, path + (name,), depth + 1)
        else:
            raise Unknown('unsupported NBT tag ' + str(tag))

    def read(self):
        if self.number('>B') != 10:
            raise Unknown('NBT root is not a compound')
        root_name = self.string()
        self.payload(10, (root_name,) if root_name else (), 0)
        if self.at != len(self.raw):
            raise Unknown('trailing NBT bytes')
        return self.strings


def region_documents(raw):
    if len(raw) < 8192 or len(raw) % 4096:
        raise Unknown('invalid Anvil region sector layout')
    occupied = {0, 1}
    documents = []
    for slot in range(1024):
        location = raw[slot * 4:slot * 4 + 4]
        offset, sectors = int.from_bytes(location[:3], 'big'), location[3]
        if offset == sectors == 0:
            continue
        if offset < 2 or sectors == 0 or (offset + sectors) * 4096 > len(raw):
            raise Unknown('invalid region chunk location')
        span = set(range(offset, offset + sectors))
        if occupied & span:
            raise Unknown('overlapping region chunk sectors')
        occupied |= span
        at = offset * 4096
        length = int.from_bytes(raw[at:at + 4], 'big')
        if length < 2 or length + 4 > sectors * 4096:
            raise Unknown('invalid region chunk length')
        documents.append((f'chunk[{slot % 32},{slot // 32}]', raw[at + 4], raw[at + 5:at + 4 + length]))
    return documents


def references(strings, catalog, adaptations, member, budget):
    result = []
    for path, value in strings:
        key = path[-1] if path else None
        for match in IDENTIFIER.finditer(value):
            budget.reference()
            identity = match.group(1)
            literal = EXACT_ID.fullmatch(value) is not None
            context = 'registry-field' if literal and key in REFERENCE_KEYS else (
                'command-candidate' if key in TEXT_KEYS else 'string-candidate')
            action, reason = 'UNKNOWN', 'literal mention is not proven registry authority'
            if context != 'string-candidate':
                if identity in adaptations:
                    action, reason = 'ADAPT', 'explicit review-only adaptation; original retained'
                elif identity in catalog:
                    action, reason = 'KEEP', 'saved reference resolves in the explicitly pinned source catalog'
                else:
                    reason = 'not in this catalog; external/unresolved is not evidence of absence'
            row = dict(member=member, path=list(path), id=identity, context=context, action=action, reason=reason)
            if action == 'ADAPT':
                row['proposal'] = adaptations[identity]
            result.append(row)
    return result


def read_json(text):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                raise Unknown('duplicate JSON key: ' + key)
            result[key] = value
        return result
    return json.loads(text, object_pairs_hook=unique)


def scan_bytes(raw, suffix, catalog, adaptations, budget=None):
    budget = budget or Budget()
    result, issues = [], []
    try:
        if suffix == '.mca':
            documents = region_documents(raw)
        elif raw[:2] == b'\x1f\x8b':
            documents = [('root', 1, raw)]
        elif raw[:1] == b'\x0a':
            documents = [('root', 3, raw)]
        elif suffix in {'.json', '.mcfunction', '.txt'}:
            if len(raw) > MAX_DOCUMENT:
                raise Unknown('text document exceeds byte budget')
            budget.take(len(raw))
            text = raw.decode('utf-8')
            if suffix == '.json':
                value = read_json(text)
                strings = []
                nodes = 0
                def walk(item, path=(), depth=0):
                    nonlocal nodes
                    nodes += 1
                    if depth > MAX_DEPTH or nodes > MAX_NODES:
                        raise Unknown('JSON depth/node budget exceeded')
                    if isinstance(item, str):
                        strings.append((path, item))
                    elif isinstance(item, dict):
                        for key, child in item.items():
                            walk(child, path + (key,), depth + 1)
                    elif isinstance(item, list):
                        for index, child in enumerate(item):
                            walk(child, path + (index,), depth + 1)
                walk(value)
            else:
                strings = [(('line', line, 'Command' if suffix == '.mcfunction' else 'text'), value)
                           for line, value in enumerate(text.splitlines(), 1)]
            return references(strings, catalog, adaptations, 'text', budget), issues
        else:
            raise Unknown('unsupported format; no guessed NBT/database/archive decoding')
    except (Unknown, ValueError, UnicodeError, RecursionError) as error:
        return [], [dict(member='file', action='UNKNOWN', reason=str(error))]
    for member, mode, packed in documents:
        try:
            decoded = decompress(packed, mode, budget)
            strings = Nbt(decoded).read()
            result.extend(references(strings, catalog, adaptations, member, budget))
        except (Unknown, ValueError) as error:
            issues.append(dict(member=member, action='UNKNOWN', reason=str(error)))
    return result, issues


def stable_read(path):
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as original:
        before = os.fstat(original.fileno())
        if not stat.S_ISREG(before.st_mode) or before.st_size > MAX_FILE:
            raise Unknown('input is not a bounded regular file')
        raw = original.read(MAX_FILE + 1)
        after = os.fstat(original.fileno())
    identity = lambda s: (s.st_dev, s.st_ino, s.st_size, s.st_mtime_ns, s.st_ctime_ns)
    if len(raw) > MAX_FILE or identity(before) != identity(after) or identity(after) != identity(path.lstat()):
        raise Unknown('input changed while reading')
    return raw


def run(inputs, catalog_path, out, adaptations_path=None):
    original_paths = [Path(path).absolute() for path in inputs]
    if not original_paths or len(original_paths) > MAX_FILES or len(set(original_paths)) != len(original_paths):
        raise Unknown('need bounded unique explicit input files')
    out = Path(out).absolute()
    if out.exists() or any(out == p or out in p.parents or p in out.parents for p in original_paths):
        raise Unknown('output must be fresh and separate from all inputs')
    catalog_raw = stable_read(Path(catalog_path))
    source = read_json(catalog_raw)
    if (not isinstance(source, dict) or not isinstance(source.get('revision'), str)
            or not isinstance(source.get('roots'), list)
            or any(not isinstance(row, dict) for row in source['roots'])):
        raise Unknown('catalog must be an explicit pinned registered-root graph')
    catalog = {row['id'] for row in source['roots'] if isinstance(row.get('kind'), str)
               and row['kind'] in {'item', 'block', 'entity'}
               and isinstance(row.get('id'), str) and EXACT_ID.fullmatch(row['id'])}
    if not catalog:
        raise Unknown('catalog has no supported registry roots')
    adaptations = {}
    adaptation_raw = None
    if adaptations_path:
        adaptation_raw = stable_read(Path(adaptations_path))
        proposals = read_json(adaptation_raw)
        if not isinstance(proposals, list):
            raise Unknown('adaptations must be a list of explicit review proposals')
        for row in proposals:
            if (not isinstance(row, dict) or not isinstance(row.get('source'), str)
                    or not EXACT_ID.fullmatch(row['source'])
                    or row.get('source') in adaptations or not isinstance(row.get('target'), str)
                    or row['target'] not in catalog
                    or not isinstance(row.get('reason'), str) or not row['reason']
                    or not isinstance(row.get('evidence'), str) or not row['evidence']):
                raise Unknown('adaptations need unique source, catalog target, reason and evidence')
            adaptations[row['source']] = row
    # Copy before parsing; failures never cause a write to an original.
    out.mkdir(parents=True)
    copies = out / 'copied-inputs'
    copies.mkdir()
    (copies / 'catalog.json').write_bytes(catalog_raw)
    if adaptation_raw is not None:
        (copies / 'adaptations.json').write_bytes(adaptation_raw)
    budget, rows, total = Budget(), [], 0
    for index, path in enumerate(original_paths):
        row = dict(original=str(path), references=[], issues=[])
        try:
            if total >= MAX_TOTAL:
                raise Unknown('total input-byte budget already exhausted')
            raw = stable_read(path)
            total += len(raw)
            if total > MAX_TOTAL:
                raise Unknown('total input-byte budget exceeded')
            copy = copies / f'{index:04d}-{path.name}'
            copy.write_bytes(raw)
            assert sha(copy.read_bytes()) == sha(raw)
            row.update(copy=str(copy.relative_to(out)), bytes=len(raw), sha256=sha(raw))
            row['references'], row['issues'] = scan_bytes(copy.read_bytes(), path.suffix.lower(), catalog, adaptations, budget)
            row['originalUnchanged'] = sha(stable_read(path)) == sha(raw)
            if not row['originalUnchanged']:
                raise Unknown('original changed after copying; not a coherent snapshot')
        except (Unknown, OSError, ValueError) as error:
            row['issues'].append(dict(member='file', action='UNKNOWN', reason=str(error)))
        rows.append(row)
    # Whole-operation recheck catches an earlier input changing while a later one was parsed.
    for row in rows:
        if 'sha256' in row:
            try:
                row['originalUnchanged'] = sha(stable_read(Path(row['original']))) == row['sha256']
                if not row['originalUnchanged']:
                    raise Unknown('original changed before audit closure')
            except (Unknown, OSError) as error:
                row['originalUnchanged'] = False
                row['issues'].append(dict(member='closure', action='UNKNOWN', reason=str(error)))
    catalog_unchanged = sha(stable_read(Path(catalog_path))) == sha(catalog_raw)
    adaptations_unchanged = (adaptation_raw is None or
                            sha(stable_read(Path(adaptations_path))) == sha(adaptation_raw))
    report = dict(schema='ungravity.saved-registry-references.v1', catalogRevision=source['revision'],
                  catalogSHA256=sha(catalog_raw), catalogRoots=len(catalog), inputs=rows,
                  catalogOriginalUnchanged=catalog_unchanged, adaptationsOriginalUnchanged=adaptations_unchanged,
                  admittedDecodedBytes=MAX_TOTAL - budget.remaining,
                  furtherInflationRefused=budget.inflation_blocked, saveEdits=0,
                  scope='Offline copied references only; not runtime registry, coherent live snapshot, migration or deletion authority')
    (out / 'REPORT.json').write_text(json.dumps(report, indent=2) + '\n')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', action='append', type=Path, required=True, help='Explicit file; may be repeated')
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--adaptations', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    try:
        report = run(args.input, args.catalog, args.out, args.adaptations)
    except (Unknown, OSError, ValueError) as error:
        parser.exit(2, str(error) + '\n')
    print(json.dumps(dict(inputs=len(report['inputs']), references=sum(len(r['references']) for r in report['inputs']),
                          unknownInputs=sum(bool(r['issues']) for r in report['inputs']), saveEdits=0)))
    return int(not report['catalogOriginalUnchanged'] or not report['adaptationsOriginalUnchanged'] or
               any(row['issues'] or any(ref['action'] == 'UNKNOWN' for ref in row['references'])
                   for row in report['inputs']))


if __name__ == '__main__':
    raise SystemExit(main())
