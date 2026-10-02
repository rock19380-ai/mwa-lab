#!/usr/bin/env python3
"""Phase 7 export boundary and predecessor-authority security scan.

This is a source/evidence gate. Hostile-value behavior is also exercised by JVM
and Android tests; a static scan alone cannot prove absence of all leaks.
"""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
BASE = "f5eaec53d911fc2423efdb3051e8316ce9e1032d"
REPORT = ROOT / "app/src/main/java/dev/mwalab/report"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def source(name):
    return (REPORT / name).read_text()


protected = (
    "app/src/main/java/dev/mwalab/approval/",
    "app/src/main/java/dev/mwalab/faults/",
    "app/src/main/java/dev/mwalab/identity/",
    "app/src/main/java/dev/mwalab/mwa/",
    "app/src/main/java/dev/mwalab/protocol/",
    "app/src/main/java/dev/mwalab/security/",
    "app/src/main/java/dev/mwalab/simulation/",
    "app/src/main/java/dev/mwalab/storage/",
    "app/src/main/java/dev/mwalab/transaction/",
)
changed = subprocess.check_output(
    ["git", "diff", "--name-only", BASE, "--", *protected], cwd=ROOT, text=True
).splitlines()
untracked = subprocess.check_output(
    ["git", "ls-files", "--others", "--exclude-standard"], cwd=ROOT, text=True
).splitlines()
require(not changed and not any(p.startswith(protected) for p in untracked),
        "Phase 6 protocol/signing/fault/simulation/storage authority changed")

model = source("DiagnosticReport.kt")
require('const val FORMAT = "mwa-lab-diagnostic-report"' in model and
        "const val VERSION = 1" in model and "enum class ReportCompleteness { COMPLETE, PARTIAL }" in model,
        "Canonical report v1 identity/completeness missing")
require(not re.search(r"\b(?:ByteArray|Throwable|Exception|Uri|File|Any\??)\b", model),
        "Unsafe generic/raw object type in canonical report")
require(not re.search(r"\bval\s+(?:privateKey|seed|mnemonic|authToken|associationToken|rawTransaction|rawMessage|rawSignature|rawRpcBody)\b", model),
        "Secret/raw field in canonical report")

assembler = source("DiagnosticReportSnapshotAssembler.kt")
for required in ("sessions.getSession(sessionId)", "capabilities.getSnapshot(sessionId)",
                 "transactions.getForEvent(sessionId, event.eventId)",
                 "simulations.getForEvent(sessionId, event.eventId)",
                 "ReportCompleteness.PARTIAL", "MAX_SNAPSHOT_PAIRS"):
    require(required in assembler, "Read-only report snapshot contract missing: " + required)
for forbidden in ("createSession(", "finishSession(", "recordProtocolEvent(",
                  "recordSnapshot(", "recordForEvent(", "RoomDatabase"):
    require(forbidden not in assembler, "Report assembler gained write authority: " + forbidden)

for renderer in ("MarkdownDiagnosticReportRenderer.kt", "JsonDiagnosticReportRenderer.kt",
                 "DiagnosticReportSummaryRenderer.kt"):
    body = source(renderer)
    require("render(report: DiagnosticReport)" in body and "ReportOutputBounds.validate(report)" in body,
            "Renderer bypasses canonical bounds: " + renderer)
    require(not any(x in body for x in ("getSession(", "getForEvent(", "RoomDatabase")) and
            not re.search(r"\bByteArray\b", body),
            "Renderer reads raw repository or payload: " + renderer)
require("not a guarantee of submission success" in source("MarkdownDiagnosticReportRenderer.kt"),
        "Simulation truthfulness notice missing")
require("SimulationLimits::publicLogLine" in source("ReportSanitizationPolicy.kt"),
        "Simulation logs bypass sanitization")

cache = source("DiagnosticReportCacheWriter.kt")
require('DIRECTORY_NAME = "diagnostic_reports"' in cache and "File.createTempFile" in cache and
        "ReportLimits.MAX_RENDERED_BYTES" in cache and "renameTo(target)" in cache,
        "Cache-only bounded export contract missing")
share = source("DiagnosticReportShareIntentFactory.kt")
require("Intent.EXTRA_STREAM" in share and "Intent.EXTRA_TEXT" not in share and
        "Intent.ACTION_SEND" in share and "Intent.ACTION_SEND_MULTIPLE" not in share and
        "Intent.FLAG_GRANT_READ_URI_PERMISSION" in share and "FileProvider.getUriForFile" in share,
        "Share Intent boundary changed")
activity = (ROOT / "app/src/main/java/dev/mwalab/MainActivity.kt").read_text()
require("DiagnosticReportShareIntentFactory" in activity and "ReportExportEffect.Copy" in activity and
        "ClipData.newPlainText" in activity and "effect.text" in activity,
        "Platform sharing or canonical clipboard effect missing")
for path in REPORT.glob("*.kt"):
    body = path.read_text()
    require(not any(marker in body for marker in ("getExternalFilesDir", "getExternalStorageDirectory",
                                               "Environment.getExternal", "file://")),
            "Unsafe external/file URI report path: " + path.name)

# Synthetic sentinels belong in tests, never in committed report examples or evidence.
sentinels = (
    b"raw-auth-secret-SENTINEL", b"private-key-secret-SENTINEL",
    b"seed-secret-SENTINEL", b"association-token-secret-SENTINEL",
    b"raw-message-secret-SENTINEL", b"raw-transaction-secret-SENTINEL",
    b"signature-secret-SENTINEL",
)
for directory in (ROOT / "docs/evidence/phase7",):
    for path in directory.rglob("*"):
        if path.is_file():
            data = path.read_bytes()
            require(not any(sentinel in data for sentinel in sentinels),
                    "Synthetic secret sentinel leaked into evidence: " + str(path.relative_to(ROOT)))
            if path.suffix in (".json", ".md") and path.name.startswith("mwa-lab-"):
                require(len(data) <= 1024 * 1024, "Committed report exceeds byte bound")
                require(not re.search(rb'(?i)"(?:private_key|seed|mnemonic|auth_token|association_token|raw_transaction|raw_message|raw_signature)"\s*:', data),
                        "Committed report contains forbidden raw field")
print("PHASE 7 SECURITY SCAN: PASS")
