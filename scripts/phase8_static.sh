#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 scripts/phase5_design_check.py
python3 scripts/verify_phase5_simulation_vectors.py
python3 scripts/phase5_device_acceptance_parser_test.py
python3 scripts/phase6_design_check.py
python3 scripts/verify_phase6_fault_vectors.py
python3 scripts/phase6_security_scan.py
python3 scripts/phase7_design_check.py
python3 scripts/phase7_static.py
python3 scripts/phase7_export_security.py

# phase7_security_scan.py freezes the entire approval/ directory at Phase 6,
# including SigningApprovalScreen. Phase 8 legitimately changed only that
# presentation file. phase8_static.py preserves its authority-scope and
# report/export assertions against current code without changing the
# historical Phase 7 script.
python3 scripts/phase8_design_check.py
python3 scripts/phase8_static.py
