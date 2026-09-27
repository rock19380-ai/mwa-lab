# MWA Lab — Phase 2 Batch A Report (2.0–2.5)

Date: 2026-09-27

## Scope completed

- Pinned walletlib/clientlib 2.0.7 API authority retained.
- Protected Android-Keystore-backed Lab signing boundary added; raw private seed remains hidden from session/UI layers.
- Structured in-memory `ProtocolEvidence` foundation extended for Phase 2 methods.
- Existing authorization / reauthorization path enabled without changing the frozen Phase 1 issuer or authorization scope.
- Lifecycle-safe, single-flight, request-bound user approval coordinator added.
- `sign_messages` implemented with explicit user approval, authorized-address binding, bounded payload validation, transaction-message rejection, Ed25519 signing, and defined MWA errors.
- Cross-association generation guard added so stale callbacks cannot terminate a replacement session.
- Demo-client Android instrumentation runner dependency made explicit.

## Verification completed

- Full Gradle lint/test/build gate: PASS.
- Phase 1 deterministic static regression: PASS.
- Phase 2 Batch A static gate: PASS.
- Primary Android instrumentation: PASS (4 tests).
- Real cross-package Phase 1 canonical flow repeated successfully: PASS.
- Real cross-session reauthorization: PASS.
- Real `sign_messages` APPROVE path through the MWA Lab UI: PASS.
- Returned Ed25519 message signature verified by the demo client: PASS.
- Real `sign_messages` REJECT path: PASS and mapped to `ERROR_NOT_SIGNED`.

## Security / compatibility invariants

- Devnet-only policy preserved.
- Phase 1 auth issuer `mwa-lab-phase1` preserved.
- Phase 1 authorization scope `mwa-lab:phase1:devnet:v1` preserved.
- No production secret logging or persistence added.
- Production `app` does not depend on clientlib.
- `sign_transactions` and `sign_and_send_transactions` remain outside this checkpoint and are implemented in the next Phase 2 batch.

## Evidence

See `docs/evidence/phase2/`, especially:

- `phase2-batch-a-api-authority.txt`
- `phase2-batch-a-security-contract.txt`
- `phase2-batch-a-session-generation-repair-acceptance.txt`

Intermediate failure/repair evidence is retained to document the AndroidTest-runner and stale-session lifecycle defects that were discovered and repaired before this checkpoint.
