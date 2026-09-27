# Protocol Support

This file records **implemented and exercised** MWA behavior for the current
Phase 2 repository state.

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
