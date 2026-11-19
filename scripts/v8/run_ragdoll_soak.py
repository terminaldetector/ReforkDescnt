#!/usr/bin/env python3
"""Root-lease-only ordinary TestServer elapsed soak and actual same-world cold proof.
A rehearsal produces SHORT_COLD_PROOF_PASS, never TWO_HOUR acceptance. No retries.
"""
import argparse, contextlib, datetime, fcntl, gzip, hashlib, json, os, pathlib, signal, struct, subprocess, sys, time, uuid, xml.etree.ElementTree as ET, zlib
P=pathlib.Path
MAX_NBT=8*1024*1024

def sha(path):
    h=hashlib.sha256()
    with open(path,'rb') as f:
        for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
    return h.hexdigest()
def save(path,value):
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,indent=2,sort_keys=True)+'\n')
def need(ok,message):
    if not ok:raise RuntimeError(message)
def free(path):return os.statvfs(path).f_bavail*os.statvfs(path).f_frsize

class NBT:
    def __init__(self,data):self.data=data;self.at=0;self.nodes=0
    def take(self,n):
        need(0<=n<=MAX_NBT and self.at+n<=len(self.data),'invalid/truncated NBT length');r=self.data[self.at:self.at+n];self.at+=n;return r
    def num(self,fmt):return struct.unpack('>'+fmt,self.take(struct.calcsize('>'+fmt)))[0]
    def string(self):return self.take(self.num('H')).decode('utf8')
    def value(self,t,depth=0):
        need(depth<64,'NBT depth cap');self.nodes+=1;need(self.nodes<500000,'NBT node cap');start=self.at
        if t in (1,2,3,4,5,6):v=self.num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
        elif t==8:v=self.string()
        elif t in (7,11,12):
            n=self.num('i');need(0<=n<=MAX_NBT,'NBT array cap');v=[self.num({7:'b',11:'i',12:'q'}[t]) for _ in range(n)]
        elif t==9:
            kind=self.num('B');n=self.num('i');need(0<=n<=500000,'NBT list cap');v=[self.value(kind,depth+1) for _ in range(n)]
        elif t==10:
            v={}
            while True:
                kind=self.num('B')
                if kind==0:break
                key=self.string();need(key not in v,'duplicate compound key');v[key]=self.value(kind,depth+1)
        else:raise RuntimeError('unsupported NBT type '+str(t))
        return {'type':t,'value':v,'raw':self.data[start:self.at]}
    def root(self):
        need(len(self.data)<=MAX_NBT,'NBT document cap');need(self.num('B')==10,'compound root required');self.string();r=self.value(10);need(self.at==len(self.data),'NBT trailing bytes');return r

def val(node,key,default=None):return node['value'].get(key,{'value':default})['value']
def uid(node,key='UUID'):
    a=val(node,key);need(isinstance(a,list) and len(a)==4,'actual int-array UUID required');return str(uuid.UUID(int=sum((int(x)&0xffffffff)<<(32*(3-i)) for i,x in enumerate(a))))
def inflate(data,kind):
    if kind==1:
        with gzip.GzipFile(fileobj=__import__('io').BytesIO(data)) as f:r=f.read(MAX_NBT+1)
    elif kind==2:
        d=zlib.decompressobj();r=d.decompress(data,MAX_NBT+1);need(not d.unconsumed_tail and d.eof,'zlib size/truncation cap')
    elif kind==3:r=data
    else:raise RuntimeError('unsupported Anvil compression '+str(kind))
    need(len(r)<=MAX_NBT,'inflated NBT cap');return r

def exact_saved_physical_ownership(world,corpses,roofs):
    """Read-only exact fixture admission. Caller holds closed-world session locks.
    Ambient ItemEntities never participate in these physical owner sets.
    """
    physical={'drmd:ragdoll':set(),'drmd:block_body':set()};seen=set();regions=[]
    for path in sorted(world.rglob('entities/*.mca')):
        before=path.stat();b=path.read_bytes()
        if not b:
            after=path.stat();need((before.st_ino,before.st_size,before.st_mtime_ns)==(after.st_ino,after.st_size,after.st_mtime_ns),'empty physical region mutated during read');regions.append({'path':str(path.relative_to(world)),'bytes':0,'sha256':hashlib.sha256(b).hexdigest(),'inode':before.st_ino,'mtimeNs':before.st_mtime_ns,'emptyEntityRegion':True});continue
        need(len(b)>=8192,'nonzero truncated physical-owner Anvil header')
        for i in range(1024):
            offset=int.from_bytes(b[i*4:i*4+3],'big')*4096;sectors=b[i*4+3]
            if not offset:continue
            need(offset>=8192 and offset+5<=len(b),'physical-owner chunk address');n=int.from_bytes(b[offset:offset+4],'big');need(1<n<=sectors*4096-4 and offset+4+n<=len(b),'physical-owner chunk length')
            kind=b[offset+4];need(not kind&128,'physical-owner external entity chunk unsupported')
            tree=NBT(inflate(b[offset+5:offset+4+n],kind)).root()
            for entity in val(tree,'Entities',[]):
                kind=val(entity,'id')
                if kind not in physical:continue
                identity=uid(entity);need(identity not in seen,'duplicate durable physical UUID '+identity);seen.add(identity);physical[kind].add(identity)
        after=path.stat();need((before.st_ino,before.st_size,before.st_mtime_ns)==(after.st_ino,after.st_size,after.st_mtime_ns),'closed physical region mutated during read')
        regions.append({'path':str(path.relative_to(world)),'bytes':len(b),'sha256':hashlib.sha256(b).hexdigest()})
    result={'schema':1,'expectedCorpses':sorted(corpses),'actualCorpses':sorted(physical['drmd:ragdoll']),'expectedRoofs':sorted(roofs),'actualRoofs':sorted(physical['drmd:block_body']),'regions':regions,'ambientItems':'excluded from exact physical owner sets'}
    result['pass']=physical['drmd:ragdoll']==set(corpses) and physical['drmd:block_body']==set(roofs)
    return result

def closed_input(runtime,attempt):
    world=runtime/'world';need((world/'level.dat').is_file(),'original saved level.dat required')
    with contextlib.ExitStack() as stack:
        for lock in sorted(world.rglob('session.lock')):
            f=stack.enter_context(open(lock,'r+b'));fcntl.lockf(f,fcntl.LOCK_EX|fcntl.LOCK_NB)
        proof=world/'data/drmd_ragdoll_soak_proof.dat';need(proof.is_file(),'actual PersistentState save required')
        with gzip.open(proof,'rb') as f:raw=f.read(MAX_NBT+1)
        root=NBT(raw).root();state=root['value'].get('data',root)
        need(val(state,'Schema')==1 and str(val(state,'Status')).endswith('PREPARE_PASS'),'real prior prepare receipt')
        roofs=set();corpses=set();floors=[]
        for row in val(state,'Rows',[]):
            if 'Roof' in row['value']:roofs.add(uid(row,'Roof'))
            for body in val(row,'ColdBodies',[]):corpses.add(uid(body,'Id'))
            floors.append(val(row,'CohortFloor'))
        need(len(corpses)==2,'exact two actual TNT corpse UUIDs from saved receipt');wanted=roofs|corpses;found={};items={};chunks=0;empty_regions=[]
        for region in sorted(world.rglob('entities/*.mca')):
            before=region.stat();b=region.read_bytes()
            if not b:
                after=region.stat();need((before.st_ino,before.st_size,before.st_mtime_ns)==(after.st_ino,after.st_size,after.st_mtime_ns),'empty entity region mutated during read');empty_regions.append({'path':str(region.relative_to(world)),'bytes':0,'sha256':hashlib.sha256(b).hexdigest(),'inode':before.st_ino,'mtimeNs':before.st_mtime_ns});continue
            need(len(b)>=8192,'nonzero truncated Anvil header required')
            for i in range(1024):
                offset=int.from_bytes(b[i*4:i*4+3],'big')*4096;sectors=b[i*4+3]
                if not offset:continue
                need(offset>=8192 and offset+5<=len(b),'Anvil chunk address');n=int.from_bytes(b[offset:offset+4],'big');need(1<n<=sectors*4096-4 and offset+4+n<=len(b),'Anvil chunk length')
                kind=b[offset+4];need(not kind&128,'external entity chunk explicitly unsupported, no invented fallback')
                tree=NBT(inflate(b[offset+5:offset+4+n],kind)).root();chunks+=1
                for entity in val(tree,'Entities',[]):
                    eid=uid(entity)
                    if eid in wanted:
                        need(eid not in found,'duplicate saved ownership '+eid);found[eid]=entity
                    elif val(entity,'id')=='minecraft:item':
                        # Include actual saved drops from the final retained cohort, bounded, not reconstructed.
                        pos=val(entity,'Pos',[]);floor=floors[-1]
                        x=floor>>38;z=(floor>>12)&0x3ffffff
                        if z>=0x2000000:z-=0x4000000
                        if len(pos)==3 and x-64<=pos[0]['value']<=x+64 and z-64<=pos[2]['value']<=z+64:
                            need(eid not in items,'duplicate saved drop UUID');items[eid]=entity
        last_row=val(state,'Rows',[])[-1];expected_drops=last_row['value'].get('ColdDropCounts');need(expected_drops is not None and val(last_row,'ColdDropHorizontalReach')==64,'explicit final cold material domain/count receipt required')
        actual_drops={}
        for entity in items.values():
            stack=entity['value']['Item'];kind=val(stack,'id');amount=val(stack,'count',val(stack,'Count'));need(isinstance(amount,int) and amount>0,'actual saved positive stack count');actual_drops[kind]=actual_drops.get(kind,0)+amount
        need(actual_drops=={k:n['value'] for k,n in expected_drops['value'].items()},'actual saved final-region drop quantities differ from last ordinary prepare receipt: '+str(actual_drops))
        physical=exact_saved_physical_ownership(world,corpses,roofs);save(attempt/'prepare-world-ownership.json',physical)
        need(physical['pass'],'exact closed fixture has missing/extra corpse or roof ownership; prepare-world-ownership.json preserves actual sets')
        need(set(found)==wanted,'saved real entities missing: '+str(sorted(wanted-set(found))))
        selected=sorted(found.items(),key=lambda kv:(0 if kv[0] in corpses else 1,kv[0]))+list(sorted(items.items()));need(0<len(selected)<=512,'cold entity cap512')
        name=b'Entities';payload=b'\x0a\x00\x00'+b'\x09'+struct.pack('>H',len(name))+name+b'\x0a'+struct.pack('>i',len(selected))+b''.join(n['raw'] for _,n in selected)+b'\x00'
        need(len(payload)<=MAX_NBT,'actual saved input8MiB cap')
        output=attempt/'actual-saved-entities.nbt.gz'
        with gzip.GzipFile(filename=str(output),mode='wb',mtime=0) as f:f.write(payload)
        decoded=NBT(gzip.decompress(output.read_bytes())).root();need([uid(x) for x in val(decoded,'Entities')]==[x for x,_ in selected],'lossless saved compound roundtrip')
        before=[]
        for path in sorted(world.rglob('*')):
            if not path.is_file():continue
            st=path.stat();digest=sha(path);after=path.stat();need((st.st_ino,st.st_size,st.st_mtime_ns)==(after.st_ino,after.st_size,after.st_mtime_ns),'closed world mutated during hash')
            before.append({'path':str(path.relative_to(world)),'bytes':st.st_size,'sha256':digest,'inode':st.st_ino,'mtimeNs':st.st_mtime_ns})
        save(attempt/'cold-inputs.json',{'schema':1,'input':str(output),'sha256':sha(output),'entities':[{'uuid':eid,'type':val(n,'id'),'payloadSha256':hashlib.sha256(n['raw']).hexdigest()} for eid,n in selected],'savedRoofs':sorted(roofs),'savedCorpses':sorted(corpses),'savedDrops':len(items),'entityChunks':chunks,'zeroByteEntityRegions':empty_regions,'admissionHelperSha256':sha(__file__),'world':before,'source':val(state,'Source'),'run':uid(state,'Run'),'prepareStatus':val(state,'Status'),'elapsedNanos':val(state,'ElapsedNanos')})
        return output

def validate_xml(path):
    need(path.is_file(),'ordinary fresh XML required');r=ET.parse(path).getroot();cases=r.findall('.//testcase');need(len(cases)==1,'exact one opt-in ordinary TestServer case')
    need(not r.findall('.//failure') and not r.findall('.//error') and not r.findall('.//skipped'),'ordinary first failure retained in XML')

def child(command,cwd,attempt,label,timeout):
    out=attempt/(label+'.log');meta={'argv':command,'cwd':str(cwd),'startedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'timeoutSeconds':timeout}
    with open(out,'xb') as log:
        proc=subprocess.Popen(command,cwd=cwd,stdout=log,stderr=subprocess.STDOUT,start_new_session=True);meta['pid']=proc.pid;save(attempt/(label+'-child.json'),meta);start=time.monotonic();fault=None;last=0
        while proc.poll() is None:
            elapsed=time.monotonic()-start
            if elapsed>timeout:fault='FINITE_TIMEOUT'
            if free(attempt)<64*1024*1024:fault='LOW_DISK_NONPASS'
            if elapsed-last>=30:
                last=elapsed;print(label,'alive',proc.pid,'elapsed',round(elapsed),'free',free(attempt),flush=True)
            java_meta=cwd/'lifecycle-proof/actual-java.json'
            if java_meta.is_file() and not (attempt/(label+'-actual-process.json')).exists():
                info=json.loads(java_meta.read_text());pid=info['pid'];p=P('/proc')/str(pid)
                if p.exists() and (label!='cold' or info.get('mode')=='cold'):save(attempt/(label+'-actual-process.json'),{'pid':pid,'argv':(p/'cmdline').read_bytes().split(b'\0')[:-1] and [(x.decode()) for x in (p/'cmdline').read_bytes().split(b'\0')[:-1]],'executable':os.readlink(p/'exe'),'reported':info})
            if fault:
                os.killpg(proc.pid,signal.SIGTERM)
                try:proc.wait(timeout=10)
                except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait()
                break
            time.sleep(1)
        meta.update(exit=proc.wait(),elapsedSeconds=time.monotonic()-start,finishedUtc=datetime.datetime.now(datetime.timezone.utc).isoformat(),observerFault=fault);save(attempt/(label+'-child.json'),meta)
    need(meta['exit']==0 and fault is None,'first '+label+' child failure: '+str(meta));return meta

def verify_inputs(receipt):
    for row in receipt['inputs']:
        p=P(row['path']);need(p.is_file() and p.stat().st_size==row['bytes'] and sha(p)==row['sha256'],'immutable actual runtime input changed: '+str(p))

def game_command(receipt, jvm):
    return [receipt['executable']]+jvm+['-cp', os.pathsep.join(receipt['classpath']), receipt['mainClass']]+receipt['args']

def main():
    a=argparse.ArgumentParser();a.add_argument('--repo',type=P,default=P('/workspace/ungravity-v5/ufo-body-physics'));a.add_argument('--attempt',type=P,required=True);a.add_argument('--source',required=True);a.add_argument('--mode',choices=['soak','rehearsal'],default='rehearsal');a.add_argument('--phase',choices=['prepare','cold','both'],default='both');a.add_argument('--run-with-root-java-lease',action='store_true');o=a.parse_args()
    need(o.run_with_root_java_lease and os.environ.get('U14_RAGDOLL_SOAK_JAVA_LEASE')=='granted','explicit exclusive root Java lease required')
    need(len(o.source)==40 and all(c in '0123456789abcdef' for c in o.source),'source40');attempt=o.attempt.resolve();runtime=attempt/'runtime'
    if o.phase!='cold':
        need(not attempt.exists(),'fresh attempt required');attempt.mkdir(parents=True);need(free(attempt)>=768*1024*1024,'768MiB initial free space guard')
        actual=subprocess.check_output(['git','rev-parse','HEAD'],cwd=o.repo,text=True).strip();need(actual==o.source,'exact source HEAD required')
        tracked=subprocess.check_output(['git','diff','--name-only','HEAD','--','src','build.gradle','gradle.properties'],cwd=o.repo,text=True);need(not tracked.strip(),'clean pinned source before compile')
        init=P(__file__).with_name('ragdoll_soak.init.gradle').resolve();save(attempt/'proposal.json',{'source':o.source,'mode':o.mode,'driver':sha(__file__),'init':sha(init),'acceptance':'two actual elapsed hours + >=12 real128death/1200tick cohorts + same-world separate JVM cold; rehearsal is SHORT only','workBudgets':'production unchanged;128 live corpses;64 loaded permanent roofs;128 zones;1MiB proof;512 cold entities;8MiB input','mspt':'actual server rolling average only; no hardware/FPS equivalence','worldHandling':'same saved world retained; no deletion, retries, actor reconstruction or manual ticks'})
        command=[str(o.repo/'gradlew'),'--offline','--no-daemon','--max-workers=1','-I',str(init),'-PdrmdLifecycleRuntime='+str(runtime),'-PdrmdLifecycleMode='+o.mode,'-PdrmdLifecycleSource='+o.source,'runGametest']
        child(command,o.repo,attempt,'compile',900)
        receipt=json.loads((attempt/'launch-inputs.json').read_text())
        need(receipt['source']==o.source and receipt['mode']==o.mode,'exact staged game identity')
        verify_inputs(receipt)
        child(game_command(receipt,receipt['allJvmArgs']),runtime,attempt,'prepare',9600 if o.mode=='soak' else 1800)
        validate_xml(runtime/'reports/gametest-results.xml')
        result=json.loads((runtime/'lifecycle-proof/prepare-result.json').read_text());need(result['source']==o.source and result['status']==('TWO_HOUR_SOAK_PREPARE_PASS' if o.mode=='soak' else 'REHEARSAL_ONLY_PREPARE_PASS'),'actual prepare status required')
        verify_inputs(receipt)
        (runtime/'reports/gametest-results.xml').rename(attempt/'prepare-gametest-results.xml')
        save(attempt/'prepare-closed.json',{'actualLastChildClosed':True,'result':result,'launchReceiptSha256':sha(attempt/'launch-inputs.json')})
    if o.phase=='prepare':print('PREPARE CLOSED; cold remains OPEN',flush=True);return
    need((attempt/'prepare-closed.json').is_file(),'first process verified closed');receipt=json.loads((attempt/'launch-inputs.json').read_text());need(receipt['source']==o.source,'same source');verify_inputs(receipt)
    need(not any((attempt/name).exists() for name in ('cold-child.json','cold.log','cold-inputs.json','prepare-world-ownership.json')),'first cold attempt already exists; inputs/failure preserved, no retry')
    saved=closed_input(runtime,attempt)
    original=receipt['allJvmArgs'];jvm=[v for v in original if not v.startswith(('-Ddrmd.lifecycle.mode=','-Ddrmd.lifecycle.savedInput='))]
    # Original actual output directories and launch config were copied BEFORE first server start.
    jvm+=['-Ddrmd.lifecycle.mode=cold','-Ddrmd.lifecycle.savedInput='+str(saved)]
    child(game_command(receipt,jvm),runtime,attempt,'cold',1800);validate_xml(runtime/'reports/gametest-results.xml');verify_inputs(receipt)
    result=json.loads((runtime/'lifecycle-proof/cold-result.json').read_text());need(result['source']==o.source and result['status'] in ('SHORT_COLD_PROOF_PASS','TWO_HOUR_SOAK_AND_COLD_PASS'),'actual cold result')
    # The actual cold server has closed; neither its two expired corpses nor old128 ghosts may persist.
    expected=json.loads((attempt/'cold-inputs.json').read_text())
    with contextlib.ExitStack() as locks:
        for lock in sorted((runtime/'world').rglob('session.lock')):
            f=locks.enter_context(open(lock,'r+b'));fcntl.lockf(f,fcntl.LOCK_EX|fcntl.LOCK_NB)
        physical=exact_saved_physical_ownership(runtime/'world',set(),set(expected['savedRoofs']));save(attempt/'cold-world-ownership.json',physical)
        need(physical['pass'],'actual cold save retained expired/extra corpse or lost/duplicated roof; cold-world-ownership.json preserves actual sets')

    save(attempt/'FINAL.json',{'result':result,'coldInputReceiptSha256':sha(attempt/'cold-inputs.json'),'launchReceiptSha256':sha(attempt/'launch-inputs.json'),'worldRetained':str(runtime/'world'),'actualAllChildrenClosed':True});print(json.dumps(result),flush=True)

if __name__=='__main__':
    try:main()
    except BaseException as e:
        print('FIRST_FAILURE',type(e).__name__,str(e),file=sys.stderr,flush=True);raise
