#!/usr/bin/env bash
# Back-compat wrapper → MCPE Master packager
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
chmod +x "$ROOT/scripts/package_mcpe_master.sh"
exec "$ROOT/scripts/package_mcpe_master.sh" "${1:-$ROOT/dist}" "${2:-1.0.2}"
