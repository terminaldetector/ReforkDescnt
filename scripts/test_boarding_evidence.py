import copy
import json
from pathlib import Path
import tempfile
import time
import unittest
from checkpoint_report import BOARDING_SCENES, verify_boarding


class BoardingEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.started = int(time.time()) - 2
        self.marker = self.root / 'build/boardingcheck/boarding-ok.json'
        self.marker.parent.mkdir(parents=True)
        self.proof = dict(actualInputs=True, authoritativeContact=True, worldUpOnly=True,
                          teleportFollow=False, jumpWorldVelocityError=0, maxPredictionError=.1,
                          scenes=[dict(scene=n, frames=3) for n in BOARDING_SCENES])
        for name in BOARDING_SCENES:
            p = self.root / f'build/boardingcheck/boarding-frames/{name}.png'
            p.parent.mkdir(exist_ok=True)
            p.write_bytes(b'\x89PNG\r\n\x1a\nfixture')
        self.write()

    def write(self):
        self.marker.write_text(json.dumps(self.proof))

    def test_complete_actual_input_run_is_accepted(self):
        self.assertTrue(verify_boarding(self.root, self.started)['actualInputs'])

    def test_missing_completed_frame_rejects_the_run(self):
        (self.root / 'build/boardingcheck/boarding-frames/jump.png').unlink()
        with self.assertRaises(ValueError):
            verify_boarding(self.root, self.started)

    def test_missing_real_input_or_using_teleport_follow_is_rejected(self):
        for key, value in (('actualInputs', False), ('teleportFollow', True)):
            with self.subTest(key=key):
                original = copy.deepcopy(self.proof)
                self.proof[key] = value
                self.write()
                with self.assertRaises(ValueError):
                    verify_boarding(self.root, self.started)
                self.proof = original

    def test_nonfinite_or_out_of_bounds_prediction_and_jump_are_rejected(self):
        for key, value in (('maxPredictionError', float('nan')), ('maxPredictionError', .8),
                           ('jumpWorldVelocityError', .03)):
            with self.subTest(key=key):
                original = copy.deepcopy(self.proof)
                self.proof[key] = value
                self.write()
                with self.assertRaises(ValueError):
                    verify_boarding(self.root, self.started)
                self.proof = original
