# MWA Lab — Phase 1 MWA API Notes

**Date:** 2026-09-26  
**Status:** Phase 1 wallet-side API authority notes  
**Pinned wallet dependency:** `com.solanamobile:mobile-wallet-adapter-walletlib:2.0.7`

## Authority

Phase 1 treats the current Mobile Wallet Adapter specification and the pinned official Solana Mobile Android wallet library as protocol authority. Example applications are implementation references, not authority over the specification.

## VERIFIED FROM PINNED / CURRENT OFFICIAL SOURCES

- Wallet-side Android dependency coordinate: `com.solanamobile:mobile-wallet-adapter-walletlib:2.0.7`.
- Wallet integration requires an Android `Activity` entry point capable of receiving incoming association URIs.
- The standard local-association URI path uses the `solana-wallet` scheme.
- Wallet-side local association uses the official wallet library's local WebSocket server scenario abstraction.
- Wallet code implements the scenario callback surface to answer MWA requests.
- Scenario lifetime should match the period in which the wallet can service the dapp session, and the scenario should be closed when the session is complete.
- The MWA specification defines `ERROR_CHAIN_NOT_SUPPORTED = -7`.
- Authorization and deauthorization are protocol/session concerns; wallet-side application code must provide identity/account selection and policy decisions.

## IMPLEMENTED IN MWA LAB AFTER THIS BATCH

- Official wallet-side dependency is pinned in the version catalog.
- Phase 1 remains Devnet-only by project invariant.
- No wallet-side Activity, Scenario, authorization callback implementation, deauthorization implementation, capability implementation, signing implementation, or sample client is claimed by this Batch A alone.

## DEFERRED TO LATER PHASE 1 BATCHES

- Android association Activity and manifest intent filters.
- `MwaSessionHost` lifecycle and Scenario ownership.
- Devnet-only `NetworkPolicy` enforcement.
- Persistent Lab Test Identity.
- First authorization state and response.
- Deauthorization invalidation.
- Truthful capability response.
- Sanitized typed protocol evidence.
- Deterministic MWA Lab Demo Client.

## EXPLICITLY DEFERRED TO PHASE 2+

- `sign_messages`.
- transaction signing.
- `sign_and_send_transactions`.
- Devnet RPC submission.
- production-wallet compatibility claims.
- mainnet support.

## UNKNOWN / MUST BE VERIFIED AGAINST THE RESOLVED 2.0.7 API BEFORE WRITING HANDLERS

The next batch must inspect the actual resolved walletlib classes/signatures instead of coding from remembered APIs. In particular:

- exact `LocalWebSocketServerScenario` constructor/signatures;
- exact `Scenario.Callbacks` callback methods and result types;
- exact authorization request/response model types;
- exact deauthorization callback/result surface;
- exact capability callback/result surface;
- exact chain/network representation exposed by this library version;
- exact session cancellation/completion callbacks;
- exact auth-token issuance/storage helper APIs provided by walletlib 2.0.7.

No Phase 1 implementation may invent these names or semantics when the resolved dependency can be inspected directly.

## Batch B source verification — 2026-09-26

Current official repository source was inspected at commit:

`9d09cb5cfdd478df6293c09d83eabc6e022b93f2`

Verified details relevant to this batch:

- `ProtocolContract.CHAIN_SOLANA_DEVNET` is `solana:devnet`.
- Legacy `ProtocolContract.CLUSTER_DEVNET` is `devnet`.
- Production identifiers include `solana:mainnet` and `mainnet-beta`.
- The Android common contract exposes unsupported-cluster/network protocol error `-7` as `ERROR_CLUSTER_NOT_SUPPORTED`.
- The official Android fake-wallet reference generates Ed25519 keypairs with BouncyCastle.
- The official reference renders Solana public keys with Base58.

MWA Lab intentionally does **not** copy the fake-wallet's raw private-key persistence model.
Its Phase 1 Lab Test Identity stores only AES-GCM ciphertext in SharedPreferences,
with the AES key held by Android Keystore. The identity preference file is excluded
from backup and device transfer.

No wallet association callbacks, authorization result APIs, or capability result APIs
are claimed implemented by Batch B. Those remain gated on direct inspection of the
resolved walletlib 2.0.7 classes before Batch C/D.

<!-- BEGIN PHASE1_WALLETLIB_207_AAR_INVENTORY -->

## Published walletlib 2.0.7 API authority — corrected 2026-09-26

Phase 1 is pinned to:

`com.solanamobile:mobile-wallet-adapter-walletlib:2.0.7`

The exact resolved AAR SHA-256 is recorded in
`docs/evidence/phase1/walletlib-2.0.7-aar.sha256`.

The resolved AAR bytecode and the official `v2.0.7` source agree on the lifecycle:

- `Scenario.start()` starts the pinned 2.0.7 scenario.
- `Scenario.close()` closes it.
- `Scenario.startAsync()` is **not present** in the pinned 2.0.7 artifact.
- `AssociationUri.parse(Uri)` parses incoming association URIs.
- `AssociationUri.createScenario(...)` creates the wallet-side scenario.
- authorization arrives through
  `Scenario.Callbacks.onAuthorizeRequest(AuthorizeRequest)`.
- deauthorization arrives through
  `Scenario.Callbacks.onDeauthorizedEvent(DeauthorizedEvent)`.
- unsupported authorization chains can be completed with
  `AuthorizeRequest.completeWithClusterNotSupported()`.
- no separate wallet-side `get_capabilities` callback exists;
  walletlib derives capability responses from `MobileWalletAdapterConfig`.

### Upstream API drift

Upstream commit
`e145e53503b2b4727800c5732c4b6464be9d716e` (2025-07-18)
deprecated `Scenario.start()` and introduced `Scenario.startAsync()`.

That API is newer than the pinned `2.0.7` artifact and **must not be used by
Phase 1 while the dependency remains pinned to 2.0.7**.

This distinction is intentional: the pinned published artifact is the compile/runtime
authority; current upstream `main` is reference material only when its API matches the
pinned artifact.

Evidence:

- `docs/evidence/phase1/walletlib-2.0.7-api-authority.txt`
- `docs/evidence/phase1/walletlib-2.0.7-class-list.txt`
- `docs/evidence/phase1/walletlib-2.0.7-api-inventory.txt`
- `docs/evidence/phase1/walletlib-2.0.7-api-members.tsv`
- `docs/evidence/phase1/batch-c-api-inventory-summary.txt`

<!-- END PHASE1_WALLETLIB_207_AAR_INVENTORY -->

## Batch C association/session boundary

Batch C uses the pinned `walletlib:2.0.7` API exactly:

- incoming `solana-wallet://` intent
- `AssociationUri.parse(Uri)`
- local association only (`LocalAssociationUri`)
- `AssociationUri.createScenario(...)`
- `Scenario.start()`
- `Scenario.close()`
- `LocalScenario.Callbacks`

The Android test client is pinned to `clientlib:2.0.7` and exists only in the
`androidTest` configuration; clientlib is not a production/runtime dependency of
MWA Lab.

### Mandatory MWA 2.0 signing boundary

MWA 2.0 defines `solana:signMessages` and `solana:signAndSendTransaction` as
mandatory wallet features. Phase 1 does not perform signing or network submission.
The corresponding callbacks are therefore present but fail closed with the library's
protocol-defined decline result. This is a Lab protocol-boundary behavior, not a
successful signing implementation, and Phase 1 must not be described as a production
signing wallet.

## Batch D authorization/deauthorization boundary

Phase 1 authorization uses the persistent MWA Lab Devnet Ed25519 identity.

The application does **not** generate or parse auth tokens. With walletlib 2.0.7,
`AuthorizeRequest.completeWithAuthorize(...)` feeds the approved account and
authorization scope into walletlib. Walletlib then owns authorization-record
persistence, HMAC-protected token issuance, token validation, and revocation.

The Phase 1 authorization policy is intentionally narrow:

- only `solana:devnet` may authorize;
- optional requested features are rejected;
- Sign In With Solana payloads are rejected because that feature is not advertised;
- requested account addresses are matched against the Base64 protocol encoding of
  the persistent Lab public key, not the Base58 display address;
- full existing-auth-token reauthorization behavior remains deferred to Phase 2.

Deauthorization is protocol-authoritative: walletlib resolves and revokes a valid
auth token before `onDeauthorizedEvent`, and MWA Lab completes that event. Device
acceptance must prove that the revoked token cannot subsequently authorize.

## Batch D protocol-address encoding

Pinned MWA 2.0.7 client/server code uses standard Base64 without line wrapping for
protocol account-address bytes. MWA Lab therefore decodes requested `addresses`
to raw bytes before comparing them with the persistent Ed25519 public key.
Base58 is display-only. The authorization policy uses BouncyCastle's standard
Base64 codec so the same code path is testable on both Android and the plain JVM.


## Phase 1.12 closeout

Phase 1 freezes against `mobile-wallet-adapter-walletlib:2.0.7`.

The final deterministic client is a separate Android application module and
package. It uses pinned `clientlib:2.0.7` through the real Android association
path and repeats connect → authorize → get_capabilities → deauthorize.

Device acceptance additionally sends a missing-chain authorization request and
verifies it fails closed.

GitHub Actions remains deterministic/non-device. Device-level MWA proof is
retained as separate evidence and is not inferred from CI.
