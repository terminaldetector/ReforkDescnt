#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mode="$1";scenario="$2"
[[ "$mode" == cold-writer || "$mode" == cold-reader ]]
[[ "$scenario" =~ ^cubic-fluid-cold-[a-z0-9-]+$ ]]
mkdir -p work/cubic-fluid-cold
python3 scripts/v8/cubic_cold_build_stamp.py check
role="${mode#cold-}"
runtime="build/cubic-fluid-cold-${role}-automation"
final="build/cubic-fluid-cold-${role}-final-${scenario}"
test ! -e "$runtime";test ! -e "$final";test ! -e "work/cubic-fluid-cold/${scenario}.log"
export CUBIC_COLD_MODE="$mode" CUBIC_COLD_SCENARIO="$scenario"
python3 scripts/v8/cubic_cold_prepare.py --server-runtime /workspace/ungravity-v5/world/build/cubic-seed2-gate --runtime "$runtime" --drmd build/libs/drmd-6dof-1.1.35-ufo-dev.1.jar --gate build/libs/drmd-6dof-1.1.35-ufo-dev.1-cubiccheck.jar --bcc /workspace/Refork-audit/work/master-v5/upstream/bettercubicchunks-fabric-0.1.1+mc1.21.1.jar --api /workspace/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.116.15+1.21.1/d25c793833a90cc028c9a61bc4546cd578a70ce1/fabric-api-0.116.15+1.21.1.jar --java /workspace/jdk21-complete/bin/java --copy-test-world --sodium /workspace/ungravity-v5/world/work/gate0/upstream/sodium-fabric-0.8.13+mc1.21.1.jar > "work/cubic-fluid-cold/${scenario}-prepare.log"
display="work/cubic-fluid-cold/${scenario}-display.txt";: > "$display"
/workspace/xvfb/usr/bin/Xvfb -displayfd 3 -screen 0 1280x1024x24 3> "$display" > "work/cubic-fluid-cold/${scenario}-xvfb.log" 2>&1 &
display_pid=$!
trap 'kill "$display_pid" 2>/dev/null || true;wait "$display_pid" 2>/dev/null || true' EXIT
for attempt in {1..100};do kill -0 "$display_pid";if [ -s "$display" ];then break;fi;sleep .1;done
test -s "$display";read -r display_number < "$display"
set +e
DISPLAY=:$display_number LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=3.3 MESA_GLSL_VERSION_OVERRIDE=330 timeout --kill-after=15 --foreground 360 python3 -c 'import os,json,sys;from pathlib import Path;p=Path(sys.argv[1]).resolve();a=json.loads((p/"launch.json").read_text());os.chdir(p);os.execv(a[0],a)' "$runtime" < /dev/null > "work/cubic-fluid-cold/${scenario}.log" 2>&1
stage_code=$?
set -e
echo "$stage_code" > "work/cubic-fluid-cold/${scenario}.exit"
if [ "$stage_code" -ne 0 ];then exit "$stage_code";fi
python3 - "$runtime" "$mode" <<'PY'
from pathlib import Path
import json,sys
r=Path(sys.argv[1]);mode=sys.argv[2];d=json.loads((r/'fluid-unknown-outcome.json').read_text())
assert d['mode']==mode and d['droppedEvents']==0 and not list((r/'crash-reports').glob('*.txt'))
expected='COLD_WRITER_UNKNOWN_STOPPED' if mode=='cold-writer' else 'COLD_READER_ARRIVAL_FLOW_SAVED'
if d['status']!=expected:print('First cold unknown-work outcome:',d['status'],d['reason']);sys.exit(2)
assert list((r/'fluid-unknown-frames').glob('*.png'))
if mode=='cold-reader':
    assert d['fixtureWrites']==0 and d['sourcePlacements']==0 and d['coldServerStarted'] and d['coldPreArrival']
    before=json.loads((r/'cold-reader-before-load.json').read_text());assert before['allSavedFilesEqualBeforeWorldLoad'] and before['onlyOmittedFile']=='session.lock'
    s=d['stoppingWitness'];assert s['sourceFull'] and s['neighbourFull'] and s['bottomFull'] and s['sourceWater'] and s['targetWater'] and s['actualFullWaterTickWitnesses']>0
    assert s['client']['targetWater'] and s['gold']==s['client']['gold'] and s['diamond']==s['client']['diamond']
    assert 'minecraft:gold_block' in s['gold'] and 'minecraft:diamond_block' in s['diamond']
    assert any(e['event']=='scheduled-enter' and e.get('actualFull') and e.get('actualWater') for e in d['events'])
    assert d['requestedClose']['serverTick']-d['arrivalReadyTick']<=400
PY
mv "$runtime" "$final"
if [ "$mode" == cold-writer ];then
    python3 scripts/v8/cubic_cold_proof.py "$final" "$scenario"
fi
