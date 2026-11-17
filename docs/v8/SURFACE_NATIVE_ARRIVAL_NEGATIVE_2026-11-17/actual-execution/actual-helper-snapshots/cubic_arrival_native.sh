#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
scenario="$1"
[[ "$scenario" =~ ^cubic-surface-arrival-[a-z0-9-]+$ ]]
mkdir -p work/cubic-arrival
python3 scripts/v8/cubic_arrival_build_stamp.py check
test ! -e "build/cubic-surface-arrival-final-${scenario}"
if [ -e build/cubic-surface-arrival-automation ]; then test ! -e "build/cubic-surface-arrival-before-${scenario}"; mv build/cubic-surface-arrival-automation "build/cubic-surface-arrival-before-${scenario}"; fi
CUBIC_ARRIVAL_SCENARIO="$scenario" python3 scripts/v8/cubic_arrival_prepare.py --server-runtime /workspace/ungravity-v5/world/build/cubic-seed2-gate --runtime build/cubic-surface-arrival-automation --drmd build/libs/drmd-6dof-1.1.35-ufo-dev.1.jar --gate build/libs/drmd-6dof-1.1.35-ufo-dev.1-cubiccheck.jar --bcc /workspace/Refork-audit/work/master-v5/upstream/bettercubicchunks-fabric-0.1.1+mc1.21.1.jar --api /workspace/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.116.15+1.21.1/d25c793833a90cc028c9a61bc4546cd578a70ce1/fabric-api-0.116.15+1.21.1.jar --java /workspace/jdk21-complete/bin/java --copy-test-world --sodium /workspace/ungravity-v5/world/work/gate0/upstream/sodium-fabric-0.8.13+mc1.21.1.jar > "work/cubic-arrival/${scenario}-prepare.log"
display_file="work/cubic-arrival/${scenario}-display.txt"
: > "$display_file"
/workspace/xvfb/usr/bin/Xvfb -displayfd 3 -screen 0 1280x1024x24 3> "$display_file" > "work/cubic-arrival/${scenario}-xvfb.log" 2>&1 &
display_pid=$!
trap 'kill "$display_pid" 2>/dev/null || true; wait "$display_pid" 2>/dev/null || true' EXIT
for attempt in {1..100}; do kill -0 "$display_pid"; if [ -s "$display_file" ]; then break; fi; sleep .1; done
test -s "$display_file";read -r display_number < "$display_file"
set +e
DISPLAY=:$display_number LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=3.3 MESA_GLSL_VERSION_OVERRIDE=330 timeout --kill-after=15 --foreground 360 python3 -c 'import os,json; from pathlib import Path; p=Path("build/cubic-surface-arrival-automation").resolve(); a=json.loads((p/"launch.json").read_text());os.chdir(p);os.execv(a[0],a)' < /dev/null > "work/cubic-arrival/${scenario}.log" 2>&1
stage_code=$?
set -e
echo "$stage_code" > "work/cubic-arrival/${scenario}.exit"
if [ "$stage_code" -ne 0 ]; then exit "$stage_code"; fi
python3 - <<'PY'
from pathlib import Path
import json,sys
r=Path('build/cubic-surface-arrival-automation');result=json.loads((r/'surface-arrival-outcome.json').read_text())
assert not list((r/'crash-reports').glob('*.txt'))
if result['status']!='PASS':
    print('No-edit arrival observer outcome:',result['status'],result['reason']);sys.exit(2)
assert result['realNativeBackend'] and result['normalPlayerInterestOnly'] and result['fixtureBlockEdits']==0
assert result['mutationsAfterBaseline']==0 and result['droppedEventCount']==0
assert result['timeline'] and result['actualAcceptedPackets']
assert all((r/'surface-arrival-frames'/(n+'.png')).is_file() for n in ['low-black-before-arrival','final-pass'])
PY
mv build/cubic-surface-arrival-automation "build/cubic-surface-arrival-final-${scenario}"
