# MWA Lab

**The on-device protocol debugger and deterministic failure simulator for Solana Mobile Wallet Adapter.**

Trace protocol sessions.<br>
Reproduce wallet failure paths.<br>
Inspect transactions.<br>
Export safe diagnostic reports.

> ⚠️ DEVNET-ONLY LAB TOOL — NEVER USE REAL FUNDS.

## Devnet vs MWA

**Devnet** tells you which Solana network a transaction is being tested on.

**Mobile Wallet Adapter (MWA)** defines how an Android dApp and wallet endpoint
authorize, negotiate capabilities, sign, submit, fail, and return protocol results.

MWA Lab is built to make failures at that protocol boundary visible,
reproducible, and fixable.

MWA Lab is not a production wallet and is not a replacement for Phantom,
Solflare, Seed Vault Wallet, or other production wallets.

Use MWA Lab for deterministic MWA protocol QA, then validate the final dApp
against real production wallets before release.

## Status

CLOCK IN hackathon implementation in progress.

Current baseline:

- Kotlin
- Jetpack Compose
- Android
- Devnet-only scope
- single `:app` Gradle module
- MWA integration not yet claimed complete

## Safety

MWA Lab must never:

- enable mainnet signing;
- import production wallet secrets;
- expose private keys or seeds;
- include raw authorization tokens in diagnostics;
- present injected failures as organically observed wallet failures.

## Build

```bash
./gradlew lint
./gradlew test
./gradlew assembleDebug
```

## Project documentation

See `docs/`.

## License

Apache-2.0.
