#!/usr/bin/env bash
# Build Fabric PC jar + MCPE Master addon into dist/
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
mkdir -p dist

echo "==> Fabric PC jar"
chmod +x gradlew
./gradlew build --no-daemon
cp -f build/libs/drmd-6dof-*.jar dist/
rm -f dist/*-sources.jar dist/*-dev.jar dist/*-dev-shadow.jar || true

echo "==> MCPE Master (.mcaddon + com.mojang zip)"
chmod +x scripts/package_mcpe.sh scripts/package_mcpe_master.sh
./scripts/package_mcpe_master.sh dist 1.0.5

cat > dist/README_EVENING_TEST.txt << 'EOF'
DRMD 6DOF — evening test pack
=============================

PC (Java / Fabric 1.21.1)
  1. Fabric Loader 1.21.1 + Fabric API for 1.21.1
  2. Put drmd-6dof-1.0.0.jar into mods/
  3. New world — 6DoF on join; Pyro GX; /d6 kit

MCPE Master / Bedrock
  A) drmd-6dof-mcpe-master-1.0.5.mcaddon — открыть в игре
  B) drmd-6dof-mcpe-master-1.0.5.zip — вручную в games/com.mojang
     behavior_packs/DRMD_6DOF_BP + resource_packs/DRMD_6DOF_RP
  3. Beta APIs ВКЛ · /function drmd/start
  4. Descent: Нос↑↓ · Бочки←→ · Рысканье · HUD на экране

Full game = PC jar. MCPE = UX sandbox (see mcpe/README.md).
EOF

echo ""
echo "Ready in dist/:"
ls -lh dist/*.{jar,mcaddon,zip,txt} 2>/dev/null || ls -lh dist/
