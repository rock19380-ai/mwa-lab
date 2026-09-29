# Changelog

## Unreleased

### Added

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

- Phase 4 persists structured public transaction metadata/hashes only; raw transaction and unknown instruction payload bytes remain outside diagnostic storage.
- Transaction inspection remains non-authoritative; legacy signing, explicit approval, authorization, and fixed Devnet submission boundaries are unchanged.
- v0 detection/partial inspection does not enable v0 signing, and no mainnet/simulation/fault/export path is introduced.
- Mainnet/testnet/unknown/missing-chain authorization remains fail-closed.
- Session-local active authorization is invalidated on deauthorize/teardown/replacement.
- Privileged requests re-check authorization around approval/signing/submission.
- Diagnostic sanitizer rejects auth/association tokens and raw message/transaction/signature fields.
