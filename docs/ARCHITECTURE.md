# Architecture

## Verified Phase 2 boundary

```text
MWA Lab Demo Client (:demo-client / clientlib 2.0.7)
        ↓ real solana-wallet:// Android association
MobileWalletAdapterActivity (:app)
        ↓
AssociationUri.parse / LocalAssociationUri
        ↓
MwaSessionHost
        ├── NetworkPolicy
        ├── LabAuthorizationPolicy
        ├── walletlib 2.0.7 authorization repository
        ├── session-generation guard
        ├── active-authorization generation guard
        ├── ApprovalCoordinator
        ├── IdentityRepository / LabSigningService
        ├── MwaCapabilityProfile
        ├── LegacyTransactionCodec
        ├── SignAndSendSubmissionExecutor
        │       └── DevnetRpcGateway
        ├── ProtocolEvidenceStore
        └── DiagnosticSanitizer
```

## Modules

### `:app`

Primary MWA Lab wallet-side endpoint.

Owns:

- Android association entrypoint;
- `MwaSessionHost`;
- Devnet-only network policy;
- protected persistent Lab test identity;
- walletlib authorization/deauthorization boundary;
- session-local active-authorization state;
- explicit signing approval coordination;
- message signing;
- legacy transaction parsing/signing;
- Devnet-only transaction submission;
- centralized capability configuration;
- typed/sanitized process-local protocol evidence.

The UI does not own protocol, authorization, capability, transaction parsing,
RPC, or key semantics.

### `:demo-client`

Deterministic cross-package test infrastructure.

Identity:

```text
MWA Lab Demo Client
FOR TESTING ONLY
```

It uses pinned `clientlib:2.0.7` to exercise authorization, reauthorization,
capability negotiation, message signing, transaction signing,
sign-and-send, rejection, revocation, and session lifecycle behavior.

It is not the primary product and is not a production dApp.

## Authority boundaries

### MWA protocol

Pinned official MWA `v2.0.7` semantics are authoritative for this phase.

### Authorization state

walletlib 2.0.7 owns auth-token issuance/records and validates/revokes those
records.

MWA Lab additionally maintains a **session-local active-authorization generation**. A successful authorize/reauthorize activates the current
association generation; deauthorize, replacement association, teardown, or host
close invalidates it.

Privileged handlers verify this local active authority before approval and again
after approval before signing/submission. This is defense in depth around the
pinned walletlib authorization state.

Raw auth-token content is never parsed into diagnostic evidence.

### Approval

`ApprovalCoordinator` is the single-flight request-bound approval boundary.

One pending signing decision cannot be stolen by another request. Cancellation,
expiry, stale completion, and duplicate completion fail closed.

### Identity and signing

`IdentityRepository` exposes public identity state.

`LabSigningService` performs Ed25519 signing inside the protected identity
boundary. The session host receives signature bytes, never raw private seed
bytes.

### Transaction codec

`LegacyTransactionCodec` owns bounded parsing and signature-slot replacement for
legacy Solana wire transactions.

It snapshots approved bytes, validates canonical structure, requires the Lab
identity to be a required signer, preserves other signature slots, and rejects
unsupported/malformed/versioned input.

### Devnet RPC

`DevnetRpcGateway` has a fixed production endpoint:

```text
https://api.devnet.solana.com
```

Callers cannot supply an alternate RPC URL.

`SignAndSendSubmissionExecutor` owns submission ordering, returned-signature
cross-checking, requested-commitment sequencing, partial failure behavior, and
cancellation/no-resubmission behavior.

### Capability truth

`MwaCapabilityProfile` supplies explicit Phase 2 request limits, legacy
transaction support, and the pinned-walletlib `sign_transactions` feature.

### Historical Phase 2 evidence

`ProtocolEvidenceStore` remains process-local and sanitized in Phase 2.

This is **not** the Phase 3 product recorder. Phase 2 does not add Room/SQLite
history, restart-surviving session timelines, full report export, or the
deterministic fault engine.

## Architectural invariants

1. UI components do not own protocol/signing/RPC logic.
2. Devnet-only authorization and RPC policy are enforced below the UI.
3. Private identity material stays behind the signing/security boundary.
4. Raw auth/association tokens, payloads, signatures, and private material do not enter diagnostics.
5. Capability values are centralized and pinned-library-aware.
6. Malformed/unsupported inputs fail closed.
7. Signing requires current authorization plus explicit approval.
8. Authorization is re-checked around approval/signing/submission.
9. Stale association callbacks cannot terminate or authorize a replacement session.
10. Production-wallet behavior is compatibility evidence, not protocol authority.
11. Phase 3 must extend this verified boundary rather than replace it.

## Phase 3 persistent product authority

```text
MWA callback -> MwaSessionHost -> ProtocolRecorder.begin -> immutable handle
-> unchanged Phase 2 operation/response -> ProtocolRecorder.complete
-> SessionRepository -> Room/SQLite -> Flow/StateFlow -> ViewModels -> Compose
```

`SessionLifecycleCoordinator` creates metadata after a real scenario candidate
exists, records bounded dApp display claims, settles pending requests, and
finalizes sessions. First close cause and epoch time are bound atomically;
duplicate callbacks reuse them. START_FAILED is recorded before cleanup.
Request handles capture session, sequence, identity, start time, and safe metadata
at begin. Completion never uses mutable active-session state. Claimed writes are
settled despite caller cancellation; close waits for in-flight writes.

Room `mwa_lab.db` version 1 exports its schema under `app/schemas/`. Events have
stable primary keys, a session foreign key, and unique(session_id, sequence).
There is no destructive migration fallback. Session status and duration are
derived from terminal events and lifecycle metadata, not mutable status columns.
Open rows survive process death as unfinished evidence with unknown liveness.

`HomeViewModel`, `SessionsViewModel`, and `SessionDetailViewModel` expose state
from the repository. Compose collects StateFlow with lifecycle awareness;
selection/back navigation is saved across configuration changes. Screens never
query DAOs, sign, authorize, submit, or parse text logs. Numeric unknown protocol
errors retain their value without a fabricated name. Details render safe summaries.

`ProtocolEvidenceStore` and `MwaSessionEvidenceStore` remain compatibility seams
for frozen predecessor tests. They are absent from the product UI authority.
Pinned walletlib handles get_capabilities internally: no synthetic event is
recorded. Capability snapshots and transaction diagnostics are Phase 4 work.

## Phase 4 capability and transaction diagnostic authority

Phase 4 preserves the Phase 3 recorder and the Phase 2 signing authorities while
adding two diagnostic branches:

```text
MwaCapabilityProfile
  -> walletlib MobileWalletAdapterConfig
  -> CapabilitySnapshotFactory
  -> capability_snapshots (Room)

transaction callback
  -> ProtocolRecorder.begin
  -> TransactionInspector / SolanaWireTransactionParser   [diagnostic only]
  -> ApprovalRequest presentation
  -> LegacyTransactionCodec / LabSigningService           [authoritative]
  -> ProtocolRecorder.complete
  -> TransactionDiagnosticSettlement
  -> transaction_diagnostics (Room)
  -> Session Detail inspector
```

Room `mwa_lab.db` is schema version 2. Migration `1 -> 2` creates only the
`capability_snapshots` and `transaction_diagnostics` structures/indexes; it does
not rewrite historical sessions/events or backfill historical capability state.
Existing Phase 3 table definitions remain unchanged.

`CapabilitySnapshotSource.CONFIGURED_WALLETLIB_PROFILE` is deliberately explicit:
the snapshot records the capability profile configured for that session. Pinned
walletlib 2.0.7 handles `get_capabilities` internally, so this configured context
is never relabelled as an observed callback and no synthetic timeline event is
created.

`TransactionInspector` and the decoder registry are read-only diagnostics. They
can detect legacy/v0 structure, derive header privileges, fingerprint payloads,
and decode the verified System/Memo/SPL subset, but they cannot authorize,
approve, sign, or submit. `ApprovalCoordinator` remains decision authority and
`LegacyTransactionCodec` remains legacy signing/validation authority. Diagnostic
persistence failure is isolated from the MWA protocol result.

Persistent transaction diagnostics contain structured public metadata and hashes,
not raw transaction payloads or raw unknown instruction bytes. v0 lookup-table
addresses are not resolved over RPC in Phase 4; partial diagnostics state the
limitation and v0 signing remains unsupported. Simulation, fault injection, and
report export are not part of this architecture phase.

## Phase 5 simulation diagnostic branch

Phase 5 introduces simulation as a **diagnostic child branch** of a genuine
`SIGN_TRANSACTIONS` or `SIGN_AND_SEND_TRANSACTIONS` protocol event:

```text
canonical MWA request
    ├─ approval/signing/submission -> unchanged authority
    └─ optional simulation        -> diagnostic child branch
                                      -> sanitized Room child evidence
```

`TransactionSimulationCoordinator` owns the transient transaction bytes and binds
them to session/event/request/payload/fingerprint identity. Public UI state never
contains those bytes. `TransactionSimulationService` invokes only the fixed
Devnet `simulateTransaction` RPC. `SimulationDiagnosticSettlement` observes the
canonical persistent completion and attaches completed simulation attempts only
after the parent event is durable.

The core authority rule is: **simulation = diagnostic child branch;
signing/submission = unchanged authority**. The simulation branch cannot call the
signing service, cannot approve/reject, cannot submit, cannot complete
`ProtocolRecorder`, and cannot rewrite the parent event outcome. Late results are
identity-bound and discarded when their session/request target is invalidated.

A finished, already accepted simulation attempt may complete its bounded
parent-then-child persistence after session teardown. Teardown rejects late
callbacks and pending targets; it does not transfer completion authority or
wait for the diagnostic write before returning the MWA response.

The funded Phase 5 live run independently verified this separation: a runtime
FAIL child accompanied a successful signed parent, while a PASS child remained
PASS after the parent returned ERROR_NOT_SIGNED/OBSERVED_PROTOCOL. All three
live sessions' canonical parent, transaction and simulation rows survived
process restart without reclassification or rebinding.

<!-- PHASE6:ARCH:BEGIN -->
## Phase 6 deterministic fault path

`PersistentFaultSelectionRepository` is the single process-wide selection authority. `MwaSessionHost` captures an immutable `FaultRequestSnapshot` at request start and evaluates `DeterministicFaultEngine` only at explicit hooks. Applied conditions are annotated on the canonical pending `ProtocolEvent`; Compose screens only project structured state. Phase 5 simulation remains a diagnostic child branch and is not used as fault authority.
<!-- PHASE6:ARCH:END -->

## Phase 7 sanitized report projection

```text
SessionRepository + CapabilitySnapshotRepository
+ TransactionDiagnosticRepository + SimulationRepository
  -> bounded consistent snapshot reads
  -> ReportSanitizationPolicy allowlist
  -> DiagnosticReport v1
  -> Markdown / JSON / Copy Summary
  -> app-private diagnostic_reports cache (file outputs only)
  -> non-exported report-only FileProvider -> Android Share Sheet
```

The snapshot assembler reads persisted structured evidence and never writes
protocol/session rows. Closed sessions require matching bounded reads to be
COMPLETE; open or changing sessions are PARTIAL. Renderer and clipboard output
share the canonical model. Transaction and simulation diagnostics remain
observational; unknown decoded semantics stay unknown. The exporter has no
approval, signing, submission, fault, simulation, or RPC execution authority.
Room stays at schema 3 and walletlib stays at 2.0.7.
