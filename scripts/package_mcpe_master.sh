#!/usr/bin/env bash
# Package DRMD MCPE Master edition:
#  - .mcaddon / .mcpack (auto-import)
#  - .zip for manual install into games/com.mojang via ZArchiver / MCPE Master
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="${1:-$ROOT/dist}"
VER="${2:-1.0.5}"
mkdir -p "$OUT_DIR"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

BP_NAME="DRMD_6DOF_BP"
RP_NAME="DRMD_6DOF_RP"

mkdir -p "$STAGE/com.mojang/behavior_packs/$BP_NAME"
mkdir -p "$STAGE/com.mojang/resource_packs/$RP_NAME"
cp -R "$ROOT/mcpe/behavior_pack/." "$STAGE/com.mojang/behavior_packs/$BP_NAME/"
cp -R "$ROOT/mcpe/resource_pack/." "$STAGE/com.mojang/resource_packs/$RP_NAME/"

cat > "$STAGE/INSTALL_MCPE_MASTER.txt" << EOF
DRMD 6DOF — установка в MCPE Master / Bedrock (Android)

Способ A — авто (если открывается):
  1) drmd-6dof-mcpe-master-${VER}.mcaddon → открыть через Minecraft / Master
  2) Новый мир → Наборы ресурсов + Наборы поведения → активировать оба DRMD
  3) Эксперименты / Beta APIs = ВКЛ

Способ B — вручную (ZArchiver / MCPE Master file manager):
  1) Распаковать этот ZIP
  2) Скопировать папки:
       behavior_packs/DRMD_6DOF_BP  →  /games/com.mojang/behavior_packs/
       resource_packs/DRMD_6DOF_RP  →  /games/com.mojang/resource_packs/
     (или Android/data/.../games/com.mojang/ на новых Android)
  3) Перезапустить игру, активировать пакеты в мире
  4) Beta APIs = ВКЛ
  5) В игре: /function drmd/start   или просто зайдите в мир (авто-кит)

Управление: кнопки хотбара (крен ←/→, вверх/вниз, стрейф, рывок)
             Панель управления · !d6 panel

Нужен Minecraft / Master примерно 1.20.60+ (лучше 1.21.x).
Полная версия Descent — Fabric jar на ПК.
EOF

# Build .mcpack from pack folders
mkdir -p "$STAGE/flat/$BP_NAME" "$STAGE/flat/$RP_NAME"
cp -R "$STAGE/com.mojang/behavior_packs/$BP_NAME/." "$STAGE/flat/$BP_NAME/"
cp -R "$STAGE/com.mojang/resource_packs/$RP_NAME/." "$STAGE/flat/$RP_NAME/"

(
  cd "$STAGE/flat"
  zip -qr "$STAGE/drmd_6dof_bp.mcpack" "$BP_NAME"
  zip -qr "$STAGE/drmd_6dof_rp.mcpack" "$RP_NAME"
)
(
  cd "$STAGE"
  zip -qr "drmd-6dof-mcpe-master-${VER}.mcaddon" drmd_6dof_bp.mcpack drmd_6dof_rp.mcpack
  zip -qr "drmd-6dof-mcpe-master-${VER}.zip" com.mojang INSTALL_MCPE_MASTER.txt
)

cp "$STAGE/drmd-6dof-mcpe-master-${VER}.mcaddon" "$OUT_DIR/"
cp "$STAGE/drmd-6dof-mcpe-master-${VER}.zip" "$OUT_DIR/"
cp "$STAGE/drmd_6dof_bp.mcpack" "$OUT_DIR/drmd-6dof-mcpe-master-bp-${VER}.mcpack"
cp "$STAGE/drmd_6dof_rp.mcpack" "$OUT_DIR/drmd-6dof-mcpe-master-rp-${VER}.mcpack"

# Legacy aliases for CI / evening bundle
cp "$OUT_DIR/drmd-6dof-mcpe-master-${VER}.mcaddon" "$OUT_DIR/drmd-6dof-fast-test-${VER}.mcaddon"
cp "$OUT_DIR/drmd-6dof-mcpe-master-bp-${VER}.mcpack" "$OUT_DIR/drmd-6dof-fast-test-bp-${VER}.mcpack"
cp "$OUT_DIR/drmd-6dof-mcpe-master-rp-${VER}.mcpack" "$OUT_DIR/drmd-6dof-fast-test-rp-${VER}.mcpack"

rm -rf "$OUT_DIR/mcpe-fast-test" "$OUT_DIR/mcpe-master"
mkdir -p "$OUT_DIR/mcpe-master"
cp -R "$ROOT/mcpe/." "$OUT_DIR/mcpe-master/"
cp "$STAGE/INSTALL_MCPE_MASTER.txt" "$OUT_DIR/mcpe-master/"
cp -R "$OUT_DIR/mcpe-master" "$OUT_DIR/mcpe-fast-test"

echo "MCPE Master packaged:"
ls -lh "$OUT_DIR"/drmd-6dof-mcpe-master-${VER}.* "$OUT_DIR"/drmd-6dof-fast-test-${VER}.mcaddon
