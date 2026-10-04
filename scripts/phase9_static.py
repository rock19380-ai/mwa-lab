#!/usr/bin/env python3
"""Phase 9 current-source pre-freeze safety/static gate."""

from pathlib import Path
import json
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/dev/mwalab"
PHASE8_HEAD = "7cb9c0da5839ee14f4ef5f45391449b5060e7ebc"
PHASE8_TAG = "phase8-world-class-ux-positioning-2026-10-03"


def require(ok: bool, message: str) -> None:
    if not ok:
        raise AssertionError(message)


def read(path) -> str:
    p = path if isinstance(path, Path) else ROOT / path
    return p.read_text()


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


require(
    subprocess.run(
        ["git", "merge-base", "--is-ancestor", PHASE8_HEAD, "HEAD"],
        cwd=ROOT,
        check=False,
    ).returncode == 0,
    "Frozen Phase 8 is not an ancestor of Phase 9",
)
require(git("rev-parse", f"{PHASE8_TAG}^{{}}") == PHASE8_HEAD, "Frozen Phase 8 tag moved")

catalog = read("gradle/libs.versions.toml")
require(
    re.search(r'^mwaWalletlib\s*=\s*"2\.0\.7"\s*$', catalog, re.MULTILINE) is not None,
    "walletlib pin changed",
)

db = read(APP / "storage/MwaLabDatabase.kt")
require(re.search(r"\bversion\s*=\s*4\b", db) is not None, "Room schema is not version 4")
require("MIGRATION_3_4" in db and "Migration(3, 4)" in db, "Room 3->4 migration missing")

schema = json.loads(read("app/schemas/dev.mwalab.storage.MwaLabDatabase/4.json"))
sessions = next(e for e in schema["database"]["entities"] if e["tableName"] == "sessions")
columns = {f["columnName"] for f in sessions["fields"]}
require("association_mode" in columns, "association_mode column missing")
require("identity_verification_state" in columns, "identity_verification_state column missing")
for forbidden in (
    "association_uri", "remote_uri", "reflector_id", "reflector_token",
    "reflector_secret", "auth_token", "association_token", "private_key",
    "seed", "mnemonic",
):
    require(forbidden not in columns, f"secret-shaped durable session column appeared: {forbidden}")

connection = read(APP / "mwa/association/ConnectionPresentation.kt")
for token in (
    "enum class AssociationMode", "LOCAL", "REMOTE",
    "enum class DappVerificationState", "VERIFIED", "UNVERIFIED",
    "NOT_AVAILABLE", "REMOTE_UNVERIFIED",
):
    require(token in connection, f"transport/verification domain missing: {token}")

host = read(APP / "mwa/MwaSessionHost.kt")
require("authorizationApprovalCoordinator.requestApproval" in host,
        "explicit authorization approval boundary missing")
require('"human_consent" to "approved"' in host,
        "authorization consent evidence missing")
require("DappVerificationState.UNVERIFIED" in host and "AssociationMode.LOCAL" in host,
        "Local transport/verification evidence missing")

auth_ui = read(APP / "approval/AuthorizationApprovalScreen.kt")
for token in ('"CONNECT DAPP"', '"REJECT"', '"APPROVE"', '"SAME-DEVICE MWA"',
              '"UNVERIFIED REMOTE DAPP"'):
    require(token in auth_ui, f"authorization presentation missing: {token}")

rpc = read(APP / "rpc/DevnetRpcGateway.kt")
require('const val DEVNET_RPC_URL = "https://api.devnet.solana.com"' in rpc,
        "fixed Devnet RPC changed")

production_text = "\n".join(
    p.read_text(errors="replace")
    for p in (ROOT / "app/src/main").rglob("*")
    if p.is_file()
)
require("api.mainnet-beta.solana.com" not in production_text,
        "mainnet RPC endpoint appeared in app source")

home = read(APP / "ui/sessions/HomeScreen.kt")
for token in ('"READY FOR DAPP CONNECTIONS"', '"HOW TO CONNECT"', '"TEST WALLET"',
              '"SEND TEST SOL"', '"RECEIVE TEST SOL"', '"REQUEST 0.5 DEVNET SOL"'):
    require(token in home, f"Phase 9 Home/Test Wallet surface missing: {token}")

require("It is NOT an MWA connection QR." in read(APP / "ui/identity/LabIdentityScreen.kt"),
        "Receive QR purpose separation missing")

send = read(APP / "wallet/TestSolTransfer.kt")
for token in (
    "SolanaPublicKeyParser", "parseSolAmountToLamports", "TestSolTransferBuilder",
    "MIN_SEND_FEE_RESERVE_LAMPORTS", "skipPreflight = false",
    'commitment = "confirmed"', "WalletSendSubmissionResult.SubmittedUnknown",
):
    require(token in send, f"Send Test SOL safety contract missing: {token}")
for forbidden in ("ProtocolRecorder", "PersistentProtocolRecorder", "SessionRepository", "recordProtocolEvent"):
    require(forbidden not in send, f"direct Test Wallet send polluted MWA authority: {forbidden}")

manifest = ET.parse(ROOT / "app/src/main/AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
permissions = {node.get(android + "name") for node in manifest.findall("uses-permission")}
require("android.permission.CAMERA" not in permissions,
        "CAMERA permission shipped while Remote release is blocked")

for token in ("PASTE REMOTE MWA URI", "SCAN REMOTE MWA QR"):
    require(token not in production_text, f"dead Remote release control shipped: {token}")

require(not re.search(r"(?i)\b(?:camerax|mlkit|barcode-scanning)\b", catalog),
        "scanner dependency shipped while Remote transport is blocked")

sanitizer = read(APP / "security/DiagnosticSanitizer.kt")
for token in ("association_uri", "remote_uri", "association_public_key",
              "reflector_id", "reflector_token", "reflector_secret"):
    require(f'"{token}"' in sanitizer, f"Remote-shaped sanitizer key missing: {token}")

report = read(APP / "report/DiagnosticReport.kt")
require("associationMode" in report and "identityVerificationState" in report,
        "diagnostic report lacks coarse transport/verification metadata")
for forbidden in ("associationUri", "remoteUri", "reflectorToken", "authToken"):
    require(forbidden not in report, f"raw transport/auth field appeared in report: {forbidden}")

readme = read("README.md")
require("Production-wallet compatibility remains **NOT VERIFIED**" in readme,
        "README overclaims production-wallet compatibility")

workflow = read(".github/workflows/android.yml")
require("      - phase9-first-run-connection-ux" in workflow,
        "Phase 9 branch missing from Android CI")
require("run: ./scripts/phase9_static.sh" in workflow,
        "Phase 9 CI static gate missing")
require("hashFiles('scripts/phase9_static.sh') != ''" in workflow,
        "Phase 9 CI routing condition missing")

for build_file, live_class in (
    ("app/build.gradle.kts", "dev.mwalab.wallet.Phase9LiveSendInstrumentedTest"),
    (
        "demo-client/build.gradle.kts",
        "dev.mwalab.democlient.DemoClientPhase9LiveInstrumentedTest",
    ),
):
    require(
        f'testInstrumentationRunnerArguments["notClass"] = "{live_class}"'
        in read(build_file),
        f"ordinary connected suite does not exclude opt-in live class: {live_class}",
    )

app_live = read("app/src/androidTest/java/dev/mwalab/wallet/Phase9LiveSendInstrumentedTest.kt")
require("requestAirdrop" not in app_live and "confirmAirdrop" not in app_live,
        "opt-in live send test may call an airdrop")
require('getString("mwa_phase9_recipient")' in app_live,
        "live send test does not require an explicit recipient")
require(
    "assumeTrue(" in app_live
    and 'getString("mwa_phase9_live") == "1"' in app_live
    and app_live.index("assumeTrue(") < app_live.index("val context ="),
    "app live spend test is not opt-in before wallet access",
)

demo_live = read(
    "demo-client/src/androidTest/java/dev/mwalab/democlient/"
    "DemoClientPhase9LiveInstrumentedTest.kt"
)
require(
    'getString("mwa_phase9_live") == "1"' in demo_live
    and re.search(
        r"fun normalSignAndSendConfirmsOnDevnet\(\) \{\s*requireLiveOptIn\(\)",
        demo_live,
    ) is not None
    and re.search(
        r"fun injectedSignRejectReturnsExpectedProtocolErrorWithoutSigningApproval"
        r"\(\) \{\s*requireLiveOptIn\(\)",
        demo_live,
    ) is not None,
    "Demo Client live acceptance is not explicitly opt-in",
)

print("PHASE 9 STATIC CHECK: PASS")
