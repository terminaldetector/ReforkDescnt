"""Bind the unknown-neighbour fluid observer and exact game to its frozen local source."""
from pathlib import Path
import hashlib,json,subprocess,sys,zipfile
ROOT=Path(__file__).resolve().parents[2]
STAMP=ROOT/'work/cubic-fluid-unknown/verified-build.json'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def inputs():
    paths=[*(ROOT/'src').rglob('*'),*(ROOT/'scripts/v8').glob('cubic_unknown*'),*(ROOT/'docs/v8/NATIVE_FLUID_API_AUDIT_2026-11-17').iterdir(),*(ROOT/'docs/v8/NATIVE_FLUID_UNKNOWN_CONTRACT_2026-11-17').iterdir(),ROOT/'docs/v8/NATIVE_FLUID_UNKNOWN_DOWN32_CONTRACT_2026-11-17.md',ROOT/'docs/v8/NATIVE_FLUID_HALO_EDGE_CONTRACT_2026-11-17.md',ROOT/'build.gradle',ROOT/'settings.gradle',ROOT/'gradle.properties']
    return {str(p.relative_to(ROOT)):sha(p) for p in sorted(paths) if p.is_file()}
def clean():
    assert not subprocess.check_output(['git','status','--porcelain','--','src','scripts/v8/cubic_unknown*','docs/v8/NATIVE_FLUID_API_AUDIT_2026-11-17','docs/v8/NATIVE_FLUID_UNKNOWN_CONTRACT_2026-11-17','docs/v8/NATIVE_FLUID_UNKNOWN_DOWN32_CONTRACT_2026-11-17.md','docs/v8/NATIVE_FLUID_HALO_EDGE_CONTRACT_2026-11-17.md','build.gradle','settings.gradle','gradle.properties'],cwd=ROOT),'Commit the exact observer/helper/API inputs first'
    return subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
def jars():
    result={}
    for suffix,required in [('', 'com/terminaldetector/drmd/world/store/SurfaceIngest.class'),('-cubiccheck','com/terminaldetector/drmd/gate/NativeFluidUnknownCheck.class')]:
        p=ROOT/'build/libs'/('drmd-6dof-1.1.35-ufo-dev.1'+suffix+'.jar')
        with zipfile.ZipFile(p) as z:assert z.testzip() is None and required in z.namelist(),p
        result[str(p.relative_to(ROOT))]=sha(p)
    return result
STAMP.parent.mkdir(parents=True,exist_ok=True)
if sys.argv[1]=='before':STAMP.with_suffix('.inputs.json').write_text(json.dumps(dict(sourceSha=clean(),inputs=inputs()),sort_keys=True))
elif sys.argv[1]=='record':
    data=json.loads(STAMP.with_suffix('.inputs.json').read_text());assert data['sourceSha']==clean() and data['inputs']==inputs()
    data.update(jars=jars(),scope='Actual remapped unknown-neighbour fluid writer observer and unchanged production game');STAMP.write_text(json.dumps(data,indent=2)+'\n')
else:
    data=json.loads(STAMP.read_text());head=clean()
    assert not subprocess.check_output(['git','diff',data['sourceSha'],head,'--','src','scripts/v8/cubic_unknown*','docs/v8/NATIVE_FLUID_API_AUDIT_2026-11-17','docs/v8/NATIVE_FLUID_UNKNOWN_CONTRACT_2026-11-17','docs/v8/NATIVE_FLUID_UNKNOWN_DOWN32_CONTRACT_2026-11-17.md','docs/v8/NATIVE_FLUID_HALO_EDGE_CONTRACT_2026-11-17.md','build.gradle','settings.gradle','gradle.properties'],cwd=ROOT)
    assert data['inputs']==inputs() and data['jars']==jars();print('Verified unknown-fluid observer/game source',data['sourceSha'])
