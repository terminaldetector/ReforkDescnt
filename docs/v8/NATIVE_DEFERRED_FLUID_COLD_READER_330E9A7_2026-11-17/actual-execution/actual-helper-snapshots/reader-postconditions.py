"""Read-only postclose checks; separate from the writer. This does not launch Java."""
from pathlib import Path
import sys,json,hashlib
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts/v8'))
from cubic_cold_nbt import read
from cubic_cold_proof import KEY

def check(runtime):
    r=Path(runtime);d=json.loads((r/'fluid-unknown-outcome.json').read_text())
    assert d['status']=='COLD_READER_ARRIVAL_FLOW_SAVED' and d['fixtureWrites']==0 and d['sourcePlacements']==0 and d['droppedEvents']==0
    before=json.loads((r/'cold-reader-before-load.json').read_text())
    assert before['savedUnknownPending']['exactKey'].items()>=KEY.items() and before['fixtureWrites']==before['fixtureReschedules']==0
    assert d['deferred']['resumed']>0, 'No normal deferred handoff recorded after saved UNKNOWN restoration'
    path=r/'saves/Cubic-Gate/data/drmd_native_deferred_fluids.dat';saved=read(path)
    assert saved['data']['schema']==1
    remaining=[e for e in saved['data']['entries'] if all(e.get(k)==v for k,v in KEY.items())]
    assert not remaining,'Completed target key remains in actual saved queue'
    assert d['requestedClose']['serverTick']-d['arrivalReadyTick']<=400
    return dict(scope='Cold reader zero edits; saved exact UNKNOWN key restored then normal future handoff/flow',mode=d['mode'],savedPendingKeyBefore=before['savedUnknownPending']['exactKey'],savedStateAfterSha256=hashlib.sha256(path.read_bytes()).hexdigest(),savedStateAfterDecoded=saved,deferredCounters=d['deferred'],arrivalReadyTick=d['arrivalReadyTick'],passTick=d['requestedClose']['serverTick'],zeroFixtureWrites=True,zeroFixtureReschedules=True)
if __name__=='__main__':print(json.dumps(check(sys.argv[1]),indent=2))
