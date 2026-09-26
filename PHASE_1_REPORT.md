# MWA Lab Phase 1 Report

**Phase:** Phase 1 — MWA Protocol Boundary Spike  
**Date:** 2026-09-26  
**Status:** LOCAL/DEVICE ACCEPTANCE PASS — awaiting final GitHub CI freeze verification

## Baseline

Phase 1 descends from frozen Phase 0 commit:

`b4e7631a7e20bd4d032bb5478582684c998801ab`

Frozen Phase 0 tag:

`phase0-android-baseline-2026-09-26`

## Pinned protocol authority

Wallet-side dependency:

`com.solanamobile:mobile-wallet-adapter-walletlib:2.0.7`

The exact artifact API was inspected and frozen in repository evidence.

Important pinned lifecycle fact:

`Scenario.start()`

is the Phase 1 lifecycle API. Newer upstream `Scenario.startAsync()` is not
present in walletlib 2.0.7.

## Delivered

- real wallet-side Android MWA association entrypoint;
- `MwaSessionHost`;
- centralized Devnet-only `NetworkPolicy`;
- persistent protected Ed25519 Lab test identity;
- first Devnet authorization;
- walletlib-managed authorization state;
- deauthorization/revocation;
- truthful pinned `get_capabilities`;
- centralized `MwaCapabilityProfile`;
- minimal typed/sanitized `ProtocolEvidenceStore`;
- separate deterministic `:demo-client`;
- canonical real cross-package protocol acceptance;
- final local/device evidence package.

## Safety

Phase 1 does not provide production wallet custody.

It does not:

- import production secrets;
- expose private key/seed material;
- place raw auth tokens in diagnostics;
- enable mainnet;
- successfully sign messages/transactions;
- submit transactions.

Mandatory MWA 2.0 signing callbacks fail closed in Phase 1.

## Final local/device acceptance

PASS:

- clean install;
- app launch;
- real association;
- session establishment;
- mainnet rejection;
- missing-chain rejection;
- Devnet authorization;
- persistent Lab public account;
- capabilities request;
- deauthorization;
- revoked-state rejection;
- session close;
- identity persistence/reset regression;
- sanitizer/security scan;
- deterministic build/static gates.

## Remaining freeze step

The only remaining Phase 1 closeout action is:

1. review/commit this Phase 1.12 closeout;
2. push the exact closeout head;
3. require GitHub Actions success for that exact SHA;
4. record CI evidence;
5. verify the final exact head again;
6. create/push the frozen Phase 1 tag.

Phase 2 must not begin until that freeze completes.
