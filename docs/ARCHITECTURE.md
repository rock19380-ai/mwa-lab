# Architecture

## Verified Phase 1 boundary

```text
MWA Lab Demo Client (:demo-client)
        ↓ real solana-wallet:// Android association
MobileWalletAdapterActivity (:app)
        ↓
AssociationUri.parse / LocalAssociationUri
        ↓
MwaSessionHost
        ├── NetworkPolicy
        ├── LabAuthorizationPolicy
        ├── IdentityRepository
        ├── MwaCapabilityProfile
        ├── walletlib 2.0.7 authorization repository
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
- protected Lab test identity;
- authorization/deauthorization policy;
- centralized capability configuration;
- minimal typed/sanitized protocol evidence seam.

The UI does not own protocol, authorization, capability, or key semantics.

### `:demo-client`

Deterministic cross-package Phase 1 test infrastructure.

Identity:

```text
MWA Lab Demo Client
FOR TESTING ONLY
```

It uses pinned `clientlib:2.0.7` to exercise:

```text
CONNECT
AUTHORIZE
GET_CAPABILITIES
DEAUTHORIZE
CLOSE
```

It is not the primary product and is not a production dApp.

## Authority boundaries

### MWA protocol

Pinned official MWA semantics are authoritative.

Phase 1 uses walletlib `2.0.7`; in that artifact the scenario lifecycle starts
with `Scenario.start()`, not newer upstream `Scenario.startAsync()`.

### Authorization state

MWA Lab decides whether a request is eligible for authorization.

walletlib 2.0.7 owns auth-token issuance, authorization-record persistence,
validation, and revocation.

MWA Lab never parses or persists raw auth-token contents for diagnostics.

### Capability truth

`MwaCapabilityProfile` is the single application-level authority for values fed
to `MobileWalletAdapterConfig`.

`get_capabilities` itself is served internally by walletlib.

### Evidence

`ProtocolEvidenceStore` is a minimal in-memory Phase 1 seam.

It records typed/sanitized summaries for protocol interactions where MWA Lab has
an application callback. The demo client records typed capability-step evidence
because pinned walletlib handles `get_capabilities` internally without a wallet
callback.

This is **not** the full Phase 3 recorder: no Room history, timeline product,
diagnostic export bundle, or fault-event model is implemented in Phase 1.

## Architectural invariants

1. UI components do not own protocol/signing logic.
2. Devnet-only authorization policy is enforced below the UI.
3. Private identity material is behind the identity/security boundary.
4. Raw auth tokens and private material do not enter diagnostics.
5. Capability values are centralized and pinned-library-aware.
6. Malformed/unsupported authorization fails closed.
7. Production-wallet behavior is compatibility evidence, not protocol authority.
8. Phase 2 must extend this frozen boundary rather than replace it.
