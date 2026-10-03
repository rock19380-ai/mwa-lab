# MWA Lab — Phase 8 World-Class UX and Positioning Report

Status at generation: **FREEZE CANDIDATE**. Phase 8 implementation, final local/device suites, canonical NORMAL and `FAULT_SIGN_REJECT` live regressions, sanitized export checks, screenshots, and security audit are complete. Exact-head GitHub Actions success and the annotated freeze tag remain the final provenance actions.

Frozen predecessor: `d4fbe20ff2d4b63d6f531f2a9cd7d6fadbe8a7e1` / `phase7-sanitized-diagnostic-reports-2026-10-02`. Starting final-pass HEAD: `8c14ded3219fb03e169f6cd9c93e6898a5d72187`. Branch: `phase8-world-class-ux-positioning`.

## Completed scope

- First-run explanation and Home first viewport establish the Mobile Wallet Adapter protocol debugger, deterministic fault lab, Devnet-only scope, and no-real-funds rule.
- Stable light/dark visual system, typed five-destination shell, readable session list/detail timeline, deliberate Fault Lab, read-only Lab Identity, and safe Settings.
- Session failure summary exposes method, protocol error, terminal failure source, and independent injected fault ID. Transaction and simulation details remain subordinate to protocol evidence.
- The signing screen keeps explicit REJECT/APPROVE and Devnet safety visible. A final device screenshot exposed a title/status-bar overlap; the only final code repair added top system-bar padding to `SigningApprovalScreen.kt`. No approval coordinator, callback, signing, storage, network, fault, report, or Room authority changed.
- Six factual final-build screenshots under `screenshots/phase8/` were visually reviewed. They include NORMAL Home, intentional fault, injected failure, successful detail/report controls, and real signing approval.

Identity Reset remains deliberately unexposed. Room remains schema 3 with no `Migration(3,4)`. walletlib remains pinned to 2.0.7. Network authority remains fixed to Solana Devnet, with no editable RPC or mainnet/testnet path. Phase 1–7 historical evidence and the Phase 7 freeze tag remain unchanged.

## Final verification

| Gate | Result |
|---|---|
| Phase 8 static and predecessor continuation | PASS |
| Offline lint, unit tests, debug APKs, AndroidTest APKs | PASS |
| App JVM XML | 287 tests, 0 failures/errors/skips |
| Demo-client JVM XML | 10 tests, 0 failures/errors/skips |
| App connected XML | 136 tests, 0 failures/errors/skips |
| Demo-client connected XML | 1 test, 0 failures/errors/skips |
| NORMAL canonical sign-and-send | PASS after one transient `RPC_NETWORK` attempt |
| `FAULT_SIGN_REJECT` canonical sign-and-send | PASS; no approval tap |
| Markdown and JSON Share Sheet, Copy Summary | PASS for both persisted sessions |
| Phase 7 read-only Markdown/JSON/Room parity | PASS for both final sessions |
| Applicable Phase 6/7/8 security continuation | PASS |
| Five-second, light/dark, large-font audits | PASS / retained from Phase 8 hardening and final connected coverage |
| Persisted active fault restored | NORMAL |
| `git diff --check` | PASS |

Final NORMAL session: `20671410-5a3a-4f1e-b28e-5166d542462d` — `SIGN_AND_SEND_TRANSACTIONS / SUCCESS / NONE / null injected fault`, with confirmed Devnet submission and a 5,000-lamport fee. Final injected session: `d34d35dd-78e4-47fa-8e83-8eb458d0ad1a` — `SIGN_AND_SEND_TRANSACTIONS / FAILURE / ERROR_NOT_SIGNED (-3) / INJECTED / FAULT_SIGN_REJECT`.

The transient failed NORMAL attempt, session `9a0fd4d1-ed65-4935-9e16-ae788c959b3b`, persisted `ERROR_NOT_SUBMITTED / RPC_NETWORK` and charged no fee. The successful retry used unchanged code. It is disclosed in the final live evidence rather than treated as a PASS.

The original Phase 7 whole-directory security scanner rejects the intended Phase 8 approval presentation diff. Its script and historical evidence were not changed. The current Phase 8 continuation gate preserves the relevant authority/report/security checks and passes. An explicit scan of the actual final Markdown/JSON files found zero forbidden raw/secret JSON keys or known secret sentinels.

Production-wallet compatibility: **NOT VERIFIED IN THIS RELEASE**. MWA Lab's own endpoint and emulator tests are not evidence for Phantom, Solflare, Seed Vault, or another production wallet.

## Freeze provenance boundary

The first closeout commit `b70c4c0e21eee5566bc903e8e8ca893915bdc3ab` reached CI, but run `37092491540` failed at an obsolete Phase 1 source-location assertion after lint, unit tests, and APK builds passed. A narrow Phase 8 CI routing repair now excludes that historical assertion on Phase 8 checkout while retaining the Phase 8 continuation gate; no application code or historical script changed. See the prefreeze evidence for the exact failure. The repair commit requires its own successful exact-head CI before freezing.

The exact final closeout SHA, GitHub Actions run ID/URL, and successful conclusion cannot be embedded in this same source commit without changing its HEAD. The annotated Phase 8 freeze tag must record them only after that exact HEAD passes CI. No Phase 8 tag or Phase 9 work is claimed by this report.

Detailed receipts: [Phase 8 evidence](docs/evidence/phase8/).
