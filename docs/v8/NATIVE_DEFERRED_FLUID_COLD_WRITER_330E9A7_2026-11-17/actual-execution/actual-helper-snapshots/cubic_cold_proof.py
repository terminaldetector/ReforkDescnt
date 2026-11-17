"""Qualify a closed, genuinely UNKNOWN writer; restore only its actual archive for a cold reader."""
from pathlib import Path,PurePosixPath
import fcntl,gzip,hashlib,json,os,sys,tarfile
from cubic_cold_nbt import read_bytes
GAME='60ca40f42a746bd60d77d6ca89679057b375bafea98775f1ab80517b671252d8'
STATE='data/drmd_native_deferred_fluids.dat'
KEY=dict(sx=8,sy=32,sz=8,tx=8,ty=31,tz=8,family='minecraft:water')
def sha(b):return hashlib.sha256(b).hexdigest()
def pending(blob):
    nbt=read_bytes(blob);data=nbt['data'];assert data['schema']==1
    entries=data['entries'];matches=[e for e in entries if all(e.get(k)==v for k,v in KEY.items())]
    assert len(matches)==1 and isinstance(matches[0]['created'],int),('Exact saved unknown water job missing/duplicated',matches)
    return dict(exactKey=matches[0],totalPending=len(entries),decodedActualNbt=nbt,bytes=len(blob),sha256=sha(blob))
def archive_files(archive):
    with gzip.open(archive,'rb') as f:
        while f.read(1024*1024):pass
    result={};state=None
    with tarfile.open(archive,'r:gz') as tf:
        for m in tf:
            p=PurePosixPath(m.name);assert m.isfile() and not p.is_absolute() and '..' not in p.parts and m.name not in result
            b=tf.extractfile(m).read();result[m.name]=dict(bytes=len(b),sha256=sha(b))
            if m.name==STATE:state=b
    assert state is not None
    return result,pending(state)
def qualify(runtime,scenario):
    runtime=Path(runtime).resolve();world=runtime/'saves/Cubic-Gate';out=runtime.parents[1]/'work/cubic-fluid-cold'
    d=json.loads((runtime/'fluid-unknown-outcome.json').read_text());assert d['status']=='COLD_WRITER_UNKNOWN_STOPPED' and d['mode']=='cold-writer'
    assert d['fixtureWrites']==273 and d['sourcePlacements']==1 and d['droppedEvents']==0
    assert d['sourceAdmission']['stableUnknownTicks']>=20 and d['requestedClose']
    for w in [d['qualifiedUnknownEdge']['enter'],d['qualifiedUnknownEdge']['returned'],d['stoppingWitness']]:
        assert w['sourceFull'] and not w['neighbourFull'] and w['neighbourReadUnavailable']
        a=w['admission'];assert not a['sourceWanted'] and not a['anyViewContainsSource'] and a['sourceHaloWantedCount']>0
        assert not a['neighbourWanted'] and not a['anyViewContainsNeighbour']
        assert len(a['source27Wanted'])==len(a['neighbour27Wanted'])==27 and a['wantedQueryCount']==56
    stop=d['stoppingWitness'];assert stop['sourceWater'] and 'minecraft:water' in stop['edge'] and not stop['queuedAvailableWater'] and stop['deferred']['pending']>0
    assert all(abs(a-b)<3 for a,b in zip(stop['playerCoordinates'],[12.5,112,8.5]))
    assert not d.get('coldArrivalRequested') and d.get('arrivalReadyTick')==-1
    assert not list((runtime/'crash-reports').glob('*.txt'))
    inputs=json.loads((runtime/'inputs.json').read_text());assert inputs['candidateJars']['build/libs/drmd-6dof-1.1.35-ufo-dev.1.jar']==GAME
    assert not any(p.is_symlink() for p in world.rglob('*'))
    out.mkdir(parents=True,exist_ok=True);a=out/(scenario+'-writer-world.tar.gz');p=out/(scenario+'-writer-proof.json');assert not a.exists() and not p.exists()
    with (world/'session.lock').open('r+b') as lock:
        fcntl.lockf(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        files={str(f.relative_to(world)):dict(bytes=f.stat().st_size,sha256=sha(f.read_bytes())) for f in sorted(world.rglob('*')) if f.is_file()}
        actual_pending=pending((world/STATE).read_bytes())
        levelPlayer=read_bytes((world/'level.dat').read_bytes())['Data']['Player']
        playerFile=world/'playerdata/9f3fe083-b69b-4dfa-a4cc-261026261026.dat'
        playerNbt=read_bytes(playerFile.read_bytes())
        for savedPlayer in [levelPlayer,playerNbt]:
            assert savedPlayer['Dimension']=='minecraft:overworld'
            assert all(abs(a-b)<3 for a,b in zip(savedPlayer['Pos'],[12.5,112,8.5]))
        savedPose=dict(levelPlayerPosition=levelPlayer['Pos'],playerDatPosition=playerNbt['Pos'],dimension=levelPlayer['Dimension'])
        with tarfile.open(a,'w:gz') as tf:
            for n in files:tf.add(world/n,arcname=n,recursive=False)
        archived,saved_pending=archive_files(a);assert archived==files and saved_pending==actual_pending
        assert all((world/n).stat().st_size==v['bytes'] and sha((world/n).read_bytes())==v['sha256'] for n,v in files.items())
    outcome=runtime/'fluid-unknown-outcome.json'
    proof=dict(scope='Qualified real saved UNKNOWN work; reader not yet run',sourceSha=inputs['sourceSha'],candidateJars=inputs['candidateJars'],originalClosedWorld=str(world),archive=str(a),archiveBytes=a.stat().st_size,archiveSha256=sha(a.read_bytes()),outcome=str(outcome),outcomeSha256=sha(outcome.read_bytes()),allFilesMatch=True,gzipReadThroughEof=True,exclusiveClosedSaveLock=True,files=files,savedUnknownPending=actual_pending,savedHighPose=savedPose,coldReaderNotRun=True)
    p.write_text(json.dumps(proof,indent=2)+'\n');print('Qualified actual savedUNKNOWN writer:',p)
def restore(proof_path,world):
    proof_path=Path(proof_path).resolve();p=json.loads(proof_path.read_text());a=Path(p['archive']);world=Path(world)
    assert p['coldReaderNotRun'] and p['savedUnknownPending']['exactKey'].items()>=KEY.items()
    assert p['candidateJars']['build/libs/drmd-6dof-1.1.35-ufo-dev.1.jar']==GAME
    assert a.stat().st_size==p['archiveBytes'] and sha(a.read_bytes())==p['archiveSha256']
    files,actual=archive_files(a);assert files==p['files'] and actual==p['savedUnknownPending']
    assert not world.exists();world.mkdir(parents=True)
    with tarfile.open(a,'r:gz') as tf:
        for m in tf:
            if m.name=='session.lock':continue
            f=world/m.name;f.parent.mkdir(parents=True,exist_ok=True);f.write_bytes(tf.extractfile(m).read())
    expected={k:v for k,v in files.items() if k!='session.lock'}
    actualFiles={str(f.relative_to(world)):dict(bytes=f.stat().st_size,sha256=sha(f.read_bytes())) for f in sorted(world.rglob('*')) if f.is_file()}
    assert expected==actualFiles and not (world/'session.lock').exists()
    return dict(writerProof=str(proof_path),writerProofSha256=sha(proof_path.read_bytes()),archiveSha256=p['archiveSha256'],onlyOmittedFile='session.lock',allSavedFilesEqualBeforeWorldLoad=True,files=actualFiles,savedUnknownPending=actual,fixtureWrites=0,fixtureReschedules=0)
if __name__=='__main__':qualify(sys.argv[1],sys.argv[2])
