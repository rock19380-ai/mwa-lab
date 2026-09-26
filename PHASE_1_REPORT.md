# MWA Lab Phase 1 Report

**Phase:** Phase 1 — MWA Protocol Boundary Spike  
**Date:** 2026-09-26  
**Status:** FREEZE CANDIDATE — final tag gated on exact-head GitHub CI

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

## Closeout CI

The first Phase 1.12 closeout commit passed GitHub Actions for its exact SHA.

- Closeout commit: `ef1ae392e9685629775f4e9105600f0a2c168bbd`
- GitHub Actions run: `36237275332`
- Result: `success`
- Run URL: `https://github.com/rock19380-ai/mwa-lab/actions/runs/36237275332`

## Final freeze rule

The next commit is evidence-only and records this closeout CI proof.

That evidence commit is the intended frozen Phase 1 tree. It must itself pass
GitHub Actions for its exact SHA before the annotated Phase 1 tag is created.

The final exact-head CI run is recorded in the annotated tag metadata rather than
inside the commit tree, because CI can only run after the commit already exists.
