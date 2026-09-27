# Changelog

## Unreleased

### Added

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

- Mainnet/testnet/unknown/missing-chain authorization remains fail-closed.
- Session-local active authorization is invalidated on deauthorize/teardown/replacement.
- Privileged requests re-check authorization around approval/signing/submission.
- Diagnostic sanitizer rejects auth/association tokens and raw message/transaction/signature fields.
