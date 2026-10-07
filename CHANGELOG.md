# Changelog

## Unreleased

No post-competition product changes are recorded after the `0.1.0-clockin`
competition candidate.

## 0.1.0-clockin — 2026-10-07

### Competition release candidate

- Shipped MWA Lab as a Devnet-only Android Mobile Wallet Adapter protocol
  debugger and deterministic failure simulator.
- Verified signed RC2 `MWA-Lab-v0.1.0-clockin-rc2.apk` with SHA-256
  `5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc`.
- Preserved same-device Local MWA as the release transport; Remote MWA remains
  hidden/not released and CAMERA permission remains absent.
- Added explicit authorization and signing approval, persistent protocol
  timelines, capability/transaction diagnostics, simulation evidence,
  deterministic fault injection, and sanitized report export.
- Added disposable Test Wallet balance, Receive Test SOL address QR, Request
  Devnet SOL, and native Devnet Send Test SOL while keeping direct wallet
  utilities outside MWA protocol-session history.
- Verified canonical NORMAL Local MWA and deterministic `FAULT_SIGN_REJECT`
  behavior with `ERROR_NOT_SIGNED (-3)` and independent `INJECTED` provenance.
- Verified final Test Wallet Send on Devnet, Receive QR purpose/address
  semantics, report sharing, final `NORMAL` state, and protected production
  source freeze during Phase 12 final QA.
- Production-wallet compatibility remains **NOT VERIFIED**. Mainnet/testnet are
  unavailable/rejected. Identity Reset UI and Remote QR scanner are not shipped.

### Security / release invariants

- Fixed Solana Devnet authority; no mainnet signing/submission path.
- No production wallet secret import.
- No seed phrase/private-key display.
- No raw authorization/association tokens in normal diagnostic reports.
- External release signing credentials remain outside Git.
- Hard-freeze authority:
  `phase11-hard-code-freeze-2026-10-06` at
  `fabf28f92b1e3c3a46a2d2a5be1fe642395b0c24`.

## Engineering history

Detailed phase-by-phase implementation and verification history remains in:

- `PHASE_1_REPORT.md` through `PHASE_11_REPORT.md`;
- `docs/evidence/phase*/`;
- historical Git tags and exact-head CI receipts.

Those records are preserved as provenance and are not rewritten to match this
release-oriented summary.
