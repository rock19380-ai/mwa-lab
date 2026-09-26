# MWA Lab

**The on-device protocol debugger and deterministic failure simulator for Solana Mobile Wallet Adapter.**

Trace protocol sessions.<br>
Reproduce wallet failure paths.<br>
Inspect protocol behavior.<br>
Build toward safe diagnostic reports.

> ⚠️ DEVNET-ONLY LAB TOOL — NEVER USE REAL FUNDS.

## Devnet vs MWA

**Devnet** tells you which Solana network a transaction is being tested on.

**Mobile Wallet Adapter (MWA)** defines how an Android dApp and wallet endpoint
discover each other, establish a session, authorize, negotiate capabilities,
request signing behavior, fail, and return protocol results.

MWA Lab is built to make that protocol boundary visible and reproducible.

MWA Lab is not a production wallet and is not a replacement for Phantom,
Solflare, Seed Vault Wallet, or other production wallets.

## Phase 1 verified boundary

Phase 1 proves the wallet-side protocol boundary against pinned
`mobile-wallet-adapter-walletlib:2.0.7`.

Verified on Android emulator/device evidence:

- real `solana-wallet://` discovery/association;
- wallet-side session establishment and teardown;
- persistent protected Devnet Lab identity;
- first Devnet authorization;
- mainnet and missing-chain authorization rejection;
- walletlib-managed authorization state;
- `get_capabilities`;
- deauthorization;
- rejected reuse of revoked authorization state;
- typed sanitized protocol evidence;
- deterministic cross-package demo client.

The deterministic test client lives in `:demo-client` and is explicitly labeled:

```text
MWA Lab Demo Client
FOR TESTING ONLY
```

It repeats the canonical Phase 1 sequence:

```text
CONNECT
→ AUTHORIZE
→ GET_CAPABILITIES
→ DEAUTHORIZE
→ CLOSE
```

## Deliberately not implemented in Phase 1

Phase 1 does **not** perform successful message signing, transaction signing,
transaction submission, Devnet RPC submission, production-wallet secret import,
or mainnet behavior.

Pinned MWA 2.0 defines some signing methods as mandatory protocol surface.
MWA Lab implements those callbacks fail-closed in Phase 1; successful signing
behavior belongs to Phase 2.

## Build

Deterministic CI gates:

```bash
./gradlew lint
./gradlew test
./gradlew assembleDebug
./gradlew :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest
./scripts/phase1_static.sh
```

Device-level association proof is intentionally separate from GitHub Actions and
is recorded under `docs/evidence/phase1/`.

## Modules

```text
:app          MWA Lab wallet-side protocol endpoint
:demo-client  deterministic cross-package Phase 1 test client
```

## Safety

MWA Lab must never:

- enable mainnet signing/submission;
- import production wallet secrets;
- expose private keys or seeds;
- include raw authorization tokens in diagnostics;
- present synthetic failures as organically observed wallet failures.

## Project documentation

See `docs/`.

## License

Apache-2.0.
