# Security and Diagnostic Invariants

## S1 — Devnet-only authorization boundary

Phase 1 authorization accepts only `solana:devnet` when all other validation
passes.

Mainnet, testnet, unknown, malformed, and missing-chain requests fail closed.

There is no UI/settings path that enables mainnet.

## S2 — Protected Lab identity

MWA Lab uses one persistent Ed25519 **test identity**.

Private seed material is encrypted at rest using an Android Keystore-backed AES
key. Identity preferences are excluded from backup/device transfer.

The UI exposes only the public Lab address.

MWA Lab does not support:

- mnemonic import;
- production private-key import;
- Seed Vault extraction;
- production-wallet migration.

## S3 — Authorization-token authority

MWA Lab does not mint auth tokens.

Pinned walletlib 2.0.7 owns:

- authorization-record persistence;
- HMAC-protected token issuance;
- token validation;
- token revocation.

MWA Lab does not log, display, export, or persist raw auth-token content for
diagnostics.

## S4 — Sanitized evidence

Phase 1 evidence prefers:

- protocol method;
- public chain identifier;
- public account metadata;
- request counts;
- outcome/error enum;
- timing/duration;
- safe result summaries.

Never diagnostic-export or log:

- private key;
- seed/mnemonic;
- raw auth token;
- Keystore/encryption secret;
- association token/public key;
- transaction/message payload;
- signature bytes.

`MwaSessionHost` passes structured protocol summaries through
`DiagnosticSanitizer` before they enter `ProtocolEvidenceStore`.

## S5 — Explicit lab identity

Primary wallet UI communicates:

```text
MWA LAB TEST ENDPOINT
SOLANA DEVNET
NO REAL FUNDS
```

The separate client communicates:

```text
MWA Lab Demo Client
FOR TESTING ONLY
```

## S6 — Phase 1 signing behavior

Successful signing is not implemented in Phase 1.

MWA 2.0 mandatory signing callbacks are present because the pinned protocol
requires them, but Phase 1 returns protocol-defined decline results.

No message/transaction is signed and no transaction is submitted.

## S7 — Fail closed

Malformed, missing-chain, unsupported-chain, unavailable-identity, unsupported
optional-feature, and unsupported sign-in authorization conditions do not become
successful authorization.

## S8 — Diagnostic truthfulness

Unknown data remains unknown.

Do not invent:

- wallet compatibility;
- program names;
- amounts;
- token symbols;
- instruction semantics;
- protocol causes;
- signing success.

## S9 — Failure-source integrity

Structured protocol evidence classifies failure source explicitly.

Synthetic/injected failure behavior belongs to later phases and must not be
represented as naturally observed protocol failure.
