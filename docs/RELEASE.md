# Release

## Phase 11 signed RC2 — current competition candidate

The current operator-signed Android release is `MWA-Lab-v0.1.0-clockin-rc2.apk`
at `/home/abbaas/Downloads/MWA_LAB_RC2_2026-10-05/`.

```text
applicationId  dev.mwalab
versionCode    1
versionName    0.1.0-clockin
APK SHA-256    5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc
signer SHA-256 a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4
network        Solana Devnet only
```

The artifact was signed from Phase 11 source commit
`4b97796ab7958b2590d32271de8e0a0786cbc824`. Protected production source
is unchanged from the Phase 10 signed baseline. Signed RC2 passed clean manual
first run, cold dApp-first Local MWA Approve/Reject, funded memo sign-and-send,
`FAULT_SIGN_REJECT` (`ERROR_NOT_SIGNED (-3)` / `INJECTED`, no submission), final
NORMAL persistence, Test Wallet Receive address QR and direct send, negative
send validation, Markdown/JSON/Copy/Share generation, actual report-byte
sanitization, and the final bounded runtime logcat audit. The Demo Client is
internal test infrastructure. Detailed observations are in
[Phase 11 evidence](evidence/phase11/) and [the Phase 11 report](../PHASE_11_REPORT.md).

```text
Local MWA:                       VERIFIED / SHIPPED
Remote MWA:                      BLOCKED_HIDDEN / NOT RELEASED
Remote QR scanner:               OMITTED
CAMERA permission:               ABSENT
Production-wallet compatibility: NOT_VERIFIED
Mainnet/testnet:                  UNAVAILABLE / REJECTED
```

Receive Test SOL QR encodes only the disposable public Devnet address. A
Solana Pay QR is a different protocol and is not an MWA connection. Simulation
is diagnostic evidence, not a submission guarantee. The annotated Phase 11
freeze tag and exact-head CI result are recorded only after this candidate is
committed and verified; their final identifiers belong in the external freeze
receipt.

## Phase 10 RC1 revision 2 — historical candidate

Current application identity:

```text
applicationId  dev.mwalab
versionCode    1
versionName    0.1.0-clockin
network        Solana Devnet only
walletlib      2.0.7
Room schema    4
```

The historical Phase 10 signed candidate was:

```text
MWA-Lab-v0.1.0-clockin-rc1-r2.apk
SHA-256: 0b17ccac5180d0bd6919f3f24c0c8a03efebebf9b42bd07f4909a2894e35bd21
signer certificate SHA-256: a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4
```

The APK was built from production app source commit
`945295a3e0124af11a5d75a76c7444f09586339e`; later Phase 10 commits add test,
evidence, and release documentation without changing the shipped app code.
The earlier signed RC1 is historical and **SUPERSEDED BY LATER PHASE 10 RC1 REVISION**.

Current signed RC1 revision 2 runtime acceptance: `PASS`. This line records the
Phase 10 checkpoint only; RC2 is the current Phase 11 artifact above.

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

## Final Phase 10 provenance (historical)

The Phase 10 release candidate was frozen by:

1. a clean final candidate worktree;
2. deterministic local gates and clean-checkout verification;
3. GitHub Actions success for the **exact final HEAD**;
4. annotated tag `phase10-release-candidate-compatibility-evidence-2026-10-05`
   pointing to that exact successful HEAD.

The Phase 10 tag is predecessor evidence for Phase 11. Its historical RC1-r2
values are not RC2 verification.

Current detailed evidence: `docs/evidence/phase10/`.
