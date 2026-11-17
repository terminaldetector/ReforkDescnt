"""One exact focused10-unit/2-STOCK child with durable actual subprocess close."""
from pathlib import Path
import hashlib,json,os,subprocess,sys,time,traceback
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[3]
CASE=Path(__file__).resolve().parent
SOURCE='f6e3d083671c8986ff60bc7419498afa1e738c51'
FOCUS='scripts/v8/surface_ingest_stock_focus.init.gradle'
CLASSES=['com.terminaldetector.drmd.world.store.SurfaceIngestHeightConventionTest','com.terminaldetector.drmd.world.store.SurfaceIngestNativeRefreshQueueTest']
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def receipt(name,data):
    with (CASE/name).open('xb') as f:f.write((json.dumps(data,indent=2)+'\n').encode());f.flush();os.fsync(f.fileno())
    fd=os.open(CASE,os.O_RDONLY)
    try:os.fsync(fd)
    finally:os.close(fd)
def inputs():
    paths=[*(ROOT/'src').rglob('*'),*(ROOT/'scripts/v8').glob('cubic_arrival*'),ROOT/FOCUS,ROOT/'build.gradle',ROOT/'settings.gradle',ROOT/'gradle.properties']
    return {str(p.relative_to(ROOT)):sha(p) for p in sorted(paths) if p.is_file()}
def main():
    assert not any((CASE/n).exists() for n in ['before.json','child-started.json','child-close.json','final-close.json','wrapper-exception.json'])
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()==SOURCE
    assert not subprocess.check_output(['git','status','--porcelain','--','src',FOCUS,'scripts/v8/cubic_arrival*','build.gradle','settings.gradle','gradle.properties'],cwd=ROOT)
    assert not (ROOT/'build/surface-stock-control/world/level.dat').exists()
    for n in ['overworld','the_end']:assert not (ROOT/f'build/resources/gametest/data/minecraft/dimension_type/{n}.json').exists()
    argv=['./gradlew','test']
    for name in CLASSES:argv+=['--tests',name]
    argv+=['runGametest','--offline','--no-daemon','-I',FOCUS]
    env=os.environ.copy();env.update(JAVA_HOME='/workspace/jdk21-complete',GRADLE_USER_HOME='/workspace/.gradle',PATH='/workspace/jdk21-complete/bin:'+env['PATH'])
    captured=inputs();started=time.time()
    (CASE/'actual-helper-snapshots').mkdir()
    for p in [Path(__file__),ROOT/FOCUS]:
        with (CASE/'actual-helper-snapshots'/p.name).open('xb') as f:f.write(p.read_bytes());f.flush();os.fsync(f.fileno())
    receipt('before.json',dict(sourceSha=SOURCE,inputs=captured,epoch=started,argv=argv,cwd=str(ROOT),wrapperSha256=sha(Path(__file__)),scope='Compile plus10 focused units and2 actual normal-profile STOCK callbacks; no remap/native'))
    with (CASE/'child-stdout-stderr.log').open('xb') as log:
        child=subprocess.Popen(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
        receipt('child-started.json',dict(pid=child.pid,nanoTime=time.monotonic_ns()))
        code=child.wait();receipt('child-close.json',dict(actualSubprocessReturnCode=code,pid=child.pid,nanoTime=time.monotonic_ns()))
        log.flush();os.fsync(log.fileno())
    assert inputs()==captured
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()==SOURCE
    result=dict(actualGradleReturnCode=code,sourceInputsUnchanged=True,nanoTime=time.monotonic_ns())
    if code==0:
        unitCount=0;files={}
        for name in CLASSES:
            p=ROOT/'build/test-results/test'/('TEST-'+name+'.xml');xml=ET.parse(p).getroot()
            assert p.stat().st_mtime>=started and int(xml.attrib['tests'])==5
            assert all(int(xml.attrib.get(k,0))==0 for k in ['failures','errors','skipped'])
            unitCount+=int(xml.attrib['tests']);files[str(p.relative_to(ROOT))]={'sha256':sha(p),'mtime':p.stat().st_mtime,'tests':int(xml.attrib['tests'])}
        p=ROOT/'build/surface-stock-control/reports/gametest-results.xml';xml=ET.parse(p).getroot();cases=list(xml.iter('testcase'))
        assert p.stat().st_mtime>=started and len(cases)==2 and not list(xml.iter('failure')) and not list(xml.iter('error')) and not list(xml.iter('skipped'))
        files[str(p.relative_to(ROOT))]={'sha256':sha(p),'mtime':p.stat().st_mtime,'tests':len(cases)}
        text=(CASE/'child-stdout-stderr.log').read_text()
        assert 'actualBottom=-64 actualTop=320' in text and 'disabled=[drmd:advanced_column]' in text
        result.update(units=unitCount,gameTests=len(cases),actualNormalProfileObserved=True,freshXml=files)
    result['wrapperFinalCode']=code if code>=0 else 128-code;receipt('final-close.json',result)
    print(json.dumps(result,indent=2),flush=True);return result['wrapperFinalCode']
if __name__=='__main__':
    try:code=main()
    except BaseException:
        detail=traceback.format_exc()
        try:receipt('wrapper-exception.json',dict(traceback=detail,wrapperFinalCode=97))
        finally:print(detail,file=sys.stderr,flush=True)
        code=97
    sys.exit(code)
