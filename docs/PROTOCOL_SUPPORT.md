# Protocol Support

This file records **implemented and exercised** MWA behavior for the current
Phase 2 protocol baseline, preserved by the current Phase 3 observability layer.

Protocol authority is pinned walletlib/clientlib `2.0.7` plus the corresponding
official `v2.0.7` source. Current upstream `main` is not substituted when its API
differs from the pinned artifacts.

| Capability | Phase 2 status | Verified behavior |
|---|---|---|
| Wallet discovery / association | VERIFIED | Real cross-package `solana-wallet://` local association |
| Session establishment / teardown | VERIFIED | Per-association generation guard; pending approval cancelled on teardown |
| authorize — first Devnet authorization | VERIFIED | Persistent Lab public account returned |
| authorize — production/mainnet chain | VERIFIED REJECTION | Fails closed |
| authorize — testnet / unknown / missing chain | VERIFIED REJECTION | Fails closed |
| authorize with existing valid auth token | VERIFIED | Cross-session reauthorization succeeds |
| deauthorize | VERIFIED | walletlib record revoked and MWA Lab active authority invalidated |
| revoked current-session authorization | VERIFIED REJECTION | Privileged request cannot reach signing approval |
| revoked auth-token reuse | VERIFIED REJECTION | Cross-session reauthorization fails |
| get_capabilities | VERIFIED | Pinned walletlib response exercised by real client |
| sign_messages | VERIFIED | Explicit approval, max 10 payloads, authorized-address binding, Ed25519 signature verification |
| sign_messages transaction-like payload | VERIFIED REJECTION | Transaction-message-shaped content is not signed as a message |
| sign_transactions | VERIFIED | Legacy transaction only, max 10, bounded parser, correct signer slot, immutable approved snapshot |
| versioned transaction signing | VERIFIED REJECTION | Phase 2 legacy-only codec rejects versioned transactions |
| sign_and_send_transactions | VERIFIED | Signed legacy transaction submitted only through fixed Devnet RPC boundary |
| requested commitment behavior | VERIFIED | Requested commitment must be reached before success where requested |
| partial / failed submission | VERIFIED | Failed positions are not retried; mapped fail-closed including `ERROR_NOT_SUBMITTED` |
| Sign In With Solana | NOT ADVERTISED / REJECTED | Optional feature is not enabled |

## Capability truth

`MwaCapabilityProfile` is the application-level authority for values supplied to
pinned walletlib `2.0.7`.

```text
maxTransactionsPerSigningRequest = 10
maxMessagesPerSigningRequest     = 10
supportedTransactionVersions     = ["legacy"]
optionalFeatures                 = [sign_transactions]
```

The `sign_transactions` feature identifier is required by the pinned walletlib
surface for that verified method. No SIWS feature is advertised.

## Signing boundary

Successful signing requires:

```text
current association generation
+ active session authorization
+ valid/bounded request
+ authorized Lab account
+ explicit approval
```

Authorization is re-checked around approval/signing/submission boundaries.
Session replacement, teardown, close, and deauthorization invalidate local
active authority.

MWA clientlib `2.0.7` permits only one outstanding JSON-RPC request on one
client association. Live evidence represents that behavior truthfully;
wallet-side approval single-flight/decision isolation is additionally covered by
deterministic `ApprovalCoordinatorTest` cases.

## Transaction scope

Phase 2 transaction support is deliberately narrow:

```text
legacy Solana wire transactions only
maximum wire size = 1232 bytes
Lab identity must occupy a required signer slot
existing non-Lab signature slots are preserved
malformed/truncated/non-canonical/trailing input fails closed
versioned transactions fail closed
```

## RPC / network scope

```text
solana:devnet                         supported
https://api.devnet.solana.com        fixed RPC endpoint
mainnet                               rejected
testnet                               rejected
unknown/missing chain                 rejected
caller-supplied RPC endpoint          not supported
```

The RPC boundary classifies transport timeout/I/O/HTTP, JSON-RPC, malformed
response, returned-signature, transaction-status, and commitment failures
without exposing raw payload material.

## Compatibility claims

Phase 2 proof uses pinned official Android MWA libraries and the deterministic
MWA Lab Demo Client.

No production-wallet compatibility claim is made by Phase 2.

## Phase 3 observation coverage

| Method | Persistent timeline status |
|---|---|
| authorize | OBSERVED when host callback occurs |
| reauthorize | OBSERVED when host callback occurs; internal token rejections are absent |
| deauthorize | OBSERVED when host deauthorized callback occurs |
| sign_messages | OBSERVED when host callback occurs |
| sign_transactions | OBSERVED when host callback occurs |
| sign_and_send_transactions | OBSERVED when host callback occurs |
| get_capabilities | NOT OBSERVABLE THROUGH PINNED WALLETLIB |
| MwaCapabilityProfile | CONFIGURED CONTEXT ONLY; not request observation |

The six observable callbacks use the canonical persistent recorder. Requests
rejected internally by walletlib before callbacks are not fabricated. Outcomes
describe host completion/diagnostic settlement, not proof of response delivery
or chain execution. Diagnostic storage failure has no protocol authority.
No pinned library, transport observer, protocol error mapping, or network policy
was changed to expand observation coverage.

## Phase 4 protocol and diagnostics truth table

The table below separates observable host callbacks, configured capability context,
and diagnostic feature scope. Status text is intentionally machine-checked by the
Phase 4 gate.

| Capability | Phase 4 status |
|---|---|
| authorize | observed |
| reauthorize | observed |
| deauthorize | observed |
| sign_messages | observed |
| sign_transactions | observed + diagnostics |
| sign_and_send_transactions | observed + diagnostics |
| get_capabilities request | not observable through pinned walletlib |
| configured capability profile | session snapshot persisted |
| legacy inspection | supported |
| v0 detection | supported |
| v0 full ALT resolution | not supported |
| v0 signing | not supported |
| System Transfer | supported |
| Memo | supported |
| SPL Token | narrow verified subset |
| unknown program | explicit unknown |
| simulation | not implemented / Phase 5 |
| fault injection | not implemented / Phase 6 |
| report export | not implemented / Phase 7 |

Configured capability snapshots are captured per new Phase 4 session from
`MwaCapabilityProfile`; historical Phase 3 sessions are not backfilled. The v0
diagnostic path can preserve static accounts, lookups, and partial instruction
metadata without resolving ALT-loaded addresses; this diagnostic visibility does
not alter `LegacyTransactionCodec`'s versioned-transaction rejection.

Program decoding is deliberately limited to verified semantics. Unknown program
or unsupported instruction semantics are shown as unknown/unavailable with public
program/account metadata plus bounded data length/SHA-256 rather than fabricated
meaning. No production-wallet compatibility claim is made by Phase 4.

## Phase 5 simulation and diagnostic classification truth table

| Capability | Phase 5 status |
|---|---|
| legacy simulation | supported, user-triggered diagnostic only |
| v0 simulation | not supported in P0 |
| v0 signing | not supported |
| simulation broadcast | never; simulation does not submit |
| PASS semantics | evidence only; does not guarantee signing, submission, or confirmation |
| FAIL semantics | `SIMULATION` child evidence; does not reject parent request |
| RPC unavailable | `RPC_NETWORK` child evidence; approval remains independent |
| local unsafe input | `LOCAL_PARSER` unavailable diagnostic |
| context slot / compute units | preserved when safely returned |
| program logs | bounded runtime structure; free-form content redacted |
| raw transaction persistence | not supported |
| raw RPC body persistence | not supported |
| synthetic `ProtocolMethod.SIMULATE` | not supported |
| fault injection | not implemented / Phase 6 |
| report export | not implemented / Phase 7 |

Simulation is diagnostic evidence only and **does not guarantee** later
submission or confirmation. The parent `ProtocolEvent` is still determined only
by the existing MWA authorization/approval/signing/submission path.

Phase 5 implementation, instrumentation, and real cross-package Devnet
acceptance support the table above. On 2026-09-30, after the exact installed
payer was funded and reverified, PASS-approve and runtime FAIL-approve both
returned verified signed payloads; PASS-reject returned ERROR_NOT_SIGNED (-3),
with parent OBSERVED_PROTOCOL and child PASS/NONE. All three reported zero
submissions. Canonical parent/transaction/simulation rows were unchanged across
force-stop/relaunch, and the persisted UI reloaded the PASS-reject relationship.
The initial zero-balance attempt remains historical evidence, not a simulation
result. RPC_UNAVAILABLE remains verified by controlled instrumentation, not a
production fault toggle or a claimed live outage.
