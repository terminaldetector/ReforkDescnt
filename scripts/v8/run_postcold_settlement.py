#!/usr/bin/env python3
"""Separate test-only two-JVM authored settlement proof in an already cold-qualified world.
No world copying/reset; no legacy generation or runtime physics mutation. Root lease only.
"""
import argparse,contextlib,fcntl,gzip,hashlib,importlib.util,json,os,pathlib,shutil,subprocess
P=pathlib.Path

def audit_final(m,world,attempt):
    from collections import defaultdict,Counter
    with contextlib.ExitStack() as locks:
        for lock in sorted(world.rglob('session.lock')):
            f=locks.enter_context(open(lock,'r+b'));fcntl.lockf(f,fcntl.LOCK_EX|fcntl.LOCK_NB)
        def persistent(name):
            with gzip.open(world/('data/'+name+'.dat'),'rb') as f:root=m.NBT(f.read(m.MAX_NBT+1)).root()
            return root['value'].get('data',root)
        state=persistent('drmd_postcold_settlement_proof');m.need(m.val(state,'Schema')==1 and m.val(state,'Status')=='POST_COLD_SETTLEMENT_BLAST_CLEANUP_PASS' and m.val(state,'Backend')=='STOCK','explicit real post-cold ordinary result before durable audit')
        positions=m.val(state,'PlanPositions');names=m.val(state,'AfterStates');m.need(isinstance(positions,list) and len(positions)==len(names)==m.val(state,'Cells') and 0<len(positions)<=131072,'bounded complete observed material receipt')
        by_chunk=defaultdict(list)
        for packed,name in zip(positions,names):
            x=packed>>38;z=(packed>>12)&0x3ffffff;z=z-(1<<26) if z>=(1<<25) else z;y=packed&4095;y=y-4096 if y>=2048 else y;by_chunk[(x//16,z//16)].append((x,y,z,name['value']))
        region_bytes={};regions={};verified=0
        for (cx,cz),cells in sorted(by_chunk.items()):
            path=world/('region/r.'+str(cx//32)+'.'+str(cz//32)+'.mca')
            if path not in region_bytes:
                if len(region_bytes)>=2:region_bytes.clear()
                before=path.stat();raw=path.read_bytes();after=path.stat();m.need((before.st_ino,before.st_size,before.st_mtime_ns)==(after.st_ino,after.st_size,after.st_mtime_ns),'closed block region mutation');m.need(len(raw)>=8192,'real durable block region header');region_bytes[path]=raw;regions[str(path.relative_to(world))]={'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}
            raw=region_bytes[path];index=(cx&31)+(cz&31)*32;offset=int.from_bytes(raw[index*4:index*4+3],'big')*4096;sectors=raw[index*4+3];m.need(offset>=8192 and offset+5<=len(raw),'actual saved authored chunk address');length=int.from_bytes(raw[offset:offset+4],'big');m.need(1<length<=sectors*4096-4 and offset+4+length<=len(raw),'actual saved authored chunk size');compression=raw[offset+4];m.need(not compression&128,'external authored chunk unsupported');chunk=m.NBT(m.inflate(raw[offset+5:offset+4+length],compression)).root();m.need(m.val(chunk,'Status')=='minecraft:full','durable actual FULL chunk')
            sections={m.val(section,'Y'):section for section in m.val(chunk,'sections',[])}
            for x,y,z,wanted in cells:
                section=sections.get(y//16)
                if section is None or 'block_states' not in section['value']:
                    # In an actual FULL STOCK chunk omitted block storage is authoritative AIR.
                    m.need(wanted=='minecraft:air','missing saved solid authored section');verified+=1;continue
                states=section['value']['block_states'];palette=m.val(states,'palette');m.need(isinstance(palette,list) and len(palette)>0,'saved palette nonempty')
                if len(palette)==1:entry=palette[0]
                else:
                    bits=max(4,(len(palette)-1).bit_length());per_long=64//bits;index=(y&15)*256+(z&15)*16+(x&15);packed=m.val(states,'data');m.need(packed is not None and len(packed)>(index//per_long),'saved palette packed storage');value=(packed[index//per_long]>>((index%per_long)*bits))&((1<<bits)-1);m.need(value<len(palette),'saved blockstate palette index');entry=palette[value]
                m.need(m.val(entry,'Name')==wanted and not m.val(entry,'Properties',{}),'actual durable post-blast cell/state mismatch at '+str((x,y,z)));verified+=1
        prior=persistent('drmd_ragdoll_soak_proof');roofs={m.uid(row,'Roof') for row in m.val(prior,'Rows',[]) if 'Roof' in row['value']};detached={m.uid(n):n for n in m.val(state,'Detached',[])};roofs|=set(detached);physical=m.exact_saved_physical_ownership(world,set(),roofs);m.save(attempt/'postcold-physical-ownership.json',physical);m.need(physical['pass'],'post-cold exact retained roof and no corpse/duplicate ownership')
        birth_ids={m.uid(n,'Id') for n in m.val(state,'DropBirthIds',[])};saved_drops=Counter();saved_bodies={};seen_owned_items=set()
        for path in sorted(world.rglob('entities/*.mca')):
            raw=path.read_bytes()
            if not raw:continue
            m.need(len(raw)>=8192,'nonzero durable entity header')
            for index in range(1024):
                offset=int.from_bytes(raw[index*4:index*4+3],'big')*4096
                if not offset:continue
                m.need(offset>=8192 and offset+5<=len(raw),'durable entity address');length=int.from_bytes(raw[offset:offset+4],'big');m.need(offset+4+length<=len(raw),'durable entity size');tree=m.NBT(m.inflate(raw[offset+5:offset+4+length],raw[offset+4])).root()
                for entity in m.val(tree,'Entities',[]):
                    identity=m.uid(entity)
                    if identity in birth_ids:
                        m.need(identity not in seen_owned_items,'duplicate durable owned Item UUID');seen_owned_items.add(identity)
                        m.need(m.val(entity,'id')=='minecraft:item','welded actual drop identity remains native ItemEntity');stack=entity['value']['Item'];saved_drops[m.val(stack,'id')]+=m.val(stack,'count',m.val(stack,'Count'))
                    if identity in detached:m.need(identity not in saved_bodies,'duplicate detached body UUID');saved_bodies[identity]=entity
        expected={k:n['value'] for k,n in state['value']['DropCounts']['value'].items()};m.need(dict(saved_drops)==expected,'exact durable owned drop quantities; unrelated ambient items excluded')
        def value(node):
            if node['type']==10:return (10,{k:value(n) for k,n in node['value'].items()})
            if node['type']==9:return (9,[value(n) for n in node['value']])
            return (node['type'],node['value'])
        m.need(set(saved_bodies)==set(detached),'every actual detached structural body serialized exactly once')
        for identity,saved in saved_bodies.items():m.need(value(saved['value']['shape'])==value(detached[identity]['value']['shape']),'actual durable detached shape/material cells unchanged')
        groups=persistent('drmd_ragdoll_groups');m.need(m.val(groups,'Schema')==1 and not m.val(groups,'Groups',[]),'durable logical groups cleaned with their actual expired corpses')
        result={'status':'POST_COLD_AUTHORED_SETTLEMENT_DURABLE_PASS','verifiedActualCells':verified,'regions':regions,'retainedRoofs':len(roofs),'newDetachedBodies':len(detached),'ownedSavedDrops':dict(saved_drops),'unexpectedCorpses':0,'logicalGroups':0,'ambientItemPolicy':'not in born UUID receipt: ignored'};m.save(attempt/'durable-material-audit.json',result);return result

def main():
    p=argparse.ArgumentParser();p.add_argument('--repo',type=P,default=P('/workspace/ungravity-v5/ufo-body-physics'));p.add_argument('--existing-runtime',type=P,required=True);p.add_argument('--attempt',type=P,required=True);p.add_argument('--source',required=True);p.add_argument('--run-with-root-java-lease',action='store_true');a=p.parse_args()
    if not(a.run_with_root_java_lease and os.environ.get('POST_COLD_SETTLEMENT_JAVA_LEASE')=='granted'):raise RuntimeError('exclusive root Java lease required')
    if a.attempt.exists():raise RuntimeError('fresh attempt, no automatic retry')
    if not(a.existing_runtime/'world/level.dat').is_file():raise RuntimeError('original existing qualified world required')
    a.attempt.mkdir(parents=True);helper=a.attempt/'frozen-runner-helper.py';shutil.copy2(P(__file__).with_name('run_ragdoll_soak.py'),helper);sp=importlib.util.spec_from_file_location('pinned_soak_helper',helper);m=importlib.util.module_from_spec(sp);sp.loader.exec_module(m)
    m.need(m.free(a.attempt)>=768*1024*1024,'original768MiB workspace guard')
    source=subprocess.check_output(['git','rev-parse','HEAD'],cwd=a.repo,text=True).strip();m.need(source==a.source,'exact source40 at compile')
    m.need(not subprocess.check_output(['git','diff','--name-only','HEAD','--','src','build.gradle','gradle.properties'],cwd=a.repo,text=True).strip(),'clean pinned source')
    world=a.existing_runtime/'world'
    with contextlib.ExitStack() as locks:
        for lock in sorted(world.rglob('session.lock')):
            f=locks.enter_context(open(lock,'r+b'));fcntl.lockf(f,fcntl.LOCK_EX|fcntl.LOCK_NB)
        with gzip.open(world/'data/drmd_ragdoll_soak_proof.dat','rb') as f:prior=m.NBT(f.read(m.MAX_NBT+1)).root()
        state=prior['value'].get('data',prior);m.need(m.val(state,'Status') in ('SHORT_COLD_PROOF_PASS','TWO_HOUR_SOAK_AND_COLD_PASS'),'actual prior cold result required; no fake initialization')
        m.save(a.attempt/'original-context.json',{'source':a.source,'priorSource':m.val(state,'Source'),'priorStatus':m.val(state,'Status'),'priorRun':m.uid(state,'Run'),'sameWorld':str(world.resolve()),'priorProofSha256':m.sha(world/'data/drmd_ragdoll_soak_proof.dat'),'helperSha256':m.sha(helper),'scope':'full authored16lot CITY via normal64CAS/WAL; actual inhabitants; separate cold nativeTNT; original corpsegroups/mass/TTLs unchanged; no 2h or nativeFPS inference'})
    init=P(__file__).with_name('postcold_settlement.init.gradle').resolve();stage=a.attempt/'staging-runtime'
    command=[str(a.repo/'gradlew'),'--offline','--no-daemon','--max-workers=1','-I',str(init),'-PdrmdLifecycleRuntime='+str(stage),'-PdrmdLifecycleMode=rehearsal','-PdrmdLifecycleSource='+a.source,'runGametest']
    m.child(command,a.repo,a.attempt,'compile',900);receipt=json.loads((a.attempt/'launch-inputs.json').read_text());m.verify_inputs(receipt)
    m.need(subprocess.check_output(['git','rev-parse','HEAD'],cwd=a.repo,text=True).strip()==a.source and not subprocess.check_output(['git','diff','--name-only','HEAD','--','src','build.gradle','gradle.properties'],cwd=a.repo,text=True).strip(),'source changed during compile; no mixed launch')
    base=[x for x in receipt['allJvmArgs'] if not x.startswith('-Dfabric-api.gametest.report-file=')]
    for phase in ('place','blast'):
        args=base+['-Ddrmd.postcold.phase='+phase,'-Ddrmd.postcold.source='+a.source,'-Dfabric-api.gametest.report-file='+str((a.attempt/(phase+'-gametest-results.xml')).resolve())]
        m.child(m.game_command(receipt,args),a.existing_runtime,a.attempt,phase,3600);m.validate_xml(a.attempt/(phase+'-gametest-results.xml'));m.verify_inputs(receipt)
        result=json.loads((a.existing_runtime/('lifecycle-proof/postcold-'+phase+'-result.json')).read_text());m.need(result['source']==a.source and result['status']==('POST_COLD_SETTLEMENT_SAVE_PASS' if phase=='place' else 'POST_COLD_SETTLEMENT_BLAST_CLEANUP_PASS'),'exact real phase outcome');m.save(a.attempt/(phase+'-result.json'),result)
        # Preserve the actual durable state before the next JVM can change its status.
        proof=world/'data/drmd_postcold_settlement_proof.dat';m.need(proof.is_file(),'actual durable authored settlement proof');shutil.copy2(proof,a.attempt/(phase+'-persistent-state.nbt.gz'))
    audit=audit_final(m,world,a.attempt)
    m.save(a.attempt/'FINAL.json',{'status':'POST_COLD_AUTHORED_SETTLEMENT_DURABLE_PASS','source':a.source,'sameWorld':str(world.resolve()),'place':json.loads((a.attempt/'place-result.json').read_text()),'blast':result,'durableMaterialAudit':audit,'scope':'actual manual fullCITY; no natural/procedural city or native performance claim','allActualChildrenClosed':True})

if __name__=='__main__':main()
