#!/usr/bin/env bash
set -euo pipefail
ROOT="${1:-$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)}"
cd "$ROOT"
# Historical gates keep their original scope. Phase 1 still applies to this build.
bash ./scripts/phase1_static.sh "$ROOT"
python3 ./scripts/phase4_static.py "$ROOT"
python3 ./scripts/generate_phase4_transaction_vectors.py --check
echo 'PHASE 4 STATIC / DIAGNOSTICS / PREDECESSOR GATE: PASS'
