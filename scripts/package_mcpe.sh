#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="${1:-$ROOT/dist}"
VER="${2:-1.0.0}"
mkdir -p "$OUT_DIR"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

mkdir -p "$STAGE/drmd_fast_test_bp" "$STAGE/drmd_fast_test_rp"
cp -R "$ROOT/mcpe/behavior_pack/." "$STAGE/drmd_fast_test_bp/"
cp -R "$ROOT/mcpe/resource_pack/." "$STAGE/drmd_fast_test_rp/"

# Zip packs then wrap as .mcaddon (zip of packs)
(
  cd "$STAGE"
  zip -qr "drmd_fast_test_bp.mcpack" drmd_fast_test_bp
  zip -qr "drmd_fast_test_rp.mcpack" drmd_fast_test_rp
  zip -qr "drmd-6dof-fast-test-${VER}.mcaddon" drmd_fast_test_bp.mcpack drmd_fast_test_rp.mcpack
)
cp "$STAGE/drmd-6dof-fast-test-${VER}.mcaddon" "$OUT_DIR/"
cp "$STAGE/drmd_fast_test_bp.mcpack" "$OUT_DIR/drmd-6dof-fast-test-bp-${VER}.mcpack"
cp "$STAGE/drmd_fast_test_rp.mcpack" "$OUT_DIR/drmd-6dof-fast-test-rp-${VER}.mcpack"
# Also keep unpacked folder for debugging
rm -rf "$OUT_DIR/mcpe-fast-test"
mkdir -p "$OUT_DIR/mcpe-fast-test"
cp -R "$ROOT/mcpe/." "$OUT_DIR/mcpe-fast-test/"
echo "MCPE packaged -> $OUT_DIR/drmd-6dof-fast-test-${VER}.mcaddon"
ls -lh "$OUT_DIR"/drmd-6dof-fast-test*
