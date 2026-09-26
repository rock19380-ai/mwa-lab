# Testing

## Deterministic CI gates

GitHub Actions runs only deterministic, non-device Phase 1 gates:

```bash
./gradlew lint
./gradlew test
./gradlew assembleDebug
./gradlew :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest
./scripts/phase1_static.sh
```

The workflow retains Gradle wrapper validation and verifies both primary and
demo-client debug APKs exist.

## Device/integration proof

Device proof is deliberately separate from GitHub Actions. A green CI build is
not treated as proof that Android MWA association works.

Phase 1 device acceptance covers:

- clean install;
- primary app launch;
- Lab identity creation/persistence/reset regression;
- real `solana-wallet://` discovery;
- local association/session establishment;
- request dispatch to `MwaSessionHost`;
- mainnet rejection;
- missing-chain rejection;
- first Devnet authorization;
- persistent Lab public account return;
- real `get_capabilities`;
- deauthorization;
- rejected revoked-token reuse;
- clean session close;
- separate cross-package demo-client canonical sequence repeated twice.

Evidence is stored under:

```text
docs/evidence/phase1/
```

## Canonical Phase 1 demo

```text
MWA Lab Demo Client
→ discover/associate with MWA Lab
→ AUTHORIZE on solana:devnet
→ receive persistent Lab public address
→ GET_CAPABILITIES
→ DEAUTHORIZE
→ close
```

The client is a separate Android package and is explicitly marked
`FOR TESTING ONLY`.

## Negative/security testing

Phase 1 verifies:

- production/mainnet chain rejects;
- missing chain rejects;
- unsupported optional feature rejects;
- requested account mismatch rejects;
- revoked authorization state does not remain authoritative;
- raw auth-token values are absent from MWA Lab diagnostics;
- private key/seed values are absent from diagnostics;
- signing/submission is not performed.

## Deferred tests

Phase 2 owns successful:

- existing-token reauthorization;
- message signing;
- transaction signing;
- sign-and-send;
- Devnet RPC submission;
- user approval/signing UI behavior.
