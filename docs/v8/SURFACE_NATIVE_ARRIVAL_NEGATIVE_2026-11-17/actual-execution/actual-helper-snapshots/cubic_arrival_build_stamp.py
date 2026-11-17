"""Freeze the no-edit observer/helpers and its actual negative-world fixture alongside both JARs."""
from pathlib import Path
import hashlib,json,subprocess,sys,zipfile

ROOT=Path(__file__).resolve().parents[2]
STAMP=ROOT/'work/cubic-arrival/verified-build.json'
FIXTURE=ROOT/'docs/v8/SURFACE_ARRIVAL_FIXTURE_2026-11-17'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def inputs():
    paths=[*(ROOT/'src').rglob('*'),*(ROOT/'scripts/v8').glob('cubic_arrival*'),*FIXTURE.iterdir(),ROOT/'build.gradle',ROOT/'settings.gradle',ROOT/'gradle.properties']
    return {str(p.relative_to(ROOT)):sha(p) for p in sorted(paths) if p.is_file()}
def artifacts():
    result={}
    for suffix,required in [('', 'com/terminaldetector/drmd/world/store/SurfaceIngest.class'),('-cubiccheck','com/terminaldetector/drmd/gate/NativeSurfaceArrivalCheck.class')]:
        p=ROOT/'build/libs'/('drmd-6dof-1.1.35-ufo-dev.1'+suffix+'.jar')
        with zipfile.ZipFile(p) as z:assert z.testzip() is None and required in z.namelist(),p
        result[str(p.relative_to(ROOT))]=sha(p)
    return result
def clean():
    assert not subprocess.check_output(['git','status','--porcelain','--','src','scripts/v8/cubic_arrival*',str(FIXTURE.relative_to(ROOT)),'build.gradle','settings.gradle','gradle.properties'],cwd=ROOT),'Commit all observer/build/helper/fixture inputs first'
    return subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
STAMP.parent.mkdir(parents=True,exist_ok=True)
if sys.argv[1]=='before':
    STAMP.with_suffix('.inputs.json').write_text(json.dumps(dict(sourceSha=clean(),inputs=inputs()),sort_keys=True))
elif sys.argv[1]=='record':
    saved=json.loads(STAMP.with_suffix('.inputs.json').read_text());assert saved['sourceSha']==clean() and saved['inputs']==inputs(),'Source changed during build'
    saved.update(jars=artifacts(),scope='Both actual no-edit arrival artifacts with unchanged guarded production source')
    STAMP.write_text(json.dumps(saved,indent=2)+'\n')
else:
    saved=json.loads(STAMP.read_text());head=clean()
    assert not subprocess.check_output(['git','diff',saved['sourceSha'],head,'--','src','scripts/v8/cubic_arrival*',str(FIXTURE.relative_to(ROOT)),'build.gradle','settings.gradle','gradle.properties'],cwd=ROOT)
    assert saved['inputs']==inputs() and saved['jars']==artifacts(),'Rebuild changed observer/artifact inputs before native'
    print('Verified no-edit arrival source',saved['sourceSha'],'and both actual remapped JARs')
