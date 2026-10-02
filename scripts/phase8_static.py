#!/usr/bin/env python3
"""Phase 8 ancestry, frozen-authority, safety, and presentation static gate.

The frozen Phase 7 security scan rejects every change in approval/, including
the Phase 8 presentation-only SigningApprovalScreen. This continuation retains
its protected-authority and report/export assertions while allowing that single
screen to change. The historical script remains untouched.
"""
from pathlib import Path
import hashlib
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASE = "d4fbe20ff2d4b63d6f531f2a9cd7d6fadbe8a7e1"
TAG = "phase7-sanitized-diagnostic-reports-2026-10-02"
BASELINE = ROOT / "docs/evidence/phase8/phase8-baseline.sha256"
APP = "app/src/main/java/dev/mwalab/"
REQUIRED_FROZEN = {
    APP + "approval/ApprovalCoordinator.kt",
    APP + "faults/DeterministicFaultEngine.kt",
    APP + "mwa/MwaSessionHost.kt",
    APP + "protocol/recorder/PersistentProtocolRecorder.kt",
    APP + "report/DiagnosticReportCacheWriter.kt",
    APP + "report/DiagnosticReportShareIntentFactory.kt",
    APP + "report/DiagnosticReportSnapshotAssembler.kt",
    APP + "report/ReportSanitizationPolicy.kt",
    APP + "security/NetworkPolicy.kt",
    APP + "simulation/TransactionSimulationCoordinator.kt",
    APP + "storage/MwaLabDatabase.kt",
    APP + "transaction/LegacyTransactionCodec.kt",
    APP + "transaction/SolanaWireTransactionParser.kt",
    "gradle/libs.versions.toml",
}
PROTECTED_DIRS = tuple(APP + name + "/" for name in (
    "approval", "faults", "identity", "mwa", "protocol", "security",
    "simulation", "storage", "transaction",
))
PRESENTATION_EXCEPTION = APP + "approval/SigningApprovalScreen.kt"


def require(ok: bool, message: str) -> None:
    if not ok:
        raise AssertionError(message)


def read(path: str) -> str:
    return (ROOT / path).read_text()


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def ancestry_and_baseline() -> None:
    require(subprocess.run(["git", "merge-base", "--is-ancestor", BASE, "HEAD"],
                           cwd=ROOT, check=False).returncode == 0,
            "Frozen Phase 7 commit is not an ancestor of HEAD")
    require(git("rev-parse", f"{TAG}^{{}}") == BASE, "Frozen Phase 7 tag moved")
    entries: dict[str, str] = {}
    for line in BASELINE.read_text().splitlines():
        if not line or line.startswith("#"):
            continue
        match = re.fullmatch(r"([0-9a-f]{64})  (.+)", line)
        require(match is not None, "Malformed Phase 8 baseline manifest row")
        expected, path = match.groups()
        require(path not in entries, f"Duplicate baseline path: {path}")
        entries[path] = expected
    require(set(entries) == REQUIRED_FROZEN, "Phase 8 baseline path set changed")
    for path, expected in entries.items():
        canonical = subprocess.check_output(["git", "show", f"{BASE}:{path}"], cwd=ROOT)
        require(hashlib.sha256(canonical).hexdigest() == expected,
                f"Baseline is not the canonical Phase 7 Git blob: {path}")
        require(hashlib.sha256((ROOT / path).read_bytes()).hexdigest() == expected,
                f"Frozen Phase 7 authority changed: {path}")

    changed = git("diff", "--name-only", BASE, "--", *PROTECTED_DIRS).splitlines()
    untracked = git("ls-files", "--others", "--exclude-standard").splitlines()
    unauthorized = [p for p in changed + untracked
                    if p.startswith(PROTECTED_DIRS) and p != PRESENTATION_EXCEPTION]
    require(not unauthorized, f"Protocol/security authority changed: {unauthorized}")


def schema_dependency_network() -> None:
    db = read(APP + "storage/MwaLabDatabase.kt")
    require(re.search(r"\bversion\s*=\s*3\b", db) is not None,
            "Room schema must remain version 3")
    require(not (ROOT / "app/schemas/dev.mwalab.storage.MwaLabDatabase/4.json").exists(),
            "Room schema 4 appeared")
    for path in (ROOT / "app/src/main/java").rglob("*.kt"):
        body = path.read_text()
        require(re.search(r"Migration\s*\(\s*3\s*,\s*4\s*\)", body) is None and
                "MIGRATION_3_4" not in body, f"Room 3-to-4 migration appeared: {path}")
    catalog = read("gradle/libs.versions.toml")
    require(re.search(r'^mwaWalletlib\s*=\s*"2\.0\.7"\s*$', catalog, re.MULTILINE) is not None,
            "walletlib pin changed")
    require('version.ref = "mwaWalletlib"' in catalog and
            "implementation(libs.solana.mobile.walletlib)" in read("app/build.gradle.kts"),
            "Pinned walletlib dependency route changed")

    network = read(APP + "security/NetworkPolicy.kt")
    for token in ("CHAIN_SOLANA_DEVNET", "CLUSTER_DEVNET", "CHAIN_SOLANA_MAINNET",
                  "CLUSTER_MAINNET_BETA", "PRODUCTION_NOT_ALLOWED", "UNSUPPORTED_NETWORK"):
        require(token in network, f"Devnet network boundary lost: {token}")
    rpc = read(APP + "rpc/DevnetRpcGateway.kt")
    require('const val DEVNET_RPC_URL = "https://api.devnet.solana.com"' in rpc and
            "URL(SolanaDevnetRpcGateway.DEVNET_RPC_URL)" in rpc and
            "private class FixedDevnetHttpTransport" in rpc,
            "Fixed Devnet RPC transport changed")
    settings = read(APP + "ui/settings/SettingsScreen.kt")
    require("SolanaDevnetRpcGateway.DEVNET_RPC_URL" in settings and
            'val mainnet: String = "Unavailable"' in settings,
            "Settings no longer display the fixed network boundary")
    require(not re.search(r"\b(?:TextField|OutlinedTextField|DropdownMenu|onRpcChange|setRpc|customRpc)\b",
                          settings, re.IGNORECASE),
            "Settings gained an editable network control")


def fault_report_navigation() -> None:
    fault = read(APP + "ui/faults/FaultLabScreen.kt")
    approval = read(APP + "approval/SigningApprovalScreen.kt")
    for token in ('"FAULT ACTIVE"', '"INTENTIONAL TEST CONDITION"',
                  '"return-to-normal"', "onSelect(FaultId.NORMAL)"):
        require(token in fault, f"Fault truthfulness control missing: {token}")
    for token in ('"request-fault-banner"', "request.faultSnapshotId",
                  "LabSafetyBanner()", '"APPROVE"', '"REJECT"'):
        require(token in approval, f"Approval safety control missing: {token}")
    require("ApprovalCoordinator" not in approval,
            "Signing presentation gained approval-coordination authority")

    model = read(APP + "report/DiagnosticReport.kt")
    require('const val FORMAT = "mwa-lab-diagnostic-report"' in model and
            "const val VERSION = 1" in model and
            "enum class ReportCompleteness { COMPLETE, PARTIAL }" in model,
            "Canonical sanitized report v1 identity changed")
    require(re.search(r"\bval\s+(?:privateKey|seed|mnemonic|authToken|associationToken|rawTransaction|rawMessage|rawSignature|rawRpcBody)\b",
                      model) is None, "Secret/raw field appeared in report model")
    for renderer in ("MarkdownDiagnosticReportRenderer.kt", "JsonDiagnosticReportRenderer.kt",
                     "DiagnosticReportSummaryRenderer.kt"):
        body = read(APP + "report/" + renderer)
        require("render(report: DiagnosticReport)" in body and
                "ReportOutputBounds.validate(report)" in body and
                "RoomDatabase" not in body and re.search(r"\bByteArray\b", body) is None,
                f"Report renderer bypasses canonical bounded snapshot: {renderer}")
    main = read(APP + "MainActivity.kt")
    detail = read(APP + "ui/sessions/SessionDetailScreen.kt")
    require("DiagnosticReportShareIntentFactory" in main and
            "ReportExportEffect.Copy" in main and
            "DiagnosticReportFormat.MARKDOWN" in main and
            "DiagnosticReportFormat.JSON" in main and
            '"Share Markdown"' in detail and '"Share JSON"' in detail and
            '"Copy Summary"' in detail and
            "SANITIZED DIAGNOSTIC REPORT" in detail,
            "Phase 7 report/export surface lost")
    manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
    android = "{http://schemas.android.com/apk/res/android}"
    require(any(p.get(android + "name") == "androidx.core.content.FileProvider"
                for p in manifest.iter("provider")), "Report FileProvider missing")
    require("not a guarantee of submission success" in
            read(APP + "report/MarkdownDiagnosticReportRenderer.kt"),
            "Report simulation truthfulness notice missing")
    for path in (ROOT / "docs/evidence/phase7").rglob("*"):
        if path.is_file():
            data = path.read_bytes()
            require(not any(value in data for value in (
                b"raw-auth-secret-SENTINEL", b"private-key-secret-SENTINEL",
                b"seed-secret-SENTINEL", b"association-token-secret-SENTINEL",
                b"raw-message-secret-SENTINEL", b"raw-transaction-secret-SENTINEL",
                b"signature-secret-SENTINEL")),
                f"Secret sentinel leaked into Phase 7 evidence: {path}")

    nav = read(APP + "ui/navigation/AppDestination.kt")
    require("enum class AppDestination" in nav and
            all(x in nav for x in ("HOME(", "SESSIONS(", "FAULT_LAB(",
                                   "LAB_IDENTITY(", "SETTINGS(", "SESSION_DETAIL(")) and
            "this != SESSION_DETAIL" in nav and
            "SESSION_DETAIL -> SESSIONS" in nav and
            "AppDestination.topLevel" in main,
            "Typed five-destination navigation contract changed")


def phase7_security_continuation() -> None:
    """Carry Phase 7 report/export security assertions forward while allowing
    the one Phase 8 presentation-only SigningApprovalScreen change.

    The historical phase7_security_scan.py remains immutable and intentionally
    freezes the whole approval/ directory. Authority protection for Phase 8 is
    enforced separately by ancestry_and_baseline().
    """
    report_dir = ROOT / "app/src/main/java/dev/mwalab/report"

    model = (report_dir / "DiagnosticReport.kt").read_text()
    require('const val FORMAT = "mwa-lab-diagnostic-report"' in model and
            "const val VERSION = 1" in model and
            "enum class ReportCompleteness { COMPLETE, PARTIAL }" in model,
            "Canonical report v1 identity/completeness missing")
    require(re.search(r"\\b(?:ByteArray|Throwable|Exception|Uri|File|Any\\??)\\b", model) is None,
            "Unsafe generic/raw object type in canonical report")
    require(re.search(r"\\bval\\s+(?:privateKey|seed|mnemonic|authToken|associationToken|rawTransaction|rawMessage|rawSignature|rawRpcBody)\\b", model) is None,
            "Secret/raw field in canonical report")

    assembler = (report_dir / "DiagnosticReportSnapshotAssembler.kt").read_text()
    for required in (
        "sessions.getSession(sessionId)",
        "capabilities.getSnapshot(sessionId)",
        "transactions.getForEvent(sessionId, event.eventId)",
        "simulations.getForEvent(sessionId, event.eventId)",
        "ReportCompleteness.PARTIAL", "MAX_SNAPSHOT_PAIRS",
    ):
        require(required in assembler, "Read-only report snapshot contract missing: " + required)
    for forbidden in (
        "createSession(", "finishSession(", "recordProtocolEvent(",
        "recordSnapshot(", "recordForEvent(", "RoomDatabase",
    ):
        require(forbidden not in assembler, "Report assembler gained write authority: " + forbidden)

    for renderer in (
        "MarkdownDiagnosticReportRenderer.kt",
        "JsonDiagnosticReportRenderer.kt",
        "DiagnosticReportSummaryRenderer.kt",
    ):
        body = (report_dir / renderer).read_text()
        require("render(report: DiagnosticReport)" in body and
                "ReportOutputBounds.validate(report)" in body,
                "Renderer bypasses canonical bounds: " + renderer)
        require(not any(x in body for x in ("getSession(", "getForEvent(", "RoomDatabase")) and
                re.search(r"\\bByteArray\\b", body) is None,
                "Renderer reads raw repository or payload: " + renderer)

    require("not a guarantee of submission success" in
            (report_dir / "MarkdownDiagnosticReportRenderer.kt").read_text(),
            "Simulation truthfulness notice missing")
    require("SimulationLimits::publicLogLine" in
            (report_dir / "ReportSanitizationPolicy.kt").read_text(),
            "Simulation logs bypass sanitization")

    cache = (report_dir / "DiagnosticReportCacheWriter.kt").read_text()
    require('DIRECTORY_NAME = "diagnostic_reports"' in cache and
            "File.createTempFile" in cache and
            "ReportLimits.MAX_RENDERED_BYTES" in cache and
            "renameTo(target)" in cache,
            "Cache-only bounded export contract missing")

    share = (report_dir / "DiagnosticReportShareIntentFactory.kt").read_text()
    require("Intent.EXTRA_STREAM" in share and
            "Intent.EXTRA_TEXT" not in share and
            "Intent.ACTION_SEND" in share and
            "Intent.ACTION_SEND_MULTIPLE" not in share and
            "Intent.FLAG_GRANT_READ_URI_PERMISSION" in share and
            "FileProvider.getUriForFile" in share,
            "Share Intent boundary changed")

    activity = read(APP + "MainActivity.kt")
    require("DiagnosticReportShareIntentFactory" in activity and
            "ReportExportEffect.Copy" in activity and
            "ClipData.newPlainText" in activity and
            "effect.text" in activity,
            "Platform sharing or canonical clipboard effect missing")

    for path in report_dir.glob("*.kt"):
        body = path.read_text()
        require(not any(marker in body for marker in (
            "getExternalFilesDir", "getExternalStorageDirectory",
            "Environment.getExternal", "file://",
        )), "Unsafe external/file URI report path: " + path.name)

    sentinels = (
        b"raw-auth-secret-SENTINEL", b"private-key-secret-SENTINEL",
        b"seed-secret-SENTINEL", b"association-token-secret-SENTINEL",
        b"raw-message-secret-SENTINEL", b"raw-transaction-secret-SENTINEL",
        b"signature-secret-SENTINEL",
    )
    for directory in (ROOT / "docs/evidence/phase7",):
        for path in directory.rglob("*"):
            if not path.is_file():
                continue
            data = path.read_bytes()
            require(not any(sentinel in data for sentinel in sentinels),
                    "Synthetic secret sentinel leaked into evidence: " + str(path.relative_to(ROOT)))
            if path.suffix in (".json", ".md") and path.name.startswith("mwa-lab-"):
                require(len(data) <= 1024 * 1024, "Committed report exceeds byte bound")
                require(re.search(rb'(?i)"(?:private_key|seed|mnemonic|auth_token|association_token|raw_transaction|raw_message|raw_signature)"\\s*:', data) is None,
                        "Committed report contains forbidden raw field")


def ci_routing() -> None:
    workflow = read(".github/workflows/android.yml")
    require("      - phase8-world-class-ux-positioning" in workflow,
            "Phase 8 branch is absent from Android CI")
    require("hashFiles('scripts/phase7_static.sh') != '' && "
            "hashFiles('scripts/phase8_static.sh') == ''" in workflow,
            "Historical Phase 7 checkout no longer routes to its own gate")
    require("hashFiles('scripts/phase8_static.sh') != ''" in workflow and
            "run: ./scripts/phase8_static.sh" in workflow,
            "Phase 8 checkout does not route to the Phase 8 gate")
    require("bash -n " in workflow and "./scripts/phase8_static.sh" in
            workflow.split("Validate shell gate syntax", 1)[1].split("- name:", 1)[0],
            "Phase 8 shell syntax validation missing")
    require((ROOT / "scripts/phase8_static.sh").stat().st_mode & 0o111,
            "Phase 8 shell gate is not executable")


def positioning_and_identity() -> None:
    strings = read("app/src/main/res/values/strings.xml")
    home = read(APP + "ui/sessions/HomeScreen.kt")
    onboarding = read(APP + "ui/onboarding/OnboardingScreen.kt")
    fault = read(APP + "ui/faults/FaultLabScreen.kt")
    require("Mobile Wallet Adapter protocol debugger" in strings and
            "deterministic failure simulator" in strings and
            "LabSafetyBanner()" in home and "LabSafetyBanner()" in onboarding and
            "INTENTIONAL TEST CONDITION" in fault,
            "Phase 8 product/safety positioning disappeared")
    require("activeFault" in home and "View Sessions" in home and "Open Fault Lab" in home,
            "Home five-second diagnostic actions disappeared")
    marketing = "\n".join(read(path) for path in (
        "README.md", "app/src/main/res/values/strings.xml",
        APP + "ui/sessions/HomeScreen.kt", APP + "ui/onboarding/OnboardingScreen.kt",
        APP + "ui/faults/FaultLabScreen.kt",
        "demo-client/src/main/java/dev/mwalab/democlient/DemoClientActivity.kt",
    ))
    for match in re.finditer(r"\b(?:replaces?\s+(?:Phantom|Solflare)|supports?\s+mainnet|mainnet\s+supported|(?:is|as)\s+a\s+production\s+wallet)\b",
                             marketing, re.IGNORECASE):
        preceding = marketing[max(0, match.start() - 70):match.start()].lower()
        require(re.search(r"\b(?:not|never|cannot|no)\b", preceding) is not None,
                f"Unsafe positive wallet claim: {match.group()}")
    identity_ui = "\n".join(read(path) for path in (
        APP + "MainActivity.kt", APP + "ui/identity/LabIdentityScreen.kt",
        APP + "ui/sessions/HomeScreen.kt", "app/src/main/res/values/strings.xml",
    ))
    require(re.search(r"\b(?:resetIdentity|Reset Identity|RESET IDENTITY|onReset)\b",
                      identity_ui) is None,
            "Identity reset was exposed in Phase 8 UI")
    identity = read(APP + "ui/identity/LabIdentityScreen.kt")
    require('DiagnosticValue("Public address"' in identity and
            "R.string.copy_address" in identity,
            "Read-only public identity presentation changed")


if __name__ == "__main__":
    ancestry_and_baseline()
    schema_dependency_network()
    fault_report_navigation()
    phase7_security_continuation()
    positioning_and_identity()
    ci_routing()
    print("PHASE 8 STATIC CHECK: PASS")
