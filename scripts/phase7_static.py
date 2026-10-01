#!/usr/bin/env python3
"""Phase 7.0–7.10 source and predecessor guard; later export/UI gates are separate."""
from pathlib import Path
import hashlib
import re
import subprocess

root = Path(__file__).resolve().parents[1]
base = "f5eaec53d911fc2423efdb3051e8316ce9e1032d"
def read(path):
    return (root / path).read_text()
def check(ok, message):
    assert ok, message

subprocess.run(["git", "merge-base", "--is-ancestor", base, "HEAD"], cwd=root, check=True)
tag = subprocess.check_output(["git", "rev-parse", "phase6-deterministic-fault-engine-2026-10-01^{}"],
                              cwd=root, text=True).strip()
check(tag == base, "Phase 6 tag moved")
for line in read("docs/evidence/phase7/phase7-baseline.sha256").splitlines():
    if not line or line.startswith("#"):
        continue
    expected, rel = line.split(None, 1)
    actual = hashlib.sha256((root / rel).read_bytes()).hexdigest()
    check(actual == expected, f"frozen predecessor changed: {rel}")
db = read("app/src/main/java/dev/mwalab/storage/MwaLabDatabase.kt")
check(re.search(r"\bversion\s*=\s*3\b", db) is not None, "Room schema changed")
check("Migration(3, 4)" not in db and not (root / "app/schemas/dev.mwalab.storage.MwaLabDatabase/4.json").exists(),
      "schema 4 introduced")
check('mwaWalletlib = "2.0.7"' in read("gradle/libs.versions.toml"), "walletlib changed")
network = read("app/src/main/java/dev/mwalab/security/NetworkPolicy.kt")
check("PRODUCTION_NOT_ALLOWED" in network and "CHAIN_SOLANA_MAINNET" in network, "Devnet boundary changed")
report_files = list((root / "app/src/main/java/dev/mwalab/report").glob("*.kt"))
check(len(report_files) >= 5, "report layer incomplete")
report_text = "\n".join(p.read_text() for p in report_files)
for forbidden in ("LabSigningService", "ApprovalCoordinator", "sendTransaction(", "RoomDatabase",
                  "PrivatePreferences", "ACTION_SEND", "rawTransaction", "privateKey"):
    check(forbidden not in report_text, f"report authority violation: {forbidden}")
for renderer in ("MarkdownDiagnosticReportRenderer.kt", "JsonDiagnosticReportRenderer.kt"):
    text = read("app/src/main/java/dev/mwalab/report/" + renderer)
    check("render(report: DiagnosticReport)" in text and "ReportOutputBounds.validate(report)" in text,
          f"renderer lacks canonical input or bounds: {renderer}")
check("getSession(sessionId)" in read("app/src/main/java/dev/mwalab/report/DiagnosticReportSnapshotAssembler.kt"),
      "assembler does not read persisted session")
print("PHASE 7.0–7.10 STATIC CHECK: PASS")
