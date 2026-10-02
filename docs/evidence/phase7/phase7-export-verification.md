# Phase 7.11–7.21 local export verification

Checkpoint: `phase7-sanitized-diagnostic-reports`, based on Phase 7.0–7.10 commit `74db72d90b4f9500edeb65e244c05642b8bfd03c`. Verification was performed on the final Prompt 2 application code before this evidence note was added. Room remains schema 3 and walletlib remains 2.0.7. No Phase 7 freeze tag is implied.

| Check | Result |
| --- | --- |
| `python3 scripts/phase7_export_security.py` | PASS |
| `python3 scripts/phase7_static.py` | PASS |
| `python3 scripts/phase7_design_check.py` | PASS |
| `sha256sum -c docs/evidence/phase7/phase7-baseline.sha256` | PASS, seven frozen files |
| `./gradlew test lint assembleDebug :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest --offline` | PASS, 280 app JVM tests and 10 demo-client JVM tests, zero failures or skips; lint and all requested APK builds passed |
| Connected `DiagnosticReportExportInstrumentedTest` on `emulator-5554` | AUTOMATED PASS, 2 tests, zero failures |
| Connected `SessionsUiInstrumentedTest` on `emulator-5554` | AUTOMATED PASS, 10 tests, zero failures |
| `python3 scripts/phase7_device_report_verify.py --serial emulator-5554 --session-id 2171aacc-9420-4802-b1b3-b9401d950242` | AUTOMATED PASS, complete NORMAL sign-and-send session, 3 events, Markdown/JSON/Room parity |
| `python3 scripts/phase7_device_report_verify.py --serial emulator-5554 --session-id 90e93e63-a01c-427e-a562-8481ca64af72` | AUTOMATED PASS, complete injected rejection session, 3 events, Markdown/JSON/Room parity |

The connected report tests cover the dedicated cache path, provider and read grants, sanitized intent metadata, hostile input, and reporting from reopened persisted Room state. The Session Detail tests cover export controls and truthful partial/injected labels. The device verifier reads app-private cache artifacts and copies Room files to a temporary host directory for comparison; it does not change app state or retain the database copy.

On `emulator-5554`, the funded Devnet test identity completed the canonical NORMAL sign-and-send demo with visible wallet approval. The demo client reported a confirmed Devnet submission. After force-stop/relaunch, the historical wallet session remained complete with `SIGN_AND_SEND_TRANSACTIONS / SUCCESS / NONE` and no injected fault. Markdown and JSON both opened Android Share Sheet; Copy Summary was available. This is **MANUALLY OBSERVED PASS** for the live flow, with automated persisted-report parity above. An earlier fee-free NORMAL signing and report path also passed with a verified signature; the canonical sign-and-send run is the stronger final acceptance.

With `FAULT_SIGN_REJECT` selected, the demo client requested the same sign-and-send operation and received `ERROR_NOT_SIGNED` (`-3`). After force-stop/relaunch, the wallet session recorded `SIGN_AND_SEND_TRANSACTIONS / FAILURE / INJECTED / FAULT_SIGN_REJECT`. Session Detail displayed `INTENTIONAL TEST CONDITION`; Markdown and JSON opened Android Share Sheet, and Copy Summary reported success. This is **MANUALLY OBSERVED PASS** for the live flow, with automated persisted-report parity above. Fault Lab was then returned to `NORMAL` and visibly showed `SELECTED`.

Both generated Markdown reports were inspected directly. They include ordered methods, event durations, outcomes, protocol errors and sources, the active fault marker where present, capability values, transaction SHA-256 and parsed public metadata, completeness, reproduction context, simulation evidence limits, and a security notice. No simulation attempt was recorded in these live sessions, which the reports say explicitly. The reports exclude raw payloads and cannot reproduce exact transaction bytes from the sanitized artifact alone.

The frozen `./scripts/phase6_static.sh` was also attempted after Prompt 2. Its Phase 5 and Phase 6 design, vector, and security subchecks passed, but its Phase 6 scope check exited FAIL because it forbids `ACTION_SEND` anywhere in main source (`Phase 7 sharing introduced early`). That prohibition was correct for the Phase 6 freeze and is expected to reject Phase 7's authorized Share Sheet. The Phase 6 script was left unchanged. The separate Phase 7 static/export checks above validate the current scope, and the frozen baseline hashes still match.
