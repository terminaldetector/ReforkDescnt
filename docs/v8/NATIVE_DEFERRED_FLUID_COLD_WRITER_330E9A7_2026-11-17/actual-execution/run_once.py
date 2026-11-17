"""Frozen one-build/one unknown-neighbour writer lease; cold reader requires separate review/grant."""
from pathlib import Path
import hashlib,json,os,subprocess,sys,time,traceback,zipfile
ROOT=Path(__file__).resolve().parents[3]
CASE=Path(__file__).resolve().parent
SOURCE='330e9a721293df7eac489ae3e62f4d4972275f7b'
SCENARIO='cubic-fluid-cold-writer-330e9a7-sodium'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def receipt(name,data):
    with (CASE/name).open('xb') as f:f.write((json.dumps(data,indent=2)+'\n').encode());f.flush();os.fsync(f.fileno())
    fd=os.open(CASE,os.O_RDONLY)
    try:os.fsync(fd)
    finally:os.close(fd)
def inputs():
    paths=[*(ROOT/'src').rglob('*'),*(ROOT/'scripts/v8').glob('cubic_unknown*'),*(ROOT/'scripts/v8').glob('cubic_cold*'),ROOT/'docs/v8/NATIVE_DEFERRED_FLUID_COLD_UNKNOWN_CONTRACT_2026-11-17.md',*(ROOT/'docs/v8/NATIVE_FLUID_API_AUDIT_2026-11-17').iterdir(),*(ROOT/'docs/v8/NATIVE_FLUID_UNKNOWN_CONTRACT_2026-11-17').iterdir(),ROOT/'docs/v8/NATIVE_FLUID_UNKNOWN_DOWN32_CONTRACT_2026-11-17.md',ROOT/'docs/v8/NATIVE_FLUID_HALO_EDGE_CONTRACT_2026-11-17.md',ROOT/'build.gradle',ROOT/'settings.gradle',ROOT/'gradle.properties']
    return {str(p.relative_to(ROOT)):sha(p) for p in sorted(paths) if p.is_file()}
def child(stage,argv):
    receipt(stage+'-start.json',dict(argv=argv,cwd=str(ROOT),nanoTime=time.monotonic_ns()))
    with (CASE/(stage+'-stdout-stderr.log')).open('xb') as log:
        p=subprocess.Popen(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
        code=p.wait();receipt(stage+'-child-close.json',dict(actualSubprocessReturnCode=code,pid=p.pid,nanoTime=time.monotonic_ns()))
        log.flush();os.fsync(log.fileno())
    return code
def main():
    assert not any((CASE/n).exists() for n in ['before.json','build-child-close.json','writer-child-close.json','final-close.json','wrapper-exception.json'])
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()==SOURCE
    assert not subprocess.check_output(['git','status','--porcelain','--','src','scripts/v8/cubic_unknown*','scripts/v8/cubic_cold*','docs/v8/NATIVE_DEFERRED_FLUID_COLD_UNKNOWN_CONTRACT_2026-11-17.md','docs/v8/NATIVE_FLUID_API_AUDIT_2026-11-17','docs/v8/NATIVE_FLUID_UNKNOWN_CONTRACT_2026-11-17','docs/v8/NATIVE_FLUID_UNKNOWN_DOWN32_CONTRACT_2026-11-17.md','docs/v8/NATIVE_FLUID_HALO_EDGE_CONTRACT_2026-11-17.md','build.gradle','settings.gradle','gradle.properties'],cwd=ROOT)
    initial=inputs();(CASE/'actual-helper-snapshots').mkdir()
    for p in [Path(__file__),*(ROOT/'scripts/v8').glob('cubic_unknown*'),*(ROOT/'scripts/v8').glob('cubic_cold*'),ROOT/'docs/v8/NATIVE_DEFERRED_FLUID_COLD_UNKNOWN_CONTRACT_2026-11-17.md']:
        if p.is_file():
            with (CASE/'actual-helper-snapshots'/p.name).open('xb') as f:f.write(p.read_bytes());f.flush();os.fsync(f.fileno())
    receipt('before.json',dict(sourceSha=SOURCE,inputs=initial,wrapperSha256=sha(Path(__file__)),scenario=SCENARIO,mode='cold-writer',readerNotAuthorized=True,nanoTime=time.monotonic_ns()))
    code=child('build',['bash','scripts/v8/cubic_cold_build.sh'])
    if code!=0:
        receipt('final-close.json',dict(closedStage='build',actualBuildCode=code,nativeNotRun=True,wrapperFinalCode=code));return code
    stamp=json.loads((ROOT/'work/cubic-fluid-cold/verified-build.json').read_text());assert stamp['sourceSha']==SOURCE and stamp['inputs']==initial and inputs()==initial
    for rel,h in stamp['jars'].items():
        assert sha(ROOT/rel)==h
        with zipfile.ZipFile(ROOT/rel) as z:assert z.testzip() is None
    baseline=ROOT/'docs/v8/NATIVE_DEFERRED_FLUID_LIVE_POSITIVE_704203E_2026-11-17/artifacts'
    previousGame=baseline/'drmd-6dof-1.1.35-ufo-dev.1.jar';previousObserver=baseline/'drmd-6dof-1.1.35-ufo-dev.1-cubiccheck.jar'
    currentGame=ROOT/'build/libs/drmd-6dof-1.1.35-ufo-dev.1.jar';currentObserver=ROOT/'build/libs/drmd-6dof-1.1.35-ufo-dev.1-cubiccheck.jar'
    assert sha(previousGame)==sha(currentGame)=='60ca40f42a746bd60d77d6ca89679057b375bafea98775f1ab80517b671252d8','Reviewed production game differs'
    assert sha(previousObserver)=='cf776cf6b53150f23cd76eb05cf5d7d95105c1e8b11595616085be48ee0ee634'
    with zipfile.ZipFile(previousObserver) as old,zipfile.ZipFile(currentObserver) as current:
        oldFiles={n:hashlib.sha256(old.read(n)).hexdigest() for n in old.namelist() if not n.endswith('/')}
        newFiles={n:hashlib.sha256(current.read(n)).hexdigest() for n in current.namelist() if not n.endswith('/')}
        changed={n:dict(before=oldFiles.get(n),after=newFiles.get(n)) for n in sorted(oldFiles.keys()|newFiles.keys()) if oldFiles.get(n)!=newFiles.get(n)}
        assert changed and not oldFiles.keys()-newFiles.keys()
        assert all(n.startswith('com/terminaldetector/drmd/gate/NativeFluidUnknownCheck') and n.endswith('.class') for n in changed),changed
    receipt('writer-before.json',dict(actualBuildStamp=stamp,wholeProductionByteIdenticalToReviewed704=True,gameSha256=sha(currentGame),observerSha256=sha(currentObserver),onlyColdObserverArtifactDiff=changed,nanoTime=time.monotonic_ns()))
    code=child('writer',['bash','scripts/v8/cubic_cold_native.sh','cold-writer',SCENARIO])
    assert inputs()==initial and subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()==SOURCE
    assert all(sha(ROOT/p)==h for p,h in stamp['jars'].items())
    final=ROOT/('build/cubic-fluid-cold-writer-final-'+SCENARIO)
    r=final if final.exists() else ROOT/'build/cubic-fluid-cold-writer-automation'
    outcome=r/'fluid-unknown-outcome.json';d=json.loads(outcome.read_text()) if outcome.exists() else None
    ex=ROOT/('work/cubic-fluid-cold/'+SCENARIO+'.exit');proof=ROOT/('work/cubic-fluid-cold/'+SCENARIO+'-writer-proof.json')
    result=dict(closedStage='writer',actualWriterHelperCode=code,actualJavaExit=int(ex.read_text()) if ex.exists() else None,
        originalOutcome=d,qualifiedProof=str(proof) if proof.exists() else None,sourceHelpersArtifactsUnchanged=True,readerNotRun=True,wrapperFinalCode=code,nanoTime=time.monotonic_ns())
    receipt('final-close.json',result)
    print(json.dumps({k:v for k,v in result.items() if k!='originalOutcome'},indent=2),flush=True);return code
if __name__=='__main__':
    try:code=main()
    except BaseException:
        detail=traceback.format_exc()
        try:receipt('wrapper-exception.json',dict(traceback=detail,wrapperFinalCode=97))
        finally:print(detail,file=sys.stderr,flush=True)
        code=97
    sys.exit(code)
