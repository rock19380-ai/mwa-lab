# Phase 11 pitch-deck candidate content

Evidence anchor: exact signed RC2 SHA-256
`5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc`;
Local MWA verified on Android 16/API36 with the internal Demo Client.
Production-wallet compatibility remains `NOT_VERIFIED`.
Use `../10-demo/architecture.svg` and the curated RC2 screenshots.

## 1. Identity

**MWA Lab**

The protocol debugger and deterministic failure simulator for Solana Mobile
Wallet Adapter.

Visual: RC2 Home hero screenshot. Footer: **DEVNET ONLY**.

## 2. Problem

Devnet tests transactions on a test chain. It does not explain the MWA
protocol path: authorization, capability negotiation, signing, submission,
or failure provenance.

Visual: simple request/response timeline. Keep simulation described as
diagnostic evidence, not a guarantee.

## 3. How developers use it

Android dApp → **Connect Wallet** → **MWA Lab** → inspect protocol behavior.

This is the verified same-device **Local MWA** path. Show the cold
authorization screenshot with truthful UNVERIFIED dApp identity.
Remote MWA is not released.

## 4. Product workflow

**Trace → Break intentionally → Inspect → Diagnose → Export.**

Show a normal persisted timeline, Fault Lab, and sanitized report controls.
The report contains bounded public metadata/fingerprints; no raw credential or
sensitive transaction/message payload.

## 5. Deterministic evidence

Same Demo Client signing request + `FAULT_SIGN_REJECT`
→ `ERROR_NOT_SIGNED (-3)`
→ failure source `INJECTED` + exact fault ID.

Contrast with the normal memo-only Devnet sign-and-send PASS. This demonstrates
fault provenance in the internal test path, not a production-wallet claim.

## 6. Test Wallet and safety

Disposable Devnet identity; **Send/Receive Test SOL only**.
No mainnet, real funds, or seed import.
Receive QR is only the public Devnet address. Direct Test Wallet send stays
outside dApp → MWA protocol history.

Test Wallet is supporting infrastructure, not the headline product.

## 7. Vision

**The standard QA and debugging companion for Solana Mobile dApps using MWA.**

Make protocol failures visible, reproducible, and fixable. The next phase is
final QA and submission packaging, with no new release feature claim.

## Evidence and claim guard

Source anchors: `../rc2-runtime-2026-10-05.md`,
`../08-security-privacy/final-bounded-logcat-audit-2026-10-06.md`,
`../12-compatibility/final-matrix.md`, and `../09-screenshots/MANIFEST.md`.
Do not advertise Phantom, Solflare, Seed Vault Wallet, Remote MWA, Solana Pay
connection support, mainnet, or SPL-token send as verified/shipped.
