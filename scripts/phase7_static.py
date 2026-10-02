#!/usr/bin/env python3
"""Phase 7 source and predecessor guard; Phase 6 scope gate stays historical."""
from pathlib import Path
import hashlib
import re
import subprocess

import phase6_static as predecessor

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
    base_blob = subprocess.check_output(["git", "show", f"{base}:{rel}"], cwd=root)
    base_hash = hashlib.sha256(base_blob).hexdigest()
    check(base_hash == expected, f"baseline manifest is not the canonical Phase 6 Git blob: {rel}")
    actual = hashlib.sha256((root / rel).read_bytes()).hexdigest()
    check(actual == expected, f"frozen predecessor changed: {rel}")
db = read("app/src/main/java/dev/mwalab/storage/MwaLabDatabase.kt")
check(re.search(r"\bversion\s*=\s*3\b", db) is not None, "Room schema changed")
check("Migration(3, 4)" not in db and not (root / "app/schemas/dev.mwalab.storage.MwaLabDatabase/4.json").exists(),
      "schema 4 introduced")
check('mwaWalletlib = "2.0.7"' in read("gradle/libs.versions.toml"), "walletlib changed")
network = read("app/src/main/java/dev/mwalab/security/NetworkPolicy.kt")
check("PRODUCTION_NOT_ALLOWED" in network and "CHAIN_SOLANA_MAINNET" in network, "Devnet boundary changed")
report_files = [p for p in (root / "app/src/main/java/dev/mwalab/report").glob("*.kt")
                if p.name not in {"DiagnosticReportCacheWriter.kt",
                                  "DiagnosticReportShareIntentFactory.kt",
                                  "DiagnosticReportExportUseCase.kt"}]
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
# Preserve every applicable Phase 6 assertion. Its scope() deliberately forbids
# ACTION_SEND before Phase 7 and must remain unchanged for Phase 6 checkouts.
for assertion in (predecessor.ancestry, predecessor.baseline_manifest,
                  predecessor.schema, predecessor.deps, predecessor.fault_contract,
                  predecessor.ui_and_vectors, predecessor.phase5_separation,
                  predecessor.ci, predecessor.docs):
    assertion()
workflow = read(".github/workflows/android.yml")
check("phase7-sanitized-diagnostic-reports" in workflow and
      "hashFiles('scripts/phase7_static.sh') == ''" in workflow and
      "hashFiles('scripts/phase7_static.sh') != ''" in workflow,
      "Phase-aware CI routing missing")
check((root / "scripts/phase7_static.sh").is_file(), "Phase 7 static entrypoint missing")
print("PHASE 7 STATIC CHECK: PASS")
