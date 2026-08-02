#!/usr/bin/env bash
# Build remapped Fabric jar for TLauncher / mobile Fabric loaders. No git push.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
chmod +x gradlew
./gradlew clean build packageTlauncher --no-daemon
echo ""
echo "Artifacts:"
ls -lh dist/drmd-6dof-*.jar dist/INSTALL_TLAUNCHER.txt 2>/dev/null || ls -lh dist/
