# MWA Lab — Phase 2 Report

**Date:** 2026-09-27
**Phase:** Phase 2 — Complete Core Request / Signing Path
**Branch:** `phase2-core-request-signing`
**Frozen Batch A parent:** `9d2dea7c17997c7b1d3f1275b7456ef77a1feab7`
**Pinned MWA libraries:** walletlib/clientlib `2.0.7`

## Objective

Phase 2 extends the frozen Phase 1 Mobile Wallet Adapter boundary into a
Devnet-only signing/submission substrate.

The product-level exit gate from the master plan is:

```text
sample dApp
→ MWA Lab
→ authorize
→ get capabilities
→ approve
→ devnet result
```

plus:

```text
sample dApp
→ MWA Lab
→ deliberate user rejection
→ defined MWA error
```

Both paths have been exercised through the deterministic cross-package demo
client and recorded in Phase 2 evidence.

## Completed scope

### Phase 2.0–2.5 — signing foundation

- protected `LabSigningService` behind the Android-Keystore identity boundary;
- existing-token reauthorization without changing the frozen Phase 1 issuer or scope;
- explicit single-flight request-bound approval coordination;
- bounded `sign_messages`;
- authorized-account binding;
- transaction-message rejection on the message-signing path;
- session-generation guard for stale association callbacks;
- richer typed/sanitized protocol evidence.

Batch A was frozen at:

```text
9d2dea7c17997c7b1d3f1275b7456ef77a1feab7
phase2-batch-a-sign-messages-2026-09-27
```

### Phase 2.6 — legacy transaction codec

`LegacyTransactionCodec` implements a bounded legacy Solana wire-transaction
parser/signer boundary:

- maximum transaction wire size `1232` bytes;
- canonical short-vector parsing;
- signature/header consistency checks;
- required Lab signer lookup;
- exact Lab signature-slot patching;
- preservation of other signature slots;
- immutable approved transaction snapshot;
- rejection of malformed, truncated, trailing, non-canonical, and versioned input.

### Phase 2.7 — sign_transactions

`MwaSessionHost` supports approved legacy transaction signing with:

- maximum 10 transactions/request;
- active-authorization checks;
- explicit approval;
- bounded codec validation;
- live Devnet blockhash validity check;
- defined invalid/too-many/not-authorized/declined result mapping.

Pinned walletlib 2.0.7 requires the `sign_transactions` optional feature to be
advertised for this method; Phase 2 advertises exactly that verified feature.

### Phase 2.8 — Devnet RPC

`SolanaDevnetRpcGateway` provides the production RPC boundary with a fixed
endpoint:

```text
https://api.devnet.solana.com
```

It supports:

- blockhash-validity checking;
- transaction submission;
- commitment/status polling;
- bounded response parsing;
- timeout/I/O/HTTP classification;
- JSON-RPC error preservation without raw-response leakage.

No caller-supplied RPC endpoint exists.

### Phase 2.9 — sign_and_send_transactions

`SignAndSendSubmissionExecutor` owns:

- deterministic submission ordering;
- returned-signature cross-check;
- requested commitment sequencing;
- wait-before-next behavior;
- partial submission handling;
- cancellation;
- no resubmission of cancelled/failed positions.

Session host maps defined submission failures to the pinned walletlib surface,
including `ERROR_NOT_SUBMITTED`.

### Phase 2.10 — demo + live Devnet acceptance

Live acceptance verified:

- `SIGN_TRANSACTION_APPROVE`;
- returned signed legacy transaction cryptographic verification;
- `SIGN_AND_SEND_REJECT` → `ERROR_NOT_SIGNED`;
- `SIGN_AND_SEND_APPROVE`;
- Devnet-only submission;
- returned signature confirmation on Devnet.

The live A/B/C sequence was re-run after the later authorization/submission
hardening and remained green.

### Phase 2.11 — hostile negative and lifecycle hardening

Step 2.11A covers deterministic malformed/over-limit/security/approval cases.

Step 2.11B1 covers deterministic and Android mocked RPC/submission failures,
commitment ordering, partial submission, cancellation, and no-resubmission.

Step 2.11B2 covers:

- revoked current-session authorization;
- revoked token cross-session behavior;
- identity reset authority change;
- clientlib 2.0.7 single-outstanding-request truth;
- live payload/signature isolation;
- pending approval cancellation on session teardown;
- fresh-session recovery.

A session-local active-authorization generation was added as defense in depth.
Authorize/reauthorize success activates the current association generation;
deauthorize/replacement/teardown/close invalidates it. Privileged flows
re-check authority around approval/signing/submission.

## Capability envelope

```text
network                             Solana Devnet only
maxTransactionsPerSigningRequest    10
maxMessagesPerSigningRequest        10
transaction versions                legacy only
optional MWA features               sign_transactions only
SIWS                                not advertised
RPC endpoint                        https://api.devnet.solana.com
```

## Security invariants preserved

- no mainnet signing/submission;
- no production-wallet secret import;
- no raw private seed exposure outside the protected signing boundary;
- no server/backend signer;
- no raw auth token, association token, message payload, transaction payload, or
  signature bytes in diagnostic evidence;
- no signing without active authorization plus explicit approval;
- malformed/unsupported input fails closed;
- cancellation does not resubmit transactions;
- injected failures are not represented as observed failures.

## Verification summary

Verified locally before Phase 2 final freeze:

```text
Phase 1 deterministic static gate                         PASS
Phase 2 deterministic static gate                        PASS
targeted hostile/unit matrix                             PASS
full Gradle lint/test/debug/APK/AndroidTest build         PASS
primary Android instrumentation                          PASS
cross-package Android instrumentation                    PASS
mocked Devnet RPC Android matrix                         PASS
live sign_transactions acceptance                        PASS
live sign_and_send reject acceptance                     PASS
live sign_and_send approve/Devnet acceptance              PASS
revoked authorization/token live acceptance               PASS
session teardown/fresh-session lifecycle acceptance       PASS
device logcat + static/evidence secret audit              PASS
git diff --check                                         PASS
```

Detailed evidence is under `docs/evidence/phase2/`.

## Clientlib concurrency limitation

Pinned `JsonRpc20Client` permits only one outstanding request on a client
association.

Phase 2 therefore does not claim that two simultaneous live wallet callbacks
were produced from one client instance. Live evidence verifies local
single-outstanding-request behavior and first-request payload/signature
isolation. Wallet callback-level single-flight/decision isolation is covered
deterministically by `ApprovalCoordinatorTest`.

## Deliberately deferred

Phase 2 does **not** implement Phase 3 product-recorder scope:

- Room/SQLite `MwaSession` persistence;
- restart-surviving session history;
- session-detail timeline;
- full diagnostic export bundle;
- deterministic fault engine.

Production-wallet compatibility is also not claimed.

## Final freeze recording

This report intentionally does not self-reference the final Phase 2 commit hash.

The authoritative final implementation commit, exact-head GitHub Actions run,
annotated Phase 2 tag, remote/local equality, and clean-tree proof are recorded
by the Phase 2.12 final closeout/freeze evidence after remote CI succeeds.
