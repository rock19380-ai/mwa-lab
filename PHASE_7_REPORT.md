# MWA Lab — Phase 7 Sanitized Diagnostic Reports Report

Status at generation: **FREEZE CANDIDATE — Phase 7 implementation, local/device
verification, live NORMAL and injected report acceptance, and prefreeze gates
are complete; exact-head GitHub Actions and the annotated tag are the remaining
freeze actions.**

Frozen predecessor: `f5eaec53d911fc2423efdb3051e8316ce9e1032d` /
`phase6-deterministic-fault-engine-2026-10-01`.

Phase 7 branch: `phase7-sanitized-diagnostic-reports`.

## Implemented scope

- canonical versioned `DiagnosticReport` projection over persisted evidence;
- read-only snapshot assembly with COMPLETE/PARTIAL truthfulness;
- bounded allowlist report sanitization;
- deterministic Markdown and JSON renderers;
- transaction/capability/simulation report projection without raw payload export;
- independent `failureSource` and `injectedFaultId` preservation;
- app-private bounded report cache;
- non-exported cache-scoped FileProvider;
- Android Share Sheet for Markdown/JSON;
- Copy Summary from the same canonical report;
- Session Detail export UI and truthful injected/partial/simulation labels;
- hostile sanitization, provider, restart, parity, local/device and live acceptance;
- phase-aware Phase 7 CI/static/security gate without weakening the historical
  Phase 6 sharing prohibition.

Room remains schema 3. walletlib remains 2.0.7. Mainnet remains unavailable.
Authorization, signing, fault-engine and simulation authorities remain frozen.

## Final live acceptance

NORMAL canonical Devnet sign-and-send session:
`8cfe5721-9674-4a2e-b73c-26895bfb98b5` — PASS, persisted/exported
`SUCCESS / NONE / no injected fault`.

Injected signing-rejection session:
`340ce109-f6bb-45d2-a492-b069f3742ecb` — PASS, persisted/exported
`FAILURE / INJECTED / FAULT_SIGN_REJECT / ERROR_NOT_SIGNED (-3)`.

Both sessions were reopened after process death and exported as Markdown and
JSON through Android Share Sheet. Copy Summary passed. The read-only device
verifier matched exported event classifications to Room.

## Freeze evidence boundary

The final exact-head GitHub Actions run ID/URL cannot be committed into this
same freeze candidate without changing the exact HEAD. It is therefore recorded
by the annotated freeze tag and the external freeze receipt after CI succeeds.

Phase 8 is **NOT STARTED**.
