#!/usr/bin/env python3
"""MWA Lab Phase 5 deterministic repository gate.

This gate protects the Phase 5 simulation-as-diagnostic contract without
rewriting the historical Phase 3/4 gates.
"""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PHASE4_BASELINE = "6028375636251eb3071f0a5240a2ad1efc78131e"
PROTECTED = {
    "scripts/phase3_static.sh": "1de48365d68aa940dba18a8535495154eaf7c0549b64582066110242e72070e6",
    "scripts/phase4_static.sh": "889b082ca911f8e546d59c45d583daa819ea7a0c862f970a19ced049dd41c687",
    "scripts/phase4_static.py": "89065c87d5593ee207967cefca7029cddb30acf0b1f27b3c4a83538ca51d93c4",
    "app/schemas/dev.mwalab.storage.MwaLabDatabase/1.json": "1952a8bcaef8bf1c1910c0e99f1d13464aebbcb2b031376caee3130f9584874c",
    "app/schemas/dev.mwalab.storage.MwaLabDatabase/2.json": "d47469a9e925020a795ba726fc2a27bca4dfa506c0d6fac59716fa6a7eb9fac7",
    "app/src/main/java/dev/mwalab/signing/LabSigningService.kt": "3cce0f6a5d055c4175e5b2d1c6fab1232404fec74f50046b4de99428c09b4bba",
    "app/src/main/java/dev/mwalab/protocol/recorder/PersistentProtocolRecorder.kt": "f0e96965042db664593afab4ee3e8b81bc2f927aa23ff178e0100dda91f2ffc9",
    "app/src/main/java/dev/mwalab/transaction/LegacyTransactionCodec.kt": "a45109373214d7c845f81534e6b5cc7ee37e771dfbd4486157ad994ae4fb45fa",
}


def fail(message: str) -> None:
    raise AssertionError(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def text(rel: str) -> str:
    path = ROOT / rel
    require(path.is_file(), f"missing required file: {rel}")
    return path.read_text()


def git(*args: str) -> str:
    return subprocess.check_output(["git", "-c", f"safe.directory={ROOT}", *args], cwd=ROOT, text=True).strip()


def check_ancestry() -> None:
    subprocess.run(["git", "-c", f"safe.directory={ROOT}", "merge-base", "--is-ancestor", PHASE4_BASELINE, "HEAD"], cwd=ROOT, check=True)


def check_protected() -> None:
    for rel, expected in PROTECTED.items():
        require(sha(ROOT / rel) == expected, f"frozen predecessor changed: {rel}")


def check_schema() -> None:
    db = text("app/src/main/java/dev/mwalab/storage/MwaLabDatabase.kt")
    require(re.search(r"\bversion\s*=\s*3\b", db) is not None, "Room schema is not 3")
    require("MIGRATION_2_3" in db and "Migration(2, 3)" in db, "MIGRATION_2_3 missing")
    require("MIGRATION_1_2" in db and "Migration(1, 2)" in db, "MIGRATION_1_2 missing")
    require("fallbackToDestructiveMigration" not in db, "destructive migration fallback present")
    for n in (1, 2, 3):
        require((ROOT / f"app/schemas/dev.mwalab.storage.MwaLabDatabase/{n}.json").is_file(), f"schema {n} missing")
    schema = json.loads((ROOT / "app/schemas/dev.mwalab.storage.MwaLabDatabase/3.json").read_text())
    entities = schema["database"]["entities"]
    sim = next((e for e in entities if e.get("tableName") == "simulation_results"), None)
    require(sim is not None, "simulation_results table missing from schema 3")
    fields = {f["columnName"].lower() for f in sim.get("fields", [])}
    forbidden = {"raw_transaction", "transaction_bytes", "signed_transaction", "signature", "auth_token",
                 "association_token", "private_key", "seed", "rpc_request", "rpc_response"}
    require(not fields.intersection(forbidden), "unsafe simulation_results column present")


def check_rpc_contract() -> None:
    rpc = text("app/src/main/java/dev/mwalab/rpc/DevnetRpcGateway.kt")
    require('const val DEVNET_RPC_URL = "https://api.devnet.solana.com"' in rpc, "fixed Devnet RPC URL missing")
    require('rpc("simulateTransaction", params)' in rpc, "simulateTransaction boundary missing")
    require('.put("encoding", "base64")' in rpc, "base64 simulation encoding missing")
    require('.put("sigVerify", false)' in rpc, "sigVerify=false missing")
    require('.put("replaceRecentBlockhash", false)' in rpc, "replaceRecentBlockhash=false missing")
    require("URL(SolanaDevnetRpcGateway.DEVNET_RPC_URL)" in rpc, "RPC transport is not fixed endpoint")


def check_authority_separation() -> None:
    sim_dir = ROOT / "app/src/main/java/dev/mwalab/simulation"
    combined = "\n".join(p.read_text() for p in sorted(sim_dir.glob("*.kt")))
    for forbidden in ("LabSigningService", "ApprovalCoordinator", "sendTransaction(", "awaitCommitment("):
        require(forbidden not in combined, f"simulation authority leak: {forbidden}")
    settlement = text("app/src/main/java/dev/mwalab/simulation/SimulationDiagnosticSettlement.kt")
    require("ProtocolRecorder.CompletionResult" in settlement, "settlement does not observe canonical completion")
    require("recorder.complete" not in settlement and ".complete(" not in settlement,
            "simulation settlement completes protocol recorder")
    method = text("app/src/main/java/dev/mwalab/protocol/ProtocolMethod.kt")
    require("SIMULATE" not in method and "SIMULATION" not in method, "synthetic simulation ProtocolMethod present")
    host = text("app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt")
    require("registerSimulationTargets" in host, "simulation targets are not attached to real signing requests")
    codec = text("app/src/main/java/dev/mwalab/transaction/LegacyTransactionCodec.kt")
    require("versioned" in codec.lower() or "legacy" in codec.lower(), "legacy signing authority unexpectedly absent")


def check_security_and_ui() -> None:
    limits = text("app/src/main/java/dev/mwalab/simulation/SimulationLimits.kt")
    require("MAX_LOG_LINES" in limits and "MAX_LOG_LINE_CODE_POINTS" in limits, "program log bounds missing")
    require("REDACTED_LOG" in limits and "publicLogLine" in limits, "free-form RPC log redaction missing")
    all_main = "\n".join(p.read_text(errors="ignore") for p in (ROOT / "app/src/main").rglob("*.kt"))
    require("solana:mainnet" not in all_main and "mainnet-beta" not in all_main, "mainnet implementation detected")
    approval = text("app/src/main/java/dev/mwalab/approval/SigningApprovalScreen.kt")
    require("SIMULATE" in approval, "SIMULATE control missing")
    require("does not guarantee" in approval.lower(), "simulation PASS disclaimer missing from approval UI")
    require("APPROVE" in approval and "REJECT" in approval, "approval controls missing")
    require("ACTION_SEND" not in all_main and "ACTION_SEND_MULTIPLE" not in all_main,
            "Phase 7 Android share export introduced early")


def check_scope() -> None:
    main_files = list((ROOT / "app/src/main").rglob("*.kt")) + list((ROOT / "demo-client/src/main").rglob("*.kt"))
    names = {p.name for p in main_files}
    require("FaultEngine.kt" not in names and "FaultProfile.kt" not in names, "Phase 6 fault engine introduced")
    require("ReportExporter.kt" not in names and "ReportExportService.kt" not in names, "Phase 7 report export introduced")
    require((ROOT / "test-vectors/simulation/legacy-good-memo-template.hex").is_file(), "good simulation vector missing")
    require((ROOT / "test-vectors/simulation/legacy-bad-system-template.hex").is_file(), "bad simulation vector missing")
    require((ROOT / "scripts/verify_phase5_simulation_vectors.py").is_file(), "vector verifier missing")


def check_ci() -> None:
    ci = text(".github/workflows/android.yml")
    require("phase5-simulation-diagnostic-classification" in ci, "Phase 5 CI branch missing")
    require("phase5_static.sh" in ci, "Phase 5 CI gate missing")
    require("MwaLabDatabase/3.json" in ci, "CI does not route schema 3 to Phase 5")
    require("phase4_static.sh" in ci, "historical Phase 4 gate routing disappeared")


def check_docs() -> None:
    required = ["README.md", "docs/ARCHITECTURE.md", "docs/SECURITY.md", "docs/TESTING.md",
                "docs/PROTOCOL_SUPPORT.md", "CHANGELOG.md", "PHASE_5_REPORT.md", "PHASE_5_FILES.txt",
                "docs/evidence/phase5/README.md"]
    for rel in required:
        require((ROOT / rel).is_file(), f"Phase 5 document missing: {rel}")
    readme = text("README.md")
    require("Phase 5" in readme and "Simulation is diagnostic evidence only" in readme,
            "README Phase 5 caveat missing")
    architecture = text("docs/ARCHITECTURE.md")
    require("diagnostic child branch" in architecture and "unchanged authority" in architecture,
            "architecture authority-separation statement missing")
    security = text("docs/SECURITY.md")
    for phrase in ("transient", "raw RPC", "bounded", "cannot authorize", "cannot sign", "cannot submit"):
        require(phrase.lower() in security.lower(), f"security Phase 5 statement missing: {phrase}")
    protocol = text("docs/PROTOCOL_SUPPORT.md")
    for phrase in ("legacy simulation", "v0 simulation", "v0 signing", "does not guarantee"):
        require(phrase.lower() in protocol.lower(), f"protocol support truth missing: {phrase}")
    inventory = [x for x in text("PHASE_5_FILES.txt").splitlines() if x and not x.startswith("#")]
    require(inventory == sorted(set(inventory)), "PHASE_5_FILES.txt must be sorted and unique")
    require(all((ROOT / p).is_file() for p in inventory), "PHASE_5_FILES.txt contains missing file")


def main() -> None:
    checks = [check_ancestry, check_protected, check_schema, check_rpc_contract, check_authority_separation,
              check_security_and_ui, check_scope, check_ci, check_docs]
    for check in checks:
        check()
    print("PHASE 5 STATIC GATE: PASS")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"PHASE 5 STATIC GATE: FAIL: {exc}", file=sys.stderr)
        raise
