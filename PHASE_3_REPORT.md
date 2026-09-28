# Phase 3 report — persistent protocol recorder and timeline

Phase 3 implementation and local acceptance are complete. Home, Sessions, and
Session Detail use persistent structured history. Phase 4 is NOT STARTED.
Freeze requires successful Android CI for the exact final commit. The annotated
`phase3-protocol-recorder-timeline-2026-09-28` tag is created only after that gate
and records final commit/remote branch, exact-head CI, and post-commit local gates.
Freeze identity is external to this report to avoid a self-referential commit hash.

## Delivered scope and provenance

This continuation preserved uncommitted Steps 3.0–3.6 on
`phase3-protocol-recorder-timeline`, descending from frozen Phase 2
`009a849441b9d4db453b0162ae1fd9f5fdad8dc4`. No reset, discard, restart, or redesign.
The detailed and condensed plans use different numbering; product outcomes
control closeout. The historical pre-Room gate conflict is resolved in
[the reconciliation](docs/phase3-persistence-gate-reconciliation.md).

Delivered: exported Room storage, session lifecycle, six-method canonical recorder,
hostile/restart/security acceptance, Home/Sessions/Detail ViewModels and Compose,
UI tests, current static/CI gate, and evidence. Complete intentional file inventory:
[PHASE_3_FILES.txt](PHASE_3_FILES.txt).

## Architecture and recorder

```text
MWA callback -> MwaSessionHost -> ProtocolRecorder.begin -> immutable handle
-> existing Phase 2 operation/response -> ProtocolRecorder.complete
-> SessionRepository -> Room/SQLite -> Flow/StateFlow -> ViewModel -> Compose
```

Callbacks capture the observing session. Begin allocates stable ID, immutable
session binding, start time, safe request summary, and per-session sequence from 1.
Completion never reads mutable active-session state. Request-start sequence controls
order across reverse completion. One terminal claim owns one persistence write;
duplicates cannot add rows. Close cancels pending handles in the original session
and awaits already-claimed writes. Claimed settlement survives caller cancellation.
A begin crossing close settles CANCELLED in its original session. First close
reason and observation time win; duplicate close reuses both. START_FAILED is
captured before cleanup callbacks. Ordinary storage failure has diagnostic-only
authority and preserves wallet responses.

ProtocolEvidenceStore/MwaSessionEvidenceStore remain predecessor compatibility
mirrors. The product UI does not consume them. Phase 2 approval, authorization
guards, parsing, signing, Devnet RPC, submission/commitment, protocol errors, and
response order remain regression-green.

## Room and lifecycle

Database **mwa_lab.db**, schema **1**, exported at
`app/schemas/dev.mwalab.storage.MwaLabDatabase/1.json`.
Identity hash: `0da311efe4dcefa6a30a9643eac37192`.

| Table | Metadata |
| --- | --- |
| sessions | ID, start/end epoch ms, bounded dApp display label, Devnet cluster, close reason |
| protocol_events | Event/session IDs, sequence, method, start/end epoch ms, outcome, nullable error, failure source, safe request/response JSON, reserved nullable capability context/fault ID |

Stable event primary keys, session FK/CASCADE, and unique(session_id, sequence)
are enforced. No destructive migration fallback. Future reserved fields stay null;
no capability snapshot or fault engine. Status/count/duration derive from aggregates.
Open rows are ACTIVE (recorded open), connection liveness unknown. Closed status:
FAIL for failed methods or start/scenario error, CANCELLED for interrupted history,
UNKNOWN for normal closure with no observed methods, otherwise PASS. Failure has
priority over cancellation. Close causes cover serving/scenario completion, error,
teardown, low-power/no connection, host close, replacement attempt, and start failure.
Session end is first close observation; cancelled request settlement can end later.

Successful, failed, and interrupted histories, labels, summaries, close causes, and
order survive database reopen. Actual force-stop/relaunch preserved all SQL session
and event records and displayed persisted PASS/FAIL histories.

## Observable methods

| Method | Persistent recorder |
| --- | --- |
| AUTHORIZE | Actual callback |
| REAUTHORIZE | Actual callback |
| DEAUTHORIZE | Actual callback |
| SIGN_MESSAGES | Actual callback |
| SIGN_TRANSACTIONS | Actual callback |
| SIGN_AND_SEND_TRANSACTIONS | Actual callback |
| GET_CAPABILITIES | NOT OBSERVABLE THROUGH PINNED WALLETLIB |

walletlib 2.0.7 handles get_capabilities internally. Real client calls work, but
configured profile is not an observed request. Synthetic begin is rejected; no row
is created. Detail states the limitation. Unknown errors preserve numeric values
without fabricated names. Safe capability context can render only when recorded;
the current host does not populate or fabricate it.

## Security

Mainnet is disabled; fixed Devnet policy/RPC and protected Lab identity are unchanged.
No production-wallet secret support was added. DiagnosticSanitizer is unchanged.
Recorder uses approved keys, bounded sanitized snapshots, counts/lengths/SHA-256,
booleans, fixed enum/reason strings, and validated public metadata. External chain
and commitment normalize to approved values or unsupported. dApp names are bounded
control-free display claims, not verified identities or an arbitrary-secret detector.

Diagnostic storage/UI do not add private keys, seeds, mnemonics, raw auth tokens,
association secrets/tokens, raw messages/transactions/signed payloads/signatures,
encrypted transport, RPC bodies, or raw exceptions. URI/icon/query fields are absent.
Actual SQLite/WAL scans include positive controls, 11 sensitive-category sentinels,
and real wire tokens/messages/signed messages/signatures. Scans passed before and
after close/reopen. Scope is diagnostic storage, not walletlib authorization storage
or Android Keystore.

## UI and hostile acceptance

Home shows public Devnet identity/latest history. Sessions shows sanitized dApp
label, UTC start, duration or recorded-open state, count/status, Devnet, close cause,
and structured failure summary. Detail shows canonical sequence/method, times,
duration, outcome, error number/known name, failure source, and safe summaries.
Saved selection/navigation survive configuration changes. Repository errors are
generic and retryable. Compose collects lifecycle-aware StateFlow; no DAO/log parsing.

Hostile acceptance covers reverse completion, old/new isolation, replacement at
preparation/approval/signing, pending host close, duplicate completions/closes,
close/scenario races, 32 concurrent Room rounds, constraints, reopen, and external
metadata smuggling. Injected create/event/label/finish repository failures preserve
successful wire authorize/sign/deauthorize. These are safe test seams, not a product
fault simulator or an organic wallet-failure claim.

ViewModel tests cover loading/empty/active/pass/fail/interrupted, ordering/errors,
safe summaries, selection isolation, generic errors/retry, loaded history/public
identity. Compose covers state/error/retry rendering, summary/capability truth, and
Room-loaded navigation. Actual cross-package approval/rejection controls and process
restart supplement UI tests; screenshots alone are not proof.

## Executed verification

Commands, timestamps, exits, test cases, and log/XML hashes:
[final local acceptance](docs/evidence/phase3/phase3-final-local-acceptance.json),
[execution ledger](docs/evidence/phase3/phase3-executed-gates.jsonl), and
[final source hashes](docs/evidence/phase3/phase3-final-source-sha256.txt).
Final source/build/gate/CI fingerprint: `c28cf16abd29614d1d423a87cbdaa6d294b4061bb2efb09513ef3920257b39cd`.
Build/unit/device gates below used identical final application/build/CI source;
only the acceptance script gained bounded fresh-hierarchy retry and full-result viewport capture afterward.
The receipt mathematically verifies that sole verifier difference. All gates are
repeated at the final commit before tagging; tag annotation records those results.

| Command | Result |
| --- | --- |
| `./gradlew :app:compileDebugKotlin` | PASS |
| `./gradlew :app:compileDebugAndroidTestKotlin` | PASS |
| `./gradlew :app:testDebugUnitTest --tests dev.mwalab.protocol.recorder.PersistentProtocolRecorderTest` | PASS |
| `./gradlew :app:testDebugUnitTest --tests dev.mwalab.session.SessionLifecycleCoordinatorTest` | PASS |
| `./gradlew :app:testDebugUnitTest --tests dev.mwalab.protocol.ProtocolEventTest --tests dev.mwalab.session.SessionDomainTest` | PASS |
| `./gradlew :app:testDebugUnitTest --tests dev.mwalab.ui.sessions.SessionViewModelsTest` | PASS |
| `bash ./scripts/phase1_static.sh` | PASS |
| `./gradlew lint` | PASS |
| `./gradlew test --rerun-tasks` | PASS |
| `./gradlew assembleDebug` | PASS |
| `./gradlew :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest` | PASS |
| `bash ./scripts/phase3_static.sh` | PASS |

Full unit run: 107 tests / 18 suites, zero failures/errors/skips.
Focused recorder 17, lifecycle 6, protocol event 5, session domain 12, ViewModel 9.
Lint: zero errors, 22 tool/dependency/label/resource warnings; no new suppression
or baseline. Pins remain AGP 9.4.1, Kotlin 2.2.10, KSP 2.3.12, Room 2.8.5,
Gradle 9.6.0, walletlib/clientlib 2.0.7; no compatibility escape flags.

Device: **MWA_Lab_API_36**, Android 16, emulator-5554. Each app class ran separately
with `./gradlew :app:connectedDebugAndroidTest
-Pandroid.testInstrumentationRunnerArguments.class=<class>`; cross-app used
`:demo-client:connectedDebugAndroidTest`. XML verifies actual nonzero execution,
zero failures and skips. APK compilation is not device execution.

| Class | Tests | Result |
| --- | --- | --- |
| `dev.mwalab.mwa.MwaSigningRecorderInstrumentedTest` | 1 | PASS |
| `dev.mwalab.mwa.MwaAuthorizationRecorderInstrumentedTest` | 1 | PASS |
| `dev.mwalab.mwa.MwaLocalAssociationInstrumentedTest` | 1 | PASS |
| `dev.mwalab.mwa.MwaSessionLifecycleInstrumentedTest` | 2 | PASS |
| `dev.mwalab.storage.RoomSessionRepositoryInstrumentedTest` | 5 | PASS |
| `dev.mwalab.storage.RoomTimelineHostileInstrumentedTest` | 4 | PASS |
| `dev.mwalab.mwa.MwaHostileRecorderInstrumentedTest` | 3 | PASS |
| `dev.mwalab.ui.sessions.SessionsUiInstrumentedTest` | 6 | PASS |
| `dev.mwalab.rpc.SolanaDevnetRpcGatewayInstrumentedTest` | 9 | PASS |
| `dev.mwalab.identity.AndroidKeystoreIdentityRepositoryInstrumentedTest` | 1 | PASS |
| `dev.mwalab.identity.LabSigningServiceInstrumentedTest` | 1 | PASS |
| `dev.mwalab.democlient.DemoClientCrossAppInstrumentedTest` | 1 | PASS |

`python3 scripts/phase3_device_acceptance.py --serial emulator-5554 --evidence
docs/evidence/phase3/phase3-process-restart-ui-acceptance.json` passed real
SIGN_MESSAGE_APPROVE/REJECT results, exact SQLite equality after process restart,
and persisted PASS/FAIL Sessions/Detail UI checks. Debug APKs were installed.

## Hard audit and limitations

Correct branch/ancestry; no predecessor deletion, generated junk, mystery staged
changes, or conflict markers; full baseline diff check clean. Export/name/version,
FK/uniqueness, recorder race authority, diagnostic isolation, UI ordering/errors,
restart history, actual secret scan, and capabilities truth gates passed.
`scripts/phase2_static.sh` is byte-for-byte unchanged. Phase 1/3 gates verify its
historical hash and 28 unchanged predecessor boundaries; behavioral regression
supplies current Phase 2 proof. No forbidden network or production secret scope.

Failed ownership/close-time/API-23 checks were repaired narrowly; failures and
classifications remain in [the repair receipt](docs/evidence/phase3/phase3-hostile-repair.txt)
and ledger. Reserved-ID UI fixture, missing SQLite setup, and transient missing
UI snapshot and clipped-result viewport failures were test/verifier repairs. Historical source-excerpt whitespace
normalization has a separate digest receipt; old results were not rewritten.

Persistence remains best effort during storage failure. Abrupt death can lose
unfinished in-memory handles and leave open rows; no invented completion/recovery
close timestamp. clientlib 2.0.7 permits one live outstanding request per association;
reverse/concurrent recorder checks use safe seams. No fresh live Devnet submission
is claimed here: unchanged submission/commitment regressions pass, and Phase 2 live
network evidence remains historical.

Phase 4 is **NOT STARTED**: capability snapshots, transaction inspector/decoding,
simulation, fault engine/framework, export/share, mainnet, and production-wallet
compatibility expansion are deferred.
