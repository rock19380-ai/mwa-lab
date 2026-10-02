#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
# The frozen Phase 6 gate forbids sharing by design. Reuse all applicable
# predecessor assertions through phase7_static.py without changing that gate.
python3 scripts/phase5_design_check.py
python3 scripts/verify_phase5_simulation_vectors.py
python3 scripts/phase5_device_acceptance_parser_test.py
python3 scripts/phase6_design_check.py
python3 scripts/verify_phase6_fault_vectors.py
python3 scripts/phase6_security_scan.py
python3 scripts/phase7_design_check.py
python3 scripts/phase7_static.py
python3 scripts/phase7_export_security.py
python3 scripts/phase7_security_scan.py
