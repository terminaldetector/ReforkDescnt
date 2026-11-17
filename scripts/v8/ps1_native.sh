#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
scenario="$1"
[[ "$scenario" =~ ^ps1-[a-z0-9-]+$ ]]
mkdir -p work/ps1
python3 scripts/v8/ps1_build_stamp.py check
test ! -e "build/cubic-client-final-${scenario}"
if [ -e build/cubic-client-automation ]; then test ! -e "build/cubic-client-before-${scenario}"; mv build/cubic-client-automation "build/cubic-client-before-${scenario}"; fi
args=()
if [[ "$scenario" != *gl32* ]]; then args+=(--sodium /workspace/ungravity-v5/world/work/gate0/upstream/sodium-fabric-0.8.13+mc1.21.1.jar); fi
PS1_SCENARIO="$scenario" python3 scripts/v8/ps1_prepare.py --server-runtime /workspace/ungravity-v5/world/build/cubic-seed2-gate --runtime build/cubic-client-automation --drmd build/libs/drmd-6dof-1.1.35-ufo-dev.1.jar --gate build/libs/drmd-6dof-1.1.35-ufo-dev.1-cubiccheck.jar --bcc /workspace/Refork-audit/work/master-v5/upstream/bettercubicchunks-fabric-0.1.1+mc1.21.1.jar --api /workspace/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.116.15+1.21.1/d25c793833a90cc028c9a61bc4546cd578a70ce1/fabric-api-0.116.15+1.21.1.jar --java /workspace/jdk21-complete/bin/java --copy-test-world "${args[@]}" > "work/ps1/${scenario}-prepare.log"
display_file="work/ps1/${scenario}-display.txt"
: > "$display_file"
/workspace/xvfb/usr/bin/Xvfb -displayfd 3 -screen 0 1280x1024x24 3> "$display_file" > "work/ps1/${scenario}-xvfb.log" 2>&1 &
display_pid=$!
trap 'kill "$display_pid" 2>/dev/null || true; wait "$display_pid" 2>/dev/null || true' EXIT
for attempt in {1..100}; do kill -0 "$display_pid"; if [ -s "$display_file" ]; then break; fi; sleep .1; done
test -s "$display_file"
read -r display_number < "$display_file"
if [[ "$scenario" == *gl32* ]]; then export MESA_GL_VERSION_OVERRIDE=3.2 MESA_GLSL_VERSION_OVERRIDE=150; else export MESA_GL_VERSION_OVERRIDE=3.3 MESA_GLSL_VERSION_OVERRIDE=330; fi
set +e
DISPLAY=:$display_number LIBGL_ALWAYS_SOFTWARE=1 timeout --kill-after=15 --foreground 360 python3 -c 'import os,json; from pathlib import Path; p=Path("build/cubic-client-automation").resolve(); a=json.loads((p/"launch.json").read_text()); os.chdir(p);os.execv(a[0],a)' < /dev/null > "work/ps1/${scenario}.log" 2>&1
stage_code=$?
set -e
echo "$stage_code" > "work/ps1/${scenario}.exit"
if [ "$stage_code" -ne 0 ]; then exit "$stage_code"; fi
python3 - <<'PY'
from pathlib import Path
import json
r=Path('build/cubic-client-automation');args=json.loads((r/'launch.json').read_text())
assert not list((r/'crash-reports').glob('*.txt'))
if '-Ddrmd.gate.skyLayers=true' in args:
    marker=json.loads((r/'sky-layers-ok.json').read_text());assert len(marker['poses'])==3 and marker['allPoseFramesPreserved'] and marker['sourceLayersUnchanged'] and marker['allGlZero'] and marker['skyPass'] and marker['lodRetentionPass']
else:
    marker=json.loads((r/'presentation-ok.json').read_text())
    assert len(marker['scenes'])==6 and marker['exceptionTargetsRestored'] and marker['actualWindowResizeAndRestore']
    assert all((r/'presentation-frames'/(s['scene']+'.png')).is_file() for s in marker['scenes'])
    if '-Ddrmd.gate.viewmodel=true' in args:assert (r/'viewmodel-proof/viewmodel-visibility.json').is_file(),'viewmodel observer did not establish visible actual geometry'
PY
mv build/cubic-client-automation "build/cubic-client-final-${scenario}"
