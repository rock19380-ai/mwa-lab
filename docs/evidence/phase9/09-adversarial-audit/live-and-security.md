# Phase 9 pre-freeze live and security audit

Date: 2026-10-04. Device: `emulator-5554`, Android 16 / API 36.

## Funding and live acceptance

The installed disposable Test Wallet public address was
`FnYk3SiU9aUqg5wdPuDPDxWn9GX4fPL9NS7Ru2aPNUZs`.

The interrupted Batch 5 opt-in live Send test stopped before transfer
construction or submission. Its in-app Devnet airdrop returned JSON-RPC
`-32603`; the test reported `LIVE_DEVNET_FUNDING_REQUIRED`. This is a funding
failure, not evidence of a transfer, signer, Room, or MWA failure.

At approximately 2026-10-04 03:02 UTC, a direct confirmed-commitment
`getBalance` call to `https://api.devnet.solana.com` returned **0 lamports**
(slot `507218545`). A bounded official `requestAirdrop` for 1,000,000 lamports
returned RPC `429`: "You've either reached your airdrop limit today or the
airdrop faucet has run dry." A second, smaller 100,000-lamport request returned
the same RPC `429`. No further faucet requests were made.

Consequently:

| Gate | Result |
|---|---|
| Test Wallet deterministic Send tests | PASS in interrupted Batch 5 |
| Opt-in live 1-lamport Test Wallet Send | BLOCKED_EXTERNAL_FUNDING |
| Canonical NORMAL Local sign-and-send | BLOCKED_EXTERNAL_FUNDING |
| Phase 9 fully live accepted | NO |

The final connected-suite run uninstalled the wallet package; the separate
Demo Client gate reinstalled it and a new protected disposable identity was
created. The **currently installed** public Test Wallet address is
`B2AFEixuhw9g4rYVk7Wv4qv2XoNz44n5Geb6JaiWWvJG`. A fixed Devnet
confirmed-commitment `getBalance` returned **0 lamports** at slot
`507225904`. No further faucet request was made after the two bounded
failures above.

The minimum manual top-up for the current opt-in live Send test is **100,000
Devnet lamports (0.0001 Devnet SOL)** to the **currently installed** address
above. This is testnet currency only. Recheck that address before funding and
do not uninstall the app before the live gates. After funding, rerun both
opt-in submissions and verify confirmation plus Room evidence before
considering a freeze tag.

## Fund-independent injected Local acceptance

With persisted fault `FAULT_SIGN_REJECT`, the opt-in Demo Client instrumentation
`injectedSignRejectReturnsExpectedProtocolErrorWithoutSigningApproval` passed
over a real cross-package Local MWA association. The test tapped the explicit
authorization approval. It did not tap signing approval. The client received
`ERROR_NOT_SIGNED` before submission.

Read-only inspection of app-private Room `mwa_lab.db` plus WAL found current-run
session `43ab015f-5cd6-4b60-8eed-3c10b8e9af3d`:

```text
association_mode=LOCAL
identity_verification_state=UNVERIFIED
method=SIGN_AND_SEND_TRANSACTIONS
outcome=FAILURE
protocol_error_code=-3 (ERROR_NOT_SIGNED)
failure_source=INJECTED
injected_fault_id=FAULT_SIGN_REJECT
```

The temporary database snapshot was removed. The app-private fault preference
was then inspected and confirmed as `NORMAL`. There is no current-run NORMAL
success row because funding prevented that scenario.

## Source and evidence audit

Current source, Room schema 4, reports, README, Phase 9 evidence, manifest,
dependency catalog, and the Batch 5 diff were searched for private-key/seed,
mnemonic, authorization/association token, raw Remote URI, reflector ID/token,
association public key, and encryption/ciphertext surfaces. Reviewed matches
were protected signing internals, transient client token use, negative safety
assertions, redaction rules, or explanatory copy. No new raw Remote secret
column or report field was found. This search cannot prove the absence of
unknown secret values in every runtime artifact.

The Receive Test SOL QR encoder takes the public address only. Direct Test
Wallet Send uses its wallet service, not the MWA protocol recorder. The fixed
submission endpoint is Devnet; no editable RPC or mainnet submission path was
found. No Remote release button, camera permission, or scanner dependency is
present. Production-wallet compatibility was not tested.
