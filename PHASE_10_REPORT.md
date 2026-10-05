# MWA Lab — Phase 10 Engineering Closeout

**Phase:** Release Candidate + Compatibility Evidence
**Date:** 2026-10-05
**Branch:** `phase10-release-candidate-compatibility-evidence`
**Frozen predecessor:** `phase9-first-run-connection-ux-2026-10-04` → `67f4b583ae1f7446c21a092c6345d45e9b258a9c`

## Outcome

Phase 10 converted the frozen Phase 9 product into an operator-signed,
release-identified, runtime-accepted competition candidate while preserving the
core product boundary: **Local MWA protocol debugging + deterministic faults +
transaction diagnostics + sanitized reports**, with a disposable Devnet Test
Wallet as supporting infrastructure.

No new protocol, mainnet path, Remote MWA transport, camera scanner, production
wallet import, SPL send UI, backend, or consumer-wallet feature was added.

## Release candidate

```text
package: dev.mwalab
versionCode: 1
versionName: 0.1.0-clockin
artifact: MWA-Lab-v0.1.0-clockin-rc1-r2.apk
APK SHA-256: 0b17ccac5180d0bd6919f3f24c0c8a03efebebf9b42bd07f4909a2894e35bd21
signer certificate SHA-256: a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4
production app source commit: 945295a3e0124af11a5d75a76c7444f09586339e
walletlib/clientlib: 2.0.7
Room schema: 4
network: Solana Devnet only
```

Release signing uses an external stable keystore and environment-only
credentials. CI holds no private signing material and verifies unsigned release
buildability.

## Signed-RC runtime acceptance

Dedicated device: `MWA_Lab_RC_API_36`, Android 16 / API 36.

Verified:

- manual first launch and Devnet/no-real-funds positioning;
- Test Wallet address, balance, Receive Test SOL QR, and one-line `REFRESH` UI;
- dApp-first cold Local MWA authorization approval and explicit rejection;
- real Local MWA `SIGN_MESSAGE_APPROVE`;
- canonical NORMAL `SIGN_AND_SEND_TRANSACTIONS`, finalized on Devnet;
- persisted LOCAL / UNVERIFIED protocol evidence after restart;
- deterministic `FAULT_SIGN_REJECT` sign-and-send returning
  `ERROR_NOT_SIGNED (-3)` / `INJECTED` with no submission;
- final persisted fault state `NORMAL`;
- one safe direct Test Wallet native-SOL send (1 lamport), finalized on Devnet;
- invalid recipient, zero, negative, overprecision, and insufficient-balance
  direct-send inputs rejected before signing;
- direct Test Wallet send did not create an MWA protocol session/event;
- Markdown, JSON, Copy Summary, Android Share Sheet, and actual generated
  report-byte sanitization for NORMAL and injected sessions;
- Test Wallet identity and accepted sessions survived process restart;
- the earlier one-off Demo Client ANR did not reproduce on the dedicated
  acceptance AVD.

Canonical MWA signature:
`4jwAPQvtmUraqK2cPSL4ZWrUJUdXPm8a1X9x8Z32qpwLZktiMFTb1WxYP2DMusDrfmoKrMgrYyrQbfXDaL8q4ZBj`
(slot `507588394`, finalized).

Direct Test Wallet signature:
`2BcmjpwLZ1mBx7NUBWzSyG6PJKmE3dpLFAgdXEyyiCou2hznxP5KSRTUYbAG9N4dMXEdsgbfpTiVt15vJr4uRKNT`
(slot `507594700`, finalized).

No live airdrop/faucet was used in the funded continuation; the user manually
funded the exact current installed Test Wallet.

## Environment caveat

The Demo Client initially produced `UnknownHostException` before signing despite
host/shell DNS resolution. Android network policy showed the Demo Client UID
background-blocked. A Demo Client-only emulator device-idle allowlist removed the
block and the unchanged HTTPS Devnet RPC path proceeded. No RPC IP hardcoding,
HTTP downgrade, TLS weakening, or production-wallet networking workaround was
introduced. This is acceptance-environment evidence, not an MWA wallet failure.

## Report/security result

The four real current-candidate report files were pulled read-only from the
acceptance AVD and hashed. Field/value inspection found bounded hashes/lengths and
structured public metadata, not raw private keys, seeds, authorization tokens,
association secrets, raw transaction/message payloads, raw signatures, or
release-signing credentials.

The signed APK verifies with v1/v2 signatures; INTERNET is present, CAMERA is
absent, the Local MWA association activity is intentionally exported, and the
report FileProvider is non-exported. Mainnet remains unavailable.

## Compatibility decision

```text
Local MWA internal deterministic path: PASS
Remote MWA: BLOCKED / NOT RELEASED
Remote QR scanner: OMITTED
Production-wallet compatibility: NOT_VERIFIED
```

No production-wallet compatibility inference is made from the internal Demo
Client.

## Phase 10 completion boundary

The implementation/runtime candidate is ready for final provenance closure.
Before the phase is called frozen, the final repository HEAD must pass the local
release gates, clean-checkout verification, and GitHub Actions for that exact
HEAD. Only then may the annotated tag
`phase10-release-candidate-compatibility-evidence-2026-10-05` be created and
pushed.

That post-commit CI/tag identity is intentionally recorded in an external final
freeze receipt to avoid changing the commit after the CI/tag it authenticates.

## Phase 11 handoff

Phase 11 is hard code freeze. Planned work after Phase 10 is limited to P0/P1
repairs, release packaging/documentation corrections, final screenshots/video/
deck preparation, and submission-blocking fixes. No feature development is
planned.
