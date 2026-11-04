#!/usr/bin/env bash
set -euo pipefail
cd /workspace/ungravity-v5/fragment-boarding
scenario="$1"
test ! -e "build/cubic-client-final-${scenario}"
if [ -e build/cubic-client-automation ]; then
  mv build/cubic-client-automation "build/cubic-client-before-${scenario}"
fi
args=()
if [[ "$scenario" != *gl32* ]]; then
  args+=(--sodium /workspace/ungravity-v5/world/work/gate0/upstream/sodium-fabric-0.8.13+mc1.21.1.jar)
fi
python3 scripts/v5/prepare_cubic_client.py --server-runtime /workspace/ungravity-v5/world/build/cubic-seed2-gate --runtime build/cubic-client-automation --drmd build/libs/drmd-6dof-1.1.34-joints.1.jar --gate build/libs/drmd-6dof-1.1.34-joints.1-cubiccheck.jar --bcc /workspace/Refork-audit/work/master-v5/upstream/bettercubicchunks-fabric-0.1.1+mc1.21.1.jar --api /workspace/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/fabric-api/0.116.15+1.21.1/d25c793833a90cc028c9a61bc4546cd578a70ce1/fabric-api-0.116.15+1.21.1.jar --java /workspace/jdk21-complete/bin/java --copy-test-world "${args[@]}" > "work/body-joints/native/final-${scenario}-prepare.log"
python3 work/body-joints/native/dedup-staged-libraries.py "$scenario"
python3 - "$scenario" <<'PY'
import sys,json,pathlib,subprocess
scenario=sys.argv[1];root=pathlib.Path('build/cubic-client-automation')
p=root/'launch.json';a=json.loads(p.read_text());a.insert(1,'-Ddrmd.gate.joints=true' if 'joints' in scenario else '-Ddrmd.gate.bodyIndex=true' if 'body-index' in scenario or 'body-scale' in scenario else '-Ddrmd.gate.bodyEdit=true' if 'body-edit' in scenario else '-Ddrmd.gate.shipGravity=true' if 'ship' in scenario else '-Ddrmd.gate.boarding=true' if 'boarding' in scenario else '-Ddrmd.gate.snapshot=true' if 'snapshot' in scenario else '-Ddrmd.gate.contacts=true' if 'contacts' in scenario or 'fracture' in scenario else '-Ddrmd.gate.gun=true' if 'gun' in scenario else '-Ddrmd.gate.actors=true' if 'actors' in scenario else '-Ddrmd.gate.terrain=true' if 'baseline' in scenario else '-Ddrmd.gate.mirrors=true');
if 'fragment-boarding' in scenario:a.insert(1,'-Ddrmd.gate.fragmentBoarding=true')
if 'fracture' in scenario:a.insert(1,'-Ddrmd.gate.fracture=true')
if 'body-scale' in scenario:a.insert(1,'-Ddrmd.gate.bodyScale=true');a.insert(1,'-Ddrmd.gate.shapeDelayTicks=30')
p.write_text(json.dumps(a,indent=2))

if 'fabulous' in scenario:
    opts=root/'options.txt'; content=opts.read_text(); import re; content=re.sub(r'(?m)^graphicsMode:.*$', 'graphicsMode:2', content) if re.search(r'(?m)^graphicsMode:',content) else content+'\ngraphicsMode:2\n';opts.write_text(content)
p=root/'inputs.json';a=json.loads(p.read_text());a['sourceSha']=subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip();a['scenario']=scenario;a['glOverride']='3.2/150' if 'gl32' in scenario else '3.3/330';a['uncommittedDiff']=subprocess.check_output(['git','diff','--stat'],text=True);p.write_text(json.dumps(a,indent=2))
PY
display_file="work/body-joints/native/final-${scenario}-display.txt"
: > "$display_file"
/workspace/xvfb/usr/bin/Xvfb -displayfd 3 -screen 0 1280x1024x24 3> "$display_file" > "work/body-joints/native/final-${scenario}-xvfb.log" 2>&1 &
display_pid=$!
trap 'kill "$display_pid" 2>/dev/null || true; wait "$display_pid" 2>/dev/null || true' EXIT
for attempt in {1..100}; do
  kill -0 "$display_pid"
  if [ -s "$display_file" ]; then break; fi
  sleep .1
done
test -s "$display_file"
read -r display_number < "$display_file"
if [[ "$scenario" == *gl32* ]]; then
 export MESA_GL_VERSION_OVERRIDE=3.2 MESA_GLSL_VERSION_OVERRIDE=150
else
 export MESA_GL_VERSION_OVERRIDE=3.3 MESA_GLSL_VERSION_OVERRIDE=330
fi
set +e
DISPLAY=:$display_number LIBGL_ALWAYS_SOFTWARE=1 timeout --foreground 360 python3 -c 'import os,json; p="/workspace/ungravity-v5/fragment-boarding/build/cubic-client-automation"; a=json.load(open(p+"/launch.json")); os.chdir(p);os.execv(a[0],a)' < /dev/null > "work/body-joints/native/final-${scenario}.log" 2>&1
stage_code=$?
set -e
echo "$stage_code" > "work/body-joints/native/final-${scenario}.exit"
if [ "$stage_code" -ne 0 ]; then exit "$stage_code"; fi
if [[ "$scenario" == *joints* ]]; then test -s build/cubic-client-automation/joints-ok.json; elif [[ "$scenario" == *body-scale* ]]; then test -s build/cubic-client-automation/body-scale-ok.json; elif [[ "$scenario" == *body-index* ]]; then test -s build/cubic-client-automation/body-index-ok.json; elif [[ "$scenario" == *body-edit* ]]; then test -s build/cubic-client-automation/body-edit-ok.json; elif [[ "$scenario" == *ship* ]]; then test -s build/cubic-client-automation/ship-boarding-ok.json; elif [[ "$scenario" == *boarding* ]]; then test -s build/cubic-client-automation/boarding-ok.json; elif [[ "$scenario" == *snapshot* ]]; then test -s build/cubic-client-automation/snapshot-ok.json; elif [[ "$scenario" == *contacts* || "$scenario" == *fracture* ]]; then test -s build/cubic-client-automation/body-contacts-ok.json; elif [[ "$scenario" == *gun* ]]; then test -s build/cubic-client-automation/portal-gun-ok.json; elif [[ "$scenario" == *actors* ]]; then test -s build/cubic-client-automation/actor-frames-ok.json; elif [[ "$scenario" == *baseline* ]]; then test -s build/cubic-client-automation/cubic-client-ok.json; else test -s build/cubic-client-automation/native-mirror-ok.json; fi
mv build/cubic-client-automation "build/cubic-client-final-${scenario}"
