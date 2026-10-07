# Release

## `0.1.0-clockin` signed RC2 — current competition candidate

Current signed Android artifact:

```text
file           MWA-Lab-v0.1.0-clockin-rc2.apk
applicationId  dev.mwalab
versionCode    1
versionName    0.1.0-clockin
APK SHA-256    5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc
signer SHA-256 a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4
network        Solana Devnet only
```

The release keystore and passwords remain outside Git. The signed APK is an
operator artifact; publish/distribute it only after matching the checksum above.

## Verified runtime boundary

The exact signed RC2 passed:

- clean manual first run;
- dApp-first cold Local MWA authorization;
- explicit authorization Approve / Reject;
- funded NORMAL Local MWA signing/submission;
- deterministic `FAULT_SIGN_REJECT` returning `ERROR_NOT_SIGNED (-3)` with
  `INJECTED` provenance and no submission;
- restart-surviving protocol/session evidence;
- sanitized Markdown / JSON / clipboard-summary report projections;
- Test Wallet balance and Receive Test SOL;
- Send Test SOL validation and live Devnet send;
- final `NORMAL` fault selection;
- bounded security/log review.

Phase 12 final QA additionally re-verified the exact signed RC2 and preserved the
hard-frozen production source.

## Release scope

```text
Local MWA:                       VERIFIED / SHIPPED
Remote MWA:                      BLOCKED_HIDDEN / NOT RELEASED
Remote QR scanner:               OMITTED
CAMERA permission               ABSENT
Mainnet/testnet:                  UNAVAILABLE / REJECTED
Production-wallet compatibility: NOT_VERIFIED
Versioned-v0 signing:             NOT SUPPORTED
Identity Reset UI:                NOT SHIPPED
```

The Demo Client is deterministic test infrastructure. It does not establish
compatibility with Phantom, Solflare, Seed Vault Wallet, or another production
wallet.

## Test Wallet scope

The Test Wallet is disposable developer infrastructure, not production custody.

Shipped actions:

```text
COPY ADDRESS
RECEIVE TEST SOL
REQUEST DEVNET SOL
SEND TEST SOL
```

Receive Test SOL QR encodes only the disposable public Devnet address. It is not
an MWA connection QR.

Direct Test Wallet actions remain outside MWA protocol-session history.

Phase 12 final QA verified a direct transfer of `0.001 SOL` on Devnet with a
`5,000` lamport fee and finalized chain status.

## Release signing

Operator signing uses:

```text
MWALAB_RELEASE_STORE_FILE
MWALAB_RELEASE_STORE_PASSWORD
MWALAB_RELEASE_KEY_ALIAS
MWALAB_RELEASE_KEY_PASSWORD
```

Partial signing configuration fails closed. No keystore, password, or private
signing key is tracked in the repository.

CI intentionally verifies deterministic buildability and freeze invariants.
The signed competition APK is verified separately with Android tooling,
certificate fingerprinting, and SHA-256.

## Provenance

Hard-freeze authority:

```text
tag   phase11-hard-code-freeze-2026-10-06
HEAD  fabf28f92b1e3c3a46a2d2a5be1fe642395b0c24
CI    37411435425 — success
```

Protected production source is unchanged from the signed RC2 baseline.

Evidence:

- `PHASE_11_REPORT.md`
- `docs/evidence/phase11/`
- `docs/COMPATIBILITY.md`
- `docs/SECURITY.md`
- `docs/TESTING.md`

## Historical candidate

Phase 10 RC1 revision 2 is superseded by RC2:

```text
file        MWA-Lab-v0.1.0-clockin-rc1-r2.apk
SHA-256     0b17ccac5180d0bd6919f3f24c0c8a03efebebf9b42bd07f4909a2894e35bd21
status      historical / superseded
```

Current signed RC1 revision 2 runtime acceptance: `PASS`.

Historical Phase 10 acceptance included canonical Local MWA `SIGN_AND_SEND_TRANSACTIONS`: `PASS`
and direct **Send Test SOL**: `PASS`. These statements describe the superseded
RC1-r2 checkpoint, not the current RC2 candidate.

Historical Phase 10 evidence remains under `docs/evidence/phase10/`.
