# CLOCK IN — Competition Positioning

MWA Lab was built for **CLOCK IN — Solana Mobile Hackathon 2026**.

## One-line product identity

**MWA Lab is the on-device protocol debugger and deterministic failure simulator
for Solana Mobile Wallet Adapter.**

## The problem

A developer can test a transaction on Solana Devnet and still have weak
visibility into the wallet-protocol path that surrounded it.

Typical questions include:

- Did the Android dApp establish an MWA session?
- Did authorization fail before signing?
- Was a signing request rejected by the user or by an intentional test fault?
- Did submission fail before or after the transaction crossed the RPC boundary?
- Can a failure be reproduced deterministically?
- Can the evidence be shared without leaking secret material?

MWA Lab focuses on that protocol-debugging gap.

## Competition build

```text
platform             Android
language             Kotlin
UI                   Jetpack Compose
network              Solana Devnet only
MWA mode shipped     Local / same-device
wallet role          disposable developer test endpoint
production custody   no
mainnet              no
backend              no
AI chatbot           no
DeFi/NFT/staking     no
```

## Product loop

```text
connect
→ trace
→ inject a deterministic failure
→ reproduce
→ inspect
→ export sanitized evidence
→ fix the dApp
→ rerun
```

The engineering priority is to make MWA failures **visible, reproducible, and
fixable**.

## Judge-facing proof points

### Product / usefulness

MWA Lab provides a repeatable pre-release QA workflow for developers integrating
Mobile Wallet Adapter rather than acting as another consumer wallet.

The Test Wallet, Demo Client, transaction inspector, simulator, Fault Lab, and
report exporter support that workflow.

### Technical depth

The release includes:

- real wallet-side Local MWA association using pinned walletlib `2.0.7`;
- explicit authorization and signing approval boundaries;
- persistent structured protocol evidence;
- transaction diagnostics and bounded legacy decoding;
- user-triggered Devnet simulation diagnostics;
- deterministic fault profiles with injected-vs-observed provenance;
- sanitized Markdown / JSON / clipboard report projections;
- fixed Devnet RPC authority;
- protected disposable test identity;
- signed RC2 release verification and exact-head freeze provenance.

### UX / clarity

A first-time developer can:

1. install MWA Lab;
2. open an MWA-enabled Android dApp;
3. tap Connect Wallet;
4. choose MWA Lab;
5. approve the Devnet test connection;
6. run the dApp action;
7. inspect the resulting session.

The app also separates:

```text
Receive Test SOL QR
= fund the disposable Devnet address

Remote MWA QR
= protocol association concept
= NOT SHIPPED in this release
```

### Failure reproduction

The canonical competition failure story uses `FAULT_SIGN_REJECT`.

The same dApp action that succeeds in NORMAL mode can be rerun with a
deterministic signing rejection and produce:

```text
ERROR_NOT_SIGNED (-3)
Failure source: INJECTED
Injected fault: FAULT_SIGN_REJECT
```

This is the central differentiation: the failure is deliberate, attributable,
persisted, and reportable.

### Evidence / presentation

The signed RC2 has evidence for:

- clean manual first run;
- dApp-first cold Local MWA launch;
- funded NORMAL Local MWA success;
- deterministic injected signing rejection;
- report sharing and sanitization;
- Receive Test SOL address QR;
- direct finalized Send Test SOL;
- final `NORMAL` state;
- protected production source freeze.

## Claims intentionally not made

This release does **not** claim:

- Remote MWA support;
- Remote QR scanner support;
- CAMERA permission;
- production-wallet compatibility;
- mainnet or testnet operation;
- versioned-v0 signing;
- production custody;
- seed/private-key import.

The internal Demo Client is test infrastructure and is not independent
production-wallet compatibility evidence.

## Current signed competition candidate

```text
file          MWA-Lab-v0.1.0-clockin-rc2.apk
applicationId dev.mwalab
versionCode   1
versionName   0.1.0-clockin
APK SHA-256   5f167fa59814fe478f0ea35e616a0d08d8fb606402c5998b40cfce5f134ebcbc
network       Solana Devnet only
```

Hard-freeze authority:

```text
tag   phase11-hard-code-freeze-2026-10-06
HEAD  fabf28f92b1e3c3a46a2d2a5be1fe642395b0c24
CI    37411435425 — success
```

## Submission checklist

Before final submission:

```text
APK final and checksum recorded
GitHub public and judge-readable
demo video final and accessible
deck final and accessible
screenshots final
manual first-run proof complete
dApp-first cold Local MWA proof complete
NORMAL Local MWA proof complete
FAULT_SIGN_REJECT proof complete
sanitized report proof complete
Send / Receive safety proof complete
QR purpose unambiguous
Remote MWA represented truthfully
production-wallet compatibility represented truthfully
registration / country fields reviewed
all submitted links opened from a logged-out/private browser
```

After submitting, retain:

```text
confirmation screenshot
submission ID, if present
submission timestamp
final APK SHA-256
final Git commit
final Git tag
```
