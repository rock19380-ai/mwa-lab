#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# Preserve Phase 5 diagnostic contracts that remain applicable without invoking
# its intentionally frozen recorder hash gate.
python3 scripts/phase5_design_check.py
python3 scripts/verify_phase5_simulation_vectors.py
python3 scripts/phase5_device_acceptance_parser_test.py
python3 scripts/phase6_design_check.py
python3 scripts/verify_phase6_fault_vectors.py
python3 scripts/phase6_security_scan.py
python3 scripts/phase6_static.py
