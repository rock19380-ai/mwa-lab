# Changelog

## Unreleased

### Phase 11 — Hard code freeze candidate

- Preserved the Phase 10 protected production source, external release signer,
  fixed Devnet authority and shipped Local MWA scope; no P0/P1 production
  repair or feature addition.
- Verified signed RC2 `MWA-Lab-v0.1.0-clockin-rc2.apk` (SHA-256
  `5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc`)
  with clean first run, cold dApp-first and funded Local MWA, deterministic
  injected rejection, disposable Test Wallet, report-byte audit and bounded
  runtime logcat audit.
- Prepared final screenshot, demo, deck, compatibility and clean-clone
  submission evidence. Exact-head CI/tag provenance is established after the
  closeout commit and recorded in the external freeze receipt.

### Phase 10 — Release Candidate + Compatibility Evidence

- Locked release identity to `dev.mwalab` / versionCode `1` / versionName
  `0.1.0-clockin` and added external-credential release signing with fail-closed
  partial configuration and no repository-held keystore/password.
- Added Phase 10 static/CI release gates while preserving walletlib/clientlib
  2.0.7, Room schema 4, fixed Devnet authority, hidden Remote MWA, absent CAMERA,
  and unavailable mainnet.
- Built and verified signed `MWA-Lab-v0.1.0-clockin-rc1-r2.apk`; refreshed the
  Test Wallet secondary action label to one-line `REFRESH` without changing
  behavior or layout authority.
- Passed signed-RC manual first-run, cold Local MWA, sign-message, finalized
  NORMAL sign-and-send, injected `ERROR_NOT_SIGNED (-3)` sign-and-send, restart
  persistence, report export/content audit, and direct 1-lamport Send Test SOL.
- Proved direct Test Wallet Send remains outside MWA protocol history and added
  fail-closed unit coverage for identity changes after review and preparation RPC
  failure.
- No live airdrop was invoked in Phase 10; the exact installed disposable Devnet
  identity was manually funded. Remote MWA remains blocked/not released and
  production-wallet compatibility remains NOT_VERIFIED.

### Phase 9 — First-run connection and Test Wallet UX

- Added explicit Local MWA authorization consent, first-run connection guidance,
  disposable Test Wallet balance/Receive QR/funding UI, Room schema 4 transport
  metadata, and native Devnet Send Test SOL.
- Kept Receive QR address-only, direct wallet utilities outside MWA protocol
  history, Remote MWA hidden, CAMERA absent, and mainnet unavailable.
- Completed live Local NORMAL/injected acceptance and froze the exact-head Phase 9
  checkpoint at `phase9-first-run-connection-ux-2026-10-04`.

### Phase 8 — World-class UX and positioning

- Added a stable light/dark design system, first-run product explanation, typed
  five-destination shell, read-only Lab Identity, and safe appearance Settings.
- Made session outcomes, timeline, injected faults, and sanitized report actions
  easier to scan while preserving their persisted authority and classifications.
- Kept Devnet/no-real-funds warnings and explicit approval visible; corrected
  the approval title's Android status-bar inset after final device review.
- Added final local, connected, canonical Devnet NORMAL/injected, security,
  screenshot, and prefreeze evidence without expanding protocol behavior.
- Retained Room schema 3, walletlib 2.0.7, fixed Devnet RPC, and unexposed
  Identity Reset. Production-wallet compatibility is NOT VERIFIED IN THIS RELEASE.

### Phase 7 — Sanitized Diagnostic Reports

- Added canonical typed report v1 over persisted session, event, capability,
  transaction, simulation, and fault evidence without changing Room schema 3.
- Added bounded allowlist sanitization and deterministic Markdown, JSON, and
  clipboard summary projections with explicit partial-session and injected vs
  observed failure semantics.
- Added app-private cache export, a report-only non-exported FileProvider,
  Android Share Sheet actions, and Session Detail export controls.
- Added hostile-value, restart, provider, renderer, and device report parity
  verification plus a dedicated Phase 7 CI/static/security gate.
- Preserved walletlib 2.0.7, Devnet-only execution, and predecessor protocol,
  signing, fault, and simulation authorities.

### Added

- Phase 5 user-triggered legacy transaction simulation through the fixed Solana Devnet RPC boundary.
- Room schema v3 sanitized simulation child evidence with deterministic attempt ordering and restart persistence.
- Explicit PASS/FAIL/UNAVAILABLE diagnostic classification preserving parent MWA authority.
- Bounded/redacted program-log diagnostics, deterministic simulation vectors, hostile sentinel scans, and a Phase 5 static/CI gate.
- Phase 5 CI checkout now fetches frozen ancestry so the unchanged ancestry assertion runs in a clean GitHub Actions checkout; exact-head implementation CI passes. Required live Devnet acceptance subsequently passed after funding the exact installed lab identity.
- Completed simulation child attempts now retain bounded settlement after immediate session close while late callbacks are rejected; a deterministic regression test proved the prior evidence-loss race.
- Phase 5 device acceptance automation now surfaces explicit funding failures promptly and cannot label UI-only checks as database/restart-verified.
- Real cross-package Devnet PASS-approve, runtime FAIL-approve, and PASS-reject acceptance, independently verified canonical parent/child persistence, restart UI, and a scoped live SQLite/WAL/SHM byte scan.

- Phase 4 session-scoped configured capability snapshots with truthful walletlib provenance.
- Room schema v2 migration adding capability snapshots and sanitized transaction diagnostics without historical backfill.
- Bounded read-only legacy/v0 transaction inspection with account privilege derivation and SHA-256 fingerprints.
- Verified System Program Transfer, bounded Memo, and narrow SPL Token Transfer/TransferChecked decoders with explicit unknown fallback.
- Pre-approval transaction diagnostics and restart-surviving Session Detail inspector without expanding signing authority.
- Deterministic transaction vectors and real cross-app Phase 4 acceptance for System Transfer, Unknown Program, v0 rejection, and process restart.
- Phase 4 static/CI gate preserving the frozen Phase 3 gate and predecessor authority boundaries.

- Canonical persistent protocol recorder with request-start sequence and immutable session handles.
- Room version 1 session/event history, exported schema, restart persistence, and safe summaries.
- Home, Sessions, and Session Detail with structured timing, outcomes, errors, and failure sources.
- Hostile recorder/lifecycle/SQLite security tests, deterministic state tests, and Compose acceptance.
- Cross-package approval/rejection and force-stop/relaunch acceptance using the actual product UI.
- Phase 3 static/CI gate preserving the historical Phase 2 script and protected source hashes.

- Initial Android/Kotlin/Jetpack Compose project baseline.
- Devnet-only MWA Lab product and security documentation.
- Real wallet-side Mobile Wallet Adapter association using pinned walletlib 2.0.7.
- Protected persistent Devnet Lab identity and walletlib-managed authorization state.
- Cross-session reauthorization and explicit signing approval UI.
- Bounded `sign_messages` with Ed25519 verification and defined rejection behavior.
- Bounded legacy `sign_transactions` with signer-slot validation and immutable approval binding.
- Fixed-endpoint Solana Devnet RPC submission for `sign_and_send_transactions`.
- Deterministic RPC/submission lifecycle executor with commitment ordering and no-resubmission cancellation behavior.
- Sanitized Phase 2 protocol evidence and hostile negative/lifecycle verification.
- Deterministic cross-package MWA Lab Demo Client used only for testing.

### Security

- Phase 5 simulation bytes are transient only; raw RPC bodies, raw transactions, signatures, auth/association tokens, and key material are excluded from diagnostic persistence.
- Simulation cannot authorize, approve, reject, sign, submit, or complete the canonical protocol event; PASS is not a success guarantee and FAIL/UNAVAILABLE cannot block user decisions.
- Free-form RPC program-log content is redacted before public/durable results while bounded runtime structure is retained.

- Phase 4 persists structured public transaction metadata/hashes only; raw transaction and unknown instruction payload bytes remain outside diagnostic storage.
- Transaction inspection remains non-authoritative; legacy signing, explicit approval, authorization, and fixed Devnet submission boundaries are unchanged.
- v0 detection/partial inspection does not enable v0 signing, and no mainnet/simulation/fault/export path is introduced.
- Mainnet/testnet/unknown/missing-chain authorization remains fail-closed.
- Session-local active authorization is invalidated on deauthorize/teardown/replacement.
- Privileged requests re-check authorization around approval/signing/submission.
- Diagnostic sanitizer rejects auth/association tokens and raw message/transaction/signature fields.

<!-- PHASE6:CHANGELOG:BEGIN -->
### Phase 6 — Deterministic Fault Engine

- Added ten-profile fault catalog including NORMAL.
- Added deterministic authorization, signing, payload, stale-blockhash, delay, RPC, and submission scenarios.
- Added canonical injected-fault evidence and injected-vs-observed truthfulness.
- Added Fault Lab, global/request snapshot visibility, session fault presentation, machine-readable vectors, and Phase 6 demo acceptance runner.
- Kept Room at schema 3, walletlib at 2.0.7, Devnet-only execution, and Phase 5 simulation authority unchanged.
- At the Phase 6 freeze, Phase 7 sanitized report export remained pending.
<!-- PHASE6:CHANGELOG:END -->
