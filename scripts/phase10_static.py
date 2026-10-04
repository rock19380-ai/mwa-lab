#!/usr/bin/env python3
"""Offline release identity, provenance, and safety checks for Phase 10."""

import json
from pathlib import Path
import re
import subprocess
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main"
PHASE9_HEAD = "67f4b583ae1f7446c21a092c6345d45e9b258a9c"
PHASE9_TAG = "phase9-first-run-connection-ux-2026-10-04"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(f"Phase 10: {message}")


def read(path: str) -> str:
    return (ROOT / path).read_text()


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


require(
    subprocess.run(["git", "merge-base", "--is-ancestor", PHASE9_HEAD, "HEAD"],
                   cwd=ROOT, check=False).returncode == 0,
    "frozen Phase 9 commit is not an ancestor of HEAD",
)
require(git("cat-file", "-t", PHASE9_TAG) == "tag", "frozen Phase 9 tag is not annotated")
require(git("rev-parse", f"{PHASE9_TAG}^{{}}") == PHASE9_HEAD,
        "frozen Phase 9 tag does not resolve to the expected commit")

catalog = tomllib.loads(read("gradle/libs.versions.toml"))
require(catalog["versions"]["mwaWalletlib"] == "2.0.7", "walletlib version changed")
for library in ("solana-mobile-walletlib", "solana-mobile-clientlib"):
    require(catalog["libraries"][library]["version"]["ref"] == "mwaWalletlib",
            f"{library} no longer uses the pinned version")
require(re.search(r"\bversion\s*=\s*4\b", read("app/src/main/java/dev/mwalab/storage/MwaLabDatabase.kt")),
        "Room schema version changed")
schema = json.loads(read("app/schemas/dev.mwalab.storage.MwaLabDatabase/4.json"))
require(schema["database"]["version"] == 4, "Room 4 export is missing")

build = read("app/build.gradle.kts")
for expression, label in (
    (r'^\s*applicationId\s*=\s*"dev\.mwalab"\s*$', "applicationId"),
    (r"^\s*versionCode\s*=\s*1\s*$", "versionCode"),
    (r'^\s*versionName\s*=\s*"0\.1\.0-clockin"\s*$', "versionName"),
):
    require(re.search(expression, build, re.MULTILINE), f"unexpected {label}")
for name, assignment in (
    ("MWALAB_RELEASE_STORE_FILE", "storeFile"),
    ("MWALAB_RELEASE_STORE_PASSWORD", "storePassword"),
    ("MWALAB_RELEASE_KEY_ALIAS", "keyAlias"),
    ("MWALAB_RELEASE_KEY_PASSWORD", "keyPassword"),
):
    require(f'"{name}"' in build and
            re.search(rf"\b{assignment}\s*=\s*.*getValue\(\"{name}\"\)", build),
            f"operator signing input {name} is not wired to {assignment}")
require("releaseSigningVariables.filterValues { it.isNullOrBlank() }" in build and
        "storeFile.canonicalFile.toPath().startsWith(rootProject.projectDir.canonicalFile.toPath())" in build and
        'signingConfig = signingConfigs.findByName("operatorRelease")' in build,
        "conditional signing or external-keystore protection missing")
require(not re.search(r'\b(?:storePassword|keyPassword)\s*=\s*"', build),
        "literal signing password in Gradle source")

tracked = git("ls-files", "-z").split("\0")
for name in tracked:
    if not name:
        continue
    require(not (name.endswith((".jks", ".keystore")) or
                 Path(name).name in ("keystore.properties", "release-signing.properties")),
            f"tracked keystore/signing properties: {name}")
ignored = read(".gitignore").splitlines()
for pattern in ("*.jks", "*.keystore", "keystore.properties", "release-signing.properties"):
    require(pattern in ignored, f"missing .gitignore protection: {pattern}")

rpc = read("app/src/main/java/dev/mwalab/rpc/DevnetRpcGateway.kt")
require('const val DEVNET_RPC_URL = "https://api.devnet.solana.com"' in rpc,
        "fixed Devnet RPC authority missing")
source_text = "\n".join(path.read_text(errors="replace") for path in SOURCE.rglob("*") if path.is_file())
require(not re.search(r"(?i)(?:api\.mainnet-beta\.solana\.com|solana:mainnet|mainnet-beta)", source_text),
        "mainnet production authority appeared")
manifest = ET.parse(SOURCE / "AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
permissions = {element.get(android + "name") for element in manifest.findall("uses-permission")}
require("android.permission.CAMERA" not in permissions, "CAMERA permission shipped")
require("android.permission.INTERNET" in permissions, "INTERNET permission missing")
association = next((element for element in manifest.iter("activity")
                    if element.get(android + "name") == ".mwa.MobileWalletAdapterActivity"), None)
require(association is not None and association.get(android + "exported") == "true" and
        any(data.get(android + "scheme") == "solana-wallet"
            for data in association.iter("data")),
        "exported MWA association handler missing")
require(not re.search(r"(?i)\b(?:camerax|mlkit|barcode-scanning)\b", read("gradle/libs.versions.toml")),
        "Remote scanner dependency shipped")
for control in ("SCAN REMOTE MWA QR", "PASTE REMOTE MWA URI"):
    require(control not in source_text, f"Remote scanner/paste control shipped: {control}")
receive = read("app/src/main/java/dev/mwalab/ui/identity/LabIdentityScreen.kt")
require("AddressQr(payload = address)" in receive and
        "disposable Devnet test-wallet address" in receive and
        "It is NOT an MWA connection QR." in receive,
        "Receive Test SOL QR no longer identifies itself as address-only")
send = read("app/src/main/java/dev/mwalab/wallet/TestSolTransfer.kt")
require("ProtocolRecorder" not in send and "recordProtocolEvent" not in send,
        "direct Test Wallet Send routes through ProtocolRecorder")

release = read("docs/RELEASE.md")
require("Remote MWA: `BLOCKED_HIDDEN`" in release and
        "production-wallet compatibility: `NOT_VERIFIED`" in release and
        "signed RC1: pending operator signing secret" in release,
        "current release status overclaims remote or production-wallet compatibility")
workflow = read(".github/workflows/android.yml")
for token in (
    "      - phase10-release-candidate-compatibility-evidence",
    "run: ./scripts/phase10_static.sh",
    "run: ./gradlew assembleRelease",
    "run: test -f app/build/outputs/apk/release/app-release-unsigned.apk",
):
    require(token in workflow, f"Phase 10 CI requirement missing: {token}")

print("PHASE 10 STATIC CHECK: PASS")
