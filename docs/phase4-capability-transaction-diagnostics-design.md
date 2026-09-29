# Phase 4 capability and transaction diagnostics contract

Status: Phase 4.1 architecture / contract freeze; feature implementation has not started.
Date: 2026-09-29.
Branch: `phase4-capability-transaction-diagnostics`.

The controlling implementation contract is
`MWA_LAB_PHASE_4_IMPLEMENTATION_PLAN_2026-09-28.md`, read in full from
`/home/abbaas/Downloads/MWA_LAB/`. The master architecture/scope input is
`MWA_LAB_MASTER_IMPLEMENTATION_PLAN_v1.1_2026-09-26.md` in the same directory.
Their SHA-256 values are, respectively:

```text
e110a3949c8dd8e04a6e1b71d9e97e679df84182f81b96ad4ae1aa1f50adce71
c5f1e87b2520ed982bb6e6bd2038537fe8a1c2eb54d0c4c1e906d00d55698f02
```

This document fixes the Phase 4 interpretation of those plans against the actual
repository. Illustrative future-product screens in the master plan do not permit
early simulation, faults, export, or invented protocol observations. Implementation
must satisfy this contract; a convenience-driven implementation cannot silently
weaken it. This checkpoint changes this document only. Phase 4.2 is a separate step.

## 1. Objective

For each newly recorded MWA Lab session, show the exact configured capability
profile associated with that session. For observable transaction callbacks,
inspect the received serialized payloads, explain verified structure and a narrow
set of known instructions before approval, and persist safe diagnostics for later
Session Detail inspection and restart.

The developer can identify the version, fee payer, required signers, account
privileges, blockhash, invoked programs, decoded operations, fingerprint, and
inspection limitations. Unknown information remains unknown. The inspector is
supporting evidence for the existing protocol timeline, not a new wallet authority.

## 2. Frozen Phase 0–3 baseline and invariants

| Baseline fact | Frozen value |
| --- | --- |
| Phase 3 commit | `913642d30c4445602b5ff7f72995e826a196938b` |
| Phase 3 branch | `phase3-protocol-recorder-timeline` |
| Phase 3 tag | `phase3-protocol-recorder-timeline-2026-09-28` |
| Annotated tag object | `f0a335eb28833b30dd18af34dfa9b8c711835957` |
| Exact-head CI | `36410566767`, SUCCESS |
| Database | `mwa_lab.db`, version 1 |
| Application tables | `sessions`, `protocol_events` |
| Schema identity hash | `0da311efe4dcefa6a30a9643eac37192` |

The Phase 4 branch starts at that exact commit. Phase 0, Phase 1, and frozen
Phase 2 are ancestors. Never move, rewrite, or replace the Phase 3 tag or its
annotated evidence. Preserve [schema 1](../app/schemas/dev.mwalab.storage.MwaLabDatabase/1.json)
byte-for-byte; its SHA-256 is
`1952a8bcaef8bf1c1910c0e99f1d13464aebbcb2b031376caee3130f9584874c`.

Phase 4 is additive. Preserve the existing toolchain and walletlib/clientlib
2.0.7 pins, Devnet-only policy and fixed RPC endpoint, protected Lab identity,
walletlib token ownership, authorization issuer/scope compatibility, request
limits, session-generation guards, revocation rechecks, explicit approval,
message signing, legacy transaction acceptance, signature-slot replacement,
submission/commitment sequencing, and exact protocol error mappings.

Preserve recorder request-start ordering, immutable session/event binding,
first-terminal ownership, duplicate suppression, first-close metadata, close
barriers, cancellation settlement, failure classifications, and ordinary storage
failure isolation. `ProtocolEvent` remains the existing alias of
`ProtocolEvidence`. Diagnostic additions do not redefine session status or timing.
The process-local evidence stores remain predecessor compatibility mirrors;
Room-backed history remains product authority.

`gradlew.bat` has CRLF in the checked-out file and LF in Git's index/blob, with
`core.autocrlf=input`; normalized content and Git's filtered blob match the frozen
commit. Git reports a clean worktree. This verified conversion is not source drift
and supplies no reason to edit the wrapper.

## 3. Capability provenance semantics

`CapabilitySnapshot` means **configured capability profile for this specific
MWA Lab session**. Its initial and only Phase 4 source is
`CapabilitySnapshotSource.CONFIGURED_WALLETLIB_PROFILE`.

It does not mean an observed GET_CAPABILITIES callback, a measured request/response,
or proof that a peer asked for or received capabilities. Pinned walletlib 2.0.7
handles `get_capabilities` internally without an attachable host callback. Do not
infer observation from a Demo Client request or an enum slot. Never fabricate a
GET_CAPABILITIES ProtocolEvent, sequence, duration, outcome, or response.

Capture once after a real scenario candidate exists and session creation returns
`SessionLifecycleCoordinator.PersistenceResult.Persisted`, before scenario start.
Use the callback-bound session ID, a nonnegative capture timestamp, and an owned
immutable copy of the configuration values. A snapshot is immutable for that
session; a later session receives its own capture. A failed session-row write means
no snapshot is inserted. Snapshot failure never rejects association or changes
scenario start/close behavior. START_FAILED sessions may retain a valid capture.

## 4. One capability authority

[MwaCapabilityProfile](../app/src/main/java/dev/mwalab/mwa/capabilities/MwaCapabilityProfile.kt)
remains the sole authority. The required relationship is:

```text
MwaCapabilityProfile
  -> actual MobileWalletAdapterConfig supplied to this scenario
  -> session-bound CapabilitySnapshot representing those configured values
```

The existing `createWalletConfig()` and `snapshot()` are two views of that single
profile. The new domain adapter binds their represented values to the session and
provenance; it must check parity with the actual host config, not recreate values
from defaults. A mismatch yields unavailable diagnostic context and preserves the
existing protocol behavior. The profile's current limits are 10 transactions and
10 messages, its transaction versions are `["legacy"]`, and its optional feature
is `ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS`. These baseline descriptions
are not additional runtime authorities.

Keep the existing `MwaCapabilitySnapshot` compatible. Normalize its `List<Any>`
at a checked boundary into typed diagnostic values: Phase 4 accepts the configured
legacy string representation, not arbitrary `Any.toString()` output. Snapshot,
wallet configuration, and existing enforcement remain equal. UI and DAOs consume
persisted domain snapshots, never independently consult or hard-code the profile.
Parity tests compare the snapshot to the real config/profile rather than copying
capability constants into new tests.

## 5. Diagnostic and wallet authority boundary

| Component | Retained authority |
| --- | --- |
| `MwaSessionHost` | Protocol orchestration, existing policy checks and responses |
| `ApprovalCoordinator` | Request-bound user decision, busy/timeout/cancel behavior |
| `LabSigningService` | Protected identity signing |
| `LegacyTransactionCodec` | Signing-validation/acceptance and signature replacement |
| `DevnetRpcGateway` | Fixed Devnet blockhash validation, submission and status RPC |
| `SignAndSendSubmissionExecutor` | Submission ordering, signature/commitment verification |
| `PersistentProtocolRecorder` | Canonical event identity, settlement and persistence |
| `SessionLifecycleCoordinator` | Diagnostic session creation/finalization and close barriers |
| `TransactionInspector` / wire parser / decoders | Read-only diagnostic interpretation only |

Diagnostic success/failure must never independently authorize or reject signing.
A diagnostic parser can explain v0 or unknown programs while the existing codec
still rejects signing. A diagnostic failure cannot bypass the codec or replace
an existing MWA result. Inspection status is separate from protocol outcome/source.

The inspector has no signing, identity-secret, authorization, approval, walletlib
response, DAO, or RPC dependency. Parser primitive duplication is acceptable to
protect the frozen codec. Do not broaden or refactor any listed authority to host
inspection. Small additive host adapters and optional approval metadata do not
change their decision contracts.

Keep all work inside the existing `:app` module and source-root convention:

```text
app/src/main/java/dev/mwalab/
  capabilities/       CapabilitySnapshot, CapabilitySnapshotSource,
                      CapabilitySnapshotRepository
  transaction/        TransactionSummary, InstructionSummary, AccountSummary,
                      TransactionVersion, TransactionInspectionStatus,
                      DecodedInstruction, TransactionInspector, wire parser,
                      decoder registry/decoders, TransactionDiagnosticRepository
  storage/capabilities/ CapabilitySnapshotEntity, CapabilitySnapshotDao
  storage/transaction/  TransactionDiagnosticEntity, TransactionDiagnosticDao
  ui/transaction/     TransactionInspectorScreen, TransactionPresentation
```

`MwaLabComposition` supplies these diagnostic dependencies alongside existing
services. `MwaCapabilityProfile` stays in `mwa/capabilities`; no parallel capability
configuration is introduced in the new domain package. Test packages mirror these
boundaries. File count may vary without changing domain/authority ownership.

## 6. Room version 1 to version 2 design

Retain `mwa_lab.db`, its existing session/event rows, columns, primary keys,
foreign keys, and indexes. Add exactly two application tables through one explicit
`MIGRATION_1_2`, registered in `MwaLabDatabase.create`. Export
`app/schemas/dev.mwalab.storage.MwaLabDatabase/2.json`; leave `1.json` unchanged.
No destructive migration or downgrade fallback is permitted.

| `capability_snapshots` column | Contract |
| --- | --- |
| `session_id` | TEXT, nonnull primary key; FK to sessions, delete CASCADE |
| `captured_at_ms` | INTEGER, nonnull nonnegative epoch timestamp |
| `source` | TEXT, nonnull recognized provenance enum |
| `max_transactions`, `max_messages` | INTEGER, nonnull configured limits |
| `supported_versions_json` | TEXT, nonnull typed canonical list |
| `optional_features_json` | TEXT, nonnull typed canonical feature list |

| `transaction_diagnostics` column | Contract |
| --- | --- |
| `transaction_id` | TEXT, nonnull primary key; internal diagnostic row identity |
| `session_id` | TEXT, nonnull FK to sessions, delete CASCADE; indexed |
| `event_id` | TEXT, nonnull FK to protocol_events, delete CASCADE |
| `payload_index` | INTEGER, nonnull original zero-based payload index |
| `fingerprint_sha256` | TEXT, nonnull canonical diagnostic fingerprint |
| `wire_length` | INTEGER, nonnull original byte length |
| `version` | TEXT, nonnull checked version discriminator |
| `inspection_status` | TEXT, nonnull recognized diagnostic status |
| `summary_json` | TEXT, nonnull versioned, bounded, typed safe summary |

Enforce unique `(event_id, payload_index)` and index event lookup. The internal
row key is deterministic from the recorder event ID and original payload index;
it is unrelated to a Solana transaction identifier and is never presented as one.

Two independent FKs do not prove that an event belongs to the supplied session.
The transaction diagnostic repository/DAO must verify the immutable parent event's
session and transaction method inside the same write transaction. Direct DAO
batch insertion must retain that guard. No host or UI queries SQL directly.

Insert each event's diagnostic batch atomically in original payload order. An
identical repeat is an idempotent diagnostic replay; inconsistent duplicate content
is a diagnostic storage failure. Never REPLACE an existing snapshot/event/summary.
Strict decode validates row/JSON identity, fingerprint, length, version/status,
indexes, and derived counts agree. Invalid stored diagnostics become unavailable
without hiding or relabeling the canonical timeline.

Both tables belong to the first complete schema-v2 definition in Step 4.2, even
though transaction population arrives in Step 4.9. Include the minimal unused
entity/DAO shape needed for that coherent schema; do not publish an incomplete
version 2 and later change its meaning without a migration. This sequencing
reconciliation does not authorize feature implementation at this checkpoint.

## 7. Diagnostic domain contract

Use immutable domain types independent of Compose and Room. Own defensive copies
of collections; Kotlin `val` alone does not make a collection immutable. No public
diagnostic model retains raw transaction, signature, or instruction byte arrays.

| Intended type | Required representation |
| --- | --- |
| `CapabilitySnapshot` | Session ID, capture epoch, source, request limits, supported version strings, optional feature strings |
| `CapabilitySnapshotSource` | `CONFIGURED_WALLETLIB_PROFILE` only in Phase 4 |
| `TransactionSummary` | Diagnostic identity, immutable session/event association, payload index, SHA-256, original wire length, version/status, verified fee payer/signers/blockhash, static accounts, instructions, limits/limitations |
| `InstructionSummary` | Original instruction index, program reference/ID when resolved, verified program label, account indexes/references, data length/hash, decoded result |
| `AccountSummary` | Static account index/public key and structurally derived signer/writable/fee-payer flags |
| `TransactionVersion` | `LEGACY`, `V0`, `VERSIONED_UNSUPPORTED(number)`, `UNKNOWN` |
| `TransactionInspectionStatus` | `PARSED`, `PARTIAL`, `UNSUPPORTED_VERSION`, `MALFORMED` |
| `DecodedInstruction` | Typed System Transfer, safe Memo, SPL Transfer/TransferChecked, or explicit unknown/unsupported/malformed instruction result |

Unverified fields are nullable/absent, not zero/empty fabricated values. Static
account count is distinct from any reliably known total including lookup references.
Unresolved v0 references retain their indexes and an explicit limitation; no fake
public keys or privileges are assigned. Unknown programs within structurally valid
legacy transactions do not make the transaction malformed.

Use `diagnosticId` for the domain row identity mapped to `transaction_id`. Normally
bind `eventId` from the recorder handle. If begin failed, allow an explicitly
unbound in-memory diagnostic representation for approval; it is nonpersistable.
Never invent a recorder event ID to satisfy storage. Persistable summaries require
valid session/event identities and original indexes. The binding may be a checked
factory/private intermediate result rather than nullable fields in durable models.

Represent decoded amounts exactly over `0..18446744073709551615` using an unsigned
or arbitrary-precision value with canonical decimal persistence. No floating-point
coercion, signed overflow, rounding, or clamping. Decimal SOL formatting is exact.
Declared token decimals are wire metadata, not verified mint metadata.

## 8. Fingerprint semantics

Fingerprint = `SHA-256(raw serialized transaction)` exactly as received, including
the existing signature section. Reuse
[DiagnosticSanitizer.sha256](../app/src/main/java/dev/mwalab/security/DiagnosticSanitizer.kt).
Use lowercase 64-character hexadecimal. Instruction fingerprints use the same
function over that instruction's original data.

UI labels are `MWA Lab payload fingerprint` or `Transaction SHA-256 fingerprint`.
Never call the fingerprint a transaction signature, transaction ID, or Solana
transaction hash. It is diagnostic identity for the received payload; it is not
the subsequently signed wire payload's fingerprint. Approval and stored diagnostics
must refer to the same original bytes and agree with existing request-summary hashes.

## 9. Parser scope and bounds

Add `TransactionInspector` and `SolanaWireTransactionParser` in the existing
`dev.mwalab.transaction` package, separate from `LegacyTransactionCodec` and the
message-signing transaction detector.

Legacy inspection validates canonical shortvecs, signature section/count versus
header, header/account counts, account keys, recent blockhash, compiled instructions,
program/account indexes, truncation, and exact end-of-input. Derive account flags
from the header and fee payer from static account zero where structurally verified.
Do not require a Lab signer or query blockhash freshness in the diagnostic parser.

Bound accepted structural inspection at 1232 wire bytes; counts are additionally
bounded at 64 signatures, 256 static accounts, 256 instructions, and 256 account
references per instruction. Every read/count/multiplication is checked against
remaining input before copying or allocating. Instruction data cannot exceed the
remaining bounded wire input. Empty/oversized/truncated/noncanonical/trailing input
returns a deterministic malformed diagnostic, never an unbounded allocation or
parser exception crossing into protocol decisions. Fingerprint/length may remain
available for unsupported/oversized input without copying it into a diagnostic model.

Locate version prefixes at the verified message offset after the signature section.
Detect v0 and other version numbers accurately. For v0, safely validate/inspect
available static structure and lookup descriptors; mark inspection PARTIAL because
loaded addresses are unresolved. Do not decode semantics dependent on unresolved
accounts. A truncated v0 transaction can retain a verified version prefix while
being MALFORMED. Unknown versions remain UNSUPPORTED_VERSION with honest limitations.
Detection never enables v0 signing or ALT RPC resolution.

Bound batch inspection to the existing configured transaction request limit.
For over-limit requests, retain the exact request count and explicitly mark omitted
diagnostic payloads; never truncate the request passed to existing wallet validation.

## 10. Decoder scope and truthfulness

Use a small `ProgramDecoderRegistry` with `SystemProgramDecoder`,
`MemoProgramDecoder`, `SplTokenProgramDecoder`, and explicit unknown fallback.
Recognize verified program IDs, never infer names from instruction resemblance.

| Decoder | Required verified subset |
| --- | --- |
| System Program | Transfer: exact discriminator/data size, resolved account references, from/to and unsigned lamports; exact SOL display |
| Memo Program | Verified Memo IDs; strict UTF-8 and safe bounded presentation under section 11 |
| Classic SPL Token Program | Transfer and TransferChecked: exact discriminator/data size, resolved account roles, unsigned raw amount; mint and declared decimals for TransferChecked |
| Unknown program | Unknown Program, actual resolved ID or unresolved reference, account references, data length/SHA-256 |
| Known program, unsupported instruction | Known program label plus explicitly unsupported instruction |
| Malformed known instruction | Explicit undecoded/malformed instruction metadata; no invented decoded operation |

Validate required account counts/positions and all indexes before reporting semantics.
System Transfer requires exactly its two resolved account references; SPL Transfer
and TransferChecked require their complete source/destination/authority positions,
including the mint position for TransferChecked, with any additional multisig
references preserved explicitly.
Multisig trailing signer accounts remain explicit references; do not mislabel an
authority as a single signer. Do not infer account ownership, available funds, or
successful on-chain execution. Token-2022 extensions and other programs are outside
the required classic SPL subset. No token symbols, prices, balances, metadata
lookup, or universal instruction interpretation is introduced.

## 11. Sanitization and persistence representation

Persist allowlisted typed structured metadata only: hashes/lengths, checked version
and status, public keys, blockhash, indexes, structurally verified flags, verified
decoded fields, fixed reason/limitation codes, and configured capability values.
Use existing `org.json` conventions with a strict versioned typed codec for nested
models; `SafeSummaryJson` remains the string-map codec for predecessor event summaries.
Validate the same contracts when decoding stored diagnostics. Bound encoded summary
size at 64 KiB per inspected payload and reject oversized diagnostic records.

Never persist raw transactions, raw instruction byte arrays/base64/hex encodings,
raw unknown instruction bytes, raw messages, raw signatures, private keys, seeds,
raw authorization/association tokens, encryption key material, ciphertext, RPC
bodies, raw exceptions, or request/URI objects. Unknown instruction data is length
plus SHA-256 only. Existing key-based redaction is defense in depth; it is not a
license to serialize arbitrary data under a harmless key.

Memo presentation is a sanitized preview, not a raw-byte field: require strict
UTF-8; permit at most 256 Unicode code points; escape newlines/tabs and suppress
unsafe controls and bidirectional formatting characters. Longer previews carry
an explicit truncated label. Keep length/hash of the complete original data.
Arbitrary memo content may contain caller-supplied secrets, so preview text is
transient approval/inspection presentation only in Phase 4. Durable Memo diagnostics
store recognition, display-availability/limitation metadata, length and SHA-256,
without memo text. Restart UI truthfully shows that text was not retained. No
memo-text persistence or secret-detection claim is implied by successful decoding.

Transient parser buffers remain internal, bounded, and released after inspection.
Queued persistence receives sanitized immutable summaries only. Approved signing
bytes remain owned by the existing signing codec; diagnostics acquire no access
to protected identity storage.

## 12. Approval UI contract

For `SIGN_TRANSACTIONS` and `SIGN_AND_SEND_TRANSACTIONS`, begin the genuine event,
inspect the received payloads in memory, execute unchanged existing preparation,
and attach optional verified summaries to `ApprovalRequest` before requesting the
existing decision. Malformed/v0 diagnostics can be retained even when preparation
rejects before any approval screen. Inspector exceptions become unavailable
diagnostics and do not replace preparation or its MWA result.

Inspect and fingerprint an owned stable view corresponding to the exact payload
used by existing preparation. Mutations cannot cause approval to describe different
bytes from those signed. Do not change the frozen codec's immutable signing binding.

The approval Activity presents each original payload in a scrollable list: version,
inspection status/limitations, fee payer, required signers, blockhash, instruction
count/programs, verified operation details, size, and diagnostic fingerprint.
Keep `MWA LAB TEST ENDPOINT`, `SOLANA DEVNET`, and `NO REAL FUNDS` visible.
Unavailable diagnostics have a clear label and do not change button authority.

APPROVE/REJECT still call `ApprovalCoordinator` using the original request ID.
UI contains no signing/policy/parser/RPC logic. Busy, expiry, cancellation, stale
generation, and revoked authorization retain existing behavior. Message signing
requests, approval content, validation, and outcomes remain unchanged.

## 13. Session Detail / Transaction Inspector information architecture

Retain Home and Sessions as current top-level destinations. The inspector is an
event-associated drill-down or expandable card within Session Detail:

```text
Sessions -> Session Detail -> transaction method event -> payload -> inspector
```

Session Detail shows `Configured capability profile`, capture provenance/time,
limits, versions, optional features, and the explicit note that GET_CAPABILITIES
is not observable through pinned walletlib. Never label the snapshot as observed.

Inspector sections are Overview, Accounts, Instructions, and Raw metadata.
Raw metadata means safe hashes/lengths/version/limitations, not a raw-byte viewer.
There is no Simulation section. Unknown Program and Partial inspection are visible.
Multi-payload diagnostics sort by original payload index beneath their actual event.

Use the existing `SessionDetailViewModel` and `SessionRepository.observeSession`;
combine these with new capability and transaction repository Flows into an additive
detail presentation model. A new `SessionDetailRepository` is not required merely
because that illustrative name appears in the external plan. Preserve canonical
`SessionSummary` status/ordering and lifecycle-aware collection. Diagnostic loading,
missing, unavailable, and retry states are independent of timeline loading/error.
Screens never query DAOs or derive current capabilities directly from the profile.

## 14. Historical sessions

Migration does not backfill either table. Phase 3 sessions and protocol events
remain semantically identical and their timelines remain visible after upgrade.

For a known historical session, display `Capability snapshot was not recorded for
this historical session` and `Transaction diagnostics were not recorded for this
historical session` when absent. Do not reconstruct historical diagnostics from
hashes or assign today's capability profile. A new-session storage failure is also
missing evidence, not proof of a historical session: when no persisted
creation-version marker exists, use neutral `not recorded for this session` wording
rather than guessing the session's age. Retry reloads stored diagnostics; it never
recaptures current capabilities or fabricates a historical inspection.

## 15. Diagnostic persistence failure isolation and settlement

Capability capture, inspection, and transaction persistence observe existing wallet
behavior. Ordinary diagnostic failures cannot alter association, authorization,
approval, signing, submission, MWA error codes/results, or canonical event outcomes.
Emit only bounded safe diagnostic-persistence evidence through the existing seam.

Introduce a transaction-specific context/helper outside recorder internals, carrying
the nullable original handle, callback-bound session, method, start metadata, and
sanitized summaries. Preserve existing protocol-response selection and ordering:

```text
genuine callback -> recorder begin -> in-memory inspection
-> unchanged preparation/approval/signing/submission/MWA response
-> canonical terminal event settlement -> confirmed parent row
-> atomic diagnostic batch -> repository Flow -> UI
```

The current host helpers discard creation/completion results. Future narrow adapters
must consume the existing result contracts without changing lifecycle or recorder
authority. Null handle or PersistenceFailed means no child insert. Persisted permits
guarded insertion. AlreadyCompleted is not proof of persistence: it can describe an
in-flight claim, including a close race.

For AlreadyCompleted, look for the actual event in the original session through
the repository. If absent, observe that original session in a supervised diagnostic
worker for at most five seconds; never block a wallet callback, protocol response,
or session-close barrier waiting for it. Insert only after confirming the immutable
parent row. On timeout/storage failure/cancellation, release the sanitized context
and leave diagnostics unavailable. No invented parent, second recorder completion,
new terminal event, resubmission, or unbounded retry is permitted. The worker is
independent of an old host's cancelled authorization scope and holds no raw payload.

Cover every existing transaction terminal path, including invalid/over-limit input,
v0 rejection, stale/revoked authorization, approval rejection/cancellation/expiry,
signing failure, submission failure, and success. Bind only to the original event;
never read mutable active-session identity at completion. Atomic duplicate handling
prevents competing diagnostic workers from adding or replacing rows.

## 16. Explicit Phase 4 exclusions

No transaction simulation, simulateTransaction RPC, program logs, compute-unit UI,
fault injection/engine/profiles, report export, Android Share Sheet, copy-report
feature, mainnet, v0 signing, ALT network resolution, universal semantic decoding,
token metadata lookup, production wallet compatibility claims, new backend, or new
Gradle module. Session comparisons, filters, and latency analytics remain deferred.
Reserved `injected_fault_id` remains null. Phase 5, Phase 6, and Phase 7 are not started.

## 17. Historical script freeze and future Phase 4 gate

[scripts/phase3_static.sh](../scripts/phase3_static.sh) remains byte-for-byte frozen:

```text
1de48365d68aa940dba18a8535495154eaf7c0549b64582066110242e72070e6
```

It intentionally requires schema 1 and absence of transaction-inspector UI.
Phase 4 gets a new `scripts/phase4_static.sh` at Step 4.14. That gate preserves
Phase 0–3 semantic protections while checking schema 2, provenance, inspector
independence, sanitization, exclusions, schema-1/script hashes, and Phase 4 evidence.
Do not weaken historical scripts or rewrite their hash receipts to pass new code.

ApprovalRequest and its coordinator currently share a file; optional model fields
will change its whole-file hash without permitting coordinator behavior changes.
The approval Activity also needs additive rendering. The new gate must distinguish
reviewed additive surfaces from protected decision logic instead of blindly replaying
all historical whole-file assertions. `LegacyTransactionCodec`, `LabSigningService`,
Devnet RPC/submission, recorder core, and lifecycle core remain protected.

At Step 4.14, update CI to include the Phase 4 branch and select the current Phase 4
gate when schema 2 exists. Schema 1 remains present, so its existence alone cannot
continue selecting the obsolete Phase 3 scope gate. Preserve earlier branch gates.
This document-only checkpoint leaves CI and scripts unchanged and needs no push/tag.

## 18. Migration, rollback, and failure behavior

Migration creates both new empty tables/indexes transactionally; it does not rewrite
old rows or synthesize captures. A failed migration rolls back and leaves the v1
database/history intact. Database-open failure uses existing unavailable-state and
diagnostic failure handling; it must not trigger database deletion/recreation or
modify walletlib authorization/identity storage.

There is no automatic v2-to-v1 downgrade. Installing an old app over schema 2 may
fail database open; preserve the database and report unavailable history. Any future
downgrade/recovery procedure requires an explicit separately reviewed policy.
Forward corrective migrations must preserve recorded evidence. Never manually alter
Room metadata or historical schema files to accept a mismatch.

Child-batch rollback leaves a committed canonical event intact and no partial
diagnostic batch. Snapshot failure leaves the session/timeline usable. Malformed
stored JSON produces unavailable diagnostics, not fabricated defaults. Abrupt death
between event settlement and child insertion can leave missing diagnostics; do not
promise atomicity across wallet responses, recorder writes, and new child storage.
Restart never reparses unavailable raw bytes or manufactures missing history.

## 19. Test and verification strategy

Tests are evidence gates, not Phase 4.1 implementation. Subsequent steps must prove:

| Area | Required evidence |
| --- | --- |
| Capability | Actual config/profile parity, typed representation, immutable lists, correct session/capture source, stable ordering, no synthetic GET_CAPABILITIES, no historical backfill |
| Domain/hash | Identity and ordering invariants, nullable/unverified fields, defensive ownership, exact unsigned amounts, same bytes/same SHA-256 and one-byte difference/different SHA-256 |
| Parser | Fixed legacy/v0/unknown-version vectors; all truncation points, empty/oversized payloads, noncanonical shortvecs, count/header/index mismatch, trailing bytes, bounded allocations |
| Decoders | System Transfer, bounded/invalid UTF-8 Memo, SPL Transfer/TransferChecked including multisig references, unsupported known instruction, unknown program, malformed known instruction |
| Migration | MigrationTestHelper schema-v1 fixture with real-shaped history; exact old-row preservation, both new tables empty, schema validation, new insert/reopen; failed migration rollback and unsupported downgrade preservation |
| Persistence | One/multiple payloads, session/event association guards, original indexes, FK/cascade behavior, identical replay/conflicting duplicate, atomic batch rollback, strict JSON rejection, restart |
| Races/isolation | Persisted/PersistenceFailed/null/AlreadyCompleted paths, delayed claimed write, close/replacement and stale session, bounded wait expiry; capability/transaction DAO failures preserve wire authorization/signing/submission/errors |
| UI | Pre-approval verified summaries, all lab warnings, message-signing parity, explicit configured provenance/observability note, partial v0/unknown program, multi-payload and independent loading/error/missing states |
| Security | Actual SQLite/WAL scans with positive controls/sentinels and real wire material; no raw payload/instruction/token/key/seed or durable Memo text; no diagnostic signing/approval/RPC dependency |
| Regression | Existing authorize/reauthorize/deauthorize, message/transaction approve/reject, revocation, replacement/close, signer binding, blockhash validation, submission signature/commitment sequencing, recorder hostile races and mainnet rejection |

Room-testing already exists in `app/build.gradle.kts`; future migration tests must
make exported schemas available as AndroidTest assets using existing AGP conventions.
Use `app/src/test` and `app/src/androidTest` package conventions. Add deterministic
vectors under `test-vectors/transactions/`: Memo, System Transfer, unknown program,
SPL Transfer/TransferChecked, malformed, truncated, and v0. Extend Demo Client with
a separate Phase 4 scenario; preserve its frozen Phase 2 acceptance runner.

Before Phase 4 freeze run lint, full unit tests with rerun, debug APK builds,
both AndroidTest APK builds, Phase 1 and the new Phase 4 static gates, relevant
connected regressions, migration/diagnostic tests, cross-app and process-restart
acceptance, and exact-candidate CI. Preserve and hash the historical Phase 3 gate;
do not execute its obsolete absence assertion as the finished Phase 4 gate.

Phase 4.1 checks only the document, source links/contract coverage, whitespace,
unchanged source/schema/script hashes, baseline ancestry/tag, and currently valid
read-only predecessor static gates. It introduces no migration, test scaffolding,
feature code, or build configuration.

## 20. Final Phase 4 acceptance scenario

1. Demo Client creates a real cross-app association, authorizes, and requests
   capabilities. Session Detail shows that session's configured snapshot with
   provenance and the pinned-walletlib observability limitation; no GET_CAPABILITIES
   event appears.
2. A separate diagnostic scenario constructs a legacy System Transfer with a fresh
   Devnet blockhash and requests `signTransactions`. Before approval, MWA Lab shows
   legacy version, fee payer, destination, System Program/Transfer, exact lamports
   and SOL, required signers, and the original diagnostic fingerprint.
3. User approves through the existing coordinator. The unchanged signing path
   returns a verifiable signature without submitting or spending Devnet SOL.
4. Session Detail shows the genuine SIGN_TRANSACTIONS result and its correctly
   associated persisted inspector sections and payload ordering.
5. Run unknown-program, malformed, and v0 fixtures. Unknown semantics remain
   unknown; malformed/v0 inputs retain existing signing rejection and honest
   diagnostics. No approval bypass or enabled v0 signing occurs.
6. Force-stop/relaunch and compare stored capability, event, and transaction rows.
   The same timeline and structured diagnostics remain visible. Historical Phase 3
   sessions show missing diagnostics truthfully. Memo text is not retained.
7. Demonstrate ordinary diagnostic persistence failure isolation, hostile race
   regression, and absence of raw transaction/instruction/token/key material in
   actual SQLite/WAL storage. Record emulator/device identity and executed results.

Only the later Phase 4 freeze may record implementation completion, exact-head
Phase 4 CI, and a new annotated tag. Phase 4.1 freezes this design and stops before
Phase 4.2.
