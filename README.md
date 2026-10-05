# MWA Lab

**The on-device protocol debugger and deterministic failure simulator for Solana Mobile Wallet Adapter.**

Trace protocol sessions.
Reproduce wallet failure paths.
Inspect signing/submission behavior.
Share sanitized diagnostic reports.

> ⚠️ DEVNET-ONLY LAB TOOL — NEVER USE REAL FUNDS.

## What MWA Lab is

**Devnet** defines which Solana network a transaction is tested on.

**Mobile Wallet Adapter (MWA)** defines how an Android dApp and wallet endpoint
discover each other, establish a session, authorize, negotiate capabilities,
request signing/submission, fail, and return protocol results.

MWA Lab is a developer-facing MWA endpoint built to make that protocol boundary
visible and reproducible. It is not a production wallet and is not a replacement
for Phantom, Solflare, Seed Vault Wallet, or another production wallet.

## Start in 30 seconds

1. Install MWA Lab and an MWA-enabled Android dApp on the same device.
2. Open the dApp and tap **Connect Wallet**.
3. Choose **MWA Lab** if Android asks.
4. Review and approve the Devnet test connection.
5. Run the dApp action, then open MWA Lab to inspect its protocol trace.

The dApp starts the Local MWA association; there is no server to start or
ordinary website URL to paste. The separate MWA Lab Demo Client is a test dApp,
not a production wallet.

**Receive Test SOL QR** contains only the disposable public Devnet address for
funding. A **Remote MWA QR** would connect a dApp to the protocol. Remote MWA
and its scanner are not released in the current Phase 10 RC, so the QR types
must not be interchanged.

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
provenance.

## Phase 4 capability and transaction diagnostics

Phase 4 adds session-scoped configured capability snapshots and a read-only
transaction diagnostic layer without changing the frozen signing/authorization
authorities. `MwaCapabilityProfile` remains the capability source of truth;
walletlib 2.0.7 still handles `get_capabilities` internally, so MWA Lab does not
fabricate a `GET_CAPABILITIES` timeline event.

For transaction signing requests, MWA Lab now records sanitized structured
diagnostics including transaction version, fee payer, signer/account privileges,
recent blockhash, program IDs, bounded instruction metadata, SHA-256 fingerprints,
and the verified decoder subset. The current decoder scope is System Program
Transfer, bounded Memo display, and the narrow SPL Token Transfer /
TransferChecked subset. Unknown semantics remain explicitly unknown.

Versioned v0 transactions are detected and represented as partial diagnostics
when lookup-table addresses are unresolved. This does **not** expand the signing
contract: `LegacyTransactionCodec` remains legacy-only signing authority and v0
signing remains rejected. Raw transaction payloads and raw unknown instruction
bytes are not persisted.

Phase 4 device acceptance exercised System Transfer, Unknown Program, and v0
authoritative rejection through the real cross-package path, then verified all
four diagnostic tables across force-stop/restart. See
[PHASE_4_REPORT.md](PHASE_4_REPORT.md) and
[Phase 4 evidence](docs/evidence/phase4/). At that Phase 4 checkpoint,
simulation was still Phase 5 work. Fault injection and report export were
outside that historical Phase 4 checkpoint.

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
./scripts/phase10_static.sh
```

GitHub Actions runs deterministic non-device gates and routes the current
Phase 10 checkout through the Phase 10 release/safety gate. Historical phase
gates remain preserved for their own checkouts. Connected-device and live Devnet
evidence are recorded separately from CI buildability.

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

## Phase 5 simulation diagnostics

Phase 5 adds **user-triggered Solana Devnet simulation for supported legacy
transactions** before signing. The simulator uses the existing fixed Devnet RPC
boundary with `simulateTransaction`, `encoding=base64`, `sigVerify=false`, and
`replaceRecentBlockhash=false`. It records bounded structured evidence such as
PASS/FAIL/UNAVAILABLE, failure source, context slot, compute units, safe error
classification, and redacted runtime-log structure.

**Simulation is diagnostic evidence only and does not guarantee later submission
or confirmation.** A simulation PASS never approves or signs a request. A
simulation FAIL or RPC_UNAVAILABLE result never rejects a parent MWA request or
disables APPROVE/REJECT. The existing approval, signing, submission, and
`ProtocolRecorder` paths remain authoritative.

Phase 5 persistence is Room schema 3. Simulation results are child diagnostics
attached only after the canonical parent protocol event is durable. Raw
transaction bytes remain transient; raw RPC bodies, signatures, authorization
tokens, association tokens, private keys, and seed material are not diagnostic
storage fields. Free-form RPC log content is redacted before public/durable
results while bounded runtime structure is retained.

Legacy simulation is the P0 scope. Versioned v0 transactions remain detectable
for diagnostics but are not simulatable/signable in this release. Fault injection
and report export were outside the historical Phase 5 checkpoint.

Real cross-package Devnet acceptance on Android 16 passed GOOD_PASS_APPROVE,
BAD_FAIL_APPROVE, and GOOD_PASS_REJECT after the user funded the exact installed
lab identity. All three sign-only scenarios reported zero submissions. Canonical
parent/transaction/simulation rows survived force-stop/relaunch unchanged, and
the persisted UI showed the rejected parent with its independent PASS child.
See `PHASE_5_REPORT.md` and `docs/evidence/phase5/phase5-live-devnet-acceptance.json`.
The earlier unfunded attempt remains recorded as historical evidence. Final
freeze provenance is recorded by the annotated tag only after exact-head CI
succeeds on the evidence commit.

<!-- PHASE6:SUMMARY:BEGIN -->
## Phase 6 — Deterministic Fault Engine

MWA Lab now supports deterministic fault profiles for authorization rejection, signing rejection, fixed delay, unsupported chain, invalid payload, payload-limit rejection, stale transaction, RPC unavailability, and submission failure. Fault selection is internal to MWA Lab and visible in Fault Lab. Persisted protocol events keep an applied `injectedFaultId` separate from the terminal `failureSource`, so synthetic conditions cannot be confused with observed failures.

At the Phase 6 freeze, sanitized report export was still pending. MWA Lab remains a Solana Devnet-only protocol debugger/test endpoint, not a production wallet.
<!-- PHASE6:SUMMARY:END -->

## Phase 7 — Sanitized Diagnostic Reports

Session Detail now builds a typed, read-only report from persisted Room evidence.
The same sanitized report produces deterministic Markdown, JSON, and a concise
clipboard summary. Share Markdown and Share JSON use app-private cache files, a
narrow FileProvider, and the Android Share Sheet. Open or changing sessions are
marked PARTIAL; injected fault markers remain separate from observed failure
sources. Simulation is diagnostic evidence only and does not guarantee later
submission success.

MWA Lab is a Devnet-only protocol debugger and failure simulator. It does not
import production wallet secrets, export raw payloads or authorization tokens,
or claim independent production-wallet compatibility. See
[PHASE_7_REPORT.md](PHASE_7_REPORT.md) and
[Phase 7 evidence](docs/evidence/phase7/).


## Phase 8 — World-class UX and positioning

Home now identifies MWA Lab as a Mobile Wallet Adapter protocol debugger and
deterministic failure simulator in its first viewport. Typed navigation reaches
Home, Sessions, Fault Lab, read-only Lab Identity, and safe Settings. Session
Detail leads with protocol outcome and keeps an applied fault ID independent
from failure source. The signing surface preserves explicit approval and the
Devnet/no-real-funds boundary; a final device audit corrected its status-bar
inset. Identity Reset remains unexposed.

The Phase 8 freeze candidate passed the final local and connected suites plus
canonical NORMAL and `FAULT_SIGN_REJECT` Devnet live scenarios. Markdown, JSON,
and Copy Summary were exercised from persisted sessions; real screenshots are
under [screenshots/phase8/](screenshots/phase8/). Production-wallet
compatibility remains **NOT VERIFIED IN THIS RELEASE**. Exact-head CI and the
annotated freeze tag are the final provenance authority; see
[PHASE_8_REPORT.md](PHASE_8_REPORT.md) and
[Phase 8 evidence](docs/evidence/phase8/).

## Phase 9 — First-run connection and Test Wallet UX (frozen)

Manual first launch now explains that MWA Lab is a Devnet-only protocol
debugger with a disposable test identity. An incoming same-device Local MWA
association still bypasses onboarding and reaches the wallet endpoint directly.
Normal supported authorization requires an explicit human Approve or Reject
decision; deterministic injected authorization rejection remains separate and
does not impersonate a user decision.

Home leads with same-device connection guidance and a visible Test Wallet.
The Test Wallet reads its public address and exact lamport balance through the
fixed Solana Devnet RPC boundary, supports explicit refresh, renders an offline
public-address QR for Receive Test SOL, and offers a conservative 0.5 Devnet SOL
airdrop request with submitted, confirmation, rate-limit, unavailable, and
unknown states. These wallet utilities do not create MWA protocol events.

Phase 9 transport-aware diagnostics use Room schema 4. Sessions persist only
coarse `LOCAL` / `REMOTE` association mode and bounded identity-verification
state; historical Phase 8 sessions migrate to `LOCAL` + `NOT_AVAILABLE`. Raw
association URIs, reflector identifiers/tokens, and transport key material are
not persisted as session metadata or diagnostic report fields.

The Test Wallet also includes **Send Test SOL** for native SOL on the fixed
Solana Devnet endpoint. It accepts one canonical Solana recipient and one
positive amount, keeps a conservative fee reserve, shows an explicit Devnet
review, signs only with the existing protected disposable identity, and treats
post-submission transport/confirmation ambiguity as **Submitted / confirmation
unknown** rather than success. Direct Test Wallet sends remain outside the MWA
protocol timeline.

Remote MWA release controls and a Remote QR scanner were not shipped in the
frozen Phase 9 checkpoint. Production-wallet compatibility remained **NOT VERIFIED**. See
[Phase 9 evidence](docs/evidence/phase9/) for the executed gates and current scope.


Phase 9 live acceptance passed after user manual Devnet funding of the installed
disposable Test Wallet. An explicitly opted-in 1-lamport direct Send confirmed
on Devnet without creating an MWA protocol session. The Demo Client's real
cross-package Local NORMAL memo sign-and-send required separate authorization
and signing approval taps, returned a verified signature, and confirmed on
Devnet. The independent injected `FAULT_SIGN_REJECT` path returned
`ERROR_NOT_SIGNED (-3)` without a signing tap or submission. Current-run Room
schema 4 evidence and the final persisted `NORMAL` fault are recorded in the
[Phase 9 report](PHASE_9_REPORT.md). No airdrop or faucet was used in the funded
continuation. Remote, scanner, Identity Reset, and production-wallet cuts above
remain in effect.

## Phase 10 — Signed release candidate + compatibility evidence

Production-wallet compatibility remains **NOT VERIFIED**.

Phase 10 does not add a new product feature. It converts the frozen product into
an auditable release candidate. The current signed candidate is
`MWA-Lab-v0.1.0-clockin-rc1-r2.apk` (`dev.mwalab`, versionCode `1`, versionName
`0.1.0-clockin`) with APK SHA-256
`0b17ccac5180d0bd6919f3f24c0c8a03efebebf9b42bd07f4909a2894e35bd21`.
The release keystore remains outside Git.

On a dedicated Android 16 / API 36 AVD, the signed candidate passed manual
first-run, dApp-first cold Local MWA authorization, `SIGN_MESSAGE_APPROVE`, a
finalized canonical Local `SIGN_AND_SEND_TRANSACTIONS`, deterministic
`FAULT_SIGN_REJECT` sign-and-send (`ERROR_NOT_SIGNED (-3)` / `INJECTED`, no
submission), restart persistence, Markdown/JSON/Copy Summary export, one safe
1-lamport direct **Send Test SOL**, and proof that direct Test Wallet actions do
not create MWA protocol history. Actual generated report bytes were inspected
and retained only sanitized structured evidence. No live airdrop was invoked;
the user manually funded the exact installed Devnet identity.

Current release truth:

```text
Local MWA                       VERIFIED / SHIPPED
Remote MWA                      BLOCKED / NOT RELEASED
Remote QR scanner               OMITTED
CAMERA permission               ABSENT
mainnet / testnet                UNAVAILABLE
production-wallet compatibility NOT_VERIFIED
```

See [Getting Started](docs/GETTING_STARTED.md), [Release](docs/RELEASE.md),
[Compatibility](docs/COMPATIBILITY.md), [Phase 10 report](PHASE_10_REPORT.md),
and [Phase 10 evidence](docs/evidence/phase10/). Final freeze authority is the
exact-head GitHub Actions success plus the annotated Phase 10 tag; those are
created only after the final candidate commit is immutable.
