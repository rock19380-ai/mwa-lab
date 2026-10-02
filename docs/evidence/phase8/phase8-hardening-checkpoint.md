# Phase 8 hardening checkpoint — Steps 8.14–8.21

Date: 2026-10-02.

This checkpoint closes the Phase 8 Prompt-2 hardening scope. It is **not** the Phase 8 freeze.

## Baseline and invariants

- Branch: `phase8-world-class-ux-positioning`.
- Frozen Phase 7 predecessor remains `d4fbe20ff2d4b63d6f531f2a9cd7d6fadbe8a7e1`.
- Room remains schema 3.
- walletlib remains 2.0.7.
- Devnet-only boundary remains fixed.
- Identity Reset remains deliberately unexposed.
- No Phase 8 freeze tag exists at this checkpoint.

## Completed hardening scope

- Step 8.14 — accessibility/layout-resilience audit, including light/dark and large-font checks.
- Step 8.15 — positioning audit.
- Step 8.16 — five-second Home comprehension audit.
- Step 8.17 — production-wallet status = **NOT VERIFIED IN THIS RELEASE** for the available emulator-only environment.
- Step 8.18 — screenshot/demo readiness audited; final canonical competition captures intentionally deferred until final live regression.
- Step 8.19 — Phase 8 design/static/security-continuation gate.
- Step 8.20 — phase-aware CI routing.
- Step 8.21 — presentation/accessibility test hardening plus complete local and connected regression.

## Recreation-test repair

The first full connected regression exposed a stale historical presentation assertion in:

`PersistentTransactionInspectorActivityInstrumentedTest.realNavigationAndActivityRecreationPreserveEventInspectorAndPinnedLabIdentity`

Phase 8 deliberately removed the redundant fixed `Session Detail` app-bar title. The first repair replaced that title assertion with header assertions, but the detail timeline can restore its pre-recreation LazyColumn scroll position, so header nodes are not guaranteed to be in the current viewport.

Production UI was not weakened or changed to satisfy the old test.

The final test contract verifies:

- `protocol-timeline` remains the active detail root after recreation;
- the test explicitly scrolls to and verifies `Back to sessions`;
- the test explicitly scrolls to and verifies the selected dApp identity;
- persisted Devnet transaction diagnostics remain available;
- the pinned `SOLANA DEVNET` evidence remains available;
- unrelated simulation/capability events remain absent as expected.

## Verification

- Targeted recreation regression: PASS.
- Full local offline Gradle gate: PASS.
- App JVM: `tests=287 failures=0 errors=0 skipped=0 xml_files=43`.
- Demo-client JVM: `tests=10 failures=0 errors=0 skipped=0 xml_files=4`.
- App connected: `tests=136 failures=0 errors=0 skipped=0 xml_files=1`.
- Demo-client connected: `tests=1 failures=0 errors=0 skipped=0 xml_files=1`.
- Device: `emulator-5554`.
- `./scripts/phase8_static.sh`: PASS.
- `git diff --check`: PASS.
- System font scale restored to `1.0`.

## Historical Phase 7 scanner note

The immutable historical Phase 7 security scanner freezes the old approval presentation directory and therefore rejects the intentional Phase 8 presentation-only `SigningApprovalScreen.kt` change if run directly.

Phase 8 does not rewrite that historical scanner. The Phase 8 continuation gate preserves protocol/signing/fault/simulation/storage authority and report/export security invariants while allowing the intended presentation-layer evolution.

## State

**Phase 8 Steps 8.14–8.21 = IMPLEMENTED / VERIFIED CHECKPOINT.**

**Phase 8 = OPEN / NOT FROZEN.**

Remaining work: Steps 8.22–8.28 — final full gate, canonical live NORMAL + `FAULT_SIGN_REJECT` regression, final security/documentation closeout, pre-freeze audit, exact-head CI, and annotated Phase 8 freeze tag.
