# MWA Lab

**The on-device protocol debugger and deterministic failure simulator for Solana Mobile Wallet Adapter.**

Trace protocol sessions.
Reproduce wallet failure paths.
Inspect signing/submission behavior.
Build toward safe, shareable diagnostics.

> ⚠️ DEVNET-ONLY LAB TOOL — NEVER USE REAL FUNDS.

## What MWA Lab is

**Devnet** defines which Solana network a transaction is tested on.

**Mobile Wallet Adapter (MWA)** defines how an Android dApp and wallet endpoint
discover each other, establish a session, authorize, negotiate capabilities,
request signing/submission, fail, and return protocol results.

MWA Lab is a developer-facing MWA endpoint built to make that protocol boundary
visible and reproducible. It is not a production wallet and is not a replacement
for Phantom, Solflare, Seed Vault Wallet, or another production wallet.

## Phase 2 verified boundary

Phase 2 extends the frozen Phase 1 association/authorization foundation and is
verified against pinned `mobile-wallet-adapter-walletlib:2.0.7` /
`clientlib:2.0.7`.

Verified behavior includes:

- real cross-package `solana-wallet://` discovery and local association;
- persistent protected Devnet Lab identity;
- first authorization and valid cross-session reauthorization;
- `get_capabilities`;
- deauthorization plus rejected revoked authorization/token reuse;
- explicit user approval for signing;
- bounded `sign_messages` with Ed25519 signatures;
- bounded legacy `sign_transactions`;
- legacy transaction parsing/signature-slot patching with immutable approval binding;
- fixed Solana Devnet RPC authority;
- `sign_and_send_transactions` submission and commitment handling;
- defined rejection/error mappings including `ERROR_NOT_SIGNED`,
  `ERROR_AUTHORIZATION_FAILED`, invalid payload/too-many-payload responses, and
  `ERROR_NOT_SUBMITTED`;
- deterministic hostile RPC/submission/lifecycle tests;
- sanitized structured protocol evidence.

The deterministic test client lives in `:demo-client` and is explicitly labeled:

```text
MWA Lab Demo Client
FOR TESTING ONLY
```

Phase 2 live acceptance exercised both:

```text
dApp
→ MWA Lab
→ authorize / reauthorize
→ approve
→ sign
→ Devnet result
```

and:

```text
dApp
→ MWA Lab
→ deliberate user rejection
→ defined MWA error
```

## Current Phase 2 capability envelope

```text
network                         Solana Devnet only
max signing payloads/request    10
message signing                 supported with explicit approval
transaction signing             legacy transactions only
max transaction wire size       1232 bytes
sign_and_send                    Devnet only
RPC endpoint                     https://api.devnet.solana.com
SIWS                             not advertised
versioned transactions           rejected in Phase 2
mainnet / testnet                rejected
```

Pinned walletlib 2.0.7 requires `sign_transactions` to be advertised as an
optional feature for that method to be callable; Phase 2 advertises exactly that
verified feature and no SIWS feature.

## Phase 3 persistent debugger

Home, Sessions, and Session Detail read structured Room history through
repository Flows and ViewModels. Inspect request-start sequence, timestamps,
duration, outcomes, protocol error numbers/names, failure sources, and bounded
sanitized request/response summaries. Successful and failed sessions remain
available after app restart. Open rows mean recorded-open history with unknown
connection liveness.

The persistent recorder covers actual host callbacks for AUTHORIZE, REAUTHORIZE,
DEAUTHORIZE, SIGN_MESSAGES, SIGN_TRANSACTIONS, and SIGN_AND_SEND_TRANSACTIONS.
GET_CAPABILITIES is **NOT OBSERVABLE THROUGH PINNED WALLETLIB**; configured
capabilities never create an event. Compatibility evidence stores remain only
for predecessor tests.

See [PHASE_3_REPORT.md](PHASE_3_REPORT.md) and
[Phase 3 evidence](docs/evidence/phase3/) for executed acceptance and freeze
provenance. Phase 4 is not started: capability snapshots, transaction inspector,
simulation, fault engine, and report export remain deferred.

## Historical Phase 2 exclusions

Phase 2 does **not** include the Phase 3 product recorder/timeline:

- no Room/SQLite session history;
- no restart-surviving protocol timeline;
- no full diagnostic report export bundle;
- no deterministic fault engine;
- no production-wallet secret import;
- no mainnet signing/submission.

Those later product layers must extend, not weaken, the verified Phase 2
protocol boundary.

## Build and deterministic verification

```bash
./gradlew lint
./gradlew test
./gradlew assembleDebug
./gradlew :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest
./scripts/phase1_static.sh
./scripts/phase3_static.sh
```

GitHub Actions runs deterministic non-device gates. Real Android association,
interactive approval, and restart/UI acceptance are recorded separately under
`docs/evidence/phase3/`. Historical live Devnet evidence remains in
`docs/evidence/phase2/`; it is not relabelled as a fresh Phase 3 network run.
The historical `phase2_static.sh` remains unchanged and retains its intentional
pre-Room scope assertion. The Phase 3 gate verifies its hash and 28 unchanged
predecessor source boundaries, alongside current behavioral regressions.

## Modules

```text
:app          MWA Lab wallet-side protocol endpoint
:demo-client  deterministic cross-package test client
```

The production `:app` uses walletlib; clientlib is used by the demo/test
boundary, not as application signing authority.

## Safety invariants

MWA Lab must never:

- enable mainnet signing/submission;
- import production wallet secrets;
- expose private keys, seeds, mnemonics, raw auth tokens, association tokens,
  raw transaction/message payloads, or raw signature material in diagnostics;
- sign without active authorization plus explicit approval;
- present synthetic/injected failures as organically observed wallet failures.

## Phase 2 report and evidence

See:

- `PHASE_2_REPORT.md`
- `docs/PROTOCOL_SUPPORT.md`
- `docs/ARCHITECTURE.md`
- `docs/SECURITY.md`
- `docs/TESTING.md`
- `docs/evidence/phase2/`

Final freeze commit/tag/remote-CI identity is recorded by the Phase 2 final
closeout/freeze evidence rather than self-referenced inside this report.

## License

Apache-2.0.
