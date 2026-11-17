"""One native-only child, exact artifacts, durable early child status before postchecks.

Prepared without executing Java. Invoke with shell exec so there is no trailing
login-shell command after this independently recorded subprocess wait result.
"""
from pathlib import Path
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import traceback
import zipfile

ROOT=Path(__file__).resolve().parents[3]
CASE=Path(__file__).resolve().parent
SOURCE='1281ce8a61e2a7f3e19d7d8aa44a12671445950a'
STAMP=ROOT/'work/cubic-arrival/verified-build.json'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def write_receipt(name,data):
    path=CASE/name
    with path.open('xb') as f:
        f.write((json.dumps(data,indent=2)+'\n').encode());f.flush();os.fsync(f.fileno())
    fd=os.open(CASE,os.O_RDONLY)
    try:os.fsync(fd)
    finally:os.close(fd)
def verify(stamp):
    assert stamp['sourceSha']==SOURCE
    for rel,digest in stamp['inputs'].items():assert sha(ROOT/rel)==digest,rel
    for rel,digest in stamp['jars'].items():
        assert sha(ROOT/rel)==digest,rel
        with zipfile.ZipFile(ROOT/rel) as z:assert z.testzip() is None,rel
def main():
    scenario=sys.argv[1]
    assert re.fullmatch(r'cubic-surface-arrival-[a-z0-9-]+',scenario)
    assert not any((CASE/n).exists() for n in ['before.json','child-started.json','child-close.json','final-close.json','wrapper-exception.json'])
    stamp=json.loads(STAMP.read_text());verify(stamp)
    head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    assert not subprocess.check_output(['git','diff',SOURCE,head,'--','src','scripts/v8/cubic_arrival*','docs/v8/SURFACE_ARRIVAL_FIXTURE_2026-11-17','build.gradle','settings.gradle','gradle.properties'],cwd=ROOT)
    assert not subprocess.check_output(['git','status','--porcelain','--','src','scripts/v8/cubic_arrival*','docs/v8/SURFACE_ARRIVAL_FIXTURE_2026-11-17','build.gradle','settings.gradle','gradle.properties'],cwd=ROOT)
    assert not (ROOT/'build/cubic-surface-arrival-automation').exists()
    assert not (ROOT/('build/cubic-surface-arrival-final-'+scenario)).exists()
    snapshots=CASE/'actual-helper-snapshots';snapshots.mkdir()
    helpers={}
    for p in [Path(__file__),*(ROOT/'scripts/v8').glob('cubic_arrival*'),STAMP]:
        if p.is_file():
            target=snapshots/p.name
            with target.open('xb') as f:f.write(p.read_bytes());f.flush();os.fsync(f.fileno())
            helpers[str(p.relative_to(ROOT))]=sha(p)
    argv=['bash','scripts/v8/cubic_arrival_native.sh',scenario]
    write_receipt('before.json',dict(actualGitHead=head,testedBuildSource=SOURCE,scenario=scenario,cwd=str(ROOT),
        argv=argv,wrapperArgv=sys.argv,wrapperSha256=sha(Path(__file__)),helperSnapshots=helpers,
        actualBuildStamp=stamp,nanoTime=time.monotonic_ns(),scope='Native only; no rebuild or source/helper edit'))
    with (CASE/'child-stdout-stderr.log').open('xb') as log:
        child=subprocess.Popen(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT)
        write_receipt('child-started.json',dict(pid=child.pid,argv=argv,nanoTime=time.monotonic_ns()))
        code=child.wait()
        # This independent wait result is written BEFORE any postcheck can fail.
        write_receipt('child-close.json',dict(actualSubprocessReturnCode=code,pid=child.pid,nanoTime=time.monotonic_ns()))
        log.flush();os.fsync(log.fileno())
    verify(stamp)
    assert subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()==head
    assert all(sha(ROOT/rel)==digest for rel,digest in helpers.items())
    native_exit=ROOT/('work/cubic-arrival/'+scenario+'.exit')
    runtime=ROOT/('build/cubic-surface-arrival-final-'+scenario) if code==0 else ROOT/'build/cubic-surface-arrival-automation'
    outcome=runtime/'surface-arrival-outcome.json'
    result=json.loads(outcome.read_text()) if outcome.is_file() else None
    receipt=dict(actualNativeHelperReturnCode=code,actualNativeProcessExit=int(native_exit.read_text()) if native_exit.exists() else None,
        originalOutcome=result,sourceHelpersAndArtifactHashesUnchanged=True,actualGitHead=head,nanoTime=time.monotonic_ns())
    if code==0:
        assert result is not None and result['status']=='PASS'
        assert receipt['actualNativeProcessExit']==0
    receipt['wrapperFinalCode']=code if code>=0 else 128-code
    write_receipt('final-close.json',receipt)
    print(json.dumps({'nativeHelperReturnCode':code,'nativeProcessExit':receipt['actualNativeProcessExit'],
        'outcome':None if result is None else result['status'],'finalReceipt':str(CASE/'final-close.json')},indent=2),flush=True)
    return receipt['wrapperFinalCode']

if __name__=='__main__':
    try:final_code=main()
    except BaseException:
        detail=traceback.format_exc()
        try:write_receipt('wrapper-exception.json',dict(traceback=detail,nanoTime=time.monotonic_ns(),wrapperFinalCode=97))
        finally:print(detail,file=sys.stderr,flush=True)
        final_code=97
    sys.exit(final_code)
