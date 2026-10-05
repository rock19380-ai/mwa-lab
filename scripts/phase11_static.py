#!/usr/bin/env python3
"""Offline Phase 11 hard-freeze policy, backed by the signed RC source commit."""

import csv
import io
import json
from pathlib import Path
import re
import subprocess
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PHASE10_TAG = "phase10-release-candidate-compatibility-evidence-2026-10-05"
PHASE10_HEAD = "fa7a909f50ab0702327bd98f28461f60fe7ad082"
RC_SOURCE = "945295a3e0124af11a5d75a76c7444f09586339e"
PROTECTED = ("app/src/main", "demo-client/src/main", "app/build.gradle.kts",
             "gradle/libs.versions.toml", "settings.gradle.kts", "gradle.properties")
REPAIRS = "docs/evidence/phase11/01-freeze-guard/approved-repairs.tsv"
REPAIR_COLUMNS = ("repair_id", "severity", "symptom", "reproduction", "affected_file",
                  "why_release_blocking", "minimal_fix", "tests", "runtime_gate", "commit", "status")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(f"Phase 11: {message}")


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=ROOT, text=True).strip()


def git_paths(*args: str) -> set[str]:
    return set(filter(None, subprocess.check_output(["git", *args], cwd=ROOT).decode().split("\0")))


def read(path: str) -> str:
    return (ROOT / path).read_text()


def changed_protected_paths() -> set[str]:
    return (git_paths("diff", "--name-only", "-z", RC_SOURCE, "--", *PROTECTED) |
            git_paths("ls-files", "--others", "--exclude-standard", "-z", "--", *PROTECTED))


def validate_repairs(changes: set[str], tsv: str, commit_paths: dict[str, set[str]]) -> None:
    rows = csv.DictReader(io.StringIO(tsv), delimiter="\t")
    require(tuple(rows.fieldnames or ()) == REPAIR_COLUMNS, "repair ledger header changed")
    approved: set[str] = set()
    identifiers: set[str] = set()
    for row in rows:
        require(None not in row and None not in row.values() and
                all(value and value.strip() == value for value in row.values()),
                "repair ledger has an incomplete or malformed row")
        require(row["repair_id"] not in identifiers, "duplicate repair ID")
        identifiers.add(row["repair_id"])
        require(row["severity"] in ("P0", "P1") and row["status"] == "accepted",
                "only accepted P0/P1 repairs can change protected source")
        require(re.fullmatch(r"[0-9a-f]{40}", row["commit"]) is not None,
                "repair must identify a committed change")
        require(row["affected_file"] in changes, "repair ledger describes no current protected drift")
        require(row["affected_file"] in commit_paths.get(row["commit"], set()),
                "repair commit does not change its claimed protected file")
        approved.add(row["affected_file"])
    require(changes == approved,
            f"unapproved production drift: {sorted(changes - approved)}; unmatched repairs: {sorted(approved - changes)}")


def check() -> None:
    require(git("cat-file", "-t", PHASE10_TAG) == "tag", "Phase 10 tag is not annotated")
    require(git("rev-parse", f"{PHASE10_TAG}^{{}}") == PHASE10_HEAD,
            "Phase 10 tag does not resolve to the expected predecessor")
    require(subprocess.run(["git", "merge-base", "--is-ancestor", PHASE10_HEAD, "HEAD"],
                           cwd=ROOT, check=False).returncode == 0, "Phase 10 is not an ancestor")
    require(subprocess.run(["git", "merge-base", "--is-ancestor", RC_SOURCE, PHASE10_HEAD],
                           cwd=ROOT, check=False).returncode == 0, "signed RC source is not in Phase 10")
    require(not git_paths("diff", "--name-only", "-z", RC_SOURCE, PHASE10_HEAD,
                          "--", *PROTECTED), "Phase 10 protected source differs from signed RC")

    changes = changed_protected_paths()
    ledger = read(REPAIRS)
    rows = list(csv.DictReader(io.StringIO(ledger), delimiter="\t"))
    commits = {row.get("commit", "") for row in rows}
    commit_paths: dict[str, set[str]] = {}
    for commit in commits:
        require(re.fullmatch(r"[0-9a-f]{40}", commit) is not None and
                subprocess.run(["git", "merge-base", "--is-ancestor", commit, "HEAD"],
                               cwd=ROOT, check=False, stdout=subprocess.DEVNULL,
                               stderr=subprocess.DEVNULL).returncode == 0,
                "repair commit is not an ancestor of HEAD")
        commit_paths[commit] = git_paths("diff-tree", "--no-commit-id", "--name-only", "-r",
                                        "-z", commit, "--", *PROTECTED)
    validate_repairs(changes, ledger, commit_paths)

    catalog = tomllib.loads(read("gradle/libs.versions.toml"))
    require(catalog["versions"]["mwaWalletlib"] == "2.0.7", "walletlib/clientlib version drift")
    for library in ("solana-mobile-walletlib", "solana-mobile-clientlib"):
        require(catalog["libraries"][library]["version"]["ref"] == "mwaWalletlib",
                f"{library} does not use the pinned version")
    require(re.search(r"\bversion\s*=\s*4\b", read("app/src/main/java/dev/mwalab/storage/MwaLabDatabase.kt")),
            "Room schema drift")
    require(json.loads(read("app/schemas/dev.mwalab.storage.MwaLabDatabase/4.json"))["database"]["version"] == 4,
            "Room schema export missing")
    build = read("app/build.gradle.kts")
    for pattern, label in ((r'^\s*applicationId\s*=\s*"dev\.mwalab"\s*$', "package"),
                           (r"^\s*versionCode\s*=\s*1\s*$", "versionCode"),
                           (r'^\s*versionName\s*=\s*"0\.1\.0-clockin"\s*$', "versionName")):
        require(re.search(pattern, build, re.MULTILINE), f"{label} drift")
    for variable, field in (("MWALAB_RELEASE_STORE_FILE", "storeFile"),
                            ("MWALAB_RELEASE_STORE_PASSWORD", "storePassword"),
                            ("MWALAB_RELEASE_KEY_ALIAS", "keyAlias"),
                            ("MWALAB_RELEASE_KEY_PASSWORD", "keyPassword")):
        require(re.search(rf'\b{field}\s*=\s*.*getValue\("{variable}"\)', build),
                f"external signing input missing: {variable}")
    require("releaseSigningVariables.filterValues { it.isNullOrBlank() }" in build and
            "storeFile.canonicalFile.toPath().startsWith(rootProject.projectDir.canonicalFile.toPath())" in build,
            "external-only and all-or-nothing signing policy changed")
    require(not re.search(r'\b(?:storePassword|keyPassword)\s*=\s*"', build),
            "literal signing password in build source")
    tracked = git_paths("ls-files", "-z")
    require(not any(path.lower().endswith((".jks", ".keystore", ".p12", ".pfx")) or
                    Path(path).name in ("keystore.properties", "release-signing.properties")
                    for path in tracked), "tracked signing material")
    require(all(pattern in read(".gitignore").splitlines()
                for pattern in ("*.jks", "*.keystore", "keystore.properties", "release-signing.properties")),
            "signing ignore rules removed")

    app_source = ROOT / "app/src/main"
    demo_source = ROOT / "demo-client/src/main"
    app_text = "\n".join(path.read_text(errors="replace") for path in app_source.rglob("*") if path.is_file())
    production_text = app_text + "\n" + "\n".join(
        path.read_text(errors="replace") for path in demo_source.rglob("*") if path.is_file())
    require('const val DEVNET_RPC_URL = "https://api.devnet.solana.com"' in
            read("app/src/main/java/dev/mwalab/rpc/DevnetRpcGateway.kt"), "Devnet RPC authority changed")
    for pattern in (r"api\.mainnet-beta\.solana\.com", r"solana:mainnet", r"mainnet-beta",
                    r"(?i)\b(?:camerax|mlkit|barcode-scanning)\b"):
        require(not re.search(pattern, production_text + "\n" + read("gradle/libs.versions.toml")),
                f"forbidden production authority/scanner dependency: {pattern}")
    for control in ("SCAN REMOTE MWA QR", "PASTE REMOTE MWA URI"):
        require(control not in app_text, f"Remote control shipped: {control}")
    manifest = ET.parse(app_source / "AndroidManifest.xml").getroot()
    demo_manifest = ET.parse(demo_source / "AndroidManifest.xml").getroot()
    android = "{http://schemas.android.com/apk/res/android}"
    for name, document in (("app", manifest), ("demo", demo_manifest)):
        permissions = {element.get(android + "name") for element in document.findall("uses-permission")}
        require("android.permission.INTERNET" in permissions, f"{name} INTERNET permission missing")
        require("android.permission.CAMERA" not in permissions, f"{name} CAMERA permission added")
    receive = read("app/src/main/java/dev/mwalab/ui/identity/LabIdentityScreen.kt")
    require("AddressQr(payload = address)" in receive and
            "disposable Devnet test-wallet address" in receive and
            "It is NOT an MWA connection QR." in receive and "DEVNET ONLY" in receive,
            "Receive QR truthfulness changed")
    send = read("app/src/main/java/dev/mwalab/wallet/TestSolTransfer.kt")
    require("ProtocolRecorder" not in send and "recordProtocolEvent" not in send,
            "direct Test Wallet send creates protocol events")
    release = read("docs/RELEASE.md")
    for claim in ("Local MWA:                       VERIFIED / SHIPPED",
                  "Remote MWA:                      BLOCKED_HIDDEN / NOT RELEASED",
                  "Remote QR scanner:               OMITTED",
                  "Production-wallet compatibility: NOT_VERIFIED",
                  "Mainnet/testnet:                  UNAVAILABLE / REJECTED"):
        require(claim in release, f"release scope changed: {claim.strip()}")
    require("Production-wallet compatibility remains **NOT VERIFIED**" in read("README.md"),
            "README broadens compatibility claims")
    current_claims = release.split("## Phase 10 RC1 revision 2", 1)[-1] + "\n" + \
        read("README.md").split("## Phase 10 — Signed release candidate + compatibility evidence", 1)[-1]
    require(not re.search(r"(?im)^\s*(?:Remote MWA|Remote QR scanner|Production-wallet compatibility)\s*:\s*"
                          r"(?:VERIFIED|SHIPPED|SUPPORTED|PASS)\b", current_claims),
            "current release documentation claims unverified compatibility")
    forbidden = (r"(?i)\bsolana[ -]pay\b", r"(?i)\bspl[ -]token[ -]send\b",
                 r"(?i)\bseed[ -]phrase[ -]import\b", r"(?i)\bproduction[ -]wallet[ -]import\b",
                 r"(?i)\b(?:ai|backend)[ -]feature\b")
    for path in changes:
        if path.startswith(("app/src/main/", "demo-client/src/main/")) and (ROOT / path).is_file():
            text = read(path)
            require(not any(re.search(pattern, text) for pattern in forbidden),
                    f"forbidden feature in changed production source: {path}")
    workflow = read(".github/workflows/android.yml")
    for token in ("      - phase11-hard-code-freeze", "bash -n ./scripts/phase11_static.sh",
                  "run: ./scripts/phase11_static.sh", "run: ./gradlew lint", "run: ./gradlew test",
                  "run: ./gradlew assembleDebug", "run: ./gradlew assembleRelease",
                  "run: ./gradlew :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest"):
        require(token in workflow, f"CI gate missing: {token}")
    print("PHASE 11 HARD CODE FREEZE STATIC CHECK: PASS")


if __name__ == "__main__":
    check()
