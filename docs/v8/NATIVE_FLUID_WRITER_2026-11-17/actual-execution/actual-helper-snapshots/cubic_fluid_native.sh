#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mode="$1";scenario="$2"
[[ "$mode" == writer || "$mode" == reader ]]
[[ "$scenario" =~ ^cubic-fluid-${mode}-[a-z0-9-]+$ ]]
mkdir -p work/cubic-fluid
python3 scripts/v8/cubic_fluid_build_stamp.py check
runtime="build/cubic-fluid-${mode}-automation"
final="build/cubic-fluid-${mode}-final-${scenario}"
test ! -e "$final"
if [ -e "$runtime" ]; then test ! -e "build/cubic-fluid-${mode}-before-${scenario}";mv "$runtime" "build/cubic-fluid-${mode}-before-${scenario}";fi
export CUBIC_FLUID_MODE="$mode" CUBIC_FLUID_SCENARIO="$scenario"
if [[ "$mode" == reader ]]; then export CUBIC_FLUID_WRITER_PROOF="$3";fi
python3 scripts/v8/cubic_fluid_prepare.py --server-runtime /workspace/ungravity-v5/world/build/cubic-seed2-gate --runtime "$runtime" --drmd build/libs/drmd-6dof-1.1.35-ufo-dev.1.jar --gate build/libs/drmd-6dof-1.1.35-ufo-dev.1-cubiccheck.jar --bcc /workspace/Refork-audit/work/master-v5/upstream/bettercubicchunks-fabric-0.1.1+mc1.21.1.jar --api /workspace/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.116.15+1.21.1/d25c793833a90cc028c9a61bc4546cd578a70ce1/fabric-api-0.116.15+1.21.1.jar --java /workspace/jdk21-complete/bin/java --copy-test-world --sodium /workspace/ungravity-v5/world/work/gate0/upstream/sodium-fabric-0.8.13+mc1.21.1.jar > "work/cubic-fluid/${scenario}-prepare.log"
display="work/cubic-fluid/${scenario}-display.txt";: > "$display"
/workspace/xvfb/usr/bin/Xvfb -displayfd 3 -screen 0 1280x1024x24 3> "$display" > "work/cubic-fluid/${scenario}-xvfb.log" 2>&1 &
display_pid=$!
trap 'kill "$display_pid" 2>/dev/null || true;wait "$display_pid" 2>/dev/null || true' EXIT
for attempt in {1..100};do kill -0 "$display_pid";if [ -s "$display" ];then break;fi;sleep .1;done
test -s "$display";read -r display_number < "$display"
set +e
DISPLAY=:$display_number LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=3.3 MESA_GLSL_VERSION_OVERRIDE=330 timeout --kill-after=15 --foreground 360 python3 -c 'import os,json,sys;from pathlib import Path;p=Path(sys.argv[1]).resolve();a=json.loads((p/"launch.json").read_text());os.chdir(p);os.execv(a[0],a)' "$runtime" < /dev/null > "work/cubic-fluid/${scenario}.log" 2>&1
stage_code=$?
set -e
echo "$stage_code" > "work/cubic-fluid/${scenario}.exit"
if [ "$stage_code" -ne 0 ];then exit "$stage_code";fi
python3 - "$runtime" "$mode" <<'PY'
from pathlib import Path
import json,sys
r=Path(sys.argv[1]);mode=sys.argv[2];d=json.loads((r/'fluid-pending-outcome.json').read_text());assert d['mode']==mode and d['droppedEvents']==0
assert not list((r/'crash-reports').glob('*.txt'))
expected='WRITER_PENDING_SAVED' if mode=='writer' else 'PASS'
if d['status']!=expected:print('Fluid observer outcome:',d['status'],d['reason']);sys.exit(2)
assert d['actualScheduledEvents'] and d['contextFramesNotPixelAcceptance']
if mode=='writer':
    w=d['stoppingWitness'];assert 0<d['observerFixtureWrites']<320 and w['allRelevantFull'] and w['sourceWater'] and w['seamWater'] and w['targetLoadedAir']
    assert w['queuedFront'] and w['actualFluidWitnessCount']>w['writerWitnessBaseline'] and min(x['y'] for x in w['queuedFront'])<=79
else:
    w=d['finalSnapshot'];assert d['observerFixtureWrites']==0 and w['readerRelevantWitnesses']>0 and w['sourceWater'] and w['targetWater'] and w['client']['targetWater']
assert list((r/'fluid-pending-frames').glob('*.png'))
PY
mv "$runtime" "$final"
if [[ "$mode" == writer ]];then
python3 - "$final" "$scenario" <<'PY'
from pathlib import Path
import hashlib,json,tarfile,gzip,sys
r=Path(sys.argv[1]).resolve();world=r/'saves/Cubic-Gate';scenario=sys.argv[2];out=Path('work/cubic-fluid').resolve()
sha=lambda b:hashlib.sha256(b).hexdigest()
files={str(p.relative_to(world)):{'bytes':p.stat().st_size,'sha256':sha(p.read_bytes())} for p in sorted(world.rglob('*')) if p.is_file()}
assert all(not p.is_symlink() for p in world.rglob('*'))
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
inputs=json.loads((r/'inputs.json').read_text());outcome=r/'fluid-pending-outcome.json'
proof=dict(sourceSha=inputs['sourceSha'],candidateJars=inputs['candidateJars'],originalClosedWorld=str(world),archive=str(a),archiveBytes=a.stat().st_size,archiveSha256=sha(a.read_bytes()),
    outcome=str(outcome),outcomeSha256=sha(outcome.read_bytes()),allFilesMatch=True,gzipReadThroughEof=True,files=files)
p=out/(scenario+'-writer-proof.json');assert not p.exists();p.write_text(json.dumps(proof,indent=2)+'\n')
print('Qualified actual closed writer fixture:',p)
PY
fi
