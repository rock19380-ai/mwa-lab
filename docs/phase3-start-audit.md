# Phase 3 start audit — Protocol Recorder + Persistent Session Timeline

Audit date: 2026-09-28. Repository: `/home/abbaas/mwa-lab`.

**PHASE 3 BASELINE AUDIT: PASS — SAFE TO BEGIN IMPLEMENTATION**

The exact Phase 2 frozen baseline is present, local and remote refs agree,
all six required baseline gates passed, and a fresh unit-test execution passed.
This is permission to proceed to the next separately scoped prompt, not a claim
that Phase 3 is implemented or that its persistence/device acceptance has passed.
This audit changes documentation/evidence only. No branch, commit, tag, source,
dependency, test, historical evidence, or verification script was changed.

## Authority and scope

Read both authoritative plans from `/home/abbaas/Downloads/MWA_LAB/`:

- `MWA_LAB_MASTER_IMPLEMENTATION_PLAN_v1.1_2026-09-26.md`
- `MWA_LAB_PHASE_3_IMPLEMENTATION_PLAN_2026-09-28.md`

They are external planning inputs, not tracked repository files. Their SHA-256
digests are recorded with the source inventory. Also inspected `PHASE_2_REPORT.md`,
the Phase 1/2 static scripts, Phase 0 wrapper gate, Android CI workflow, current
architecture/testing/API documentation, production source, store consumers,
relevant tests, and Phase 2 API/security/hostile/lifecycle/closeout evidence.
The tracked inventory contains 206 files. No applicable `AGENTS.md` was found
in the repository or its ancestor directories.

Historical Phase 2 device/live-network results are retained as historical
evidence; they are not represented as freshly executed by this audit.

## A. Exact frozen baseline

| Item | Verified value |
|---|---|
| Current branch | `phase2-core-request-signing` |
| Current HEAD | `009a849441b9d4db453b0162ae1fd9f5fdad8dc4` |
| HEAD tree | `b6240cea8499dca3b595583be8217a9be191cbd3` |
| Frozen final tag | `phase2-core-request-signing-2026-09-27` |
| Annotated tag object | `3153308e409715453188771f6ba8a56ed9237a1a` |
| Peeled tag commit | `009a849441b9d4db453b0162ae1fd9f5fdad8dc4` |
| Initial worktree/index | Clean; no tracked or untracked changes |
| Remote Phase 2 branch | Same commit as HEAD |
| Remote final tag | Same tag object and peeled commit as local |

The final freeze manifest names implementation commit
`3d70dc62295937248fc456f738d93c460ee565b8`. That is the implementation predecessor,
not the final frozen closeout commit. The annotated final tag identifies the
containing closeout commit `009a849…`, implementation CI run `36315211028`, and
closeout CI run `36315789556`. CI identifiers here are tag provenance; GitHub
Actions was not rerun or queried for a new conclusion in this audit.

Relevant tags, newest first, with peeled commits:

| Tag | Commit |
|---|---|
| `phase2-core-request-signing-2026-09-27` | `009a849441b9d4db453b0162ae1fd9f5fdad8dc4` |
| `phase2-batch-a-sign-messages-2026-09-27` | `9d2dea7c17997c7b1d3f1275b7456ef77a1feab7` |
| `phase1-mwa-protocol-boundary-2026-09-26` | `76aeab50c7202bd372d6986bd8ca266aff61f086` |
| `phase0-android-baseline-2026-09-26` | `b4e7631a7e20bd4d032bb5478582684c998801ab` |

`git ls-remote --symref` confirms all four local tag objects match remote tags.
Local branches and remote-tracking refs for main, Phase 1, and Phase 2 match the
corresponding live remote heads. Remote default `HEAD` points to `main`, still
at the Phase 0 commit. This is not drift in the Phase 2 baseline; do not use
remote default HEAD as the Phase 3 starting point.

No repair/reset/fetch/checkout was performed. The suggested Phase 3 branch has
not been created because this prompt requests an audit before implementation.
Create it from the verified final commit in the next authorized implementation
step; do not restart from the implementation predecessor or remote main.

## B. Executed baseline gates

| Command | Result |
|---|---|
| `./gradlew lint` | PASS, exit 0 |
| `./gradlew test` | PASS, exit 0; unit-test tasks were up to date |
| `./gradlew assembleDebug` | PASS, exit 0 |
| `./gradlew :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest` | PASS, exit 0 |
| `./scripts/phase1_static.sh` | PASS, exit 0 |
| `./scripts/phase2_static.sh` | PASS, exit 0 |
| `./gradlew test --rerun-tasks` | PASS, exit 0; 58 tests, 0 failures, 0 errors, 0 skipped |
| `bash -n scripts/phase0_gate.sh scripts/phase1_static.sh scripts/phase2_static.sh` | PASS, exit 0 |

The extra test invocation was necessary to replace cached unit-test results
with fresh execution evidence. Build/lint logs preserve Gradle's actual task
statuses; up-to-date tasks are not described as newly executed tests.
AndroidTest APK assembly is compilation/package proof, not instrumentation
execution. No connected instrumentation, interactive approval, public Devnet
submission, app restart, or Room persistence test ran in this audit.

The sandbox failed before commands could run with
`bwrap: loopback: Failed RTM_NEWADDR: Operation not permitted`.
Checks were rerun through approved execution outside the sandbox. Initial
artifact inspection also found `javap` absent from PATH; using the existing
Gradle-managed JDK 25 resolved that tooling issue. Neither issue is a test
failure. No tests or source were weakened to obtain these results.

## C. Current architecture and responsibility map

Paths in this table are relative to `app/src/main/java/dev/mwalab/` unless shown
otherwise. These are existing files, not proposed abstractions.

| Responsibility | Exact file/type and behavior |
|---|---|
| Android association entry/lifetime | `mwa/MobileWalletAdapterActivity.kt`: `onCreate`, `onNewIntent`, `processAssociationIntent`, `onDestroy`; manifest declares `singleTask`, dedicated task affinity, `solana-wallet` filters |
| Association/session lifecycle | `mwa/MwaSessionHost.kt`: `openAssociation`, `closeCurrentScenario`, `close`, `createCallbacks(generation)`, `notifySessionFinishedIfCurrent`; `mwa/association/AssociationOpenResult.kt` defines rejected reasons |
| Pinned scenario/transport | walletlib `AssociationUri` / `LocalAssociationUri.createScenario`, `LocalWebSocketServerScenario`, `LocalScenario`, `BaseScenario`; host invokes `Scenario.start()` / `close()` |
| Protocol callbacks | `MwaSessionHost.createCallbacks`: authorize, reauthorize, deauthorized event, sign messages, sign transactions, sign-and-send; no capability callback |
| Protocol evidence | `protocol/ProtocolEvidence.kt`: `ProtocolEvidence`, `ProtocolEvidenceSink`, `ProtocolEvidenceStore`; host `recordProtocol` constructs completed records |
| Lifecycle evidence | `mwa/evidence/MwaSessionEvidence.kt`: `MwaSessionEvent`, `MwaSessionEvidence`, sink/store; host `record` writes enum plus fixed detail |
| Sequence/event identity | Host `protocolSequence: AtomicLong`, `activeSessionId`, `recordProtocol`; sequence assigned at recording completion |
| Timing | Callback start and `recordProtocol` use `System.currentTimeMillis`; `ProtocolEvidence.durationMillis` derives nonnegative duration; lifecycle evidence defaults timestamp similarly |
| Request/response summaries | Host `signingRequestSummary` supplies count, length, SHA-256, chain/account count; `recordProtocol` sanitizes both maps with `security/DiagnosticSanitizer.kt` |
| Authorization policy/state | `mwa/authorization/LabAuthorizationPolicy.kt`, `security/NetworkPolicy.kt`, host generation guards; walletlib `AuthRepositoryImpl` owns authorization records/token issuance/validation/revocation |
| Approval | `approval/ApprovalCoordinator.kt`: single pending request, request-ID-bound decisions, StateFlow, cancellation, 120-second timeout; approval UI in `MobileWalletAdapterActivity.kt` |
| Message signing | Host `handleSignMessages`; `transaction/SolanaTransactionMessageDetector.kt` rejects transaction-like messages; `signing/LabSigningService.kt` abstracts protected signer |
| Transaction signing | Host `prepareLegacyTransactions`, `signLegacyTransactions`, `handleSignTransactions`; `transaction/LegacyTransactionCodec.kt` bounds parsing and patches only the Lab signature slot |
| Protected identity/signing implementation | `identity/AndroidKeystoreIdentityRepository.kt` implements `IdentityRepository` and `LabSigningService`; `identity/Ed25519IdentityMaterial.kt` performs key/signature primitives |
| Submission | Host `handleSignAndSendTransactions`; `rpc/SignAndSendSubmissionExecutor.kt` owns ordering, signature cross-check, partial failure, commitment gating, cancellation/no resubmission |
| Devnet RPC | `rpc/DevnetRpcGateway.kt`: interface, `SolanaDevnetRpcGateway`, private `FixedDevnetHttpTransport`, typed results; fixed `https://api.devnet.solana.com`, no caller URL |
| Composition | `app/MwaLabComposition.kt`: synchronized application-context identity/signer, approval, RPC singletons; no recorder/database |
| UI/navigation/state | `MainActivity.kt`: single home composition with remembered `IdentityUiState`; approval Activity observes coordinator StateFlow. No navigation graph, session UI, or ViewModels exist |
| Demo client | `demo-client/src/main/java/dev/mwalab/democlient/`: `DemoClientActivity.kt`, `DemoClientRunner.kt`, `Phase2AcceptanceRunner.kt`, `DemoDevnetRpc.kt`, `DemoLegacyTransactionFactory.kt`; separate test infrastructure |

## D. Existing Phase 3 groundwork

`ProtocolEvidence` currently has exactly:

| Field | Type/default |
|---|---|
| `sessionId` | `String = "unknown"` |
| `eventId` | `String = "unknown:0"` |
| `sequence` | `Long = 0` |
| `method` | `ProtocolMethod`, required |
| `startedAtEpochMillis` | `Long`, required |
| `completedAtEpochMillis` | `Long`, required |
| `outcome` | `ProtocolOutcome`, required |
| `protocolErrorCode` | `Int? = null` |
| `failureSource` | `ProtocolFailureSource = NONE` |
| `requestSummary` | `Map<String, String> = emptyMap()` |
| `responseSummary` | `Map<String, String> = emptyMap()` |

Computed `durationMillis = (completedAtEpochMillis - startedAtEpochMillis).coerceAtLeast(0)`.
There is no stored duration, persistent session model, event handle, injected
fault ID, or per-event capability context.

Exact enum values:

- `ProtocolMethod`: `AUTHORIZE`, `REAUTHORIZE`, `DEAUTHORIZE`,
  `GET_CAPABILITIES`, `SIGN_MESSAGES`, `SIGN_TRANSACTIONS`,
  `SIGN_AND_SEND_TRANSACTIONS`. Wire names are the corresponding lowercase
  snake-case strings. An enum member alone is not observation evidence.
- `ProtocolOutcome`: `SUCCESS`, `FAILURE`, `CANCELLED`.
- `ProtocolFailureSource`: `NONE`, `INJECTED`, `OBSERVED_PROTOCOL`, `SIMULATION`,
  `RPC_NETWORK`, `LOCAL_PARSER`, `UNKNOWN`. All seven distinctions must survive.

Both evidence stores are process-global objects backed by
`CopyOnWriteArrayList`; they only append, snapshot, and reset for tests. They
are unbounded, process-local, nonpersistent, and not observable database Flows.
`MwaSessionEvidence` has only event, timestamp, and optional detail: no session
identifier or generation. Existing identity/auth persistence is separate from
these diagnostic stores and must not be migrated into the recorder.

### Session and sequence ownership

Host lines 81–97 initialize a UUID, then assign a fresh `activeSessionId` and
reset `protocolSequence` for every `openAssociation` call, before URI validation.
Generation increments and the old scenario closes before validation too.
Signing callbacks capture `activeSessionId` and pass it through their handlers
to `ApprovalRequest.sessionId`. That protects approval attribution only.

Host lines 1418–1433 increment the current sequence and read the mutable
`activeSessionId` inside `recordProtocol`, after asynchronous work. Completion
time can therefore read replacement-session state. UUID and counter reads are
not one atomic session-bound operation. The first emitted event is sequence 1,
but numbering represents completion order and resets even on rejected opens.
Generation checks protect frozen protocol behavior; they are not an immutable
diagnostic event handle and do not remove every check-to-record race.

No stale-callback race was newly reproduced here. This is a source-confirmed
attribution hazard and the exact gap Phase 3 is designed to remove.

### Current evidence completeness and classification

Many normal success/rejection branches record typed metadata. However, stale
generation returns, post-sign authorization-invalid returns, and submission
executor cancellation can complete/decline a request without recording a
terminal protocol event. No begin record or exactly-once completion guard exists.
Some internal-error completions record `protocolErrorCode = null`; Phase 3 must
audit the pinned completion/error mapping rather than infer an RPC error code
or relabel a response. Completion timestamps mean the host completed its
request object, not proof of delivery to the remote client.

Current examples: unsupported chain/authorization rejection/user rejection use
`OBSERVED_PROTOCOL`; malformed payload/transaction/options use `LOCAL_PARSER`;
RPC unavailability/submission failures use `RPC_NETWORK`; internal identity or
signer failures use `UNKNOWN`; success defaults to `NONE`. A blockhash reported
invalid on Devnet currently uses `OBSERVED_PROTOCOL`. Approval cancellation is
`CANCELLED` with `ERROR_NOT_SIGNED`; rejection/busy/expiry are `FAILURE` with that
same protocol code. Preserve distinctions and audit diagnostic changes explicitly.
No implemented synthetic fault or simulation behavior uses the reserved enums.

### Sanitization boundary

`DiagnosticSanitizer` redacts values whose normalized keys contain listed
sensitive fragments; other values are truncated to 512 characters. It does
not validate arbitrary values, constrain key/map counts, enforce an allowlist,
or deep-freeze mutable maps. Both stores accept externally supplied records
without independently sanitizing. Lifecycle detail relies on fixed host strings.
Current safe summary builders avoid raw payloads; this is not sufficient proof
that any arbitrary map or dApp identity is persistence-safe. Phase 3 needs a
bounded safe-input contract and persisted-data sentinel tests before Room writes.

### Existing test consumers

Only `app/src/androidTest/java/dev/mwalab/mwa/MwaLocalAssociationInstrumentedTest.kt`
directly consumes either evidence store. Its
`phase1AuthorizeCapabilitiesDeauthorizeAndRevocationFlow` resets both, waits for
lifecycle events, asserts authorize success/failure and deauthorize success,
and checks summary/detail safety. It makes a real client capability call but
does not assert a `GET_CAPABILITIES` host event. Preserve this test/store seam.
No unit test or demo-client source directly consumes either store.

Related regression suites include `ApprovalCoordinatorTest`,
`LabAuthorizationPolicyTest`, `NetworkPolicyTest`, `DiagnosticSanitizerTest`,
`MwaCapabilityProfileTest`, `LegacyTransactionCodecTest`,
`SolanaTransactionMessageDetectorTest`, `Ed25519SigningTest`,
`Ed25519IdentityMaterialTest`, `DevnetSendOptionsTest`,
`SignAndSendSubmissionExecutorTest`, Android identity/signing/RPC suites,
and `DemoClientCrossAppInstrumentedTest`. The evidence inventory lists exact
paths and test method names. The existing 58 JVM tests do not prove Room,
event ordering across replacement, or recorder exactly-once semantics.

## E. Pinned get_capabilities observation status

Inspected the resolved walletlib **2.0.7 AAR bytecode**, not remembered current
upstream APIs. SHA-256:
`e5c639964c5740e187cacf31776f0e46b9a2433e26b50fc027ab8ffb481b177c`.
It matches the frozen Phase 1 artifact evidence. Extracted `classes.jar` SHA-256:
`9edb779aae07dd571cbde6e7e474a1ed6899a8bb41f10becab48b0880272eba1`.
Full commands and disassembly are retained in the observability evidence.

Verified path:

1. `LocalAssociationUri.createScenario` constructs `LocalWebSocketServerScenario`.
2. `LocalScenario.createMessageReceiver()` constructs a
   `MobileWalletAdapterSession` wrapping a newly constructed
   `MobileWalletAdapterServer`.
3. `MobileWalletAdapterServer.dispatchRpc` dispatches `get_capabilities` directly
   to private `handleGetCapabilities`.
4. That handler validates parameters and constructs the response from `mConfig`:
   clone authorization false, configured transaction/message limits when nonzero,
   transaction versions, optional features, and sign-and-send support. It calls
   `handleRpcResult` internally. It never calls a host method handler.
5. Neither `Scenario.Callbacks`, `LocalScenario.Callbacks`, nor
   `MobileWalletAdapterServer.MethodHandlers` contains a capability callback.

There are lower-level extensible methods: `dispatchRpc` and result/error methods
are protected, and `createMessageReceiver` is public. They are **not an injectable
read-only observer on the current factory path**. The factory constructs the
concrete server internally; `BaseScenario.mMethodHandlers` is package-private
and `LocalScenario.mSessionStateCallbacks` is private. Replacing receiver
construction would cross authorization/session/transport ownership. Merely
wrapping the returned session receiver does not expose decrypted method dispatch.
No safe drop-in request observer is exposed to the existing host by these APIs.

Decision: preserve walletlib/clientlib 2.0.7 and record **no host-observed
GET_CAPABILITIES event**. Treat the existing `MwaCapabilityProfile.snapshot()`
as configured context only. It is not proof that a request occurred. A future
UI must label any such context “Configured capability profile”; full persisted
capability snapshots remain Phase 4. Do not manufacture an event, time, sequence,
or outcome. A future library seam would require separate source/digest review
and parity tests; none was patched or implemented here.

The same observability rule applies to internal walletlib rejections: bytecode
shows invalid/revoked-token reauthorization and unknown-token deauthorization
can be handled without a host callback. Malformed/over-limit signing requests
can also be rejected in server dispatch. Do not claim host-level coverage of
all wire traffic or fabricate missing method callbacks.

## F. Frozen behavior and Phase 3 risks

Regression-protected behavior includes pinned 2.0.7 libraries, local association
semantics, Devnet-only network policy, unchanged issuer `mwa-lab-phase1` and scope
`mwa-lab:phase1:devnet:v1`, walletlib token authority, current-session authorization
generation, stale-session protection, explicit request-bound approval, protected
Ed25519 signing, max 10 messages/transactions, 64 KiB message bound, transaction-like
message rejection, 1232-byte legacy-only transaction parsing, signer binding,
immutable transaction input snapshot, Devnet blockhash validation, exact protocol
completion/error mappings, fixed Devnet RPC, submission ordering/commitments,
partial failure, cancellation/no resubmission, mainnet rejection, and safe diagnostics.

Historical supporting evidence includes Phase 2 Batch A security/API and session
generation repair acceptance; Batch B API/security contracts; Step 2.10 live
Devnet acceptance/closeout; Step 2.11A hostile contract; Step 2.11B1 RPC/submission
contract/acceptance; Step 2.11B2 lifecycle contract/acceptance; Step 2.11 closeout;
and Step 2.12 final closeout/freeze manifest. Earlier intermediate scope statements
must not override the final code/report (for example Batch A declined transactions).

Risks and narrow handling:

1. **Cross-session attribution/order:** capture immutable session, sequence,
   start time, and sanitized request summary at request start. Completion must
   never read replacement-session state; test A→B replacement with late A work.
2. **Incomplete/duplicate terminal evidence:** audit every early return and
   cancellation, use idempotent completion, and preserve the actual protocol
   response. Persistence errors must not cause a second response or alter signing.
3. **Persistence privacy:** enforce safe maps/dApp display names before storage;
   never send raw request objects, URIs, tokens, payloads, signatures, or exception
   bodies to DAOs. Current backup rules exclude identity preferences only;
   review explicit database backup/transfer policy when adding local history.
4. **Lifecycle discrepancy:** the plan describes replacement after a valid
   association, but current `openAssociation` closes the old scenario before
   validating the new URI. Preserve that behavior. Close the old diagnostic
   session truthfully even for a rejected replacement attempt, while creating no
   product session for malformed intents. Freeze exact close reasons separately.
5. **Static-gate contradiction:** `scripts/phase2_static.sh` currently rejects
   `androidx.room`, `Room.databaseBuilder`, `@Entity`, and `@Database` in production
   source. Phase 3 plan §32 requires Room and calls this gate first. Both cannot
   pass unchanged once Room exists. Before the Room step, explicitly separate
   the historical “no Phase 3 before freeze” assertion from reusable behavioral
   checks, preserving the frozen default/history and all protocol/security
   assertions. An explicit Phase 3 gate mode is a possible narrow change; do not
   silently remove checks, ignore failures, or hide Room from the scan. No script
   changed in this audit. This is a future integration decision, not a current
   baseline failure or blocker to the domain-contract step.
6. **Plan numbering discrepancy:** detailed steps call recorder/sequence/sanitizer
   3.3–3.5 and Room 3.6, while §37's condensed checkpoints call Room 3.3 and recorder
   3.5. Follow the explicit dependency requirement: freeze domain, recorder, and
   sanitization contracts before storage. Use detailed step labels below; map
   checkpoint labels explicitly in later prompts rather than silently renumbering.
7. **Durability/lifetime:** define write ordering, database failure behavior,
   process-death handling, and idempotent session close. Host coroutine cancellation
   must not drop already-sanitized terminal evidence. Unfinished sessions after
   process death must not silently display PASS.
8. **Truthfulness:** do not equate host completion with wire delivery, configured
   capabilities with observations, or successful builds with device/persistence
   proof. Preserve all failure-source enums and numeric error values.

## G. Expected file boundaries for later implementation

Existing files likely to need narrow additive changes:

```text
app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt
app/src/main/java/dev/mwalab/app/MwaLabComposition.kt
app/src/main/java/dev/mwalab/MainActivity.kt
app/src/main/java/dev/mwalab/protocol/ProtocolEvidence.kt
app/src/main/java/dev/mwalab/security/DiagnosticSanitizer.kt
app/src/test/java/dev/mwalab/security/DiagnosticSanitizerTest.kt
app/build.gradle.kts
gradle/libs.versions.toml
.github/workflows/android.yml
README.md
docs/ARCHITECTURE.md
docs/PROTOCOL_SUPPORT.md
docs/SECURITY.md
docs/TESTING.md
```

Conditional, justification required: `scripts/phase2_static.sh` for the documented
freeze-only assertion conflict; `app/src/main/res/xml/backup_rules.xml` and
`data_extraction_rules.xml` for database privacy; `docs/PRIVACY.md` if storage
policy changes. No need to change the wallet association Activity or manifest
merely to add recorder composition.

Proposed new paths (not current implementation; freeze names in the domain step):

```text
app/src/main/java/dev/mwalab/session/SessionId.kt
app/src/main/java/dev/mwalab/session/MwaSession.kt
app/src/main/java/dev/mwalab/session/SessionCloseReason.kt
app/src/main/java/dev/mwalab/session/SessionSummary.kt
app/src/main/java/dev/mwalab/session/SessionRepository.kt
app/src/main/java/dev/mwalab/protocol/EventId.kt
app/src/main/java/dev/mwalab/protocol/ProtocolEvent.kt
app/src/main/java/dev/mwalab/protocol/ProtocolEventHandle.kt
app/src/main/java/dev/mwalab/protocol/ProtocolRecorder.kt
app/src/main/java/dev/mwalab/protocol/EventClock.kt
app/src/main/java/dev/mwalab/storage/MwaLabDatabase.kt
app/src/main/java/dev/mwalab/storage/RoomSessionRepository.kt
app/src/main/java/dev/mwalab/storage/session/SessionEntity.kt
app/src/main/java/dev/mwalab/storage/session/SessionDao.kt
app/src/main/java/dev/mwalab/storage/protocol/ProtocolEventEntity.kt
app/src/main/java/dev/mwalab/storage/protocol/ProtocolEventDao.kt
app/src/main/java/dev/mwalab/storage/converters/ProtocolStorageConverters.kt
app/src/main/java/dev/mwalab/ui/home/HomeViewModel.kt
app/src/main/java/dev/mwalab/ui/sessions/SessionsViewModel.kt
app/src/main/java/dev/mwalab/ui/sessions/SessionsScreen.kt
app/src/main/java/dev/mwalab/ui/sessions/SessionDetailViewModel.kt
app/src/main/java/dev/mwalab/ui/sessions/SessionDetailScreen.kt
app/src/test/java/dev/mwalab/protocol/ProtocolRecorderTest.kt
app/src/test/java/dev/mwalab/session/SessionSummaryTest.kt
app/src/androidTest/java/dev/mwalab/storage/SessionPersistenceInstrumentedTest.kt
app/src/androidTest/java/dev/mwalab/mwa/ProtocolTimelineInstrumentedTest.kt
scripts/phase3_static.sh
PHASE_3_REPORT.md
```

Room schema export will add generated files under `app/schemas/`; their exact
database-class filename is not frozen before the domain/storage step.
Use existing `src/main/java` Kotlin layout, Compose, coroutines, and composition
conventions; do not migrate frameworks or Gradle modules.

Keep these existing files untouched unless a separately justified change is necessary:

```text
app/src/main/java/dev/mwalab/mwa/MobileWalletAdapterActivity.kt
app/src/main/java/dev/mwalab/mwa/association/AssociationOpenResult.kt
app/src/main/java/dev/mwalab/mwa/authorization/LabAuthorizationPolicy.kt
app/src/main/java/dev/mwalab/mwa/capabilities/MwaCapabilityProfile.kt
app/src/main/java/dev/mwalab/mwa/evidence/MwaSessionEvidence.kt
app/src/main/java/dev/mwalab/security/NetworkPolicy.kt
app/src/main/java/dev/mwalab/approval/ApprovalCoordinator.kt
app/src/main/java/dev/mwalab/signing/LabSigningService.kt
app/src/main/java/dev/mwalab/identity/IdentityRepository.kt
app/src/main/java/dev/mwalab/identity/TestEndpointIdentity.kt
app/src/main/java/dev/mwalab/identity/AndroidKeystoreIdentityRepository.kt
app/src/main/java/dev/mwalab/identity/Ed25519IdentityMaterial.kt
app/src/main/java/dev/mwalab/transaction/LegacyTransactionCodec.kt
app/src/main/java/dev/mwalab/transaction/SolanaTransactionMessageDetector.kt
app/src/main/java/dev/mwalab/rpc/DevnetRpcGateway.kt
app/src/main/java/dev/mwalab/rpc/SignAndSendSubmissionExecutor.kt
app/src/main/java/dev/mwalab/protocol/ProtocolMethod.kt
app/src/main/java/dev/mwalab/protocol/ProtocolOutcome.kt
app/src/main/java/dev/mwalab/protocol/ProtocolFailureSource.kt
app/src/main/AndroidManifest.xml
app/src/androidTest/java/dev/mwalab/mwa/MwaLocalAssociationInstrumentedTest.kt
demo-client/build.gradle.kts
demo-client/src/main/java/dev/mwalab/democlient/DemoClientActivity.kt
demo-client/src/main/java/dev/mwalab/democlient/DemoClientRunner.kt
demo-client/src/main/java/dev/mwalab/democlient/Phase2AcceptanceRunner.kt
demo-client/src/main/java/dev/mwalab/democlient/DemoDevnetRpc.kt
demo-client/src/main/java/dev/mwalab/democlient/DemoLegacyTransactionFactory.kt
scripts/phase0_gate.sh
scripts/phase1_static.sh
PHASE_1_REPORT.md
PHASE_2_BATCH_A_REPORT.md
PHASE_2_REPORT.md
```

Preserve all existing regression tests and all historical files under
`docs/evidence/phase1/` and `docs/evidence/phase2/`; the digest inventory enumerates
each exact path. Retain both process-local evidence stores as regression adapters
until equivalent tests are proven. Product history must have one persistent
authority: recorder → repository → Room.

## H. Proposed order, using the detailed Phase 3 plan labels

| Step | Work and prerequisite |
|---|---|
| 3.0 | This audit; next prompt may create the Phase 3 branch from the verified final tag |
| 3.1 | Freeze session/event IDs, lifecycle close reasons, summary/outcome semantics; distinguish lifecycle state from failure source |
| 3.2 | Freeze capability observability limitation from pinned artifact evidence; no synthetic events or library patch |
| 3.3–3.5 | Recorder begin/complete contract, immutable handles, request-start per-session sequence, testable clock, safe summaries/sanitization before any persistence |
| 3.6–3.7 | Resolve static-gate conflict explicitly; add audited Room dependencies, version-1 `mwa_lab.db`, exported schema, DAOs, unique session/sequence and foreign key, repository/Flows; no destructive migration fallback |
| 3.8 | Wire valid-session creation and idempotent lifecycle closure; preserve current replacement/generation behavior |
| 3.9 | Migrate observable authorize/reauthorize/deauthorize then signing/submission callbacks, including all early returns; preserve old test sinks |
| 3.10–3.11 | Audit exact errors/failure sources and bind bounded truthful dApp display identity to the original session |
| 3.12–3.14 | Home/Sessions/Session Detail ViewModels and timeline; protocol/signing authority remains outside UI |
| 3.15–3.19 | Recorder semantics, Room reopen/restart, secret sentinel persistence, method trace integrity, hostile A/B replacement tests; add these alongside the responsible implementation steps |
| 3.20 | Phase 3 static gate and deterministic CI integration, keeping reusable Phase 1/2 checks |
| 3.21–3.22 | Real cross-package success/rejection and force-stop/relaunch acceptance; verify timing, order, numeric error, failure source, original-session binding |
| 3.23 | Reconcile docs/report and evidence; final freeze/commit/tag only when separately instructed and required tests/CI pass |

§37's compact checkpoints group this same work differently; explicit contract
and sanitization prerequisites above take precedence over starting with Room.
No transaction inspector/decoders, simulation, fault engine, diagnostic export,
Share Sheet, production-wallet claims, mainnet, or full capability snapshots are
part of this implementation order.

## I. Audit artifacts, final status, and remaining work

Complete manual inventory of newly created repository files:

```text
docs/phase3-start-audit.md
docs/evidence/phase3/phase3-step3.0-baseline.txt
docs/evidence/phase3/phase3-step3.0-source-digests.txt
docs/evidence/phase3/phase3-step3.0-local-acceptance.txt
docs/evidence/phase3/phase3-step3.0-architecture-inventory.txt
docs/evidence/phase3/phase3-get-capabilities-observability.txt
docs/evidence/phase3/phase3-step3.0-final-review.txt
```

Evidence records actual commands, outputs, exit codes, artifact digests, and
fresh test XML counts. Final review compares every preexisting tracked file's
digest and records expanded Git status. Gradle also updated ignored build/cache
outputs, and temporary capture/disassembly files live under
`/tmp/mwa-phase3-start-audit/`; these are not product source changes.

Final repository state: original 206 tracked files unchanged; seven new
untracked audit files only; no staged changes; branch and HEAD unchanged at
`phase2-core-request-signing` / `009a849441b9d4db453b0162ae1fd9f5fdad8dc4`.
No commit created. The worktree is now intentionally non-clean due solely to
the requested audit artifacts, not baseline drift.

No baseline blocker remains. The static-gate/Room conflict must be resolved
before the storage step; lifecycle/numbering discrepancies and recorder risks
above must be frozen in the appropriate contract steps. The absence of a safe
capability observer is a documented plan-supported limitation, not permission
to invent events. Safe to proceed to the domain-contract prompt only; stop here.
