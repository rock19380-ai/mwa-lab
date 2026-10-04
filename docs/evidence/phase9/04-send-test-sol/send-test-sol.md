# Phase 9 — Send Test SOL

Date: 2026-10-04

## Scope

Send Test SOL is intentionally narrow:

- Solana Devnet only;
- native SOL only;
- one recipient;
- one positive decimal amount with at most nine decimal places;
- legacy System Program transfer;
- existing protected disposable Test Wallet signing authority only;
- no SPL-token send UI;
- no contacts/address book;
- no editable RPC;
- no mainnet path;
- no MWA protocol-event fabrication.

## Safety semantics

Preparation requires a canonical 32-byte base58 recipient, integer-safe
SOL-to-lamport conversion, the authoritative Test Wallet identity, current
Devnet balance, and transfer amount plus a conservative fee reserve.

Confirmation requires an explicit review showing From, To, Amount, Solana
Devnet, and test-funds-only language.

The transaction uses a fresh confirmed Devnet blockhash, canonical legacy
System Program transfer construction, the existing Lab signing service,
preflight-enabled `sendTransaction`, and confirmed commitment polling.

Once a signed transaction crosses the submission transport boundary,
timeout/IO/malformed response, signature mismatch, false confirmation, or
confirmation transport failure is represented as
**Submitted / confirmation unknown**, never confirmed success and never an
automatic retry signal.

## Gate repairs

V1 stopped on a false-positive static check: `HomeViewModel` already used
`SessionRepository` for recent-session history before Batch 4. The Send delta
did not add protocol/session-repository coupling. The corrected safety audit
checks `TestSolTransfer.kt` directly and only Batch-4-added `HomeViewModel`
lines for forbidden coupling.

V2 then reached the full app connected suite. The sole failure was a new
instrumentation test calling `compose.setContent` twice on one
`ComponentActivity`, which Compose explicitly forbids. V3 splits the input and
review assertions into independent tests. Product Send behavior is unchanged.

## Verification

- main/unit/android-test Kotlin compilation: PASS
- repaired Phase 9 Send UI instrumentation: PASS
- targeted Send/Test Wallet/legacy-codec JVM tests: PASS
- full app connected regression: PASS
- Demo Client cross-package Local MWA regression: PASS
- full lint/JVM/APK gate: PASS
- frozen Phase 8 static gate from detached worktree: PASS
- `git diff --check`: PASS
- dependency delta: NONE

## Live-network boundary

This deterministic gate does not spend faucet funds and does not auto-submit a
live transfer. Live Devnet Send acceptance remains a pre-freeze acceptance
item; no live-network success is fabricated by this receipt.

## Status

Implementation and deterministic safety gates: **GREEN**.

Live Devnet transfer smoke: **NOT YET EXECUTED BY THIS AUTOMATED GATE**.
