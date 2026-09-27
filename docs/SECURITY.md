# Security and Diagnostic Invariants

## S1 — Devnet-only network boundary

MWA Lab accepts supported authorization/signing behavior only for
`solana:devnet`.

Mainnet, testnet, unknown, malformed, and missing-chain authorization requests
fail closed.

Transaction submission uses only:

```text
https://api.devnet.solana.com
```

There is no caller-supplied RPC URL and no UI/settings path that enables
mainnet.

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

The signing implementation re-derives public-key integrity before use and
zeroes temporary decrypted seed material in `finally`.

## S3 — Authorization authority and revocation

MWA Lab does not mint auth tokens.

Pinned walletlib 2.0.7 owns:

- authorization-record persistence;
- HMAC-protected token issuance;
- token validation;
- token revocation.

MWA Lab adds a session-local active-authorization generation so privileged
requests fail closed even if a stale/revoked callback reaches the application
boundary.

Successful authorize/reauthorize activates the current generation.
Deauthorize, association replacement, session teardown, and host close
invalidate it.

Privileged signing/submission handlers re-check authorization before approval
and after approval before signing/submission.

## S4 — Explicit user approval

Successful `sign_messages`, `sign_transactions`, and
`sign_and_send_transactions` require explicit approval in the normal Phase 2
path.

Approval is:

- single-flight;
- request-bound;
- cancellable;
- expiry-aware;
- resistant to stale or duplicate completion.

Session teardown cancels pending approval.

## S5 — Bounded signing inputs

Message and transaction signing requests are bounded to 10 payloads.

Message signing rejects transaction-message-shaped content.

Legacy transaction signing additionally enforces:

- maximum wire size 1232 bytes;
- canonical short-vector structure;
- signature/header consistency;
- Lab identity in a required signer slot;
- immutable approved transaction snapshot;
- malformed/truncated/trailing/versioned input rejection.

## S6 — Submission fail-closed behavior

`sign_and_send_transactions` uses the fixed Devnet RPC boundary and validates:

- returned signature shape;
- returned signature matches the signed transaction;
- requested commitment is reached before success where required;
- transaction-status errors are not treated as commitment success.

Transport timeout/I/O/HTTP, JSON-RPC, malformed-response, signature-mismatch,
commitment failure, and submission failure remain defined failures.

Cancellation prevents later submissions and does not resubmit a failed/cancelled
transaction.

## S7 — Sanitized evidence

Structured evidence may include:

- protocol method;
- public chain identifier;
- public account metadata;
- request counts/lengths;
- outcome/error enum;
- failure source;
- timing/duration;
- safe result summaries.

Never diagnostic-export or log:

- private key;
- seed/mnemonic;
- raw auth/authorization token;
- Keystore/encryption secret;
- association token;
- raw message payload;
- raw transaction payload;
- raw signature bytes.

`DiagnosticSanitizer` is applied before structured protocol data enters
`ProtocolEvidenceStore`.

## S8 — Explicit lab identity

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

## S9 — Diagnostic truthfulness

Unknown data remains unknown.

Do not invent:

- production-wallet compatibility;
- program names;
- amounts;
- token symbols;
- instruction semantics;
- protocol causes;
- signing/submission success.

MWA clientlib 2.0.7 permits only one outstanding request per client association;
Phase 2 evidence records that limitation rather than claiming two live
same-association wallet callbacks.

## S10 — Failure-source integrity

Synthetic/injected failure behavior belongs to later phases.

Phase 2 does not present injected failures as naturally observed wallet or RPC
failures.

## S11 — Phase boundary

Phase 2 intentionally does not add:

- Room/SQLite protocol history;
- persistent session timeline;
- full diagnostic export bundle;
- deterministic fault engine.

Those later features must preserve all invariants above.
