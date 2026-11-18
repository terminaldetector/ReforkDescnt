import gzip
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
import zlib

spec = importlib.util.spec_from_file_location('saved_registry_references', Path(__file__).with_name('saved_registry_references.py'))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def string(value):
    raw = value.encode()
    return struct.pack('>H', len(raw)) + raw


def compound(fields):
    return b'\x0a\0\0' + b''.join(b'\x08' + string(k) + string(v) for k, v in fields) + b'\0'


def region(payload, mode=2):
    packed = zlib.compress(payload) if mode == 2 else payload
    record = struct.pack('>I', len(packed) + 1) + bytes([mode]) + packed
    sectors = (len(record) + 4095) // 4096
    head = bytearray(8192)
    head[:4] = (2).to_bytes(3, 'big') + bytes([sectors])
    return bytes(head) + record.ljust(sectors * 4096, b'\0')


class SavedReferenceTests(unittest.TestCase):
    known = {'drmd:weapon_d6_heavy', 'drmd:ship_laser', 'drmd:alloy_plate'}

    def scan(self, raw, suffix='.dat', adapt=None):
        return audit.scan_bytes(raw, suffix, self.known, adapt or {})

    def test_real_named_nbt_inventory_and_palette_paths_are_preserved(self):
        # Root compound -> Inventory list(compounds) -> id, and block-state Name.
        raw = (b'\x0a\0\0\x09' + string('Inventory') + b'\x0a\0\0\0\x01\x08' + string('id') +
               string('drmd:weapon_d6_heavy') + b'\0\x08' + string('Name') + string('drmd:ship_laser') + b'\0')
        refs, issues = self.scan(gzip.compress(raw))
        self.assertEqual(issues, [])
        self.assertEqual([r['action'] for r in refs], ['KEEP', 'KEEP'])
        self.assertEqual(refs[0]['path'], ['Inventory', 0, 'id'])

    def test_unknown_registry_and_display_mention_are_never_deletion_candidates(self):
        refs, issues = self.scan(compound([('id', 'other:future_entity'), ('CustomName', 'drmd:weapon_d6_heavy')]))
        self.assertFalse(issues)
        self.assertEqual([r['action'] for r in refs], ['UNKNOWN', 'UNKNOWN'])

    def test_valid_anvil_zlib_uses_actual_chunk_slot(self):
        refs, issues = self.scan(region(compound([('Name', 'drmd:ship_laser')])), '.mca')
        self.assertFalse(issues)
        self.assertEqual((refs[0]['member'], refs[0]['action']), ('chunk[0,0]', 'KEEP'))

    def test_overlapping_region_chunks_reject_before_partial_authority(self):
        raw = bytearray(region(compound([('id', 'drmd:ship_laser')])) )
        raw[4:8] = raw[:4]
        refs, issues = self.scan(bytes(raw), '.mca')
        self.assertFalse(refs)
        self.assertIn('overlapping', issues[0]['reason'])

    def test_external_region_and_lz4_stay_explicitly_unknown(self):
        for mode in (128 + 2, 4):
            refs, issues = self.scan(region(compound([('id', 'drmd:ship_laser')]), mode), '.mca')
            self.assertFalse(refs)
            self.assertIn('unsupported compression', issues[0]['reason'])

    def test_gzip_footer_corruption_and_truncated_nbt_cannot_emit_partial_keep(self):
        raw = gzip.compress(compound([('id', 'drmd:ship_laser')]))
        for invalid in (raw[:-1], raw[:-8] + b'\0' * 8, compound([('id', 'drmd:ship_laser')])[:-1]):
            refs, issues = self.scan(invalid)
            self.assertFalse(refs)
            self.assertEqual(issues[0]['action'], 'UNKNOWN')

    def test_duplicate_keys_negative_lists_and_trailing_payload_refuse(self):
        for raw in (compound([('id', 'drmd:ship_laser'), ('id', 'drmd:alloy_plate')]),
                    b'\x0a\0\0\x09\0\x01x\x08\xff\xff\xff\xff\0', compound([]) + b'garbage'):
            refs, issues = self.scan(raw)
            self.assertFalse(refs)
            self.assertTrue(issues)

    def test_depth_and_inflation_limits_are_enforced_on_real_streams(self):
        nested = b'\x0a\0\0' + (b'\x0a\0\x01x' * 8) + (b'\0' * 9)
        with patch.object(audit, 'MAX_DEPTH', 4):
            self.assertTrue(self.scan(nested)[1])
        with patch.object(audit, 'MAX_DOCUMENT', 32):
            self.assertTrue(self.scan(gzip.compress(b'x' * 1000))[1])

    def test_commands_are_read_never_executed_and_unknown_archives_stay_unknown(self):
        refs, issues = self.scan(b'give @s drmd:weapon_d6_heavy\n# future:unresolved\n', '.mcfunction')
        self.assertFalse(issues)
        self.assertEqual([r['action'] for r in refs], ['KEEP', 'UNKNOWN'])
        self.assertTrue(self.scan(b'PK\x03\x04', '.zip')[1])

    def test_explicit_review_adaptation_retains_original_and_provenance(self):
        proposal = {'source': 'drmd:old', 'target': 'drmd:ship_laser', 'reason': 'review', 'evidence': 'issue-1'}
        refs, issues = self.scan(compound([('id', 'drmd:old')]), adapt={'drmd:old': proposal})
        self.assertFalse(issues)
        self.assertEqual((refs[0]['id'], refs[0]['action'], refs[0]['proposal']), ('drmd:old', 'ADAPT', proposal))

    def test_many_repeated_identifiers_cannot_expand_the_output_without_bound(self):
        with patch.object(audit, 'MAX_REFERENCES', 2):
            refs, issues = self.scan(compound([('Command', 'give @s drmd:ship_laser drmd:ship_laser drmd:ship_laser')]))
        self.assertFalse(refs)
        self.assertIn('reference-count budget', issues[0]['reason'])

    def test_duplicate_json_key_does_not_hide_an_unresolved_reference(self):
        refs, issues = self.scan(b'{"id":"external:legacy","id":"drmd:ship_laser"}', '.json')
        self.assertFalse(refs)
        self.assertIn('duplicate JSON key', issues[0]['reason'])

    def test_exhausted_global_budget_prevents_further_inflation(self):
        budget = audit.Budget()
        budget.remaining = 0
        with patch.object(audit.zlib, 'decompressobj', wraps=zlib.decompressobj) as inflater:
            refs, issues = audit.scan_bytes(region(compound([('id', 'drmd:ship_laser')])), '.mca', self.known, {}, budget)
        self.assertFalse(refs)
        self.assertTrue(issues)
        self.assertEqual(inflater.call_count, 0)

    def test_numeric_json_nodes_count_toward_the_node_budget(self):
        with patch.object(audit, 'MAX_NODES', 2):
            refs, issues = self.scan(b'[1,2,3,4,5,6]', '.json')
        self.assertFalse(refs)
        self.assertIn('JSON depth/node budget', issues[0]['reason'])

    def fixture(self, directory):
        p = Path(directory)
        cat = p / 'catalog.json'
        cat.write_text(json.dumps({'revision': 'test-pin', 'roots': [{'kind': 'item', 'id': identity} for identity in self.known]}))
        source = p / 'original.dat'
        source.write_bytes(gzip.compress(compound([('id', 'drmd:weapon_d6_heavy')])))
        return p, cat, source

    def test_actual_fresh_audit_copies_bytes_and_preserves_original_inode_and_content(self):
        with tempfile.TemporaryDirectory() as directory:
            p, cat, source = self.fixture(directory)
            original, inode = source.read_bytes(), source.stat().st_ino
            report = audit.run([source], cat, p / 'audit')
            self.assertEqual(source.read_bytes(), original)
            self.assertEqual(source.stat().st_ino, inode)
            self.assertEqual((p / 'audit' / report['inputs'][0]['copy']).read_bytes(), original)
            self.assertEqual(report['inputs'][0]['sha256'], hashlib.sha256(original).hexdigest())
            self.assertTrue(report['inputs'][0]['originalUnchanged'])
            self.assertTrue(report['catalogOriginalUnchanged'])
            self.assertEqual(report['saveEdits'], 0)
            with self.assertRaises(audit.Unknown):
                audit.run([source], cat, p / 'audit')

    def test_symlink_and_invalid_adaptation_never_change_target(self):
        with tempfile.TemporaryDirectory() as directory:
            p, cat, source = self.fixture(directory)
            link = p / 'link.dat'
            link.symlink_to(source)
            report = audit.run([link], cat, p / 'audit')
            self.assertEqual(report['inputs'][0]['issues'][0]['action'], 'UNKNOWN')
            self.assertNotIn('copy', report['inputs'][0])
            proposal = p / 'proposal.json'
            proposal.write_text(json.dumps([{'source': 'drmd:old', 'target': 'drmd:unknown', 'reason': 'r', 'evidence': 'e'}]))
            with self.assertRaises(audit.Unknown):
                audit.run([source], cat, p / 'bad-audit', proposal)
            self.assertFalse((p / 'bad-audit').exists())

    def test_fifo_input_is_rejected_without_waiting_for_a_writer(self):
        with tempfile.TemporaryDirectory() as directory:
            p, cat, _ = self.fixture(directory)
            fifo = p / 'untrusted.dat'
            os.mkfifo(fifo)
            result = subprocess.run([sys.executable, str(Path(audit.__file__)), '--input', str(fifo),
                                     '--catalog', str(cat), '--out', str(p / 'audit')],
                                    timeout=3, capture_output=True, text=True)
            self.assertEqual(result.returncode, 1)
            report = json.loads((p / 'audit/REPORT.json').read_text())
            self.assertIn('bounded regular file', report['inputs'][0]['issues'][0]['reason'])

    def test_mutation_during_later_input_is_reported_unknown_at_closure(self):
        with tempfile.TemporaryDirectory() as directory:
            p, cat, source = self.fixture(directory)
            later = p / 'later.dat'
            later.write_bytes(compound([]))
            original_scan = audit.scan_bytes
            calls = []
            def scan(*args, **kwargs):
                calls.append(1)
                if len(calls) == 2:
                    source.write_bytes(compound([('id', 'other:new')]))
                return original_scan(*args, **kwargs)
            with patch.object(audit, 'scan_bytes', scan):
                report = audit.run([source, later], cat, p / 'audit')
            self.assertFalse(report['inputs'][0]['originalUnchanged'])
            self.assertEqual(report['inputs'][0]['issues'][-1]['member'], 'closure')


if __name__ == '__main__':
    unittest.main()
