#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
python3 scripts/phase5_design_check.py
python3 scripts/verify_phase5_simulation_vectors.py
python3 scripts/phase5_device_acceptance_parser_test.py
python3 scripts/phase5_static.py
