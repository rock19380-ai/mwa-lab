# Product Positioning

## Identity

MWA Lab is an on-device Mobile Wallet Adapter protocol debugger and
deterministic failure simulator.

The embedded developer-wallet behavior is test infrastructure. It is not the
product identity.

## Core moat

Priority order:

1. protocol observability and session tracing;
2. deterministic fault injection;
3. protocol-level error reproduction;
4. sanitized diagnostic reports;
5. transaction inspection and simulation;
6. the Devnet test endpoint required to exercise those capabilities.

## Devnet vs MWA

Devnet is the blockchain/network test environment.

MWA is the protocol boundary between a mobile dApp and wallet endpoint for
authorization, capabilities, signing, submission, and protocol-level errors.

MWA Lab must never position itself as "a better Devnet wallet."

## Production wallets

Production wallets remain necessary for final compatibility validation.

MWA Lab is used before and alongside real-wallet testing.

No compatibility claim may be published without current verification.
