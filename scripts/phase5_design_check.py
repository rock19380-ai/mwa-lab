#!/usr/bin/env python3
"""Check the frozen Phase 5 authority contract before runtime implementation."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
design = (root / "docs/phase5-simulation-diagnostic-classification-design.md").read_text()
required = (
    "Simulation PASS does not grant approval.",
    "Simulation FAIL does not reject the MWA request.",
    "Simulation UNAVAILABLE does not disable approval.",
    "Simulation does not submit a transaction.",
    "Simulation does not complete ProtocolRecorder.",
    "Simulation evidence cannot override the parent ProtocolEvent outcome.",
    "diagnostic-only", "synthetic", "LegacyTransactionCodec", "v0 signing remains",
    "sigVerify=false", "replaceRecentBlockhash=false", "encoding=base64",
    "minContextSlot", "64 KiB", "Room schema 3", "No historical backfill",
    "SimulationDiagnosticSettlement", "Phase 6 fault injection", "Phase 7 Markdown/JSON",
    "UNKNOWN_SIMULATION_ERROR", "RPC_NETWORK", "LOCAL_PARSER",
)
missing = [phrase for phrase in required if phrase not in design]
if missing:
    sys.exit("PHASE 5 DESIGN CHECK FAIL: missing " + ", ".join(missing))
print("PHASE 5 DESIGN CHECK: PASS")
