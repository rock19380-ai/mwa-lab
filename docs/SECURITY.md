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

## S11 — Historical Phase 2 boundary

Phase 2 intentionally does not add:

- Room/SQLite protocol history;
- persistent session timeline;
- full diagnostic export bundle;
- deterministic fault engine.

Those later features must preserve all invariants above.

## S12 — Phase 3 persistence and UI boundary

Diagnostic Room storage contains only session metadata and bounded sanitized
summaries. Request builders provide counts, lengths, SHA-256, enum/reason strings,
booleans, and public metadata. The recorder enforces approved keys, defensive
copies, and known chain/commitment values or an unsupported marker. The existing
DiagnosticSanitizer remains unchanged and runs before storage; UI re-applies it
before summary rendering. Neither Room entities nor ViewModels accept wallet
request objects, private keys, seeds, mnemonics, raw auth/association tokens,
raw payloads/signatures, encrypted transport material, raw RPC bodies, or exception
text. dApp labels are trimmed, bounded, control-free display claims, not verified
identities or a general-purpose secret detector. URI/icon/query fields are absent.

SQLite/WAL byte scans include positive controls, sensitive-key sentinels, and
actual wire auth tokens, raw/signed messages, signatures, and identity URI values
from representative protocol flows. Scope is diagnostic `mwa_lab.db` and its WAL,
not walletlib-owned authorization storage or Android Keystore.

Injected repository failures are test seams only. They do not introduce a product
fault engine or relabel successful wallet responses as organic failures.
Persistence is best effort during storage failure; missing diagnostic writes
cannot establish a complete protocol history. No raw exception text is exposed.
Abrupt process death can leave an open session and lose unfinished in-memory
handles; the UI preserves that uncertainty without invented terminal events.

Mainnet remains disabled. No production-wallet import or server signing was added.

## S13 — Phase 4 diagnostic authority and persistence boundary

Phase 4 transaction parsing is observational. `TransactionInspector`, the wire
parser, and program decoders have no authorization, approval, signing, submission,
or RPC authority. `ApprovalCoordinator`, `LegacyTransactionCodec`,
`LabSigningService`, and the fixed Devnet RPC boundary keep their predecessor
roles. A diagnostic parse success cannot make an otherwise invalid request
signable, and diagnostic persistence failure cannot change the MWA result.

Capability snapshots are session-scoped configured context sourced from
`MwaCapabilityProfile`. They are not evidence that MWA Lab observed a
`get_capabilities` callback. Historical Phase 3 sessions are not backfilled with
current capability values.

Schema version 2 persists only sanitized transaction metadata: SHA-256 fingerprint,
wire length, version/status, public account/program metadata, bounded decoded
fields, and for unknown instruction data only length plus SHA-256. It does not
persist raw transactions, raw unknown instruction bytes, private keys, seeds,
raw authorization/association tokens, encryption secrets, or raw signatures.

Versioned v0 inspection is deliberately non-authoritative and partial when lookup
table addresses are unresolved. It does not enable v0 signing. Phase 4 introduces
no mainnet path, transaction simulation, synthetic fault engine, report exporter,
or Android share-sheet diagnostic export.

## Phase 5 simulation security boundary

Simulation transaction bytes are **transient only** inside the coordinator and
RPC call. They are never a Room column or a public ViewModel/Compose field. Raw
RPC request/response bodies are not persisted or exported. Simulation results
store only bounded structured metadata such as fingerprints, numeric slot/compute
units, allowlisted error categories, and redacted program-log structure.

RPC program logs are untrusted input. Line count, per-line code points, and total
code points are bounded; control/format characters are rejected or replaced; and
free-form log content is redacted before entering public/durable results. This
prevents arbitrary RPC/log text from becoming a secret-bearing diagnostic
channel.

Simulation **cannot authorize**, **cannot sign**, and **cannot submit**. PASS does
not grant approval; FAIL/UNAVAILABLE does not reject or disable approval. The
fixed Devnet endpoint remains the only production RPC authority, mainnet remains
unavailable, v0 signing remains unsupported, and malformed/oversized RPC data
fails closed.

The 2026-09-30 Phase 5 audit reran the static security scan and five Room
simulation instrumentation tests. The disposable schema-3 database scan (including WAL/SHM when present)
found its stored-fingerprint positive control and did not find synthetic raw
transaction, signature, token, key/seed, or RPC-body sentinels. This establishes
the test persistence boundary. A subsequent funded live run also inspected the
actual installed diagnostic database, WAL and SHM after three real requests.
All three fingerprint positive controls were present. Reconstructed unsigned
payloads, signed-message bodies and their checked encodings, acceptance memo,
raw RPC envelope markers and synthetic secret sentinels were absent. No database
bytes or raw payloads were retained in evidence. Individual real signatures,
auth/association tokens and protected keys were not extracted for literal
matching: their safety remains supported by schema/source controls and the
existing instrumented sentinel tests. The live byte scan does not claim to
search for unknown secret values.
