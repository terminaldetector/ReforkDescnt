import json,time,unittest,tempfile,copy
from pathlib import Path
from checkpoint_report import verify_body_index,BODY_INDEX_SCENES
class BodyIndexProofTest(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.started=int(time.time())-2
  self.marker=self.root/'build/bodyindexcheck/body-index-ok.json';self.marker.parent.mkdir(parents=True)
  self.proof=dict(actualInputs=True,legacyMissClient=True,legacyMissServer=True,edgePicked=True,holeClear=True,materialCharged=True,teleportFollow=False,revisionChanges=3,predictionSamples=50,maxPredictionError=.01,travelDistance=5,scenes=[dict(scene=n,frames=3) for n in BODY_INDEX_SCENES])
  for name in BODY_INDEX_SCENES:
   p=self.root/f'build/bodyindexcheck/body-index-frames/{name}.png';p.parent.mkdir(exist_ok=True);p.write_bytes(b'\x89PNG\r\n\x1a\nfixture')
  self.write()
 def write(self):self.marker.write_text(json.dumps(self.proof))
 def test_actual_far_edge_proof_passes(self):self.assertTrue(verify_body_index(self.root,self.started)['edgePicked'])
 def test_missing_legacy_failure_real_input_motion_or_prediction_is_rejected(self):
  for key,value in [('actualInputs',False),('legacyMissClient',False),('legacyMissServer',False),('edgePicked',False),('holeClear',False),('materialCharged',False),('teleportFollow',True),('travelDistance',0),('revisionChanges',2),('predictionSamples',0),('maxPredictionError',float('nan')),('maxPredictionError',.8)]:
   before=copy.deepcopy(self.proof)
   with self.subTest(key=key),self.assertRaises(ValueError):self.proof[key]=value;self.write();verify_body_index(self.root,self.started)
   self.proof=before
 def test_missing_frame_is_rejected(self):
  (self.root/'build/bodyindexcheck/body-index-frames/edge-cut.png').unlink()
  with self.assertRaises(ValueError):verify_body_index(self.root,self.started)
 def test_missing_far_edge_scene_is_rejected(self):
  self.proof['scenes'].pop();self.write()
  with self.assertRaises(ValueError):verify_body_index(self.root,self.started)
