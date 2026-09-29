# MWA Lab — Phase 4 Report

## Status

**Capability Snapshot + Transaction Diagnostics implementation is complete
through the Phase 4.14 documentation/static-gate checkpoint.**

This report intentionally does not claim the final Phase 4 freeze/tag or
exact-head remote CI yet. The hostile pre-freeze audit and final closeout remain
separate gates.

## Frozen predecessor

```text
Phase 3 commit: 913642d30c4445602b5ff7f72995e826a196938b
Phase 3 tag:    phase3-protocol-recorder-timeline-2026-09-28
```

The Phase 3 static script remains frozen with SHA-256:

```text
1de48365d68aa940dba18a8535495154eaf7c0549b64582066110242e72070e6
```

## Phase 4 implementation checkpoints

```text
aa88abc  docs(phase4): freeze capability and transaction diagnostics design
87c9677  feat(phase4): add session capability snapshot persistence
68ebf46  feat(phase4): capture and display session capabilities
e60f821  feat(phase4): add bounded transaction diagnostics parser
6297625  feat(phase4): add verified transaction instruction decoders
8285302  feat(phase4): show transaction diagnostics before approval
3453036  feat(phase4): persist sanitized transaction diagnostics
19dff4a  feat(phase4): add persistent transaction inspector UI
e1529dc  test(phase4): add diagnostic vectors and cross-app scenario
```

The documentation/static-gate commit is created after its gates pass.

## Delivered capability context

`MwaCapabilityProfile` remains the capability authority. Each new Phase 4
session persists a `CapabilitySnapshot` with explicit provenance:

```text
CONFIGURED_WALLETLIB_PROFILE
```

The snapshot represents the walletlib configuration for that session. It does
not claim an observed `get_capabilities` callback. Pinned walletlib 2.0.7 handles
that request internally, and Phase 4 creates no synthetic `GET_CAPABILITIES`
protocol event.

Historical Phase 3 sessions are not backfilled with current capability values.

## Room migration and persistence

The product diagnostic database advances from schema version 1 to version 2 by
an explicit `MIGRATION_1_2`.

New structures:

```text
capability_snapshots
transaction_diagnostics
```

The migration creates the new diagnostic structures/indexes without rewriting
historical Phase 3 session/event rows. The historical schema-v1 export remains
unchanged. There is no destructive migration fallback.

Transaction diagnostics are bound to the terminal persisted ProtocolEvent and
payload index. Diagnostic persistence is non-authoritative: a diagnostic write
failure cannot change the MWA signing/submission/protocol result.

## Transaction inspection scope

The read-only Phase 4 diagnostic path supports:

- bounded legacy transaction structural inspection;
- accurate v0/versioned detection with partial diagnostics where ALT-loaded
  addresses are unresolved;
- fee payer and message-header signer/writable privilege derivation;
- recent blockhash and instruction/program metadata;
- SHA-256 diagnostic transaction fingerprint;
- sanitized account/program metadata;
- deterministic malformed-input classification.

`LegacyTransactionCodec` remains the signing/validation authority. v0 diagnostic
visibility does not enable v0 signing.

## Verified decoder scope

Implemented semantic decoding is intentionally narrow:

```text
System Program: Transfer
Memo Program: bounded safe memo display
SPL Token: Transfer, TransferChecked
```

Unknown programs and unsupported/unsafe semantics remain explicit unknown or
unavailable states. Unknown instruction payloads are represented by length and
SHA-256 rather than raw bytes.

Phase 4 does not perform token metadata/name/value lookup.

## Approval and product UI

Transaction diagnostics can be shown before approval, but the inspector/UI owns
no signing or authorization logic. Explicit approval remains controlled by the
existing approval boundary.

Session Detail renders restart-surviving transaction diagnostics with sections
for Overview, Accounts, Instructions, and Raw metadata. Historical sessions
without Phase 4 data remain truthful rather than being retroactively populated.

## Security invariants

Phase 4 does not persist diagnostic copies of:

- raw transaction payloads;
- raw unknown instruction bytes;
- raw authorization/association tokens;
- private keys or seeds;
- encryption secret material;
- raw signatures.

Mainnet remains unavailable. Normal signing remains explicit and user-visible.
Diagnostic success cannot grant signing authority.

## Step 4.11 local evidence

`docs/evidence/phase4/phase4-step4.11-local-gates.json` records, at implementation
commit `e1529dcdc990f0f08b34127fd8eb953ec7877b84`:

- deterministic Phase 4 vector check: PASS;
- Phase 1 static gate: PASS;
- app + demo-client JVM tests: PASS;
- lint: PASS (zero errors; warnings recorded in evidence);
- debug APK assembly: PASS;
- AndroidTest APK assembly: PASS;
- debug APK installation: PASS;
- targeted connected tests: PASS;
- Phase 4 diagnostic device acceptance: PASS;
- `git diff --check`: PASS.

Recorded JVM counts at that checkpoint:

```text
app:         224 tests, 0 errors, 0 failures, 0 skipped
demo-client:   6 tests, 0 errors, 0 failures, 0 skipped
```

Recorded connected-test counts:

```text
app:         10 tests, 0 errors, 0 failures, 0 skipped
demo-client:  1 test, 0 errors, 0 failures, 0 skipped
```

These counts are historical evidence for Step 4.11, not a substitute for later
closeout runs.

## Step 4.11 Android acceptance

`docs/evidence/phase4/phase4-step4.11-device-acceptance.json` records a successful
Android 16 emulator run:

```text
model: sdk_gphone64_x86_64
API:   36
status: PASS
submittedTransactions: 0
```

The real cross-package acceptance proves:

1. System Transfer diagnostics are shown before approval and the authoritative
   legacy signing path succeeds.
2. Unknown Program diagnostics remain explicitly unknown.
3. v0 is detected/partially inspected while the authoritative legacy-only path
   still rejects signing.
4. No synthetic GET_CAPABILITIES ProtocolEvent is created.
5. Force-stop/restart preserves equivalent structured state for:
   `sessions`, `protocol_events`, `capability_snapshots`, and
   `transaction_diagnostics`.
6. Transaction inspector product state renders again after restart.

The acceptance intentionally uses `signTransactions`; it submits zero Devnet
transactions solely for Phase 4 inspection proof.

## Static/CI closeout design

`scripts/phase3_static.sh` is preserved byte-for-byte as historical evidence.
It is not used as the current Phase 4 gate because its frozen scope intentionally
asserts that future Phase 4 diagnostics do not exist.

Phase 4 adds:

```text
scripts/phase4_static.sh
scripts/phase4_static.py
```

The Phase 4 gate protects toolchain/network/schema/migration boundaries, reviewed
runtime hashes, diagnostic authority separation, absence of future-phase product
features, documentation truth, and deterministic transaction vectors. CI routes
schema-v1/no-v2 repositories to the Phase 3 gate and schema-v2 repositories to
the Phase 4 gate.

## Explicitly deferred

Phase 4 does not implement or claim:

- transaction simulation — Phase 5;
- deterministic fault injection — Phase 6;
- Markdown/JSON diagnostic report export — Phase 7;
- mainnet signing/submission;
- v0 signing;
- ALT RPC resolution;
- universal Solana semantic decoding;
- production-wallet compatibility.

## Remaining closeout gates

Before Phase 4 can be declared frozen:

1. run the hostile pre-freeze audit and repair only evidenced defects;
2. run final local/device acceptance as required;
3. push the exact final HEAD;
4. require exact-head GitHub Actions success;
5. create/push the annotated Phase 4 freeze tag only after that CI success.

Until those gates complete, Phase 5 is not started.

## Step 4.14 local closeout evidence

`docs/evidence/phase4/phase4-step4.14-local-gates.json` records PASS for the
Phase 4 structural gate, deterministic vectors, shell syntax, lint, JVM tests,
debug APK assembly, AndroidTest APK assembly, current `phase4_static.sh`, and
`git diff --check`. This closes the documentation/static/CI implementation step.

The next required gate is the hostile pre-freeze audit. Exact-head remote CI and
the final annotated freeze tag are still intentionally unclaimed.

## Pre-freeze hostile audit closeout

The final hostile pre-freeze audit was executed against Phase 4 candidate
`387183f183564f885c963cac7ba72b41817101b5` and passed without a product-source repair.

Verified audit properties:

- fresh Demo Client cross-app instrumentation: PASS;
- installed `solana-wallet` endpoint discovery: PASS;
- real System Transfer diagnostics and terminal binding: PASS;
- unknown-program explicit-unknown diagnostics and terminal binding: PASS;
- v0 detection with authoritative signing rejection: PASS;
- force-stop/restart persistence across the Phase 4 durable tables: PASS;
- Phase 4 static/schema/source-authority gate: PASS;
- deterministic transaction vectors: PASS;
- durable-schema secret/raw-payload column scan: PASS;
- unresolved Git conflict-marker scan: PASS;
- tracked worktree remained clean.

Audit summary SHA-256: `d5d716836c0f56a5aa920a8a62631d68b93e869dc68b698005700cc82ac0de55`.

The final freeze remains conditional on GitHub Actions succeeding for the exact
post-audit-evidence closeout commit. The annotated freeze tag is the final
external receipt for that exact-head CI result. Phase 5 is not started.
