# MWA Lab

**The on-device protocol debugger and deterministic failure simulator for Solana Mobile Wallet Adapter.**

Trace protocol sessions. Reproduce wallet failure paths. Inspect transaction
behavior. Export sanitized evidence.

> ⚠️ **DEVNET-ONLY LAB TOOL — NEVER USE REAL FUNDS.**

## Why MWA Lab

Solana Devnet tells a developer **where** a transaction runs. It does not explain
what happened across the **Mobile Wallet Adapter (MWA)** boundary between a dApp
and a wallet endpoint.

MWA Lab makes that boundary visible and reproducible:

```text
Android dApp
   ↓
Local MWA association
   ↓
authorize / capabilities / signing / submission
   ↓
MWA Lab protocol timeline + transaction diagnostics
   ↓
deterministic fault injection
   ↓
sanitized report
```

Use MWA Lab to answer questions such as:

- Did the dApp actually reach a wallet-side MWA session?
- Which request failed?
- Was the failure observed or intentionally injected?
- Did the app handle `ERROR_NOT_SIGNED` correctly?
- What transaction metadata and simulation evidence were available?
- Can the failure be reproduced and shared without exporting secrets?

MWA Lab is **not** a production wallet and does not replace Phantom, Solflare,
Seed Vault Wallet, or another production wallet.

## Start in 30 seconds

Same Android device:

1. Install MWA Lab.
2. Open an MWA-enabled Solana Android dApp.
3. Tap **Connect Wallet**.
4. Choose **MWA Lab** if Android asks.
5. Review and approve the Devnet test connection.
6. Run the dApp action, then open MWA Lab to inspect the wallet-side protocol trace.

You do **not** need to open MWA Lab first. The dApp initiates the Local MWA
association.

The included `:demo-client` module is deterministic test infrastructure and is
labeled **MWA Lab Demo Client — FOR TESTING ONLY**.

## The core workflow

### 1. Trace a normal Local MWA session

Run the same-device dApp flow and inspect the persisted timeline for supported
MWA requests, timing, outcomes, protocol errors, and sanitized summaries.

### 2. Break the same flow intentionally

Open **Fault Lab**, select a deterministic scenario such as
`FAULT_SIGN_REJECT`, and repeat the same dApp action.

MWA Lab keeps the injected condition separate from the terminal protocol result,
so an intentional failure cannot be mistaken for an organically observed one.

### 3. Inspect transaction evidence

For supported legacy transactions, inspect bounded structured metadata,
recognized instructions, signer/account roles, simulation evidence, and
submission/confirmation behavior.

Simulation is diagnostic evidence only. A simulation PASS is not a submission
guarantee.

### 4. Export a sanitized report

Session Detail can produce deterministic Markdown, JSON, and clipboard-summary
views built from persisted structured evidence.

Secrets, raw authorization tokens, raw association tokens, private keys, seeds,
mnemonics, and raw transaction/message payloads are outside the report contract.

## What ships in `0.1.0-clockin`

```text
Local MWA                       VERIFIED / SHIPPED
Devnet-only authorization       SHIPPED
Explicit authorization consent  SHIPPED
Signing approval                SHIPPED
Persistent protocol timeline    SHIPPED
Transaction diagnostics         SHIPPED
Legacy simulation diagnostics   SHIPPED
Deterministic Fault Lab         SHIPPED
Sanitized report export         SHIPPED
Receive Test SOL                SHIPPED
Send Test SOL                   SHIPPED
Request Devnet SOL              SHIPPED
Remote MWA                      NOT RELEASED
Remote QR scanner               OMITTED
CAMERA permission               ABSENT
Mainnet / testnet               UNAVAILABLE / REJECTED
Production-wallet compatibility NOT VERIFIED
Identity Reset UI               NOT SHIPPED
```

The current signed competition candidate is **RC2**:

```text
file          MWA-Lab-v0.1.0-clockin-rc2.apk
applicationId dev.mwalab
versionCode   1
versionName   0.1.0-clockin
APK SHA-256   5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc
network       Solana Devnet only
```

See [Release](docs/RELEASE.md) and [Compatibility](docs/COMPATIBILITY.md).

## Test Wallet: support infrastructure, not the product

The disposable Test Wallet exists so a developer can exercise protocol and
transaction QA without importing a production wallet identity.

It supports:

```text
COPY ADDRESS
RECEIVE TEST SOL
REQUEST DEVNET SOL
SEND TEST SOL
```

The Receive QR encodes **only the disposable public Devnet address**. It is
explicitly **not an MWA connection QR**.

Direct Test Wallet actions are not fabricated as dApp→MWA protocol events.
They remain outside the protocol-session history.

Phase 12 final QA independently verified a finalized direct Devnet transfer:

```text
amount       0.001 SOL
fee          0.000005 SOL
status       finalized
mainnet path none
```

## Release truthfulness

This release intentionally does **not** claim:

- Remote MWA support;
- a camera-based Remote MWA scanner;
- mainnet or testnet operation;
- production-wallet compatibility;
- versioned-v0 signing;
- production custody;
- seed phrase import;
- private-key import.

Production-wallet compatibility remains **NOT VERIFIED**.

The current verified scope is same-device **Local MWA on Solana Devnet** using
the signed RC2 candidate.

## Developer value

MWA Lab is built for a recurring pre-release QA loop:

```text
connect
→ observe
→ inject a failure
→ reproduce
→ inspect
→ export evidence
→ fix the dApp
→ rerun
```

That loop is the product. The disposable Test Wallet, Demo Client, transaction
inspector, and report exporter exist to support it.

## Architecture

```text
:app
├── Android / Jetpack Compose UI
├── wallet-side MWA host
├── authorization + signing approval boundaries
├── protected disposable Devnet identity
├── fixed Solana Devnet RPC boundary
├── persistent Room protocol evidence
├── transaction diagnostics + simulation
├── deterministic fault engine
└── sanitized report renderer/export

:demo-client
└── deterministic cross-package MWA test dApp
```

Pinned MWA dependencies:

```text
mobile-wallet-adapter-walletlib  2.0.7
clientlib                        2.0.7
```

The production `:app` uses walletlib. Clientlib is used by the demo/test
boundary, not as application signing authority.

See [Architecture](docs/ARCHITECTURE.md) and
[Protocol Support](docs/PROTOCOL_SUPPORT.md).

## Security model

MWA Lab must never:

- enable mainnet signing/submission;
- import production wallet secrets;
- expose private keys, seeds, mnemonics, raw auth tokens, association tokens,
  raw transaction/message payloads, or raw signature material in diagnostics;
- sign without the required authorization and approval boundaries;
- present synthetic/injected failures as organically observed failures.

The stable release keystore remains outside Git.

See [Security](docs/SECURITY.md).

## Verification

The release is backed by deterministic JVM/static/CI gates plus connected-device
and live Devnet acceptance evidence.

Current hard-freeze authority:

```text
tag   phase11-hard-code-freeze-2026-10-06
HEAD  fabf28f92b1e3c3a46a2d2a5be1fe642395b0c24
CI    37411435425 — success
```

Phase 12 final QA additionally verified:

- exact signed RC2 install identity;
- manual first launch;
- dApp-first cold Local MWA launch;
- NORMAL Local MWA success;
- deterministic `FAULT_SIGN_REJECT` with `ERROR_NOT_SIGNED (-3)`;
- sanitized report sharing;
- Receive QR purpose and exact address payload;
- finalized Devnet `Send Test SOL`;
- protected production source unchanged from the hard freeze.

See [Testing](docs/TESTING.md), [Phase 11 evidence](docs/evidence/phase11/), and
the phase reports/evidence directories for detailed provenance.

## Demo

The canonical competition story is intentionally short:

```text
Devnet tests the chain. MWA Lab tests the wallet protocol.

normal Local MWA flow
→ persisted trace
→ FAULT_SIGN_REJECT
→ ERROR_NOT_SIGNED / INJECTED
→ sanitized report
→ brief Test Wallet utility
```

See [Demo](docs/DEMO.md) for the recording sequence.

## Competition

MWA Lab was built for **CLOCK IN — Solana Mobile Hackathon 2026**.

The competition build stays deliberately narrow: Android, Kotlin, Jetpack
Compose, Solana Devnet, Local MWA protocol QA, deterministic faults, diagnostics,
and evidence export.

See [Competition](docs/COMPETITION.md).

## Build

```bash
./gradlew lint
./gradlew test
./gradlew assembleDebug
./gradlew :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest
./scripts/phase11_static.sh
```

The signed competition APK is an operator artifact. CI verifies deterministic
buildability and freeze invariants; connected-device/live-network evidence is
recorded separately.

## Engineering history

The phase reports remain in the repository as implementation and verification
provenance:

```text
PHASE_1_REPORT.md   wallet-side MWA boundary
PHASE_2_REPORT.md   signing/submission hardening
PHASE_3_REPORT.md   persistent protocol debugger
PHASE_4_REPORT.md   capability + transaction diagnostics
PHASE_5_REPORT.md   simulation diagnostics
PHASE_6_REPORT.md   deterministic fault engine
PHASE_7_REPORT.md   sanitized diagnostic reports
PHASE_8_REPORT.md   UX + positioning
PHASE_9_REPORT.md   first-run + Test Wallet + Local MWA acceptance
PHASE_10_REPORT.md  release candidate + compatibility evidence
PHASE_11_REPORT.md  hard code freeze
```

Historical evidence is intentionally preserved rather than rewritten to match
the final judge-facing narrative.

## Phase 10 — Signed release candidate + compatibility evidence

Historical Phase 10 release-candidate provenance is retained for the frozen
static/CI chain. The RC1-r2 candidate is superseded by the current RC2 and is
not the current competition artifact. See `PHASE_10_REPORT.md` and
`docs/evidence/phase10/` for that historical checkpoint.

## License

Apache-2.0.
