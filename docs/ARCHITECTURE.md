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

### Evidence

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
