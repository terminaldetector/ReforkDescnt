"""First short lease only: focused19units then2actual STOCK controls. No remap/native."""
from pathlib import Path
import hashlib,json,os,subprocess,sys,time,traceback,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[3];CASE=Path(__file__).resolve().parent
SOURCE='704203e9e809af8a388356eefa65d4c1642f609a'
FOCUS='scripts/v8/deferred_fluid_stock_focus.init.gradle'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def receipt(name,data):
 with (CASE/name).open('xb') as f:f.write((json.dumps(data,indent=2)+'\n').encode());f.flush();os.fsync(f.fileno())
 fd=os.open(CASE,os.O_RDONLY)
 try:os.fsync(fd)
 finally:os.close(fd)
def inputs():
 files=[*(ROOT/'src').rglob('*'),*(ROOT/'scripts/v8').glob('cubic_unknown*'),ROOT/FOCUS,ROOT/'build.gradle',ROOT/'settings.gradle',ROOT/'gradle.properties']
 return {str(p.relative_to(ROOT)):sha(p) for p in sorted(files) if p.is_file()}
def run(name,argv):
 env=os.environ.copy();env.update(JAVA_HOME='/workspace/jdk21-complete',GRADLE_USER_HOME='/workspace/.gradle');env['PATH']=env['JAVA_HOME']+'/bin:'+env['PATH']
 receipt(name+'-start.json',dict(argv=argv,cwd=str(ROOT),nanoTime=time.monotonic_ns()))
 with (CASE/(name+'.log')).open('xb') as log:
  p=subprocess.Popen(argv,cwd=ROOT,env=env,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT);code=p.wait()
  receipt(name+'-child-close.json',dict(pid=p.pid,actualChildExit=code,nanoTime=time.monotonic_ns()));log.flush();os.fsync(log.fileno())
 return code
def xmlcheck(path,expected):
 b=path.read_bytes();tree=ET.fromstring(b);cases=list(tree.iter('testcase'));failures=list(tree.iter('failure'))+list(tree.iter('error'));assert len(cases)==expected and not failures,(path,len(cases),len(failures))
 with (CASE/path.name).open('xb') as f:f.write(b)
 return dict(path=str(path),sha256=sha(path),tests=len(cases),failures=0)
def main():
 assert not any((CASE/n).exists() for n in ['before.json','units-child-close.json','stock-child-close.json','final-close.json','wrapper-exception.json'])
 assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()==SOURCE
 assert not subprocess.check_output(['git','status','--porcelain','--','src',FOCUS,'scripts/v8/cubic_unknown*','build.gradle','settings.gradle','gradle.properties'],cwd=ROOT)
 unit=ROOT/'build/test-results/test/TEST-com.terminaldetector.drmd.world.compat.NativeDeferredFluidsTest.xml'
 stock=ROOT/'build/deferred-fluid-stock-control/reports/gametest-results.xml'
 assert not unit.exists() and not stock.exists() and not (stock.parents[1]/'world/level.dat').exists()
 initial=inputs();receipt('before.json',dict(sourceSha=SOURCE,inputs=initial,wrapperSha256=sha(Path(__file__)),focusHelperSha256=sha(ROOT/FOCUS),nativeNotAuthorized=True,nanoTime=time.monotonic_ns()))
 (CASE/'actual-helper-snapshots').mkdir()
 for p in [Path(__file__),ROOT/FOCUS]:
  (CASE/'actual-helper-snapshots'/p.name).write_bytes(p.read_bytes())
 code=run('units',['./gradlew','test','--tests','com.terminaldetector.drmd.world.compat.NativeDeferredFluidsTest','--offline','--no-daemon'])
 results={}
 if code==0:
  results['units']=xmlcheck(unit,19)
  code=run('stock',['./gradlew','runGametest','--offline','--no-daemon','-I',FOCUS])
  generated=ROOT/'build/resources/gametest/fabric.mod.json';original=ROOT/'src/gametest/resources/fabric.mod.json'
  if generated.exists():
   actual=generated.read_bytes();(CASE/'actual-executed-gametest-manifest.json').write_bytes(actual)
   data=json.loads(actual)
   if data.get('entrypoints',{}).get('fabric-gametest')==['com.terminaldetector.drmd.world.compat.NativeDeferredFluidStockGameTests']:
    generated.write_bytes(original.read_bytes())
    receipt('generated-resource-cleanup.json',dict(executedManifestSha256=hashlib.sha256(actual).hexdigest(),restoredGeneratedManifestFromTrackedSource=True,restoredSha256=sha(generated),trackedSourceUnchanged=True))
  if code==0:results['stock']=xmlcheck(stock,2)
 assert inputs()==initial and subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()==SOURCE
 receipt('final-close.json',dict(sourceSha=SOURCE,actualFinalChildExit=code,results=results,sourceHelpersUnchanged=True,nativeNotRun=True,wrapperFinalCode=code,nanoTime=time.monotonic_ns()))
 print(json.dumps(dict(sourceSha=SOURCE,actualFinalChildExit=code,results=results,sourceHelpersUnchanged=True,nativeNotRun=True,wrapperFinalCode=code)),flush=True);return code
if __name__=='__main__':
 try:code=main()
 except BaseException:
  t=traceback.format_exc()
  try:receipt('wrapper-exception.json',dict(traceback=t,wrapperFinalCode=97))
  finally:print(t,file=sys.stderr,flush=True)
  code=97
 sys.exit(code)
