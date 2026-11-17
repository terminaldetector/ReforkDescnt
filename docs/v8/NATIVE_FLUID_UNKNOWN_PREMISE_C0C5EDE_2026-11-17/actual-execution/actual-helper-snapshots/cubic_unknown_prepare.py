"""Independent native writer with ordinary supported VD2 config; no backend/tick state injection."""
from pathlib import Path
import hashlib,importlib.util,json,os,re,sys,zipfile
ROOT=Path(__file__).resolve().parents[2]
official=Path('/workspace/ungravity-v5/ufo-body-physics/scripts/v5/prepare_cubic_client.py')
libraries=Path('/workspace/ungravity-v5/boarding-attachment/build/cubic-client-final-body-scale-volumes-final-gl32/libraries')
donor=Path('/workspace/ungravity-v5/ufo-body-physics/build/cubic-client-final-flight-body-current-phase-sodium')
stamp=json.loads((ROOT/'work/cubic-fluid-unknown/verified-build.json').read_text())
spec=importlib.util.spec_from_file_location('official_unknown_fluid_stager',official);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
original=module.verified_copy
def copy(source,target,sha1=None):
    if 'libraries' in target.parts:
        p=libraries.joinpath(*target.parts[target.parts.index('libraries')+1:])
        if p.is_file() and module.digest(p)==module.digest(source) and (not sha1 or module.digest(p,'sha1')==sha1):
            target.parent.mkdir(parents=True,exist_ok=True);os.link(p,target);return target
    return original(source,target,sha1)
module.verified_copy=copy
module.main()
runtime=Path(sys.argv[sys.argv.index('--runtime')+1]).resolve();manifest=json.loads((runtime/'inputs.json').read_text())
for rel,digest in stamp['jars'].items():
    p=runtime/'mods'/Path(rel).name;assert module.digest(p)==digest and next(a['sha256'] for a in manifest['artifacts'] if a['path']==str(p.relative_to(runtime)))==digest
name='libraries/com/mojang/minecraft/1.21.1/minecraft-1.21.1-client.jar';prior=json.loads((donor/'inputs.json').read_text())
assert next(a['sha256'] for a in manifest['artifacts'] if a['path']==name)==next(a['sha256'] for a in prior['artifacts'] if a['path']==name)
assert module.digest(runtime/name)==module.digest(donor/name)
p=donor/'.fabric/remappedJars/minecraft-1.21.1-0.19.5/client-intermediary.jar';q=runtime/p.relative_to(donor)
with zipfile.ZipFile(p) as z:assert z.testzip() is None and not any(n.startswith('com/terminaldetector/') for n in z.namelist())
q.parent.mkdir(parents=True,exist_ok=True);os.link(p,q)
template=ROOT/'docs/v8/NATIVE_FLUID_UNKNOWN_CONTRACT_2026-11-17/actual-accepted-water-default-config.txt'
text=template.read_text();originalConfig=json.loads(re.sub(r'//[^\n]*','',text))
assert originalConfig['verticalViewDistance']==originalConfig['verticalLoadDistance']==8 and originalConfig['verticalGenerationLookahead']==2
for key in ['verticalViewDistance','verticalLoadDistance']:
    text,count=re.subn(r'("'+key+r'"\s*:\s*)8\b',lambda m:m.group(1)+'2',text);assert count==1
actualConfig=json.loads(re.sub(r'//[^\n]*','',text));expected=dict(originalConfig,verticalViewDistance=2,verticalLoadDistance=2);assert actualConfig==expected
config=runtime/'config/bettercubicchunks.json';config.parent.mkdir(exist_ok=True);assert not config.exists();config.write_text(text)
manifest['artifacts'].append(dict(path=str(config.relative_to(runtime)),sha256=module.digest(config),source='Pinned ordinary BCC config template; only verticalViewDistance and verticalLoadDistance 8->2 before startup'))
launch=runtime/'launch.json';argv=json.loads(launch.read_text());argv.insert(1,'-Ddrmd.gate.fluidUnknown=writer');launch.write_text(json.dumps(argv,indent=2)+'\n')
world=runtime/'saves/Cubic-Gate';worldFiles={str(p.relative_to(world)):{'bytes':p.stat().st_size,'sha256':module.digest(p)} for p in sorted(world.rglob('*')) if p.is_file()};assert 'session.lock' not in worldFiles
manifest.update(sourceSha=stamp['sourceSha'],candidateJars=stamp['jars'],officialStagerSha256=module.digest(official),mode='writer',scenario=os.environ['CUBIC_UNKNOWN_SCENARIO'],glOverride='3.3/330',
    ordinaryPrelaunchConfig=dict(template=str(template),templateSha256=module.digest(template),actualSha256=module.digest(config),changedOnly=['verticalViewDistance','verticalLoadDistance'],effectiveExpected=actualConfig),
    sourceFixture=dict(source='/workspace/ungravity-v5/world/build/cubic-seed2-gate/gate-v5-cubic-fixed',copiedSavedFilesBeforeLaunch=worldFiles,scope='Disposable existing native seed; not clean-preset creation or the accepted completed-water world'),
    derivedMinecraftCache=dict(source=str(p),sha256=module.digest(p),scope='Minecraft only; raw input SHA verified; no donor DRMD classes/save/markers'))
(runtime/'inputs.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Verified isolated unknown-fluid writer inputs; ordinary prelaunch VD2/load2, unchanged lookahead2; no runtime backend state injection')
