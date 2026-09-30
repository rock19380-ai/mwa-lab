# Phase 5 simulation and diagnostic classification design

Status: frozen for Steps 5.2–5.4 on 2026-09-30. The predecessor is Phase 4 commit
`6028375636251eb3071f0a5240a2ad1efc78131e`, annotated tag
`phase4-capability-transaction-diagnostics-2026-09-29`, Room schema 2, and
walletlib 2.0.7. The local Phase 5 plan in `/home/abbaas/Downloads/MWA_LAB/`
informs this design; this document fixes the choices used by implementation.

## Scope and authority

Phase 5 P0 simulation accepts only a bounded (1–1232 byte) legacy wire transaction
already eligible for the existing signing path. Phase 4 v0/versioned inspection
remains visible, but v0 simulation P0 is unavailable and v0 signing remains
unsupported. `LegacyTransactionCodec` remains authoritative for validation and
signing. Solana Devnet is the only RPC network; no caller-selected URL exists.

Simulation is diagnostic-only. It is a child of a genuine `SIGN_TRANSACTIONS` or
`SIGN_AND_SEND_TRANSACTIONS` protocol event and never creates a synthetic
`ProtocolMethod`. The existing `ApprovalCoordinator` owns decisions, the existing
signing service owns signatures, the existing Devnet submission path owns actual
submission, and `ProtocolRecorder` owns protocol completion. Child evidence
cannot override the parent `ProtocolEvent` outcome or failure source.

Simulation PASS does not grant approval.
Simulation FAIL does not reject the MWA request.
Simulation UNAVAILABLE does not disable approval.
Simulation does not submit a transaction.
Simulation does not complete ProtocolRecorder.
Simulation evidence cannot override the parent ProtocolEvent outcome.

An approval screen may show a safe result and the independent APPROVE/REJECT
controls. A PASS warning must read: “Simulation passed on Devnet at the recorded
context. This does not guarantee later signing, submission, confirmation, or
unchanged chain state.” No preflight result is a policy gate.

## Result and failure-source matrix

| Observation | Child outcome | Child `ProtocolFailureSource` | Parent |
| --- | --- | --- | --- |
| RPC `value.err == null` | PASS | NONE | unchanged |
| Safely bounded `value.err != null` | FAIL | SIMULATION | unchanged |
| Timeout, I/O, HTTP, JSON-RPC numeric error | UNAVAILABLE | RPC_NETWORK | unchanged |
| Malformed JSON, missing/invalid required result/value, oversized response | UNAVAILABLE | RPC_NETWORK | unchanged |
| Unsafe local conversion or diagnostic target | UNAVAILABLE | LOCAL_PARSER | unchanged |
| Explicitly unclassified safe fallback | UNAVAILABLE | UNKNOWN | unchanged |
| User rejects after PASS | PASS child | NONE child | existing protocol rejection |
| User approves after FAIL | FAIL child | SIMULATION child | existing signing/submission result |

Unknown but safely bounded non-null simulation errors are FAIL with
`UNKNOWN_SIMULATION_ERROR`. Unknown structures never gain invented program,
transaction, or custom-code meaning. The parser recognizes only exact known
shapes such as `BlockhashNotFound`, `AccountNotFound`,
`InsufficientFundsForFee`, and `InstructionError` with bounded index and optional
numeric `Custom` code. Everything else is unknown. A top-level JSON-RPC error is
an availability failure, even if its server text resembles an execution error.

## RPC contract and transient payload

Extend the existing `DevnetRpcGateway` and `SolanaDevnetRpcGateway` fixed
`https://api.devnet.solana.com` transport. The JSON-RPC method is exactly
`simulateTransaction`. Options are internal and fixed: `encoding=base64`,
`sigVerify=false`, `replaceRecentBlockhash=false`, `innerInstructions=false`;
`accounts` is omitted. No simulation API exposes submission or a URL.

For `sign_transactions`, commitment is `processed`, with no minContextSlot.
For `sign_and_send_transactions`, use the validated request commitment if present,
otherwise `processed`; propagate validated minContextSlot only for that method.
The RPC request uses the exact pre-approval wire bytes. `sigVerify=false` permits
an unsigned transaction; `replaceRecentBlockhash=false` preserves stale-blockhash
evidence. The existing 64 KiB HTTP/JSON response cap applies before JSON parsing.
Parse only `context.slot`, `value.err`, `value.logs`, and `value.unitsConsumed`.
Reject unsafe field types and negative or out-of-range numeric values.

Raw transaction bytes exist only as an owned transient copy inside the future
coordinator/service execution path and RPC encoding call. They never enter Room,
public `SimulationResult`, ViewModel, Compose state, protocol summaries, logs, or
exported evidence. Invalidation on request/session close releases the target;
after use, zero the simulator-owned copy as a best effort, without claiming JVM
memory erasure. Raw signed transactions, signatures, tokens, keys, seeds,
encryption secrets, and complete RPC request/response bodies never enter
diagnostic persistence or exported evidence.

## Safe domain representation

`SimulationTargetRef` contains nonblank session ID, event ID, request ID,
nonnegative payload index, and canonical lowercase SHA-256 transaction
fingerprint. It carries no payload bytes. The coordinator binds this immutable
reference to the original approval request and session generation, plus the
parent event and payload fingerprint. A stale/mismatched trigger is ignored.

`SimulationResult` contains simulation ID; target reference; positive attempt
number; nonnegative start/completion epochs and duration; PASS/FAIL/UNAVAILABLE;
matching failure source; commitment; optional nonnegative context slot and units;
structured `SimulationErrorSummary`; optional numeric RPC code or fixed transport
reason; bounded ordered program logs; and a log-truncated flag. It contains no
raw transaction, signature, arbitrary RPC JSON, exception text, or server text.
`SimulationErrorSummary` holds an allowlisted category, optional instruction
index, optional numeric custom error code, and no invented program meaning.
PASS has no error; FAIL has an error; UNAVAILABLE has no execution error. RPC
availability fields are allowed only for RPC_NETWORK.

The HTTP body cap is 65,536 bytes. Program logs are untrusted: at most 64 lines,
512 Unicode code points per line, and 32,768 code points total. Replace ASCII
controls (including newlines, tabs, NUL, DEL), bidi format controls, and other
unsafe format characters with a visible replacement; do not render them as
trusted UI text. Preserve line order. If any line/count/total bound is hit, add
the fixed marker `Program logs truncated by MWA Lab diagnostic limit.` via the
truncation flag. Never persist raw log lines or claim that log contents have
verified semantics.

## Later Phase 5 integration contract

`TransactionSimulationCoordinator` is the eventual single owner of transient
payload registration and one in-flight attempt per target. It will reject stale
session/request/event/fingerprint bindings and duplicate taps. A retry creates a
new attempt only while approval remains pending. It never owns approval,
signing, submission, Room, or recorder completion. A late result is discarded
when its original target is invalidated; it is never rebound to the next request.

Room schema 3 will add `simulation_results` with a forward-only 2→3 migration,
foreign keys to the genuine session/event, unique simulation ID and target/attempt
identity, and bounded sanitized fields only. No historical backfill occurs.
Schema 1 and 2 exports and migration 1→2 remain byte-for-byte frozen. This prompt
stops at Step 5.4, so the database stays at schema 2.

`SimulationDiagnosticSettlement` will wait for a confirmed persisted parent event
before inserting children, following the bounded Phase 4 settlement pattern.
Null or failed parent persistence means no child insert. An `AlreadyCompleted`
claim is not proof of a committed row. Parent-close and child-write races cannot
change the canonical first terminal event; late/missing child evidence remains
missing. Duplicate attempts must not overwrite earlier evidence. Persistence
failures never change MWA results.

The approval UI will show IDLE/RUNNING/PASS/FAIL/UNAVAILABLE independently of
APPROVE/REJECT and show the PASS warning. Persistent Session Detail will show
ordered attempts under the real transaction event, including safe failure source,
error fields, compute units, context, duration, bounded logs, and the warning.
Historical sessions with no child rows show “not recorded for this session”; no
result is reconstructed from hashes after restart.

## Explicit exclusions and acceptance

Phase 6 fault injection and fault profiles are excluded. Phase 7 Markdown/JSON
report export and Android Share Sheet export are excluded. No new DI framework,
backend, mainnet path, ALT resolution, universal decoder, or v0 signing is added.

Deterministic acceptance scenarios for Steps 5.2–5.4: valid and invalid domain
construction; exact RPC flags and scoped minContextSlot; err-null PASS; known
instruction/custom and blockhash FAIL; safely bounded unknown FAIL; timeout,
I/O, HTTP and JSON-RPC UNAVAILABLE; malformed/missing/oversized response
UNAVAILABLE; null/absent optional fields; negative/unsafe fields fail closed;
control-filled and over-limit logs are sanitized and bounded. Later Phase 5
acceptance must also prove PASS→reject, FAIL→approve, racing close, restart,
and live Devnet known-good/known-bad behavior before Phase 5 freeze.
