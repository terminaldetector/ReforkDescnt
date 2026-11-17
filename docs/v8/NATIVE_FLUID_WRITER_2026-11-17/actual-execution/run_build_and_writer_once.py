"""Frozen one-build/one-writer lease; reader must be a separately granted case."""
from pathlib import Path
import hashlib,json,os,subprocess,sys,time,traceback,zipfile
ROOT=Path(__file__).resolve().parents[3]
CASE=Path(__file__).resolve().parent
SOURCE='6c23897d721aaf4429e2bd0054644838ddc1dd5c'
SCENARIO='cubic-fluid-writer-6c23897-sodium'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def receipt(name,data):
    with (CASE/name).open('xb') as f:f.write((json.dumps(data,indent=2)+'\n').encode());f.flush();os.fsync(f.fileno())
    fd=os.open(CASE,os.O_RDONLY)
    try:os.fsync(fd)
    finally:os.close(fd)
def inputs():
    paths=[*(ROOT/'src').rglob('*'),*(ROOT/'scripts/v8').glob('cubic_fluid*'),*(ROOT/'docs/v8/NATIVE_FLUID_API_AUDIT_2026-11-17').iterdir(),ROOT/'build.gradle',ROOT/'settings.gradle',ROOT/'gradle.properties']
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
    assert not subprocess.check_output(['git','status','--porcelain','--','src','scripts/v8/cubic_fluid*','docs/v8/NATIVE_FLUID_API_AUDIT_2026-11-17','build.gradle','settings.gradle','gradle.properties'],cwd=ROOT)
    initial=inputs();(CASE/'actual-helper-snapshots').mkdir()
    for p in [Path(__file__),*(ROOT/'scripts/v8').glob('cubic_fluid*')]:
        if p.is_file():
            with (CASE/'actual-helper-snapshots'/p.name).open('xb') as f:f.write(p.read_bytes());f.flush();os.fsync(f.fileno())
    receipt('before.json',dict(sourceSha=SOURCE,inputs=initial,wrapperSha256=sha(Path(__file__)),scenario=SCENARIO,mode='writer',readerNotAuthorized=True,nanoTime=time.monotonic_ns()))
    code=child('build',['bash','scripts/v8/cubic_fluid_build.sh'])
    if code!=0:
        receipt('final-close.json',dict(closedStage='build',actualBuildCode=code,nativeNotRun=True,wrapperFinalCode=code));return code
    stamp=json.loads((ROOT/'work/cubic-fluid/verified-build.json').read_text());assert stamp['sourceSha']==SOURCE and stamp['inputs']==initial and inputs()==initial
    for rel,h in stamp['jars'].items():
        assert sha(ROOT/rel)==h
        with zipfile.ZipFile(ROOT/rel) as z:assert z.testzip() is None
    assert sha(ROOT/'build/libs/drmd-6dof-1.1.35-ufo-dev.1.jar')=='0855f657e53742e3a5b95bce8f30b60a76bc93020574dc5826bccb86514f8df9','Production game differs from accepted arrival component'
    receipt('writer-before.json',dict(actualBuildStamp=stamp,wholeGameByteIdenticalToArrivalPositive=True,nanoTime=time.monotonic_ns()))
    code=child('writer',['bash','scripts/v8/cubic_fluid_native.sh','writer',SCENARIO])
    assert inputs()==initial and subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()==SOURCE
    assert all(sha(ROOT/p)==h for p,h in stamp['jars'].items())
    r=ROOT/('build/cubic-fluid-writer-final-'+SCENARIO) if code==0 else ROOT/'build/cubic-fluid-writer-automation'
    outcome=r/'fluid-pending-outcome.json';d=json.loads(outcome.read_text()) if outcome.exists() else None
    ex=ROOT/('work/cubic-fluid/'+SCENARIO+'.exit');proof=ROOT/('work/cubic-fluid/'+SCENARIO+'-writer-proof.json')
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
