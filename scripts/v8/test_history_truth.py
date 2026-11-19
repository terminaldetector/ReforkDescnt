import hashlib,importlib.util,io,json,pathlib,subprocess,tempfile,unittest,zipfile
spec=importlib.util.spec_from_file_location('truth',pathlib.Path(__file__).with_name('history_truth.py'));truth=importlib.util.module_from_spec(spec);spec.loader.exec_module(truth)
class HistoryTruthTest(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.base=pathlib.Path(self.tmp.name);self.repo=self.base/'repo';self.repo.mkdir();self.g('init','-q');self.g('config','user.email','test@example.invalid');self.g('config','user.name','Synthetic Test');(self.repo/'base.txt').write_text('base\n');self.commit();self.head=self.g('rev-parse','HEAD').strip()
 def tearDown(self):self.tmp.cleanup()
 def g(self,*a):return subprocess.check_output(['git','-C',str(self.repo),*a],stderr=subprocess.DEVNULL).decode()
 def commit(self):self.g('add','.');self.g('commit','-qm','synthetic')
 def test_index_and_worktree_patch_layers_replay_exactly(self):
  (self.repo/'base.txt').write_text('index\n');self.g('add','base.txt');(self.repo/'base.txt').write_text('working\n');(self.repo/'binary.bin').write_bytes(b'\0old\xff');self.commit();self.head=self.g('rev-parse','HEAD').strip()
  (self.repo/'base.txt').write_text('index-again\n');self.g('add','base.txt');(self.repo/'base.txt').write_text('working-again\n');(self.repo/'binary.bin').write_bytes(b'\0new\xfe')
  before=self.g('status','--porcelain');inv,payload=truth.source_inventory(self.repo);self.assertTrue(inv['all_registered_source_captured']);self.assertEqual(before,self.g('status','--porcelain'))
  clean=self.base/'clean';subprocess.run(['git','clone','-q','--no-hardlinks',str(self.repo),str(clean)],check=True)
  for label,args in [('staged',['--index']),('unstaged',[])]:subprocess.run(['git','-C',str(clean),'apply',*args,'-'],input=payload['worktrees/000/'+label+'.patch'],check=True)
  self.assertEqual((clean/'base.txt').read_bytes(),(self.repo/'base.txt').read_bytes());self.assertEqual((clean/'binary.bin').read_bytes(),(self.repo/'binary.bin').read_bytes());self.assertEqual(subprocess.check_output(['git','-C',str(clean),'show',':base.txt']),self.g('show',':base.txt').encode())
 def test_all_registered_worktrees_and_private_artifact_exclusion(self):
  child=self.base/'child';self.g('worktree','add','-qb','other',str(child));(child/'src').mkdir();(child/'src/new.java').write_text('class RealSource {}');(child/'work').mkdir();(child/'work/private-user.txt').write_text('SECRET_USER_PAYLOAD')
  inv,payload=truth.source_inventory(self.repo);self.assertEqual(2,len(inv['worktrees']));self.assertTrue(inv['all_registered_source_captured']);self.assertIn(b'class RealSource {}',payload.values());self.assertNotIn(b'SECRET_USER_PAYLOAD',payload.values());self.assertNotIn('SECRET_USER_PAYLOAD',json.dumps(inv))
 def test_external_source_symlink_is_unknown_and_never_read(self):
  (self.repo/'src').mkdir();outside=self.base/'private';outside.write_text('SECRET_OUTSIDE');(self.repo/'src/a.py').symlink_to(outside)
  inv,payload=truth.source_inventory(self.repo);self.assertFalse(inv['all_registered_source_captured']);self.assertEqual('UNKNOWN_NONREGULAR',inv['worktrees'][0]['untracked_source'][0]['status']);self.assertNotIn(b'SECRET_OUTSIDE',payload.values())
 def test_already_dirty_file_changes_during_capture_must_be_unstable(self):
  from unittest.mock import patch
  (self.repo/'base.txt').write_text('dirty-first\n');original=truth.git;changed=[False]
  def racing(repo,*args,**kw):
   result=original(repo,*args,**kw)
   if args==('diff','--binary') and not changed[0]:(self.repo/'base.txt').write_text('dirty-second\n');changed[0]=True
   return result
  with patch.object(truth,'git',side_effect=racing):inv,_=truth.source_inventory(self.repo)
  self.assertFalse(inv['all_registered_source_captured'],'M→M status equality cannot prove unchanged patch bytes')
 def test_missing_commit_does_not_become_absent_work_or_pass(self):
  rows=truth.historical(self.repo,self.head,[('f'*40,'unprovided external claim')]);self.assertEqual('NOT_PROVIDED_OR_AMBIGUOUS',rows[0]['status']);self.assertNotIn('runtime_acceptance',rows[0])
 def fixture_snapshot(self):
  p=self.repo/'dist/probe';p.mkdir(parents=True);b=io.BytesIO()
  with zipfile.ZipFile(b,'w') as z:z.writestr('fabric.mod.json',json.dumps({'id':'synthetic','version':'0','environment':'*'}))
  data=b.getvalue();(p/'game.jar').write_bytes(data);h=hashlib.sha256(data).hexdigest()
  for name,obj in [('PACKAGE.json',{'source':self.head,'gameSHA256':h}),('DELIVERY.json',{'gameSHA256':h}),('MANIFEST.json',{}),('VALIDATION.json',{})]:(p/name).write_text(json.dumps(obj))
  self.commit();return self.g('rev-parse','HEAD').strip(),p
 def test_snapshot_declared_hash_must_match_actual_git_blob(self):
  commit,p=self.fixture_snapshot();(p/'DELIVERY.json').write_text(json.dumps({'gameSHA256':'0'*64}));self.commit()
  with self.assertRaisesRegex(ValueError,'hash differs'):truth.snapshot(self.repo,self.g('rev-parse','HEAD').strip(),'dist/probe')
 def test_snapshot_metadata_head_is_not_compiled_production(self):
  commit,p=self.fixture_snapshot();self.assertTrue(truth.snapshot(self.repo,commit,'dist/probe')['production_sources_unchanged']);(self.repo/'src/main').mkdir(parents=True);(self.repo/'src/main/Changed.java').write_text('class Changed {}');self.commit()
  got=truth.snapshot(self.repo,self.g('rev-parse','HEAD').strip(),'dist/probe');self.assertFalse(got['production_sources_unchanged']);self.assertEqual(['src/main/Changed.java'],got['production_source_delta_since_snapshot']);self.assertIn('not a live user',got['runtime_scope'])
 def test_existing_output_cannot_replace_first_evidence(self):
  out=self.base/'proof';out.mkdir();(out/'sentinel').write_text('first failure')
  with self.assertRaises(FileExistsError):truth.capture(self.repo,self.head,out)
  self.assertEqual('first failure',(out/'sentinel').read_text())
if __name__=='__main__':unittest.main()
