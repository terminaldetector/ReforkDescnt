"""One fresh build and one unchanged-observer native positive; preserve first close/failure."""
from pathlib import Path
import hashlib,json,os,subprocess,sys,time,traceback,zipfile

ROOT=Path(__file__).resolve().parents[3]
CASE=Path(__file__).resolve().parent
SOURCE='29f848d84f517263267748307518c04196cb7000' # Docs-only tip of tested productionf6.
SCENARIO='cubic-surface-arrival-positive-29f848d-sodium'
FIXTURE=ROOT/'docs/v8/SURFACE_ARRIVAL_FIXTURE_2026-11-17'
STAMP=ROOT/'work/cubic-arrival/verified-build.json'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def receipt(name,data):
    with (CASE/name).open('xb') as f:f.write((json.dumps(data,indent=2)+'\n').encode());f.flush();os.fsync(f.fileno())
    fd=os.open(CASE,os.O_RDONLY)
    try:os.fsync(fd)
    finally:os.close(fd)
def inputs():
    paths=[*(ROOT/'src').rglob('*'),*(ROOT/'scripts/v8').glob('cubic_arrival*'),*FIXTURE.iterdir(),ROOT/'build.gradle',ROOT/'settings.gradle',ROOT/'gradle.properties']
    return {str(p.relative_to(ROOT)):sha(p) for p in sorted(paths) if p.is_file()}
def run_child(stage,argv):
    receipt(stage+'-start.json',dict(argv=argv,cwd=str(ROOT),nanoTime=time.monotonic_ns()))
    with (CASE/(stage+'-stdout-stderr.log')).open('xb') as log:
        child=subprocess.Popen(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
        code=child.wait()
        receipt(stage+'-child-close.json',dict(actualSubprocessReturnCode=code,pid=child.pid,nanoTime=time.monotonic_ns()))
        log.flush();os.fsync(log.fileno())
    return code
def main():
    assert not any((CASE/n).exists() for n in ['before.json','build-child-close.json','native-child-close.json','final-close.json','wrapper-exception.json'])
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()==SOURCE
    assert not subprocess.check_output(['git','status','--porcelain','--','src','scripts/v8/cubic_arrival*',str(FIXTURE.relative_to(ROOT)),'build.gradle','settings.gradle','gradle.properties'],cwd=ROOT)
    before=inputs();(CASE/'actual-helper-snapshots').mkdir()
    for p in [Path(__file__),*(ROOT/'scripts/v8').glob('cubic_arrival*')]:
        if p.is_file():
            with (CASE/'actual-helper-snapshots'/p.name).open('xb') as f:f.write(p.read_bytes());f.flush();os.fsync(f.fileno())
    receipt('before.json',dict(actualGitHead=SOURCE,productionSource='f6e3d083671c8986ff60bc7419498afa1e738c51',
        inputs=before,wrapperSha256=sha(Path(__file__)),scenario=SCENARIO,nanoTime=time.monotonic_ns()))
    code=run_child('build',['bash','scripts/v8/cubic_arrival_build.sh'])
    if code!=0:
        receipt('final-close.json',dict(closedStage='build',actualChildCode=code,nativeNotRun=True,wrapperFinalCode=code))
        return code
    stamp=json.loads(STAMP.read_text());assert stamp['sourceSha']==SOURCE and stamp['inputs']==before
    assert inputs()==before
    for rel,digest in stamp['jars'].items():
        p=ROOT/rel;assert sha(p)==digest
        with zipfile.ZipFile(p) as z:assert z.testzip() is None
    observer=ROOT/'build/libs/drmd-6dof-1.1.35-ufo-dev.1-cubiccheck.jar'
    assert sha(observer)=='38c2e40b8cd67f7753a5c6463f46dc3bb08bd0a27fa596a974c3105b03d4d90d','Unchanged observer artifact differs; preserve build before native'
    receipt('native-before.json',dict(actualBuildStamp=stamp,wholeObserverByteIdenticalTo1281=True,nanoTime=time.monotonic_ns()))
    prior=ROOT/'build/cubic-surface-arrival-automation'
    if prior.exists():
        original=json.loads((prior/'surface-arrival-outcome.json').read_text())
        assert original['status']=='FAIL_NO_AVAILABLE_REFRESH' and original['observationTicksAfterFull']==600
        assert json.loads((prior/'inputs.json').read_text())['sourceSha']=='1281ce8a61e2a7f3e19d7d8aa44a12671445950a'
        assert (ROOT/'work/cubic-arrival/cubic-surface-arrival-baseline-1281ce8-sodium.exit').read_text().strip()=='255'
        receipt('preserved-prior-runtime.json',dict(path=str(prior),originalOutcomeSha256=sha(prior/'surface-arrival-outcome.json'),
            action='Unchanged native helper will rename this closed previous runtime to its unique before-scenario path; original world archive/evidence already sealed'))
    code=run_child('native',['bash','scripts/v8/cubic_arrival_native.sh',SCENARIO])
    assert inputs()==before and subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()==SOURCE
    assert all(sha(ROOT/p)==h for p,h in stamp['jars'].items())
    path=ROOT/('build/cubic-surface-arrival-final-'+SCENARIO) if code==0 else ROOT/'build/cubic-surface-arrival-automation'
    outcome=path/'surface-arrival-outcome.json';result=json.loads(outcome.read_text()) if outcome.is_file() else None
    exit_file=ROOT/('work/cubic-arrival/'+SCENARIO+'.exit')
    final=dict(closedStage='native',actualNativeHelperCode=code,actualJavaExit=int(exit_file.read_text()) if exit_file.exists() else None,
        originalOutcome=result,sourceHelpersArtifactsUnchanged=True,wrapperFinalCode=code,nanoTime=time.monotonic_ns())
    receipt('final-close.json',final)
    print(json.dumps({k:v for k,v in final.items() if k!='originalOutcome'},indent=2),flush=True)
    return code
if __name__=='__main__':
    try:code=main()
    except BaseException:
        detail=traceback.format_exc()
        try:receipt('wrapper-exception.json',dict(traceback=detail,wrapperFinalCode=97))
        finally:print(detail,file=sys.stderr,flush=True)
        code=97
    sys.exit(code)
