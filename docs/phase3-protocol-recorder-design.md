# Phase 3 domain and protocol recorder contract

Status: domain contract, Room persistence, recorder core, and persistent session lifecycle wiring implemented through detailed plan step 3.4.
Date: 2026-09-28.
Baseline: `009a849441b9d4db453b0162ae1fd9f5fdad8dc4`, annotated tag
`phase2-core-request-signing-2026-09-27`.
Working branch: `phase3-protocol-recorder-timeline`, created at that exact commit.

Authority: `MWA_LAB_MASTER_IMPLEMENTATION_PLAN_v1.1_2026-09-26.md` and
`MWA_LAB_PHASE_3_IMPLEMENTATION_PLAN_2026-09-28.md` from
`/home/abbaas/Downloads/MWA_LAB/`, plus [the start audit](phase3-start-audit.md).
The detailed step labels are used here; the plan's condensed checkpoint list
uses different numbering. Room persistence is now present and verified, and the
recorder core and session-lifecycle host integration are implemented. Protocol-method recorder migration, UI, fault behavior, and capability snapshots remain deferred.

## 1. One event representation

`ProtocolEvidence` remains the existing concrete Kotlin/JVM data class.
`ProtocolEvent` is a typealias for it, not a second parallel data class. Existing
host constructors, evidence sinks, process-local stores, and test consumers keep
their source names. This avoids mapping drift between two representations.
No JSON or database format depends on a reflected Kotlin class name.

`SessionId` and `EventId` are named String aliases. This is deliberately source
compatible with existing host UUID strings; these aliases do not provide nominal
type safety or validate arbitrary input. The recorder/repository boundary will
validate assigned identities. No implicit ID is extracted from a URI, JSON-RPC
ID, auth token, association key, or secret. A new session receives a random
application-generated UUID; an event receives the already-used
`<sessionId>:<sequence>` identity when its sequence is reserved. IDs are opaque
outside that allocator; consumers do not parse them for ordering.

Existing `ProtocolMethod`, `ProtocolOutcome`, and `ProtocolFailureSource` enums
are unchanged. All three values SUCCESS/FAILURE/CANCELLED and all seven failure
sources remain distinct. The presence of an enum value is not evidence that a
method ran.

### ProtocolEvent fields

| Field | Contract |
|---|---|
| `sessionId: SessionId` | Original accepted session, bound at request start |
| `eventId: EventId` | Stable app-generated identity, never reused |
| `sequence: Long` | Positive request-start order within that session |
| `method: ProtocolMethod` | Actual host-observed method callback |
| `startedAtEpochMillis: Long` | Epoch milliseconds captured at callback acceptance |
| `completedAtEpochMillis: Long` | Epoch milliseconds captured at terminal recording |
| `durationMillis: Long` | Derived `max(completedAt - startedAt, 0)`; not stored separately |
| `outcome: ProtocolOutcome` | Terminal diagnostic result |
| `protocolErrorCode: Int?` | Exact known numeric protocol completion error; null when none is known |
| `failureSource: ProtocolFailureSource` | Explicit origin; never inferred only from error number |
| `requestSummary`, `responseSummary` | Sanitized metadata maps, never raw request/response objects |
| `injectedFaultId: String?` | Reserved metadata slot, null for Phase 3 producers |
| `capabilityContext: Map<String, String>?` | Reserved sanitized metadata slot, null for Phase 3 producers |

The two nullable slots do not activate faults or implement a capability model.
No producer in this step fills either slot. Future changes to their populated
semantics require the corresponding phase's contract. Numeric unknown errors
are retained verbatim; no symbolic name or causal explanation is invented.
RPC JSON-RPC error numbers are not automatically MWA protocol error numbers.

Compatibility defaults `unknown`, `unknown:0`, and sequence 0 remain available
on `ProtocolEvidence` for existing callers. They are not valid product-history
identities. `SessionSummary` rejects unassigned/blank event IDs, nonpositive
sequences, duplicates, and events from another session. The canonical recorder and repository reject these before persistence as well.

## 2. Session model and derived summary

`MwaSession` is immutable metadata: ID, start, nullable completion, nullable dApp
display name, and nullable `SessionCloseReason`. Its `cluster` property is always
`solana:devnet`; there is no network constructor/copy option or mainnet setting.
Storage decoding must reject a non-Devnet row rather than silently relabel it.

Completion timestamp and close reason must both be null (open) or both present
(closed). Epoch timestamps are nonnegative. Clock rollback is allowed without
rewriting timestamps; the derived duration clamps to zero. An open session has
no final duration, rather than a mutable elapsed-duration database column.

The optional display name must already be sanitized, trimmed, nonblank, at most
128 UTF-16 code units, and free of ISO control characters. The constructor checks
these structural conditions; it does not establish authenticity or discover
secrets hidden in arbitrary text. Keep absent/untrusted/unusable metadata null.
The later UI can show Unknown dApp. Never substitute identity URI, icon URI,
query parameters, token text, or an exception as a missing name. A supplied
name is a display claim, not verified package identity.

`SessionSummary(session, events)` is a derived, nonpersistent view. It snapshots
list membership, sorts by sequence rather than completion time, checks original
session ownership and unique IDs/sequences, and derives count, duration, status.
It does not add a second mutable status authority. Summary maps retain the existing event model's read-only Map API. The canonical
recorder now makes defensive safe copies before persistence; Kotlin `val` alone
is still not treated as an immutability/security boundary.

Status is derived in this exact priority order:

1. **ACTIVE** if completion is absent. This means recorded-open, not proof that
   a transport is currently alive. Failed events remain individually visible.
2. **FAIL** if any event failed, or lifecycle closed with SCENARIO_ERROR/START_FAILED.
3. **CANCELLED** if any event was cancelled, or closure was HOST_CLOSED,
   REPLACED_BY_ASSOCIATION_ATTEMPT, LOW_POWER_NO_CONNECTION, or TEARDOWN_COMPLETE.
4. **UNKNOWN** if normally closed with no observed method events.
5. **PASS** only for SERVING_COMPLETE/SCENARIO_COMPLETE with at least one event
   and all observed events successful.

PASS describes recorded observable methods, not completeness of all wire traffic
or a guarantee of remote delivery/chain execution. An ordinary session can have
zero observed events, so UNKNOWN avoids a false success claim. Lifecycle failure
can derive FAIL without manufacturing a method event or protocol error.
A list/summary UI must show the lifecycle cause separately from event errors.
There is no aggregate protocol-error column: a later view can select the first
failed event in sequence order and must retain access to every event/error.

A summary cannot infer missing events from nothing. Before publishing a final
aggregate, the later recorder must settle every begun event and persist session
closure plus the final event set coherently. A partially loaded list is not a
final summary. After process death, an open row is unfinished evidence; do not
fabricate a completion time, close cause, protocol result, or a live connection.
Recovery/presentation of such rows must preserve that uncertainty.

## 3. Begin/complete lifecycle and immutable binding

The recorder core now implements this behavior; host callback migration is the
next integration step:

```text
accepted genuine callback
  -> begin(sessionId, method, safe request summary)
  -> immutable handle(sessionId, eventId, sequence, start, safe summary)
  -> unchanged Phase 2 decision / approval / signing / response
  -> complete(handle, outcome, error, source, safe response summary)
  -> one terminal ProtocolEvent for that handle
```

Reserve sequence at begin, starting at 1 independently per session. The event ID
and sequence are never recalculated on completion. No event may increment the
replacement session's counter. Atomic allocation and completion are recorder
responsibilities; Room's eventual unique(session_id, sequence) constraint is a
second guard, not the allocator. Never sort by callback completion or wall clock.
Never emit an extra GET_CAPABILITIES record to fill a perceived sequence gap.

`ProtocolEvent` represents a completed event. A pending operation is a recorder
handle, not a terminal event with made-up completion/outcome. Repeated completion
of one handle must be idempotent: first terminal result wins, subsequent calls
cannot create a duplicate or change its identity. A late A callback uses its A
handle even after B starts. The frozen generation/authorization guards still
control whether signing or a protocol response is allowed; diagnostic ownership
never grants authority to a stale request.

`ProtocolRecorder.cancelPendingForSession` can settle remaining begun handles as
diagnostic CANCELLED in request-start sequence order. It uses a null protocol
error and UNKNOWN source with an approved safe lifecycle-cancellation reason; it
does not claim the peer received ERROR_NOT_SIGNED. Later completions of an
already-settled handle return the first terminal event and cannot create another
write. Wiring this close behavior into actual walletlib lifecycle callbacks is
still deferred to the host-integration step.

Production event times use `SystemEventClock`, a testable `EventClock` backed by
`System.currentTimeMillis`. Timing covers recorder begin through terminal
completion, including later approval/RPC waits once the host is wired. It does
not claim transport reception/decryption or remote delivery timing. The frozen
host still has not been migrated in this step. Duration remains derived rather
than duplicated persistent state.

## 4. Exact lifecycle close semantics

Create a product session only after `LocalAssociationUri.createScenario` returns
an actual candidate, before attempting `candidate.start()`. URI rejection or
scenario construction failure creates no new product session. Once created,
record a failed synchronous start as START_FAILED. Record explicit initiating
close cause before calling the existing close path, so cleanup callbacks cannot
overwrite it. `finish` must be idempotent: first observed terminal cause and time
win, later notifications do not reopen or relabel the session.

| Current path | Planned diagnostic mapping |
|---|---|
| `onScenarioReady` / `onScenarioServingClients` | Nonterminal; no close reason |
| `onScenarioServingComplete` | SERVING_COMPLETE; captures observed end of serving before existing `closeCurrentScenario(false)` |
| `onScenarioComplete` | SCENARIO_COMPLETE only if no earlier initiating cause; otherwise cleanup/no change |
| `onScenarioError` | SCENARIO_ERROR before existing main-queue finish notification |
| `onScenarioTeardownComplete` | TEARDOWN_COMPLETE only if still open; normally duplicate cleanup |
| `onLowPowerAndNoConnection` | LOW_POWER_NO_CONNECTION before existing finish notification |
| explicit `host.close()` | HOST_CLOSED if still open, before increment/invalidation/close side effects |
| another `openAssociation(...)` | REPLACED_BY_ASSOCIATION_ATTEMPT for the previous open session, even if new URI later fails |
| `candidate.start()` throws | START_FAILED before candidate cleanup |
| authorize / reauthorize / deauthorize | Method events; deauthorization revokes authority but does not itself close association |

The replacement name deliberately differs from the plan's illustrative
REPLACED_BY_NEW_ASSOCIATION. Current host closes the old scenario **before**
validating the new intent; the chosen name truthfully covers a rejected attempt
without changing that order or inventing a new accepted session.

Pinned local source confirms `LocalWebSocketServerScenario.close()` emits
onScenarioComplete, closes the server, then emits onScenarioTeardownComplete.
LocalScenario emits onScenarioServingComplete for both last-client close and
last-client session error. The host cannot distinguish those two underlying
causes; SERVING_COMPLETE records only the callback fact. Do not call it proof
of a clean encrypted-transport shutdown. SCENARIO_ERROR is the distinct observable
scenario error callback. These causes are lifecycle metadata, not failure-source
or protocol-error enum replacements. No process-death callback or timer-derived
fictional terminal cause is added.

## 5. Failure-source and sanitization contracts

| Source | Meaning |
|---|---|
| NONE | No failure origin to report, normally success |
| INJECTED | Explicit synthetic fault evidence; reserved, not produced in Phase 3 |
| OBSERVED_PROTOCOL | Actual host-observed protocol policy/authorization/approval rejection |
| SIMULATION | Simulation result origin; reserved, no simulation in Phase 3 |
| RPC_NETWORK | Actual RPC/transport/submission-origin failure |
| LOCAL_PARSER | Local bounded parsing/validation failure |
| UNKNOWN | Cause not established, including unclassified internal failure |

Keep current host classifications intact in this step. Real signing rejection
is FAILURE / ERROR_NOT_SIGNED (-3) / OBSERVED_PROTOCOL with no injected fault ID.
The same numeric code must never imply INJECTED by itself. A cancelled operation
is not automatically a user rejection. Future host migration must audit each
branch and exact walletlib error mapping, including current null codes on some
internal-error records, without changing the response it sends.

Sanitization flow to implement before persistence:

```text
wallet callback -> approved metadata builder -> DiagnosticSanitizer
-> immutable safe snapshot -> recorder -> repository -> storage
```

Allowed summary inputs are enum/stable reason strings, validated public metadata,
booleans, counts, lengths, payload SHA-256, and bounded display labels. Unknown
external identifiers must become a fixed unknown/unsupported marker rather than
arbitrary raw text. Never supply raw wallet request objects to a recorder/DAO.
Never persist private material, seeds, mnemonics, raw auth/association tokens,
association secrets, raw signing/message/transaction payloads, raw signatures,
encryption secrets, raw RPC bodies, or exception strings. Do not harvest names
or identifiers by serializing requests.

The existing key-based sanitizer is preserved. The canonical recorder adds a
second boundary: only an explicit approved metadata-key set plus bounded
`payload_<n>_{sha256,length}` keys may enter recorder summaries, maps are bounded,
request/response summaries are sanitized separately, and defensive copies are
made before persistence. `ProtocolEvidence` remains permissive for source
compatibility and is not itself an approved persistence bypass. Step 3.2 already
proved secret sentinels do not survive the Room persistence boundary; recorder
unit tests additionally prove caller mutation cannot alter recorded summaries.
Approved host summary builders remain responsible for value provenance; raw
request objects are never accepted by the recorder API.

## 6. get_capabilities observation decision

**NOT OBSERVABLE THROUGH PINNED WALLETLIB** on the current unmodified local
scenario/host integration. **CONFIGURED CAPABILITY PROFILE** is a separate fact.
No GET_CAPABILITIES event, start/end time, sequence, or success is manufactured.

Source inspected at official `v2.0.7`, resolved commit
`166c0f96868c45633253a0c0bcd61e828334b349`, rather than upstream main.
The source archive SHA-256 is
`831fc62e497585b4cdf91d1697799ed72c9650d24f66504bd74ead9c582ed617`.
Cached resolved AAR SHA-256 remains
`e5c639964c5740e187cacf31776f0e46b9a2433e26b50fc027ab8ffb481b177c`.
The source paths/signatures/dispatch agree with the pinned AAR disassembly
retained in the start audit; this is not a claim to have reproduced the AAR build.

[Source inspection evidence](evidence/phase3/phase3-step3.2-walletlib-source-observation.txt)
contains immutable URLs, per-file digests, numbered excerpts, and a hook search
across walletlib source. The downloaded archive/source stays under `/tmp` and
is not introduced as a dependency or vendored library.

| Candidate seam | Verified result |
|---|---|
| `Scenario.Callbacks`, `LocalScenario.Callbacks` | No get-capabilities request callback |
| `MobileWalletAdapterServer.MethodHandlers` | authorize/deauthorize/signing handlers only; no capability handler/observer |
| `MobileWalletAdapterServer.dispatchRpc(Object, String, Object)` | Protected, subclassable dispatch exists; get_capabilities calls private `handleGetCapabilities` directly |
| `handleGetCapabilities` | Validates params, builds result from config, calls inherited result method internally; no host notification |
| `JsonRpc20Server.dispatchRpc` and result/error methods | Lower-level protected methods, no attached read-only observer/listener |
| `LocalScenario.createMessageReceiver()` | Constructs concrete MobileWalletAdapterServer itself, then wraps it in MobileWalletAdapterSession; no server factory argument |
| `BaseScenario.mMethodHandlers` / `LocalScenario.mSessionStateCallbacks` | Package-private / private ownership prevents a drop-in equivalent custom receiver with all existing delegates |
| outer WebSocket receiver | Receives session framing/encrypted payloads; cannot establish a decrypted method observation |
| `MobileWalletAdapterSessionCommon` | Decrypted receiver is private final; no observer setter. Protected decrypt operation exists, but a new subclass still cannot be injected into the current construction path without replacing it |

The capability dispatch is visible in the official
[MobileWalletAdapterServer source](https://github.com/solana-mobile/mobile-wallet-adapter/blob/166c0f96868c45633253a0c0bcd61e828334b349/android/walletlib/src/main/java/com/solana/mobilewalletadapter/walletlib/protocol/MobileWalletAdapterServer.java#L492).
The construction restriction is visible in
[LocalScenario](https://github.com/solana-mobile/mobile-wallet-adapter/blob/166c0f96868c45633253a0c0bcd61e828334b349/android/walletlib/src/main/java/com/solana/mobilewalletadapter/walletlib/scenario/LocalScenario.java#L78).

Conclusion is scoped: a subclassable dispatcher exists, but there is no safe
attachable observer for this host path. Replacing scenario/session construction,
reflecting into private receivers, adding split-package access, or duplicating
walletlib handling does not qualify as an existing safe read-only seam. Keep
2.0.7 unchanged. Do not infer a request from `MwaCapabilityProfile.snapshot()`
or from a demo-client request in another process.

OBSERVED applies only to actual host callbacks: authorize, reauthorize,
deauthorized event, sign messages, sign transactions, sign-and-send. Even for
these methods, requests rejected internally before a callback are not host
observations. Capability behavior remains functional on the wire; capability
observability is unavailable through the pinned integration. An enum slot and a
configured profile must not be described as protocol observation.

If an observation hook is proposed in a separately authorized future step,
require source/digest review and parity tests for: callback/response count and
ordering, params/error behavior, authorization-independent capability calls,
response field/limit/version parity, unchanged token/crypto/transport behavior,
observer failure isolation, stale-session binding, and absence of secrets.
No such hook or parity claim is implemented here. The source handler's existing
nonempty-params path even reports an error without an immediate return before
building the result; this audit preserves that pinned behavior and does not
patch it under the guise of observation.

## 7. Persistence boundary and recorder authority

Step 3.2 introduced `mwa_lab.db` version 1, exported Room schemas under
`app/schemas/`, `SessionRepository`, and the required session/event constraints
without destructive migration fallback. The frozen Phase 2 static script remains
unchanged as historical evidence; Phase 3 verifies its checksum/critical-source
invariants rather than weakening its intentional pre-Room absence assertion.

Step 3.3 introduces the single persistent product authority:

```text
ProtocolRecorder -> SessionRepository -> Room
```

`PersistentProtocolRecorder` owns request-start sequence allocation, immutable
session/event binding, timing, bounded approved metadata intake, sanitization,
defensive copies, duplicate-terminal suppression, and explicit persistence
failure reporting. Room's unique(session_id, sequence) remains a second guard.
Recorder persistence failures do not throw ordinary storage failures into the
future protocol-response path; coroutine cancellation is preserved rather than
silently swallowed. Step 3.4 adds `SessionLifecycleCoordinator` for best-effort
session create/identity/finalize persistence while preserving wallet authority.
Existing process-local evidence stores remain for predecessor-test compatibility
until protocol method migration is complete.

Step 3.4 wires session creation/finalization into MwaSessionHost. Finalization settles recorder-owned pending handles before closing the persistent session, so later method-recorder wiring can preserve complete terminal history. The actual protocol method callbacks are still on the legacy evidence path until the next steps.

## 8. Wire/protocol noninterference and validation

The existing evidence data class retains its source/JVM identity and default-null
reserved metadata. `MwaLabComposition` exposes the recorder and session-lifecycle coordinator backed
by the Room `SessionRepository`. Step 3.4 changes `MwaSessionHost` only at the
association/session lifecycle boundary: valid local scenarios create product
sessions, lifecycle callbacks finalize them, and observed dApp display names are
normalized before persistence. Protocol method responses and legacy
`recordProtocol` behavior remain unchanged until the method-wiring steps.

Step 3.4 preserves association transport, authorization/reauthorization/
deauthorization responses, issuer/scope, generation guards, approval, signing,
transaction parsing, submission ordering, error mapping, fixed Devnet RPC,
limits, and mainnet rejection. Session persistence is diagnostic-only: an
ordinary database failure records only a sanitized local diagnostic failure and
does not reject/approve/sign/submit on behalf of the protocol. No UI, walletlib
patch, fault behavior, simulation, inspector, export, Share Sheet, or
production-wallet claim is added. No commit is created by the delivery scripts.

Focused domain tests cover compatible event identity, timing rollback, exact
outcome/source/error preservation, sanitized-summary representation, fixed cluster,
paired close fields, display-label shape, derived status precedence, empty history,
sequence order, collection ownership, duplicate identity/sequence rejection, and
cross-session aggregate rejection. They do not claim live protocol or durable
storage proof. Validation results and the exact touched-file inventory are
recorded in `docs/evidence/phase3/phase3-step3.1-domain-acceptance.txt` after gates run.

## Step 3.5 — authorization-family recorder migration

`AUTHORIZE`, observable `REAUTHORIZE`, and `DEAUTHORIZE` are the first real
walletlib callback methods migrated to the persistent `ProtocolRecorder`.

The callback captures the association's immutable `persistentSessionId` and
calls `ProtocolRecorder.begin(...)` at request observation time, before policy
evaluation or terminal response. Completion uses the returned
`ProtocolEventHandle`; it never resolves ownership from mutable
`activeSessionId` at completion time.

Canonical persistent behavior:

- `AUTHORIZE`: request-start handle before authorization policy evaluation;
  success/failure preserves the Phase 2 protocol response and error mapping.
- `REAUTHORIZE`: recorded only when walletlib 2.0.7 actually invokes the
  observable callback. Token failures rejected internally before that callback
  are not fabricated as app-observed events.
- `DEAUTHORIZE`: request-start handle before authorization invalidation and
  completion; the persisted terminal result is `revoked`.
- `GET_CAPABILITIES`: still **NOT OBSERVABLE THROUGH PINNED WALLETLIB** and no
  synthetic timeline event is created.

Diagnostic persistence remains non-authoritative. Recorder begin/completion
failure emits bounded local diagnostic evidence but does not grant, revoke,
reject, sign, or otherwise change a wallet protocol decision. Pending handles
are still settled by `SessionLifecycleCoordinator` when their bound session
closes or is replaced.

The Phase 2 process-local `ProtocolEvidenceStore` path is retained temporarily
as a compatibility mirror until all supported Phase 2 methods have migrated.
It is not the Phase 3 product authority. The persistent Room timeline is the
canonical diagnostic history for migrated methods.

## Step 3.6 — signing-family recorder migration

`SIGN_MESSAGES`, `SIGN_TRANSACTIONS`, and `SIGN_AND_SEND_TRANSACTIONS` now use the
same canonical persistent `ProtocolRecorder` authority as the authorization
family.

For every observable signing callback, `MwaSessionHost` computes only bounded
safe request metadata (method/chain/counts plus payload SHA-256/length and
approved send-option fields), then calls `ProtocolRecorder.begin(...)` with the
association callback's immutable `persistentSessionId` before any diagnostic
dApp-name Room write, approval wait, signing, parsing, or RPC operation. The
summary preserves exact total payload count but persists SHA-256/length metadata
for at most the first 10 payloads, matching the advertised Phase 2 request bound
and keeping hostile over-limit requests inside the recorder's fixed metadata
budget. The returned handle is carried through the existing Phase 2 handler
without reading mutable `activeSessionId` to determine persistent event ownership.

Terminal persistent completion is additive and non-authoritative. Existing
Phase 2 response ordering, approval decisions, legacy-transaction parsing,
Devnet blockhash validation, signing, submission sequencing, protocol error
codes, limits, and fail-closed authorization checks remain the protocol
authority. Recorder completion follows the already-selected response path and
ordinary persistence failure cannot alter that response.

All early terminal paths are settled, including stale-generation cancellation,
inactive/revoked authorization, invalid/too-many payloads, parser/RPC failure,
approval cancellation/rejection/busy/expiry, signing failure, sign-and-send
submission cancellation/not-submitted/fatal outcomes, and successful signing or
submission. Session finalization remains a second safety net that settles any
still-pending handle before the persistent session closes.

`GET_CAPABILITIES` remains **NOT OBSERVABLE THROUGH PINNED WALLETLIB**. No
synthetic event is introduced.

At the end of Step 3.6 all genuinely observable Phase 2 protocol methods are on
the canonical Room timeline:

```text
AUTHORIZE
REAUTHORIZE
DEAUTHORIZE
SIGN_MESSAGES
SIGN_TRANSACTIONS
SIGN_AND_SEND_TRANSACTIONS
```

The process-local `ProtocolEvidenceStore` mirror is still retained temporarily
for frozen Phase 2 regression instrumentation. It is not product authority and
may be retired only after a later Phase 3 predecessor-compatibility gate replaces
that dependency explicitly.
