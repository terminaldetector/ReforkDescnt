import copy
import json
from pathlib import Path
import tempfile
import time
import unittest
from checkpoint_report import SHIP_BOARDING_SCENES, verify_ship_boarding


class ShipBoardingEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.started = int(time.time()) - 2
        self.marker = self.root / 'build/shipboardingcheck/ship-boarding-ok.json'
        self.marker.parent.mkdir(parents=True)
        self.proof = dict(actualInputs=True, shipGravity=True, orientedCollision=True, teleportFollow=False,
                          predictionSamples=300, maxPredictionError=.1, jumpVelocityError=0,
                          maxFrameError=.001, maxModelUpError=.001, maxCameraUpError=.0001, modelFrames=100, cameraFrames=100,
                          scenes=[dict(scene=n, frames=3) for n in SHIP_BOARDING_SCENES])
        for scene in SHIP_BOARDING_SCENES:
            p = self.root / f'build/shipboardingcheck/ship-boarding-frames/{scene}.png'
            p.parent.mkdir(exist_ok=True)
            p.write_bytes(b'\x89PNG\r\n\x1a\nfixture')
        self.write()

    def write(self):
        self.marker.write_text(json.dumps(self.proof))

    def test_actual_complete_rotation_proof_is_accepted(self):
        self.assertTrue(verify_ship_boarding(self.root, self.started)['shipGravity'])

    def test_missing_ceiling_or_camera_pass_cannot_be_published(self):
        (self.root / 'build/shipboardingcheck/ship-boarding-frames/pitch-ceiling.png').unlink()
        with self.assertRaises(ValueError):
            verify_ship_boarding(self.root, self.started)

    def test_insufficient_samples_and_unbounded_errors_are_rejected(self):
        for key, value in (('predictionSamples', 0), ('modelFrames', 0), ('cameraFrames', 0),
                           ('maxPredictionError', .8), ('jumpVelocityError', .1),
                           ('maxFrameError', .01), ('maxCameraUpError', .001), ('maxModelUpError', float('nan'))):
            original = copy.deepcopy(self.proof)
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.proof[key] = value
                self.write()
                verify_ship_boarding(self.root, self.started)
            self.proof = original

    def test_teleport_follow_or_missing_real_rotations_are_rejected(self):
        for key, value in (('teleportFollow', True), ('orientedCollision', False),
                           ('scenes', self.proof['scenes'][:4])):
            original = copy.deepcopy(self.proof)
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.proof[key] = value
                self.write()
                verify_ship_boarding(self.root, self.started)
            self.proof = original
