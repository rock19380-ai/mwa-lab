# Release

## Phase 10 RC1 revision 2 — current competition candidate

Current application identity:

```text
applicationId  dev.mwalab
versionCode    1
versionName    0.1.0-clockin
network        Solana Devnet only
walletlib      2.0.7
Room schema    4
```

Current externally stored signed candidate:

```text
MWA-Lab-v0.1.0-clockin-rc1-r2.apk
SHA-256: 0b17ccac5180d0bd6919f3f24c0c8a03efebebf9b42bd07f4909a2894e35bd21
signer certificate SHA-256: a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4
```

The APK was built from production app source commit
`945295a3e0124af11a5d75a76c7444f09586339e`; later Phase 10 commits add test,
evidence, and release documentation without changing the shipped app code.
The earlier signed RC1 is historical and **SUPERSEDED BY LATER PHASE 10 RC1 REVISION**.

Current signed RC1 revision 2 runtime acceptance: `PASS`.

Verified on the dedicated Android 16 / API 36 acceptance AVD:

- clean manual first run: `PASS`;
- dApp-first cold Local MWA launch, explicit authorization approval and rejection: `PASS`;
- `SIGN_MESSAGE_APPROVE`: `PASS`;
- canonical Local MWA `SIGN_AND_SEND_TRANSACTIONS`: `PASS`, finalized on Devnet;
- injected `FAULT_SIGN_REJECT` sign-and-send: `PASS`, `ERROR_NOT_SIGNED (-3)`, `INJECTED`, no submission;
- final persisted fault selection: `NORMAL`;
- direct **Send Test SOL**: `PASS`, one 1-lamport transfer finalized on Devnet;
- direct Test Wallet send creates no MWA protocol session/event: `PASS`;
- Markdown / JSON Share Sheet, Copy Summary, and actual generated report-byte sanitization audit: `PASS`;
- restart persistence of Test Wallet identity, canonical session, and final NORMAL fault: `PASS`.

The canonical Local MWA transaction and direct Test Wallet transaction signatures
are recorded in `docs/evidence/phase10/04-local-mwa/funded-rc1-r2.md` and
`docs/evidence/phase10/05-test-wallet/funded-rc1-r2.md` respectively.

No live airdrop or faucet was invoked during the funded Phase 10 acceptance.
The user manually funded the exact installed disposable Devnet identity.

## Release scope and limitations

```text
Local MWA:                       VERIFIED / SHIPPED
Remote MWA:                      BLOCKED_HIDDEN / NOT RELEASED
Remote QR scanner:               OMITTED
CAMERA permission:               ABSENT
Production-wallet compatibility: NOT_VERIFIED
Mainnet/testnet:                  UNAVAILABLE / REJECTED
Versioned-v0 signing:             NOT SUPPORTED
```

The internal MWA Lab Demo Client is deterministic test infrastructure, not a
production-wallet compatibility test. Do not claim Phantom, Solflare, Seed Vault
Wallet, or other production-wallet compatibility without direct release-specific
evidence.

The only shipped QR is **Receive Test SOL QR**, containing the disposable Devnet
public address. A Remote MWA QR would represent protocol association, but Remote
MWA is not released. Solana Pay QR is a separate protocol and is not an MWA
connection input.

## Release signing

The stable release keystore remains outside this repository. Operator signing
uses these environment variables:

```text
MWALAB_RELEASE_STORE_FILE
MWALAB_RELEASE_STORE_PASSWORD
MWALAB_RELEASE_KEY_ALIAS
MWALAB_RELEASE_KEY_PASSWORD
```

Partial signing configuration fails closed. No keystore, password, or private
signing key is tracked. CI intentionally verifies unsigned release buildability;
the signed competition APK is an operator artifact and must be verified with
`apksigner`, `aapt`, and SHA-256 before distribution.

## Final Phase 10 provenance

The release candidate is technically accepted. The final Phase 10 freeze is
established only by:

1. a clean final candidate worktree;
2. deterministic local gates and clean-checkout verification;
3. GitHub Actions success for the **exact final HEAD**;
4. annotated tag `phase10-release-candidate-compatibility-evidence-2026-10-05`
   pointing to that exact successful HEAD.

This document intentionally does not self-record the future final CI run/tag
inside the commit they authenticate. The final run ID, URL, remote tag target,
and artifact digest belong in the external freeze receipt generated after the
tag is verified.

Current detailed evidence: `docs/evidence/phase10/`.
