# Phase 8 final security and invariant scan

Date: 2026-10-03 (Asia/Yangon). Scope: repaired Phase 8 presentation source, frozen predecessor authority, final live reports, and app-private active-fault state.

Applicable current gates passed:

```text
python3 scripts/phase6_security_scan.py
python3 scripts/phase7_export_security.py
python3 scripts/phase8_static.py
./scripts/phase8_static.sh
```

The original `phase7_security_scan.py` was also run directly and failed its historical whole-directory freeze assertion. Its protected-directory diff against its Phase 6 base contains only `app/src/main/java/dev/mwalab/approval/SigningApprovalScreen.kt`, which Phase 8 intentionally changed for presentation. It was not edited to accept Phase 8. `phase8_static.py` carries forward the approval coordinator, signing, fault, persistence, fixed Devnet, report and sharing security assertions while permitting this presentation file to evolve.

An explicit keyword review of Phase 8 UI/resources found only negative/safety statements for private key, seed, mnemonic, and auth token; Settings displays Mainnet as `Unavailable` and the fixed Devnet RPC read-only. No UI control for key import/display, Identity Reset, custom RPC, mainnet, automatic approval, or automatic signing was found. Room remains version 3 with no schema 4 or `Migration(3,4)`; walletlib remains 2.0.7. Frozen Phase 1–7 evidence files were unchanged.

The existing read-only Phase 7 device verifier confirmed both final live reports against Room. A separate read-only semantic scan of the actual app-private Markdown/JSON artifacts found both formats present and bounded below 1 MiB. Normal artifacts were 4,733/3,340 bytes (Markdown/JSON); injected artifacts were 4,815/3,299 bytes. It found zero forbidden raw/secret JSON keys and zero known secret sentinels. The JSON event classifications were `SUCCESS / NONE / null` and `FAILURE / INJECTED / FAULT_SIGN_REJECT`, respectively. Security-notice sentences that say secrets are excluded were not treated as leaks.

The scan is a bounded source/model/sentinel check, not a claim that unknown secret values can be exhaustively detected. Canonical sanitization and the hostile JVM/connected tests supply the complementary behavioral evidence.
