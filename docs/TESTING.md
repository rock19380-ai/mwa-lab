# Testing

## Deterministic CI gates

GitHub Actions runs deterministic, non-device gates:

```bash
./gradlew lint
./gradlew test
./gradlew assembleDebug
./gradlew :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest
./scripts/phase1_static.sh
./scripts/phase4_static.sh
```

The workflow retains Gradle wrapper validation and verifies both primary and
demo-client debug APKs exist.

The historical `phase2_static.sh` enforced the pinned walletlib version, frozen Phase 1
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

## Phase 3 verification

The frozen Phase 2 script is byte-for-byte preserved, including Room absence.
The Phase 3 gate runs Phase 1 and checks the historical script/predecessor source
hashes, schema constraints, recorder/UI boundaries, and scope. CI selects the
current gate when the exported Room schema is present. It does not execute the
historical Room-absence gate on Phase 3. Existing protocol behavioral tests remain
required; source checks supplement them rather than replacing them.

JVM suites cover domain status, request-start ordering, immutable session binding,
duplicates, reverse completion, safe metadata, diagnostic failure isolation,
claimed-write/close races, and first close-time authority. Deterministic ViewModel
tests cover loading/empty/active/pass/fail/interrupted/error/retry states, ordered
errors and summaries, selection isolation, newly loaded history, and public identity.

Device suites include all five predecessor/Step 3.6 classes, Room hostile reopen,
foreign keys/uniqueness, 32 concurrent close/completion rounds, real walletlib
replacement at preparation/approval/signing, pending host close, injected storage
failures, actual SQLite/WAL secret scans, and six focused Compose tests.

Run instrumentation classes separately. This local AGP/runner path executed only
the first class from comma-separated filters; XML must prove each requested class,
nonzero test counts, zero failures, and zero skipped cases. APK assembly alone
is not execution evidence. The acceptance JSONL records failures and repairs as
well as subsequent green runs; use the final successful records for closeout.

For full process restart and real cross-package UI acceptance, install debug APKs
with `adb install -r` and run:

```bash
python3 scripts/phase3_device_acceptance.py --serial emulator-5554 \
  --evidence docs/evidence/phase3/phase3-process-restart-ui-acceptance.json
```

This uses the existing demo client's approved and rejected message flows through
actual controls, verifies exact protocol results, force-stops/relaunches MWA Lab,
compares SQLite session/event records, and inspects both Sessions/Detail timelines.
It never deletes session history or substitutes logs for product state. The
connected harness can remove its target app at teardown; the script initializes
Home before copying and validates actual SQLite headers. Screenshots/database
copies remain in `/tmp`, while only safe structured results are committed.

The six observable method tests preserve GET_CAPABILITIES absence. Reverse and
concurrent recorder tests use repository seams because clientlib 2.0.7 allows
only one outstanding live request per association. Live Devnet submission evidence
from Phase 2 stays historical; Phase 3 deterministic regression includes existing
submission ordering, failure, commitment, and no-resubmission tests.

## Phase 4 verification

Phase 4 adds deterministic parser/decoder, storage, UI, and cross-app acceptance
without re-running historical gates whose frozen scope intentionally prohibited
future phases. `scripts/phase3_static.sh` is byte-for-byte preserved; CI selects
`phase4_static.sh` when exported schema v2 exists. The Phase 4 gate runs the still
applicable Phase 1 gate, validates current schema/migration/source authority
contracts, and checks deterministic Phase 4 transaction vectors.

JVM coverage includes capability-profile parity, schema migration/persistence,
bounded legacy/v0 parsing, account privilege derivation, malformed/hostile input,
System Transfer, Memo, SPL Token Transfer/TransferChecked, unknown-program
truthfulness, SHA-256 fingerprints, pre-approval presentation, settlement, and
diagnostic failure isolation.

The Step 4.11 local gate evidence records 224 app JVM tests and 6 demo-client JVM
tests with zero errors/failures/skips, plus lint, debug APK assembly, AndroidTest
APK assembly, installation, and targeted connected tests. The recorded connected
classes contain 10 app instrumentation tests and 1 demo-client instrumentation
test, all passing. These counts describe that recorded checkpoint; later closeout
runs must record their own results rather than reuse counts blindly.

Real Android emulator acceptance uses:

```bash
python3 scripts/phase4_diagnostic_acceptance.py --serial emulator-5554 \
  --evidence-dir docs/evidence/phase4
```

The recorded Android 16 (`sdk_gphone64_x86_64`, API 36) run exercises:

- System Program Transfer inspection before approval;
- Unknown Program rendering without invented semantics;
- v0 partial inspection followed by the unchanged authoritative legacy-signing
  rejection;
- absence of a synthetic `GET_CAPABILITIES` protocol event;
- force-stop/restart equality for `sessions`, `protocol_events`,
  `capability_snapshots`, and `transaction_diagnostics`;
- product inspector rendering again after restart.

Phase 4 does not claim simulation, fault injection, report export, ALT RPC
resolution, v0 signing, or production-wallet compatibility. Those remain outside
this phase's acceptance matrix.

## Phase 5 simulation test matrix

Phase 5 adds deterministic public transaction templates and synthetic RPC
fixtures under `test-vectors/simulation/`. The templates use a synthetic public
payer/blockhash only for deterministic reconstruction; live acceptance must
substitute the authorized payer and a fresh Devnet blockhash. Synthetic fixtures
are never described as live-network evidence.

Required test coverage includes domain invariants; fixed RPC flags and bounded
response parsing; PASS/FAIL/custom-error/blockhash/unknown/RPC-unavailable
classification; coordinator duplicate-tap, retry, stale-session, fingerprint,
and v0 rejection; parent/child settlement ordering and persistence-failure
isolation; Room 1->2->3 and 2->3 migrations; approval UI states and PASS warning;
restart-surviving Session Detail evidence; hostile log/JSON/identity cases; and a
SQLite/WAL sentinel scan proving safe positive-control metadata is present while
raw payload/token/signature/private/RPC-body sentinels are absent.

Local deterministic gates are:

```bash
python3 scripts/verify_phase5_simulation_vectors.py
./scripts/phase5_static.sh
./gradlew lint test assembleDebug assembleDebugAndroidTest
```

Device/live-Devnet acceptance remains a separate pre-freeze requirement and may
only be marked PASS when it was actually executed through the real cross-package
MWA path.

### Phase 5 closeout checkpoint (2026-09-30)

Local checks passed: 245 app JVM tests, 9 demo-client JVM tests, 109 app
connected tests, and 1 demo-client connected test; Gradle lint and both debug
APK/AndroidTest APK builds passed. The five Room simulation instrumentation
tests include a database byte scan (including WAL/SHM when present) with a stored-fingerprint positive
control and absent synthetic raw-payload/secret sentinels. The repaired
full-history CI checkout passed at implementation commit
`ad0b584980a8322705070d20de59c978021cd47a` (run 36693030227).

The initial real cross-package Phase 5 attempt stopped before SIMULATE: the
then-authorized lab identity had zero Devnet lamports against a 5,000-lamport
fee. Faucet errors and that blocked attempt remain historical evidence.
Subsequently, the user funded the exact current installed identity; its confirmed
balance was 1,000,000,000 lamports at slot 505877125. Fresh template fees were
5,000 lamports, and each actual demo request fetched its own fresh blockhash,
fee and balance. The installed identity was preserved throughout live acceptance.

`python3 scripts/phase5_device_acceptance.py --scenario ALL` passed all three
real cross-package UI/wire scenarios. Independent read-only SQLite checks bound
each fingerprint to its exact session, canonical event, transaction and simulation
row. GOOD_PASS_APPROVE and BAD_FAIL_APPROVE returned independently verified
signatures; GOOD_PASS_REJECT returned -3, with parent OBSERVED_PROTOCOL and an
unchanged PASS/NONE child. Each demo reported submittedTransactions=0.

The real process was force-stopped and relaunched. All three sessions' rows,
payload indexes, attempt order and classifications remained identical. The last
session's persisted UI independently displayed the parent error, transaction
inspector, matching simulation fingerprint, redacted logs and PASS warning.
The live DB/WAL/SHM byte scan is separately scoped from the disposable Room
sentinel test; see `phase5-live-audit-method.md` and the live acceptance JSON.
No product code changed during this continuation, so the already-passing JVM,
connected, lint and build suites were retained rather than rerun. The new
final evidence commit must still receive its own exact-head CI success.

A session-close regression test was added after the initial CI repair. It failed
on the earlier settlement implementation and passes after commit
`7d7f709ebeea42fa454c9009557311f4a291fe28`. The test asserts that a
completed child attempt is not lost if session teardown happens immediately
after canonical parent persistence, while late callbacks are rejected.

The device runner now fails promptly on an explicit demo-client funding error
and emits only a preliminary UI/demo-wire receipt after visible success. Its
deterministic parser self-test is included in `scripts/phase5_static.sh` and
prevents a UI-only result from claiming Room binding, raw-payload absence, or
restart persistence. The repaired settlement commit passed exact-head CI run
36698868948.

<!-- PHASE6:TESTING:BEGIN -->
## Phase 6 verification

Phase 6 adds catalog/engine tests, selection persistence tests, recorder fault invariants, concrete walletlib callback tests, delay cancellation and association-replacement tests, Fault Lab Activity-recreation coverage, session-presentation truthfulness tests, independent machine-readable fault vectors, and a client-side acceptance runner. Historical Phase 5 full static gating is not reused because it intentionally freezes the pre-Phase-6 recorder hash; Phase 6 CI runs the still-applicable Phase 5 design/vector/parser checks plus the dedicated Phase 6 gate.
<!-- PHASE6:TESTING:END -->
