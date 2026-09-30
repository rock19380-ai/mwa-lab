# MWA Lab — Phase 5 Simulation and Diagnostic Classification Report

**Status:** IMPLEMENTED / PRE-FREEZE — final device/live-Devnet acceptance and exact-head CI freeze still required.

## Baseline

Phase 5 branches from frozen Phase 4 commit
`6028375636251eb3071f0a5240a2ad1efc78131e` and preserves the historical Phase
3/4 static gates and Room schema exports 1/2. Phase 5 advances Room to schema 3
with additive `MIGRATION_2_3` and no historical backfill.

## Implemented contract

- user-triggered simulation for supported legacy transaction approval targets;
- fixed Solana Devnet `simulateTransaction` RPC with base64 encoding,
  `sigVerify=false`, and `replaceRecentBlockhash=false`;
- PASS / FAIL / UNAVAILABLE plus `NONE`, `SIMULATION`, `RPC_NETWORK`,
  `LOCAL_PARSER`, and safe unknown classification;
- context slot, compute-unit and allowlisted error metadata;
- bounded/redacted untrusted RPC program-log structure;
- transient byte ownership bound to exact session/event/request/payload/fingerprint;
- Room schema 3 simulation child evidence attached only after a canonical parent
  protocol event is durable;
- approval and Session Detail UI that keep simulation visibly diagnostic;
- deterministic public transaction templates and synthetic RPC parser vectors;
- Phase 5 static/CI routing without rewriting frozen predecessor gates.

## Authority invariants

Simulation never approves, rejects, signs, submits, or completes a protocol
request. PASS does not guarantee signing/submission/confirmation. FAIL and
UNAVAILABLE do not disable APPROVE/REJECT or rewrite the parent `ProtocolEvent`.
Versioned v0 signing remains unsupported. Mainnet remains unavailable. Phase 6
fault injection and Phase 7 report export are not implemented.

## Security invariants

Raw simulation transaction bytes are transient only. Diagnostic persistence does
not include raw transaction bytes, signatures, auth/association tokens, private
keys/seeds, or raw RPC bodies. Program logs are untrusted, bounded, and free-form
content is redacted before public/durable results.

## Deterministic acceptance vectors

`test-vectors/simulation/` contains a known-good Memo transaction template, a
structurally valid unknown-System-opcode runtime-failure template, and PASS/FAIL/
custom-error/blockhash/unknown/RPC-unavailable response fixtures. They are
synthetic deterministic inputs and are not live Devnet receipts.

## Remaining pre-freeze gates

The repository must still execute the generated final local gate script on the
real development environment, including Gradle lint/JVM/build checks and Android
instrumentation where available. The real cross-package device path and live
Devnet PASS/FAIL evidence must not be claimed until actually run. After those
receipts are green, Phase 5 can proceed to exact-head GitHub Actions and a final
freeze/tag closeout.
