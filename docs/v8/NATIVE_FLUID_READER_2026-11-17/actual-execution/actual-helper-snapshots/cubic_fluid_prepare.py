"""Official native stager; reader is an exact archived closed writer, never recreated fluid data."""
from pathlib import Path
import hashlib,importlib.util,json,os,sys,tarfile,zipfile
ROOT=Path(__file__).resolve().parents[2]
official=Path('/workspace/ungravity-v5/ufo-body-physics/scripts/v5/prepare_cubic_client.py')
libraries=Path('/workspace/ungravity-v5/boarding-attachment/build/cubic-client-final-body-scale-volumes-final-gl32/libraries')
donor=Path('/workspace/ungravity-v5/ufo-body-physics/build/cubic-client-final-flight-body-current-phase-sodium')
mode=os.environ['CUBIC_FLUID_MODE'];assert mode in ['writer','reader']
stamp=json.loads((ROOT/'work/cubic-fluid/verified-build.json').read_text())
spec=importlib.util.spec_from_file_location('official_fluid_stager',official);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
original=module.verified_copy
def copy(source,target,sha1=None):
    if 'libraries' in target.parts:
        p=libraries.joinpath(*target.parts[target.parts.index('libraries')+1:])
        if p.is_file() and module.digest(p)==module.digest(source) and (not sha1 or module.digest(p,'sha1')==sha1):
            target.parent.mkdir(parents=True,exist_ok=True);os.link(p,target);return target
    return original(source,target,sha1)
module.verified_copy=copy
proof=None;staged=None;original_tree=module.shutil.copytree
if mode=='reader':
    proofPath=Path(os.environ['CUBIC_FLUID_WRITER_PROOF']).resolve();proof=json.loads(proofPath.read_text())
    archive=Path(proof['archive']);baseline=Path(proof['outcome'])
    assert archive.stat().st_size==proof['archiveBytes'] and module.digest(archive)==proof['archiveSha256']
    assert module.digest(baseline)==proof['outcomeSha256'] and proof['candidateJars']==stamp['jars'] and proof['sourceSha']==stamp['sourceSha']
    assert json.loads(baseline.read_text())['status']=='WRITER_PENDING_SAVED'
    def writer_copy(source,target,**kwargs):
        global staged
        assert Path(source).name=='gate-v5-cubic-fixed' and Path(target).name=='Cubic-Gate' and staged is None and not Path(target).exists()
        target=Path(target);target.mkdir(parents=True);found={}
        with tarfile.open(archive,'r:gz') as tf:
            for m in tf:
                name=Path(m.name);assert m.isfile() and not m.issym() and not m.islnk() and not name.is_absolute() and '..' not in name.parts
                data=tf.extractfile(m).read();found[str(name)]={'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
                if str(name)=='session.lock':continue
                p=target/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
        assert found==proof['files']
        actual={str(p.relative_to(target)):{'bytes':p.stat().st_size,'sha256':module.digest(p)} for p in sorted(target.rglob('*')) if p.is_file()}
        assert actual=={k:v for k,v in proof['files'].items() if k!='session.lock'}
        staged=dict(writerProof=str(proofPath),archive=str(archive),archiveSha256=proof['archiveSha256'],allSavedFileBytesMatch=True,omitted=['session.lock'],files=actual)
        return str(target)
    module.shutil.copytree=writer_copy
try:module.main()
finally:module.shutil.copytree=original_tree
runtime=Path(sys.argv[sys.argv.index('--runtime')+1]).resolve();manifest=json.loads((runtime/'inputs.json').read_text())
for rel,digest in stamp['jars'].items():
    p=runtime/'mods'/Path(rel).name;assert module.digest(p)==digest and next(a['sha256'] for a in manifest['artifacts'] if a['path']==str(p.relative_to(runtime)))==digest
name='libraries/com/mojang/minecraft/1.21.1/minecraft-1.21.1-client.jar';prior=json.loads((donor/'inputs.json').read_text())
assert next(a['sha256'] for a in manifest['artifacts'] if a['path']==name)==next(a['sha256'] for a in prior['artifacts'] if a['path']==name)
assert module.digest(runtime/name)==module.digest(donor/name)
p=donor/'.fabric/remappedJars/minecraft-1.21.1-0.19.5/client-intermediary.jar';q=runtime/p.relative_to(donor)
with zipfile.ZipFile(p) as z:assert z.testzip() is None and not any(n.startswith('com/terminaldetector/') for n in z.namelist())
q.parent.mkdir(parents=True,exist_ok=True);os.link(p,q)
launch=runtime/'launch.json';argv=json.loads(launch.read_text());argv.insert(1,'-Ddrmd.gate.fluidPending='+mode);launch.write_text(json.dumps(argv,indent=2)+'\n')
if mode=='reader':
    assert staged is not None
    (runtime/'fluid-writer-baseline.json').write_bytes(Path(proof['outcome']).read_bytes())
    assert module.digest(runtime/'fluid-writer-baseline.json')==proof['outcomeSha256']
manifest.update(sourceSha=stamp['sourceSha'],candidateJars=stamp['jars'],officialStagerSha256=module.digest(official),mode=mode,scenario=os.environ['CUBIC_FLUID_SCENARIO'],
    writerFixture=staged,glOverride='3.3/330',derivedMinecraftCache=dict(source=str(p),sha256=module.digest(p),scope='Minecraft only; raw input SHA verified; no donor DRMD classes/save/markers'))
(runtime/'inputs.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Verified pending-fluid mode',mode,'actual artifacts and immutable library/Minecraft inputs; reader saved data unchanged' if mode=='reader' else 'new disposable seed fixture')
