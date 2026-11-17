#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
scenario="$1"
[[ "$scenario" =~ ^cubic-fluid-unknown-writer-[a-z0-9-]+$ ]]
mkdir -p work/cubic-fluid-unknown
python3 scripts/v8/cubic_unknown_build_stamp.py check
runtime="build/cubic-fluid-unknown-writer-automation"
final="build/cubic-fluid-unknown-writer-final-${scenario}"
test ! -e "$final"
test ! -e "work/cubic-fluid-unknown/${scenario}.log"
if [ -e "$runtime" ];then test ! -e "build/cubic-fluid-unknown-writer-before-${scenario}";mv "$runtime" "build/cubic-fluid-unknown-writer-before-${scenario}";fi
export CUBIC_UNKNOWN_SCENARIO="$scenario"
python3 scripts/v8/cubic_unknown_prepare.py --server-runtime /workspace/ungravity-v5/world/build/cubic-seed2-gate --runtime "$runtime" --drmd build/libs/drmd-6dof-1.1.35-ufo-dev.1.jar --gate build/libs/drmd-6dof-1.1.35-ufo-dev.1-cubiccheck.jar --bcc /workspace/Refork-audit/work/master-v5/upstream/bettercubicchunks-fabric-0.1.1+mc1.21.1.jar --api /workspace/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.116.15+1.21.1/d25c793833a90cc028c9a61bc4546cd578a70ce1/fabric-api-0.116.15+1.21.1.jar --java /workspace/jdk21-complete/bin/java --copy-test-world --sodium /workspace/ungravity-v5/world/work/gate0/upstream/sodium-fabric-0.8.13+mc1.21.1.jar > "work/cubic-fluid-unknown/${scenario}-prepare.log"
display="work/cubic-fluid-unknown/${scenario}-display.txt";: > "$display"
/workspace/xvfb/usr/bin/Xvfb -displayfd 3 -screen 0 1280x1024x24 3> "$display" > "work/cubic-fluid-unknown/${scenario}-xvfb.log" 2>&1 &
display_pid=$!
trap 'kill "$display_pid" 2>/dev/null || true;wait "$display_pid" 2>/dev/null || true' EXIT
for attempt in {1..100};do kill -0 "$display_pid";if [ -s "$display" ];then break;fi;sleep .1;done
test -s "$display";read -r display_number < "$display"
set +e
DISPLAY=:$display_number LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=3.3 MESA_GLSL_VERSION_OVERRIDE=330 timeout --kill-after=15 --foreground 360 python3 -c 'import os,json,sys;from pathlib import Path;p=Path(sys.argv[1]).resolve();a=json.loads((p/"launch.json").read_text());os.chdir(p);os.execv(a[0],a)' "$runtime" < /dev/null > "work/cubic-fluid-unknown/${scenario}.log" 2>&1
stage_code=$?
set -e
echo "$stage_code" > "work/cubic-fluid-unknown/${scenario}.exit"
if [ "$stage_code" -ne 0 ];then exit "$stage_code";fi
python3 - "$runtime" <<'PY'
from pathlib import Path
import json,sys
r=Path(sys.argv[1]);d=json.loads((r/'fluid-unknown-outcome.json').read_text());assert d['mode']=='writer' and d['droppedEvents']==0
assert not list((r/'crash-reports').glob('*.txt'))
if d['status']!='WRITER_UNKNOWN_EDGE_ARRIVAL_SAVED':print('First unknown-neighbour observer outcome:',d['status'],d['reason']);sys.exit(2)
assert d['sourcePlacements']==1 and d['fixtureWrites']==273 and d['contextFramesNotPixelAcceptance']
assert d['dryLoadedWitness'] and d['sourceAdmission']['stableUnknownTicks']>=20
for w in [d['qualifiedUnknownEdge']['enter'],d['qualifiedUnknownEdge']['returned']]:
    assert w['sourceFull'] and not w['neighbourFull'] and w['neighbourReadUnavailable']
    assert w['admission']['sourceWanted'] and not w['admission']['neighbourWanted'] and not w['admission']['anyViewContainsNeighbour']
w=d['stoppingWitness'];assert w['sourceFull'] and w['neighbourFull'] and w['bottomFull'] and w['sourceWater'] and w['targetWater'] and w['postEdgeWitnesses']>0 and w['client']['targetWater']
assert 'minecraft:gold_block' in w['gold'] and 'minecraft:diamond_block' in w['diamond'] and w['gold']==w['client']['gold'] and w['diamond']==w['client']['diamond']
assert list((r/'fluid-unknown-frames').glob('*.png'))
PY
mv "$runtime" "$final"
python3 - "$final" "$scenario" <<'PY'
from pathlib import Path
import hashlib,json,tarfile,gzip,sys
r=Path(sys.argv[1]).resolve();world=r/'saves/Cubic-Gate';scenario=sys.argv[2];out=Path('work/cubic-fluid-unknown').resolve()
sha=lambda b:hashlib.sha256(b).hexdigest()
files={str(p.relative_to(world)):{'bytes':p.stat().st_size,'sha256':sha(p.read_bytes())} for p in sorted(world.rglob('*')) if p.is_file()}
assert not any(p.is_symlink() for p in world.rglob('*'))
a=out/(scenario+'-writer-world.tar.gz');assert not a.exists()
with tarfile.open(a,'w:gz') as tf:
    for n in files:tf.add(world/n,arcname=n,recursive=False)
with gzip.open(a,'rb') as g:
    while g.read(1024*1024):pass
actual={}
with tarfile.open(a,'r:gz') as tf:
    for m in tf:
        assert m.isfile();b=tf.extractfile(m).read();actual[m.name]={'bytes':len(b),'sha256':sha(b)}
assert actual==files
inputs=json.loads((r/'inputs.json').read_text());outcome=r/'fluid-unknown-outcome.json'
proof=dict(sourceSha=inputs['sourceSha'],candidateJars=inputs['candidateJars'],originalClosedWorld=str(world),archive=str(a),archiveBytes=a.stat().st_size,archiveSha256=sha(a.read_bytes()),outcome=str(outcome),outcomeSha256=sha(outcome.read_bytes()),allFilesMatch=True,gzipReadThroughEof=True,files=files,coldReaderNotRun=True)
p=out/(scenario+'-writer-proof.json');assert not p.exists();p.write_text(json.dumps(proof,indent=2)+'\n')
print('Qualified unknown-neighbour closed writer:',p,'; cold reader requires separate review/lease')
PY
