"""Official immutable-library stager, with an explicitly proven genuine negative-world fixture."""
from pathlib import Path
import importlib.util,json,os,sys,tarfile,zipfile

root=Path(__file__).resolve().parents[2]
official=Path('/workspace/ungravity-v5/ufo-body-physics/scripts/v5/prepare_cubic_client.py')
libraries=Path('/workspace/ungravity-v5/boarding-attachment/build/cubic-client-final-body-scale-volumes-final-gl32/libraries')
donor=Path('/workspace/ungravity-v5/ufo-body-physics/build/cubic-client-final-flight-body-current-phase-sodium')
fixture=root/'docs/v8/SURFACE_ARRIVAL_FIXTURE_2026-11-17'
proof=json.loads((fixture/'fixture-provenance.json').read_text());archive=fixture/proof['archive']
spec=importlib.util.spec_from_file_location('official_arrival_stager',official);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
assert archive.stat().st_size==proof['archiveBytes'] and module.digest(archive)==proof['archiveSha256']
original_copy=module.verified_copy
def copy(source,target,sha1=None):
    if 'libraries' in target.parts:
        prior=libraries.joinpath(*target.parts[target.parts.index('libraries')+1:])
        if prior.is_file() and module.digest(prior)==module.digest(source) and (not sha1 or module.digest(prior,'sha1')==sha1):
            target.parent.mkdir(parents=True,exist_ok=True);os.link(prior,target);return target
    return original_copy(source,target,sha1)
module.verified_copy=copy
original_tree=module.shutil.copytree
world_receipt=None
def fixture_copy(source,target,**kwargs):
    # Official stager's only world-copy call. Select the declared actual negative
    # as a whole; never seed a fake surface value or replace/delete a staged cache.
    global world_receipt
    assert Path(source).name=='gate-v5-cubic-fixed' and Path(target).name=='Cubic-Gate'
    assert world_receipt is None and not Path(target).exists()
    target=Path(target);target.mkdir(parents=True)
    observed={}
    with tarfile.open(archive,'r:gz') as tf:
        for member in tf:
            name=Path(member.name)
            assert member.isfile() and not member.issym() and not member.islnk()
            assert not name.is_absolute() and '..' not in name.parts
            data=tf.extractfile(member).read()
            import hashlib
            observed[str(name)]={'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
            if str(name)=='session.lock':continue # Every new process acquires its own lock.
            p=target/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    assert observed==proof['files']
    staged={str(p.relative_to(target)):{'bytes':p.stat().st_size,'sha256':module.digest(p)} for p in sorted(target.rglob('*')) if p.is_file()}
    assert staged=={k:v for k,v in proof['files'].items() if k!='session.lock'}
    world_receipt=dict(archive=str(archive),archiveSha256=proof['archiveSha256'],originalClosedWorld=proof['originalClosedWorld'],
        actualNegativeSource=proof['actualNegativeSource'],allStagedBytesMatchActualNegative=True,omitted=['session.lock'],files=staged)
    return str(target)
module.shutil.copytree=fixture_copy
try:module.main()
finally:module.shutil.copytree=original_tree
assert world_receipt is not None
runtime=Path(sys.argv[sys.argv.index('--runtime')+1]).resolve()
stamp=json.loads((root/'work/cubic-arrival/verified-build.json').read_text());manifest=json.loads((runtime/'inputs.json').read_text())
for relative,digest in stamp['jars'].items():
    p=runtime/'mods'/Path(relative).name
    assert module.digest(p)==digest and next(a['sha256'] for a in manifest['artifacts'] if a['path']==str(p.relative_to(runtime)))==digest
name='libraries/com/mojang/minecraft/1.21.1/minecraft-1.21.1-client.jar'
prior=json.loads((donor/'inputs.json').read_text())
assert next(a['sha256'] for a in manifest['artifacts'] if a['path']==name)==next(a['sha256'] for a in prior['artifacts'] if a['path']==name)
assert module.digest(runtime/name)==module.digest(donor/name)
p=donor/'.fabric/remappedJars/minecraft-1.21.1-0.19.5/client-intermediary.jar';q=runtime/p.relative_to(donor)
with zipfile.ZipFile(p) as z:assert z.testzip() is None and not any(n.startswith('com/terminaldetector/') for n in z.namelist())
q.parent.mkdir(parents=True,exist_ok=True);os.link(p,q)
scenario=os.environ['CUBIC_ARRIVAL_SCENARIO'];launch=runtime/'launch.json';args=json.loads(launch.read_text())
args.insert(1,'-Ddrmd.gate.surfaceArrival=true');assert not any('surfaceIngest=true' in x for x in args)
launch.write_text(json.dumps(args,indent=2)+'\n')
manifest.update(sourceSha=stamp['sourceSha'],candidateJars=stamp['jars'],officialStagerSha256=module.digest(official),scenario=scenario,
    fixture=world_receipt,glOverride='3.3/330',
    derivedMinecraftCache=dict(source=str(p),sha256=module.digest(p),scope='Minecraft intermediary only; raw original inputs checked; no donor DRMD classes/save/markers'))
(runtime/'inputs.json').write_text(json.dumps(manifest,indent=2)+'\n')
(runtime/'actual-negative-fixture-inputs.json').write_text(json.dumps(world_receipt,indent=2)+'\n')
print('Verified actual saved black-cache fixture, immutable libraries/Minecraft-only cache, and exact candidate JARs')
