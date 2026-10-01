# MWA Lab — Phase 6 Deterministic Fault Engine Report

Status at generation: **PREFREEZE — 6.0–6.20 implementation and local verification in progress**.

Frozen predecessor: `bcd42c11adbe18abdbea18da3f29ae302c9518be` / `phase5-simulation-diagnostic-classification-2026-09-30`.
Phase 6 branch: `phase6-deterministic-fault-engine`.

## Implemented scope

- centralized deterministic fault catalog and request-start snapshot authority;
- private one-active-fault selection persistence;
- canonical `ProtocolEvent.injectedFaultId` annotation with injected-failure invariant;
- authorization/signing/payload/stale/delay/RPC/submission fault profiles;
- Fault Lab and active-fault/request-snapshot visibility;
- persisted session presentation that keeps injected condition separate from terminal failure source;
- demo-client Phase 6 acceptance scenarios;
- safe machine-readable fault vectors;
- dedicated Phase 6 design/vector/security/static gates and CI routing.

Room remains version 3, walletlib remains 2.0.7, mainnet remains unavailable, and Phase 5 simulation remains diagnostic-only.

## Truthfulness invariant

An error code alone never proves injection. `injectedFaultId` records an intentional condition that was actually applied; `failureSource` records the actual terminal failure source. Therefore a delay may end SUCCESS/NONE or later FAILURE/OBSERVED_PROTOCOL while retaining `FAULT_DELAY_5S`.

## Remaining freeze requirement

Final live NORMAL-mode Devnet sign-and-send evidence, exact-head GitHub Actions success, clean worktree, and the Phase 6 freeze tag must be completed by the final freeze script. Phase 7 sanitized Markdown/JSON report export is **NOT STARTED**.
