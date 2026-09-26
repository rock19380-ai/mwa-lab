# Architecture

## Product boundary

Conceptual flow:

Android Intent / MWA Association
→ MwaSessionHost
→ ProtocolRecorder
→ Capability Snapshot / Fault Engine / Transaction Diagnostics
→ Approval / Result UI
→ Devnet Test Signer
→ RPC / MWA Response
→ Sanitized Event Store
→ Timeline UI / Report Export

## Architectural invariants

1. Every user-visible diagnosis must derive from structured protocol/session
   state, not ad-hoc log strings.
2. UI components must not own signing or protocol logic.
3. Report export must not reach directly into secret/key storage.
4. Transaction parsing and simulation must not control authorization state.
5. Fault behavior must use explicit, testable interception points.
6. MWA specification semantics take priority over convenience.
7. Production-wallet behavior is compatibility evidence, not protocol authority.

## Initial module strategy

The competition baseline uses a single Gradle `:app` module.

Architectural separation is maintained primarily by Kotlin package/domain
boundaries until additional Gradle modules are justified.
