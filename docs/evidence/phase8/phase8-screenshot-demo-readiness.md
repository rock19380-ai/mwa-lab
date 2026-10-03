# Phase 8 final screenshot and demo-readiness audit

Date: 2026-10-03 (Asia/Yangon). The six captured screenshots under `screenshots/phase8/` came from the exact repaired debug APK and real persisted Devnet/test sessions on `emulator-5554`. They were visually reviewed at 1080×2424; no screenshot state was fabricated.

| File | Observed state |
|---|---|
| `home-normal.png` | NORMAL, Devnet/no-real-funds, developer-tool identity, latest PASS session |
| `fault-sign-reject-active.png` | Selected `FAULT_SIGN_REJECT`, active and intentional warning, expected `ERROR_NOT_SIGNED` |
| `session-injected-error-not-signed.png` | Persisted failed sign-and-send, `INJECTED`, and independent fault ID |
| `session-success.png` | Persisted PASS and successful sign-and-send timeline |
| `session-success-export.png` | Same successful session with sanitized Markdown/JSON/Copy controls |
| `signing-approval-devnet.png` | Real pending request, Devnet safety, transaction diagnostics, explicit REJECT/APPROVE |

The first approval capture exposed Android status-bar overlap. `SigningApprovalScreen.kt` gained top system-bar padding; the final capture was taken after the repaired build passed full local and connected tests and visibly clears the title from the status bar. No protocol or approval authority changed.

The injected screenshots explicitly label the condition intentional and do not present it as an observed production-wallet defect. The approval screenshot shows public Devnet metadata only, not secret key material or raw transaction bytes. The final active fault was restored to persisted NORMAL after screenshot capture. These are screenshot candidates, not production-wallet compatibility proof.

Result: **SIX FACTUAL SCREENSHOT CANDIDATES CAPTURED AND REVIEWED**.
