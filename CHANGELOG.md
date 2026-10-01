# Changelog

## Unreleased

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
- Phase 7 sanitized report export remains pending.
<!-- PHASE6:CHANGELOG:END -->
