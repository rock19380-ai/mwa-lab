# Phase 9 Batch 2 — First-run and Test Wallet P0

Date: 2026-10-03 (Asia/Yangon)

## Provenance

- Frozen Phase 8 predecessor: `7cb9c0da5839ee14f4ef5f45391449b5060e7ebc`
- Frozen tag: `phase8-world-class-ux-positioning-2026-10-03`
- Phase 9 branch: `phase9-first-run-connection-ux`
- Batch 1 predecessor: `a7ceb49c72e42ddffd9e0d22ec6095e258b8abcc`
- Walletlib/clientlib: pinned `2.0.7`
- Room schema at this checkpoint: `3`

## Delivered

- Manual first launch identifies MWA Lab as an MWA Protocol Debugger, states Devnet-only and no-real-funds safety, explains the disposable Test Wallet, and gives same-device connection steps.
- Incoming Local MWA associations continue to enter the wallet endpoint directly and are not blocked behind onboarding.
- Home prioritizes product purpose, same-device connection guidance, Test Wallet state, active fault state, and recent session.
- The Test Wallet exposes only its public Devnet address and exact integer-lamport balance through the existing fixed Devnet RPC authority.
- Balance states distinguish loading, available, timeout, I/O, HTTP, rate limit, RPC error, and malformed response outcomes.
- Explicit refresh runs on relevant screen/lifecycle entry without a polling loop.
- Receive Test SOL provides copy plus an offline address QR and clearly states that it is a Devnet public-address QR, not an MWA connection QR.
- Request Devnet SOL requests a bounded 0.5 SOL airdrop and distinguishes requesting, submitted, confirmed, rate limited, unavailable, failed, and unknown confirmation.
- Airdrop confirmation refreshes the displayed balance. Faucet failure leaves the debugger usable and presents address/CLI/faucet fallback guidance.
- Wallet utilities do not create protocol sessions or protocol timeline events.
- Remote release controls remain hidden.

## Repairs found by the gates

1. Legacy UI tests used a global single-node `DEVNET ONLY` selector after Phase 9 introduced two truthful labels. Stable Home semantics tags now scope those assertions while both safety messages remain visible.
2. RPC integer parsing no longer converts a generic JSON `Number` through `Double`; fractional and out-of-range lamport values fail closed.
3. The Demo Client connected gate now installs the current MWA Lab debug APK as a task dependency. This makes the exact cross-package gate independent of app-test uninstall cleanup.

## Dependency delta

No dependency was added or upgraded. The address QR encoder is local, deterministic, dependency-free code and works offline. Walletlib remains `2.0.7`.

## Evidence boundaries

- QR correctness is covered against a fixed reference matrix plus invalid and oversized payload cases.
- Devnet RPC behavior is exercised with deterministic mocked transports on device; the gate does not claim live faucet availability.
- The airdrop UI never labels a submission as confirmed before commitment evidence.
- No Send Test SOL surface is shipped at this checkpoint.
- No Remote MWA input, transport, or scanner surface is shipped at this checkpoint.
- Production-wallet compatibility remains NOT VERIFIED.

## Green gates

| Command | Result |
|---|---|
| `./gradlew :app:compileDebugAndroidTestKotlin --offline` | PASS |
| targeted legacy Home/Fault/transaction UI classes | PASS |
| `Phase9FirstRunWalletUiInstrumentedTest` | PASS |
| targeted wallet/QR/Home/simulation JVM tests | PASS |
| `SolanaDevnetRpcGatewayInstrumentedTest` | PASS |
| `./gradlew :app:connectedDebugAndroidTest --offline` | PASS; 146 tests |
| `./gradlew :demo-client:connectedDebugAndroidTest --offline` | PASS; real cross-package Local MWA, two runs |
| `./gradlew lint test assembleDebug :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest --offline` | PASS |
| frozen-tag detached-worktree `./scripts/phase8_static.sh` | PASS |
| `git diff --check` | PASS |

## Sanitization review

A source/evidence search was reviewed for private-key, seed, mnemonic, authorization-token, association-secret, reflector-secret, and encryption-key terms. Matches are existing signing internals, transient client protocol use, negative canaries, redaction policy/tests, and explicit safety copy. Batch 2 adds no secret persistence, logging, report field, screenshot evidence, or protocol event.

## Status

**Batch 2 COMPLETE / GREEN.**

Next: transport-aware evidence and forward-only Room schema `3 -> 4` migration.
