# MWA Lab Phase 1 Evidence Summary

Date: 2026-09-26

## Identity

- Branch: `phase1-mwa-protocol-boundary`
- Frozen Phase 0 ancestor: `b4e7631a7e20bd4d032bb5478582684c998801ab`
- Frozen Phase 0 tag: `phase0-android-baseline-2026-09-26`
- Verified Batch E commit: `bdec451df38f2936a2900e4dfcd5a4cdd6b33a73`
- Wallet dependency: `com.solanamobile:mobile-wallet-adapter-walletlib:2.0.7`

## Verified Phase 1 behavior

PASS:

- real Android `solana-wallet://` discovery/association;
- wallet-side MWA session establishment/teardown;
- protected persistent Devnet Lab identity;
- first Devnet authorization;
- returned account equals the Lab public identity;
- production/mainnet authorization rejection;
- missing-chain authorization rejection;
- walletlib-managed authorization state;
- truthful pinned `get_capabilities` response;
- deauthorization and invalidated authorization state;
- revoked-token reuse rejection;
- typed sanitized protocol evidence;
- deterministic cross-package demo-client sequence;
- clean install and primary app launch;
- local deterministic Gradle/static gates;
- final security scan.

## Canonical client

`dev.mwalab.democlient` is explicitly labeled:

`MWA Lab Demo Client — FOR TESTING ONLY`

Canonical sequence:

`CONNECT → AUTHORIZE → GET_CAPABILITIES → DEAUTHORIZE → CLOSE`

## Deliberately deferred

Phase 1 does not claim successful:

- existing-valid-token reauthorization;
- message signing;
- transaction signing;
- sign-and-send;
- Devnet RPC submission;
- production-wallet compatibility;
- mainnet support.

## CI boundary

GitHub Actions is deterministic and non-device.

The workflow preserves:

- `./gradlew lint`
- `./gradlew test`
- `./gradlew assembleDebug`

and adds deterministic AndroidTest APK assembly plus the Phase 1 static gate.

Real Android association/device proof remains separate evidence and must not be
inferred solely from CI.
