"""Use the existing official stager and hash-verified immutable library/Minecraft caches only."""
from pathlib import Path
import hashlib,importlib.util,json,os,subprocess,sys,zipfile

root=Path(__file__).resolve().parents[2]
official=Path('/workspace/ungravity-v5/ufo-body-physics/scripts/v5/prepare_cubic_client.py')
libraries=Path('/workspace/ungravity-v5/boarding-attachment/build/cubic-client-final-body-scale-volumes-final-gl32/libraries')
donor=Path('/workspace/ungravity-v5/ufo-body-physics/build/cubic-client-final-flight-body-current-phase-sodium')
spec=importlib.util.spec_from_file_location('official_stager',official);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
original=module.verified_copy
def copy(source,target,sha1=None):
    parts=target.parts
    if 'libraries' in parts:
        prior=libraries.joinpath(*parts[parts.index('libraries')+1:])
        if prior.is_file() and module.digest(prior)==module.digest(source) and (not sha1 or module.digest(prior,'sha1')==sha1):
            target.parent.mkdir(parents=True,exist_ok=True);os.link(prior,target);return target
    return original(source,target,sha1)
module.verified_copy=copy
module.main()
runtime=Path(sys.argv[sys.argv.index('--runtime')+1]).resolve();stamp=json.loads((root/'work/ps1/verified-build.json').read_text())
manifest=json.loads((runtime/'inputs.json').read_text())
for relative,digest in stamp['jars'].items():
    p=runtime/'mods'/Path(relative).name
    assert module.digest(p)==digest and next(a['sha256'] for a in manifest['artifacts'] if a['path']==str(p.relative_to(runtime)))==digest
name='libraries/com/mojang/minecraft/1.21.1/minecraft-1.21.1-client.jar'
original_donor=json.loads((donor/'inputs.json').read_text())
assert next(a['sha256'] for a in manifest['artifacts'] if a['path']==name)==next(a['sha256'] for a in original_donor['artifacts'] if a['path']==name)
assert module.digest(runtime/name)==module.digest(donor/name)
p=donor/'.fabric/remappedJars/minecraft-1.21.1-0.19.5/client-intermediary.jar';q=runtime/p.relative_to(donor)
with zipfile.ZipFile(p) as z:
    assert z.testzip() is None and not any(n.startswith('com/terminaldetector/') for n in z.namelist())
q.parent.mkdir(parents=True,exist_ok=True);os.link(p,q)
scenario=os.environ['PS1_SCENARIO'];launch=runtime/'launch.json';args=json.loads(launch.read_text());args.insert(1,'-Ddrmd.gate.presentation=true');launch.write_text(json.dumps(args,indent=2)+'\n')
if 'viewmodel' in scenario:
    args.insert(1,'-Ddrmd.gate.viewmodel=true');launch.write_text(json.dumps(args,indent=2)+'\n')
if 'ps1-color' in scenario:
    args.insert(1,'-Ddrmd.gate.ps1Color=true')
    if '-Ddrmd.gate.viewmodel=true' not in args:args.insert(1,'-Ddrmd.gate.viewmodel=true')
    launch.write_text(json.dumps(args,indent=2)+'\n')
if 'sky-layers' in scenario:
    args=[a for a in args if a!='-Ddrmd.gate.presentation=true'];args.insert(1,'-Ddrmd.gate.skyLayers=true');launch.write_text(json.dumps(args,indent=2)+'\n')
if 'fabulous' in scenario:
    with (runtime/'options.txt').open('a') as f:f.write('graphicsMode:2\n')
manifest.update(sourceSha=stamp['sourceSha'],candidateJars=stamp['jars'],officialStagerSha256=module.digest(official),scenario=scenario,
    glOverride='3.2/150' if 'gl32' in scenario else '3.3/330',
    derivedMinecraftCache=dict(source=str(p),sha256=module.digest(p),scope='Minecraft intermediary only; official raw input SHA checked on both runtimes, no DRMD classes/saves/markers borrowed'))
(runtime/'inputs.json').write_text(json.dumps(manifest,indent=2)+'\n')
print('Verified immutable libraries, original Minecraft inputs, derived Minecraft-only cache, and both actual candidate JARs')
