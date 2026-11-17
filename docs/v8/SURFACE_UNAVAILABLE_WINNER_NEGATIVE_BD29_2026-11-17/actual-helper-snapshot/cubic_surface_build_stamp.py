"""Bind both candidate JARs to this worktree's exact source; never borrow another mod build."""
from pathlib import Path
import hashlib,json,subprocess,sys,zipfile

ROOT=Path(__file__).resolve().parents[2]
STAMP=ROOT/'work/cubic-surface/verified-build.json'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def inputs():
    return {str(p.relative_to(ROOT)):sha(p) for p in sorted([*(ROOT/'src').rglob('*'),*(ROOT/'scripts/v8').glob('cubic_surface*'),ROOT/'build.gradle',ROOT/'settings.gradle',ROOT/'gradle.properties']) if p.is_file()}
def artifacts():
    result={}
    for suffix,required in [('', 'com/terminaldetector/drmd/world/store/SurfaceIngest.class'),('-cubiccheck','com/terminaldetector/drmd/gate/NativeSurfaceIngestCheck.class')]:
        p=ROOT/'build/libs'/('drmd-6dof-1.1.35-ufo-dev.1'+suffix+'.jar')
        with zipfile.ZipFile(p) as z:assert z.testzip() is None and required in z.namelist(),p
        result[str(p.relative_to(ROOT))]=sha(p)
    return result
def clean():
    assert not subprocess.check_output(['git','status','--porcelain','--','src','scripts/v8/cubic_surface*','build.gradle','settings.gradle','gradle.properties'],cwd=ROOT),'Commit all build and launcher inputs first'
    return subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
STAMP.parent.mkdir(parents=True,exist_ok=True)
if sys.argv[1]=='before':
    STAMP.with_suffix('.inputs.json').write_text(json.dumps(dict(sourceSha=clean(),inputs=inputs()),sort_keys=True))
elif sys.argv[1]=='record':
    saved=json.loads(STAMP.with_suffix('.inputs.json').read_text());assert saved['sourceSha']==clean() and saved['inputs']==inputs(),'Source changed during build'
    saved.update(jars=artifacts(),scope='Both remapped candidate artifacts; independent of the main mechanism build')
    STAMP.write_text(json.dumps(saved,indent=2)+'\n')
else:
    saved=json.loads(STAMP.read_text());head=clean()
    assert not subprocess.check_output(['git','diff',saved['sourceSha'],head,'--','src','scripts/v8/cubic_surface*','build.gradle','settings.gradle','gradle.properties'],cwd=ROOT)
    assert saved['inputs']==inputs() and saved['jars']==artifacts(),'Rebuild changed source/artifacts before native launch'
    print('Verified candidate source',saved['sourceSha'],'and both exact remapped JARs')
