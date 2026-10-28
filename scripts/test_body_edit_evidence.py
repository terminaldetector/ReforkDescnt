import json,time,unittest,tempfile,copy
from pathlib import Path
from checkpoint_report import verify_body_edits,BODY_EDIT_SCENES
class BodyEditProofTest(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name);self.started=int(time.time())-2
  self.marker=self.root/'build/bodyeditcheck/body-edit-ok.json';self.marker.parent.mkdir(parents=True)
  self.proof=dict(actualInputs=True,survivalMining=True,materialCharged=True,savedState=True,comRebased=True,revisions=3,predictionSamples=50,maxPredictionError=.01,maxPivotStep=.001,dropStacks=1,toolDamage=1,scenes=[dict(scene=n,frames=3) for n in BODY_EDIT_SCENES])
  for name in BODY_EDIT_SCENES:
   p=self.root/f'build/bodyeditcheck/body-edit-frames/{name}.png';p.parent.mkdir(exist_ok=True);p.write_bytes(b'\x89PNG\r\n\x1a\nfixture')
  self.write()
 def write(self):self.marker.write_text(json.dumps(self.proof))
 def test_complete_actual_edit_proof_passes(self):self.assertTrue(verify_body_edits(self.root,self.started)['savedState'])
 def test_unverified_inventory_geometry_and_clock_fail(self):
  for key,value in [('actualInputs',False),('materialCharged',False),('survivalMining',False),('comRebased',False),('revisions',2),('predictionSamples',0),('maxPredictionError',float('nan')),('maxPivotStep',.8),('dropStacks',2),('toolDamage',0)]:
   before=copy.deepcopy(self.proof)
   with self.subTest(key=key),self.assertRaises(ValueError):self.proof[key]=value;self.write();verify_body_edits(self.root,self.started)
   self.proof=before
 def test_missing_real_frame_fails(self):
  (self.root/'build/bodyeditcheck/body-edit-frames/hull-cut.png').unlink()
  with self.assertRaises(ValueError):verify_body_edits(self.root,self.started)
 def test_missing_actual_survival_scene_fails(self):
  self.proof['scenes'].pop(3);self.write()
  with self.assertRaises(ValueError):verify_body_edits(self.root,self.started)
