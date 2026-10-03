# Phase 8 prefreeze verification

Date: 2026-10-03 (Asia/Yangon). Pre-closeout HEAD: `8c14ded3219fb03e169f6cd9c93e6898a5d72187`. Branch: `phase8-world-class-ux-positioning`. Result: **PASS — ready for the closeout commit and exact-head GitHub Actions**. This file does not claim that CI or the freeze tag already exists.

## Hostile audit

| Boundary | Observed result |
|---|---|
| History | Phase 7 frozen commit `d4fbe20ff2d4b63d6f531f2a9cd7d6fadbe8a7e1` remains an ancestor; its annotated tag dereferences to the exact same commit. No Phase 8 tag existed at audit time. The Prompt-2 HEAD and remote branch were still `8c14ded3219fb03e169f6cd9c93e6898a5d72187`. |
| Frozen authority | Against the Phase 7 predecessor, the only protected-authority-directory source change is Phase 8 presentation file `SigningApprovalScreen.kt`. Protocol, approval coordinator, signing, RPC, fault, storage, report, and schema authority remained unchanged. Phase 1–7 historical evidence paths had no diff. |
| Database/dependency | Room `version = 3`; schema exports 1/2/3 only; no schema 4 or `Migration(3,4)`. walletlib `2.0.7`. |
| Network/settings | Fixed `https://api.devnet.solana.com` and `solana:devnet`; Mainnet displayed `Unavailable`; no custom RPC editor or new network switch. |
| Secrets/approval | No key/seed/mnemonic/raw token/payload/signature/RPC-body export field, public Identity Reset, automatic approval, or automatic signing. The actual final JSON/Markdown artifacts passed bounded semantic/sentinel checks. |
| Fault truthfulness | Final injected event independently recorded `failureSource=INJECTED` and `injectedFaultId=FAULT_SIGN_REJECT`; UI labeled it intentional. After testing, UI and persisted `active_fault_id` both showed NORMAL. |
| UX | Five-second Home audit passed; Phase 8 light/dark and 1.3× large-font audit retained; final connected constrained dark/large-font approval test passed; final repaired approval screenshot showed the title clear of Android status bar. Navigation, Session Detail, Lab Identity, and read-only Settings were in the full connected suite. |
| Local gate | `./scripts/phase8_static.sh`, offline Gradle lint/test/debug/AndroidTest builds, and `git diff --check` passed after the only final code repair. |
| JVM XML | App 287, demo-client 10; zero failures, errors, skips. |
| Connected XML | App 136, demo-client 1; zero failures, errors, skips; suites sequential on one emulator, font scale restored to 1.0. |
| Canonical NORMAL | Final session `20671410-5a3a-4f1e-b28e-5166d542462d`: Devnet confirmed client PASS, visible approval once, persisted sign-and-send `SUCCESS / NONE / null`, 5,000-lamport fee. One earlier same-build `RPC_NETWORK` failure was disclosed and was not counted as PASS. |
| Canonical injected | Final session `d34d35dd-78e4-47fa-8e83-8eb458d0ad1a`: client expected rejection PASS without approval tap; persisted `FAILURE / ERROR_NOT_SIGNED (-3) / INJECTED / FAULT_SIGN_REJECT`. |
| Exports | Markdown and JSON Share Sheet plus Copy Summary worked for both reopened persisted sessions. Read-only Phase 7 verifier matched both formats to Room; actual artifact scan found zero forbidden raw/secret keys and known sentinels. |
| Security | Applicable Phase 6 security, Phase 7 export security, Phase 8 static/security continuation passed. Historical Phase 7 whole-directory scanner's intentional approval-presentation mismatch is documented without modifying it. |
| Positioning/compatibility | Five-second and positioning audits passed. Production-wallet compatibility remains **NOT VERIFIED IN THIS RELEASE**. |
| Evidence/date | Final evidence describes executed checks, actual current XML counts, real Devnet sessions and screenshots, and the local 2026-10-03 date. No CI run ID or future freeze result is invented. Phase 9 has not started. |

The closeout commit must include `PHASE_8_FILES.txt` and the exact final evidence. Then only a GitHub Actions run whose `headSha` equals that closeout commit and whose conclusion is success may authorize the dated annotated Phase 8 freeze tag.

## Post-closeout exact-head CI incident

The first closeout SHA `b70c4c0e21eee5566bc903e8e8ca893915bdc3ab` was pushed and its exact-head Android CI run [37092491540](https://github.com/rock19380-ai/mwa-lab/actions/runs/37092491540) completed with **failure**. Lint, unit tests, both debug APK builds, and both AndroidTest APK builds passed. The unconditional historical Phase 1 step failed because it searched `MainActivity.kt` for `MWA LAB TEST ENDPOINT`; Phase 8 had intentionally moved safety text to the current shared banner and signing screen. This run does not authorize a freeze.

The Phase 8 repair gates the historical Phase 1 workflow step to checkouts without `scripts/phase8_static.sh`; the Phase 8 step still runs on Phase 8 checkout. `scripts/phase8_static.py` now asserts that routing. Historical Phase 1/7 scripts and all application sources are unchanged by this repair. Full offline local build/lint/JVM verification, Phase 8 static continuation, shell syntax, and diff hygiene passed after the change. The new exact-head CI run must complete successfully before any tag.
