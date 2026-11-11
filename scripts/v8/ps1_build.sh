#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p work/ps1
python3 scripts/v8/ps1_build_stamp.py before
JAVA_HOME=/workspace/jdk21-complete PATH=/workspace/jdk21-complete/bin:$PATH GRADLE_USER_HOME=/workspace/.gradle ./gradlew remapJar remapCubiccheckJar --offline --no-daemon > work/ps1/verified-build.log 2>&1
python3 scripts/v8/ps1_build_stamp.py record
