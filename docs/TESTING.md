# Testing

## Deterministic CI gates

GitHub Actions runs deterministic, non-device gates:

```bash
./gradlew lint
./gradlew test
./gradlew assembleDebug
./gradlew :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest
./scripts/phase1_static.sh
./scripts/phase2_static.sh
```

The workflow retains Gradle wrapper validation and verifies both primary and
demo-client debug APKs exist.

`phase2_static.sh` also enforces the pinned walletlib version, frozen Phase 1
issuer/scope compatibility, Devnet-only RPC authority, signing limits,
authorization guards, hostile-test anchors, sanitizer guards, documentation
truth markers, and Phase 2 closeout evidence.

## Phase 2 deterministic tests

Covered locally and in CI where platform-independent:

- authorization policy:
  - valid Devnet;
  - missing/testnet/unknown chain rejection;
  - malformed requested address;
  - scope mutation rejection;
- capability profile:
  - explicit request limits;
  - legacy transaction support;
  - `sign_transactions` feature;
  - SIWS not advertised;
- approval coordinator:
  - approve/reject;
  - single-flight Busy behavior;
  - cancellation/expiry;
  - stale/duplicate completion rejection;
- message/diagnostic sanitization checks;
- legacy transaction codec:
  - valid legacy transaction;
  - malformed/truncated/non-canonical input;
  - unsupported version;
  - missing/wrong signer;
  - signature-slot preservation;
  - immutable approved payload snapshot;
- Devnet send option validation;
- sign-and-send submission executor:
  - success;
  - partial failure;
  - returned-signature mismatch;
  - commitment ordering;
  - cancellation before/after RPC completion;
  - no resubmission.

## Device/instrumentation proof

Device proof is deliberately separate from GitHub Actions. Green CI is not
treated as proof that Android MWA association or interactive approval works.

Phase 2 device evidence includes:

- Android 16 emulator instrumentation;
- protected identity persistence/reset regression;
- real cross-package `solana-wallet://` association;
- first authorization and valid reauthorization;
- `get_capabilities`;
- approved/rejected `sign_messages`;
- approved legacy `sign_transactions`;
- approved/rejected `sign_and_send_transactions`;
- revoked current-session authorization rejection;
- revoked token cross-session rejection;
- pending approval cancellation on dApp/session teardown;
- fresh-session recovery after teardown;
- clientlib single-outstanding-request live behavior;
- mocked Devnet RPC failure matrix on Android;
- primary and cross-package regression instrumentation;
- device logcat secret audit.

## Live Devnet proof

Step 2.10 live acceptance verifies:

```text
SIGN_TRANSACTION_APPROVE
→ fetch live Devnet blockhash
→ approve
→ returned signed legacy transaction verifies
```

and:

```text
SIGN_AND_SEND_REJECT
→ reject
→ ERROR_NOT_SIGNED
```

and:

```text
SIGN_AND_SEND_APPROVE
→ approve
→ submit only to fixed Devnet RPC
→ returned signature matches signed transaction
→ requested commitment reached
```

The live A/B/C sequence was re-run successfully after the Step 2.11
authorization/submission hardening.

## Clientlib concurrency truth

Pinned clientlib 2.0.7 permits only one outstanding JSON-RPC request on one
client association.

Therefore:

- live evidence verifies local second-request rejection plus payload/signature
  isolation for the first outstanding request;
- deterministic `ApprovalCoordinatorTest` covers wallet callback-level
  single-flight and decision isolation;
- Phase 2 does **not** claim two simultaneous live wallet callbacks on one
  client association.

## Negative / hostile campaign

Phase 2.11 covers, among other cases:

- missing/testnet/unknown chain;
- revoked authorization;
- unknown account;
- unsupported optional feature / SIWS;
- transaction-like message;
- empty/over-limit/oversized payloads;
- approval cancellation and stale/duplicate completion;
- malformed/truncated/versioned legacy transaction input;
- wrong/missing signer;
- immutable approval binding;
- RPC I/O/timeout/HTTP/JSON-RPC/malformed responses;
- malformed/mismatched returned signatures;
- failed requested commitment;
- partial submission;
- cancellation/no-resubmission;
- secret-bearing diagnostics/logcat scans.

Evidence is stored under:

```text
docs/evidence/phase2/
```

## Phase 2 closeout state

`docs/evidence/phase2/phase2-step2.11-closeout.txt` records the final hostile
campaign closeout.

Phase 2.12 reconciles documentation/report/CI and then performs exact-head
remote CI and final Git freeze. Commit/tag identity belongs to that final freeze
evidence.

## Deferred to Phase 3+

Phase 2 does not test or claim:

- Room/SQLite persistent session history;
- restart-surviving protocol timelines;
- full diagnostic export;
- deterministic fault injection;
- production-wallet compatibility.
