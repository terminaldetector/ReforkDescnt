"""Native-only first reader, using the qualified actual closed writer and its exact artifacts."""
from pathlib import Path
import gzip,hashlib,json,os,subprocess,sys,tarfile,time,traceback,zipfile
ROOT=Path(__file__).resolve().parents[3]
CASE=Path(__file__).resolve().parent
SOURCE='6c23897d721aaf4429e2bd0054644838ddc1dd5c'
HEAD='bc392f3b8794af96ae9691c4a21c95d6983ff385'
SCENARIO='cubic-fluid-reader-first-6c23897-sodium'
PROOF=ROOT/'work/cubic-fluid/cubic-fluid-writer-6c23897-sodium-writer-proof.json'
JARS={'build/libs/drmd-6dof-1.1.35-ufo-dev.1.jar':'0855f657e53742e3a5b95bce8f30b60a76bc93020574dc5826bccb86514f8df9','build/libs/drmd-6dof-1.1.35-ufo-dev.1-cubiccheck.jar':'6c6263aeb0ebeba11f0c61b0b12520b9dd60842beccbeb115da7f826a9db810a'}
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def receipt(name,data):
    with (CASE/name).open('xb') as f:f.write((json.dumps(data,indent=2)+'\n').encode());f.flush();os.fsync(f.fileno())
    fd=os.open(CASE,os.O_RDONLY)
    try:os.fsync(fd)
    finally:os.close(fd)
def inputs():
    paths=[*(ROOT/'src').rglob('*'),*(ROOT/'scripts/v8').glob('cubic_fluid*'),*(ROOT/'docs/v8/NATIVE_FLUID_API_AUDIT_2026-11-17').iterdir(),ROOT/'build.gradle',ROOT/'settings.gradle',ROOT/'gradle.properties']
    return {str(p.relative_to(ROOT)):sha(p) for p in sorted(paths) if p.is_file()}
def main():
    assert not any((CASE/n).exists() for n in ['before.json','reader-child-close.json','final-close.json','wrapper-exception.json'])
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()==HEAD
    assert not subprocess.check_output(['git','status','--porcelain','--','src','scripts/v8/cubic_fluid*','docs/v8/NATIVE_FLUID_API_AUDIT_2026-11-17','build.gradle','settings.gradle','gradle.properties'],cwd=ROOT)
    stamp=json.loads((ROOT/'work/cubic-fluid/verified-build.json').read_text());initial=inputs()
    assert stamp['sourceSha']==SOURCE and stamp['jars']==JARS and stamp['inputs']==initial
    assert not subprocess.check_output(['git','diff',SOURCE,HEAD,'--','src','scripts/v8/cubic_fluid*','docs/v8/NATIVE_FLUID_API_AUDIT_2026-11-17','build.gradle','settings.gradle','gradle.properties'],cwd=ROOT)
    for rel,h in JARS.items():
        p=ROOT/rel;assert sha(p)==h
        with zipfile.ZipFile(p) as z:assert z.testzip() is None
    proof=json.loads(PROOF.read_text());archive=Path(proof['archive']);outcome=Path(proof['outcome'])
    assert proof['sourceSha']==SOURCE and proof['candidateJars']==JARS and archive.stat().st_size==1374863
    assert sha(archive)==proof['archiveSha256']=='860e0793fa8a7373ec124f40d7f8dc3c71a3949e509df5266ad0e7922337caba'
    assert sha(outcome)==proof['outcomeSha256']=='1f8c2d583d6ca0e65577326c3201f6dbbcea473dd0cd460a8efeccc4935b180b'
    writer=json.loads(outcome.read_text());assert writer['status']=='WRITER_PENDING_SAVED';assert min(x['y'] for x in writer['stoppingWitness']['queuedFront'])==78
    with gzip.open(archive,'rb') as f:
        while f.read(1024*1024):pass
    actual={}
    with tarfile.open(archive,'r:gz') as tf:
        for m in tf:
            assert m.isfile() and not m.issym() and not m.islnk();data=tf.extractfile(m).read();actual[m.name]={'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
    assert actual==proof['files'] and len(actual)==93
    (CASE/'actual-helper-snapshots').mkdir()
    for p in [Path(__file__),*(ROOT/'scripts/v8').glob('cubic_fluid*')]:
        if p.is_file():
            with (CASE/'actual-helper-snapshots'/p.name).open('xb') as f:f.write(p.read_bytes());f.flush();os.fsync(f.fileno())
    receipt('before.json',dict(compiledSourceSha=SOURCE,actualDocsHead=HEAD,actualBuildStamp=stamp,inputs=initial,wrapperSha256=sha(Path(__file__)),scenario=SCENARIO,mode='reader',writerProof=str(PROOF),writerProofSha256=sha(PROOF),actualWriterArchiveSha256=sha(archive),actualWriterOutcomeSha256=sha(outcome),savedFrontierY=78,buildNotAuthorized=True,savedShaftEditsOrReschedulesNotAuthorized=True,deadlineTicksAfterReadiness=400,nanoTime=time.monotonic_ns()))
    argv=['bash','scripts/v8/cubic_fluid_native.sh','reader',SCENARIO,str(PROOF)]
    receipt('reader-start.json',dict(argv=argv,cwd=str(ROOT),nanoTime=time.monotonic_ns()))
    with (CASE/'reader-stdout-stderr.log').open('xb') as log:
        p=subprocess.Popen(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT);code=p.wait()
        receipt('reader-child-close.json',dict(actualSubprocessReturnCode=code,pid=p.pid,nanoTime=time.monotonic_ns()));log.flush();os.fsync(log.fileno())
    assert inputs()==initial and subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()==HEAD
    assert all(sha(ROOT/p)==h for p,h in JARS.items()) and sha(PROOF)==json.loads((CASE/'before.json').read_text())['writerProofSha256'] and sha(archive)==proof['archiveSha256'] and sha(outcome)==proof['outcomeSha256']
    r=ROOT/('build/cubic-fluid-reader-final-'+SCENARIO) if code==0 else ROOT/'build/cubic-fluid-reader-automation'
    outcomePath=r/'fluid-pending-outcome.json';d=json.loads(outcomePath.read_text()) if outcomePath.exists() else None
    ex=ROOT/('work/cubic-fluid/'+SCENARIO+'.exit')
    staged=json.loads((r/'inputs.json').read_text()) if (r/'inputs.json').exists() else None
    if staged:
        fixture=staged['writerFixture'];assert fixture['allSavedFileBytesMatch'] and fixture['omitted']==['session.lock']
        assert fixture['files']=={k:v for k,v in proof['files'].items() if k!='session.lock'} and staged['candidateJars']==JARS
        assert sha(r/'fluid-writer-baseline.json')==proof['outcomeSha256']
    if d:
        assert d['mode']=='reader' and d['observerFixtureWrites']==0 and d['droppedEvents']==0
        if d['status']=='PASS':
            s=d['finalSnapshot'];assert s['readerFrontierY']==78 and s['readerRelevantWitnesses']>0 and s['sourceWater'] and s['targetWater'] and s['client']['targetWater']
    result=dict(closedStage='reader',actualReaderHelperCode=code,actualJavaExit=int(ex.read_text()) if ex.exists() else None,originalOutcome=d,sourceHelpersArtifactsWriterFixtureUnchanged=True,buildNotRun=True,wrapperFinalCode=code,nanoTime=time.monotonic_ns())
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
