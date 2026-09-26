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
