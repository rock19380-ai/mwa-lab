# Protocol Support

This file records **implemented and exercised** MWA behavior for the current
repository state.

Protocol authority for Phase 1 is pinned walletlib `2.0.7` plus the corresponding
official `v2.0.7` source. Current upstream `main` is not substituted when its API
differs from the pinned artifact.

| Capability | Phase 1 status | Verified behavior |
|---|---|---|
| Wallet discovery / association | VERIFIED | Real cross-package `solana-wallet://` local association |
| Session establishment / teardown | VERIFIED | Pinned `Scenario.start()` lifecycle and clean close |
| authorize — first Devnet authorization | VERIFIED | Persistent Lab public account returned |
| authorize — production/mainnet chain | VERIFIED REJECTION | Defined unsupported-chain failure |
| authorize — missing chain | VERIFIED REJECTION | Fails closed |
| authorize with existing valid auth token | DEFERRED | Full behavior belongs to Phase 2 |
| deauthorize | VERIFIED | walletlib authorization state revoked |
| revoked auth-token reuse | VERIFIED REJECTION | Revoked state is not authoritative |
| get_capabilities | VERIFIED | walletlib 2.0.7 response exercised by real client |
| sign_messages success | DEFERRED | Phase 1 callback returns protocol decline; no signing |
| transaction signing success | DEFERRED | Phase 1 callback returns protocol decline; no signing |
| sign_and_send_transactions success | DEFERRED | Phase 1 callback returns protocol decline; no submission |
| Sign In With Solana | NOT ADVERTISED / REJECTED | Phase 1 optional feature set is empty |

## Capability truth

Pinned walletlib 2.0.7 owns `get_capabilities` internally.

Phase 1 centralizes the values supplied to walletlib:

```text
maxTransactionsPerSigningRequest = 0
maxMessagesPerSigningRequest     = 0
supportedTransactionVersions     = ["legacy"]
optionalFeatures                 = []
```

For this pinned library, zero request maxima mean no configured limit. They do
not mean MWA Lab performs successful signing in Phase 1.

MWA 2.0 treats `signMessages` and `signAndSendTransaction` as mandatory protocol
surface. Phase 1 implements their callbacks but deliberately declines requests;
successful signing/submission is deferred to Phase 2.

## Network scope

```text
solana:devnet  supported for Phase 1 authorization
mainnet        rejected
testnet        rejected
unknown        rejected
missing chain  rejected
```

There is no settings/UI path that enables mainnet.

## Compatibility claims

The Phase 1 proof uses the pinned official Android MWA libraries and the
deterministic MWA Lab Demo Client.

No production-wallet compatibility claim is made by Phase 1.
