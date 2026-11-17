"""Frozen native-only cold reader. Same330compiledtuple, actual3d8documentationHEAD; no build/source/world mutation."""
from pathlib import Path
import hashlib,json,os,subprocess,sys,time,traceback,zipfile
ROOT=Path(__file__).resolve().parents[3]
CASE=Path(__file__).resolve().parent
SOURCE='330e9a721293df7eac489ae3e62f4d4972275f7b'
FROZEN_HEAD='3d8ffcf694cb2b9a550d10874a6622cdfde3dc36'
PROOF=ROOT/'work/cubic-fluid-cold/cubic-fluid-cold-writer-330e9a7-sodium-writer-proof.json'
PROOF_SHA='1608f041ddb65d22f76e922ae9f6880bd9793b21f891b4c2fc9174eb0f734fa1'
POST=ROOT/'work/cubic-fluid-cold/reader-postconditions.py'
SCENARIO='cubic-fluid-cold-reader-330e9a7-sodium'
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
    assert not any((CASE/n).exists() for n in ['before.json','reader-child-close.json','final-close.json','wrapper-exception.json'])
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()==FROZEN_HEAD
    assert not subprocess.check_output(['git','status','--porcelain','--','src','scripts/v8/cubic_unknown*','scripts/v8/cubic_cold*','docs/v8/NATIVE_DEFERRED_FLUID_COLD_UNKNOWN_CONTRACT_2026-11-17.md','docs/v8/NATIVE_FLUID_API_AUDIT_2026-11-17','docs/v8/NATIVE_FLUID_UNKNOWN_CONTRACT_2026-11-17','docs/v8/NATIVE_FLUID_UNKNOWN_DOWN32_CONTRACT_2026-11-17.md','docs/v8/NATIVE_FLUID_HALO_EDGE_CONTRACT_2026-11-17.md','build.gradle','settings.gradle','gradle.properties'],cwd=ROOT)
    initial=inputs();(CASE/'actual-helper-snapshots').mkdir()
    for p in [Path(__file__),POST,PROOF,*(ROOT/'scripts/v8').glob('cubic_unknown*'),*(ROOT/'scripts/v8').glob('cubic_cold*'),ROOT/'docs/v8/NATIVE_DEFERRED_FLUID_COLD_UNKNOWN_CONTRACT_2026-11-17.md']:
        if p.is_file():
            with (CASE/'actual-helper-snapshots'/p.name).open('xb') as f:f.write(p.read_bytes());f.flush();os.fsync(f.fileno())
    assert sha(PROOF)==PROOF_SHA
    proof=json.loads(PROOF.read_text());archive=Path(proof['archive'])
    assert sha(archive)==proof['archiveSha256']=='e5589f78bf1b2513f9adff1b728420e497e3d03c3f8bf92a27a2e43efc22c165'
    assert proof['sourceSha']==SOURCE and proof['savedUnknownPending']['exactKey']['family']=='minecraft:water'
    postSha=sha(POST)
    receipt('before.json',dict(sourceSha=SOURCE,actualFrozenDocumentationHead=FROZEN_HEAD,inputs=initial,wrapperSha256=sha(Path(__file__)),scenario=SCENARIO,mode='cold-reader',writerProofSha256=PROOF_SHA,archiveSha256=sha(archive),postconditionsSha256=postSha,noBuildAuthorized=True,nanoTime=time.monotonic_ns()))
    checkcode=child('stamp-check',['python3','scripts/v8/cubic_cold_build_stamp.py','check'])
    assert checkcode==0
    stamp=json.loads((ROOT/'work/cubic-fluid-cold/verified-build.json').read_text());assert stamp['sourceSha']==SOURCE and stamp['inputs']==initial and inputs()==initial
    assert sha(ROOT/'work/cubic-fluid-cold/verified-build.json')=='0a4eecc65c12362ed04b4c993c64237e06ac90b327e1322de4f1e4863cdfbcc7'
    assert sha(ROOT/'build/libs/drmd-6dof-1.1.35-ufo-dev.1.jar')=='60ca40f42a746bd60d77d6ca89679057b375bafea98775f1ab80517b671252d8'
    assert sha(ROOT/'build/libs/drmd-6dof-1.1.35-ufo-dev.1-cubiccheck.jar')=='c74c79136dc1412b59195661833e34ed5794e9946a6647ae81d5037479e5706a'
    assert all(sha(ROOT/p)==h for p,h in stamp['jars'].items())
    os.environ['CUBIC_COLD_WRITER_PROOF']=str(PROOF)
    receipt('reader-before.json',dict(actualBuildStamp=stamp,sameExistingArtifactsNoRebuild=True,compiledSourceSha=SOURCE,actualFrozenDocumentationHead=FROZEN_HEAD,writerProof=proof,sourceHelpersUnchanged=True,nanoTime=time.monotonic_ns()))
    code=child('reader',['bash','scripts/v8/cubic_cold_native.sh','cold-reader',SCENARIO])
    assert inputs()==initial and subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()==FROZEN_HEAD
    assert all(sha(ROOT/p)==h for p,h in stamp['jars'].items())
    final=ROOT/('build/cubic-fluid-cold-reader-final-'+SCENARIO)
    r=final if final.exists() else ROOT/'build/cubic-fluid-cold-reader-automation'
    outcome=r/'fluid-unknown-outcome.json';d=json.loads(outcome.read_text()) if outcome.exists() else None
    ex=ROOT/('work/cubic-fluid-cold/'+SCENARIO+'.exit');originalProof=PROOF
    postResult=None
    assert sha(PROOF)==PROOF_SHA and sha(POST)==postSha and sha(archive)==proof['archiveSha256']
    if code==0:
        import importlib.util
        spec=importlib.util.spec_from_file_location('cold_reader_postconditions',POST);module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
        postResult=module.check(r);receipt('postconditions.json',postResult)
    result=dict(closedStage='reader',actualReaderHelperCode=code,actualJavaExit=int(ex.read_text()) if ex.exists() else None,
        originalOutcome=d,actualPostconditions=postResult,writerProof=str(PROOF),sourceHelpersArtifactsUnchanged=True,sameExistingArtifactsNoRebuild=True,wrapperFinalCode=code,nanoTime=time.monotonic_ns())
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
