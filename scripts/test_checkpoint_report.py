"""Regression checks for the release evidence gate, using temporary synthetic fixtures."""
import json
import io
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
from unittest.mock import patch
import zipfile

import checkpoint_report as checkpoint


class EvidenceGateTest(unittest.TestCase):
    def setUp(self):
        # Synthetic release fixtures must never produce GitHub workflow annotations.
        output = patch('sys.stdout', new_callable=io.StringIO)
        output.start()
        self.addCleanup(output.stop)
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.started = int(time.time()) - 2
        self.write('gradle.properties', 'mod_version=test\narchives_base_name=drmd-6dof\n')
        self.write('build/checkpoint/started-at.txt', str(self.started))
        self.write('build/checkpoint/SOURCE_SHA.txt', 'abc123\n')
        self.write('dist/INSTALL_TLAUNCHER.txt', 'Test fixture, not a playable mod.\n')
        for folder in ('test-results/test', 'gametest/reports'):
            self.write(f'build/{folder}/tests.xml', '<testsuite><testcase name="ok"/></testsuite>')
        for name in checkpoint.MARKERS:
            self.write(f'build/rendercheck/{name}.txt', 'Fixture marker\n')
        self.write('build/rendercheck/swarm-ai-ok.txt',
                   'Live swarm synchronized 128 drones; indexed=128; moving=96; maximum candidates/query=96.\n')
        for name in checkpoint.FRAMES:
            self.write(f'build/rendercheck/screenshots/{name}.png', b'\x89PNG\r\n\x1a\nfixture')
        for name in checkpoint.SHOT_FRAMES:
            self.write(f'build/shootcheck/screenshots/{name}.png', b'\x89PNG\r\n\x1a\nfixture')
        self.write('build/shootcheck/shootcheck-ok.json', json.dumps(dict(scenes=10, fovs=[70,80,90],
                   rangePixelsChecked=True, fastSpeed=8, fastTravel=32, fastFrames=3, muzzleEvents=2, impactEvents=2)))
        jar = self.root / 'build/libs/drmd-6dof-test.jar'
        jar.parent.mkdir(parents=True)
        with zipfile.ZipFile(jar, 'w') as archive:
            archive.writestr('fabric.mod.json', json.dumps(dict(id='drmd', version='test', entrypoints={})))
            archive.writestr('com/terminaldetector/drmd/DescentMod.class', b'fixture')
        self.git = patch.object(subprocess, 'check_output', return_value='abc123\n')
        self.git.start()
        self.addCleanup(self.git.stop)

    def write(self, name, contents):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(contents if isinstance(contents, bytes) else contents.encode())
        return path

    def test_complete_run_stages_one_jar_with_hash(self):
        checkpoint.verify(self.root)
        out = self.root / 'build/checkpoint'
        proof = json.loads((out / 'verified.json').read_text())
        jars = list((out / 'distribution').glob('*.jar'))
        self.assertEqual(1, len(jars))
        self.assertEqual(checkpoint.sha256(jars[0]), proof['sha256'])
        self.assertEqual('abc123', proof['source_sha'])

    def test_stale_marker_is_rejected(self):
        path = self.root / 'build/rendercheck/collapse-ok.txt'
        os.utime(path, (self.started - 10, self.started - 10))
        with self.assertRaisesRegex(ValueError, 'Stale evidence'):
            checkpoint.verify(self.root)

    def test_missing_frame_is_rejected(self):
        (self.root / 'build/rendercheck/screenshots/drone-swarm-flight.png').unlink()
        with self.assertRaisesRegex(ValueError, 'Missing or empty evidence'):
            checkpoint.verify(self.root)

    def test_incomplete_swarm_is_rejected(self):
        self.write('build/rendercheck/swarm-ai-ok.txt',
                   'Live swarm synchronized 128 drones; indexed=128; moving=95; maximum candidates/query=96.\n')
        with self.assertRaisesRegex(ValueError, 'Swarm criteria failed'):
            checkpoint.verify(self.root)

    def test_test_harness_in_release_is_rejected(self):
        with zipfile.ZipFile(self.root / 'build/libs/drmd-6dof-test.jar', 'a') as archive:
            archive.writestr('com/terminaldetector/drmd/HybridRenderCheck.class', b'fixture')
        with self.assertRaisesRegex(ValueError, 'Test-only code'):
            checkpoint.verify(self.root)

    def test_no_unit_tests_is_not_a_pass(self):
        self.write('build/test-results/test/tests.xml', '<testsuite/>')
        with self.assertRaisesRegex(ValueError, 'incomplete or failing test report'):
            checkpoint.verify(self.root)

    def test_no_weapon_verification_is_rejected(self):
        (self.root / 'build/shootcheck/shootcheck-ok.json').unlink()
        with self.assertRaisesRegex(ValueError, 'Missing or empty evidence'):
            checkpoint.verify(self.root)

    def test_failed_run_still_has_report_without_certifying_jar(self):
        with patch.dict(os.environ, {'VERIFY_OUTCOME': 'failure'}):
            checkpoint.report(self.root)
        report = (self.root / 'build/checkpoint/REPORT.md').read_text()
        self.assertIn('NOT VERIFIED', report)
        self.assertIn('NOT RUN', report)
        self.assertNotIn('## Verified distribution', report)


if __name__ == '__main__':
    unittest.main()
