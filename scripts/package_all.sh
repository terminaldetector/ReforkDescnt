#!/usr/bin/env bash
# Build Fabric PC jar + MCPE Fast Test .mcaddon into dist/
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mkdir -p dist

echo "==> Fabric PC jar"
chmod +x gradlew
./gradlew build --no-daemon
cp -f build/libs/drmd-6dof-*.jar dist/
rm -f dist/*-sources.jar dist/*-dev.jar dist/*-dev-shadow.jar || true

echo "==> MCPE Fast Test .mcaddon"
chmod +x scripts/package_mcpe.sh
./scripts/package_mcpe.sh dist 1.0.0

cat > dist/README_EVENING_TEST.txt << 'EOF'
DRMD 6DOF — evening test pack
=============================

PC (Java / Fabric 1.21.1)
  1. Fabric Loader 1.21.1 + Fabric API for 1.21.1
  2. Put drmd-6dof-1.0.0.jar into mods/
  3. New world — 6DoF on join; Pyro GX; /d6 kit for engineer tools

MCPE / Bedrock Fast Test
  1. Open drmd-6dof-fast-test-1.0.0.mcaddon (or import .mcpack pair)
  2. Enable packs + Beta APIs / Scripting if prompted
  3. Pyro Beacon = 6DoF toggle; Construction Wand; Gravity Torch

Full game = PC jar. MCPE = feel-check sandbox only.
EOF

echo ""
echo "Ready in dist/:"
ls -lh dist/*.{jar,mcaddon,txt} 2>/dev/null || ls -lh dist/
