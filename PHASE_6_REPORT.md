# MWA Lab — Phase 6 Deterministic Fault Engine Report

Status at generation: **FREEZE CANDIDATE — Phase 6 implementation, local/device verification, and live NORMAL-mode Devnet regression complete; exact-head CI and tag are the remaining freeze actions.**

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

## Freeze evidence boundary

Live NORMAL-mode Devnet sign-and-send acceptance is recorded in `docs/evidence/phase6/phase6-live-normal-regression.md`. The final exact-head GitHub Actions run ID/URL is recorded by the annotated freeze tag and the external freeze receipt because recording that run in a new commit would itself change the exact HEAD. Phase 7 sanitized Markdown/JSON report export is **NOT STARTED**.
