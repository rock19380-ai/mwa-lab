# Phase 9 live and security audit

Date: 2026-10-04. Device: `emulator-5554`, Android 16 / API 36.

## Funding and live acceptance

The prior pre-freeze run used a different disposable identity, `FnYk3SiU9aUqg5wdPuDPDxWn9GX4fPL9NS7Ru2aPNUZs`. Its in-app Devnet airdrop returned JSON-RPC `-32603`; two bounded official RPC attempts returned `429`. It submitted no transaction. Ordinary connected testing then reinstalled the wallet and rotated the identity. That historical address was not used in the resumed live acceptance.

Before the resumed run, the installed protected identity was verified as `B2AFEixuhw9g4rYVk7Wv4qv2XoNz44n5Geb6JaiWWvJG`. A confirmed-commitment balance query against the product's fixed `https://api.devnet.solana.com` endpoint returned **800,000,000 lamports** at slot `507231647`, supplied by a user manual Devnet transfer. **No airdrop or faucet call was made in this continuation.** The opt-in live test harness was changed to fail closed below 100,000 lamports and to require an explicit canonical recipient.

| Gate | Result |
|---|---|
| Deterministic Test Wallet Send safety tests | PASS |
| Direct 1-lamport Test Wallet Send | PASS; confirmed, later finalized at slot `507232918` |
| Direct Send MWA session count unchanged | PASS |
| Canonical NORMAL Local MWA sign-and-send | PASS; confirmed, later finalized at slot `507234746` |
| Injected Local `FAULT_SIGN_REJECT` | PASS; `ERROR_NOT_SIGNED (-3)` before signing approval |
| Final persisted fault | NORMAL |

Public direct transaction signature: `5eeswxUrvCkZeveYc5tYQ52Q6kenXMNjLCHtnzJVyQomWtFbZbzMpigaN7yfH5e3fZ9sY3JmbQ4KofXni3UefxhX`. Devnet `getTransaction` returned `err=null`, a 1-lamport System Program transfer from the current Test Wallet to an already funded System account, and a 5,000-lamport fee. The product test required confirmed commitment and equal MWA session counts before and after.

Public canonical memo signature: `h6PrjH49yxWYpu5BiqvrqfeM6d1pcMGQNEWZmM2avrix6auhoo95HBcRZ81HTvq3CwjdxqwoggrJ7xXZeFmMMBS`. Devnet `getTransaction` returned `err=null`, the expected memo, and a 5,000-lamport fee. The cross-package Demo Client test tapped real CONNECT DAPP and SIGNING APPROVAL controls, checked the returned signature cryptographically, and required confirmed submission.

The first direct attempt was definitively rejected at preflight with RPC `-32002` because its generated 1-lamport recipient had no account; confirmed wallet balance stayed unchanged. The test harness now requires an explicit canonical recipient and never calls `requestAirdrop`. Three earlier NORMAL UI automation runs reached a visible real signing approval screen but failed to find its accessibility text; each ended with a cancelled, unsubmitted Room event. Test-only node traversal repaired the lookup. These attempts are recorded as failures, not passes.

## Read-only Room evidence

A temporary read-only snapshot of app-private `mwa_lab.db` plus WAL was queried only for coarse session and protocol columns. It reported schema version 4.

- NORMAL session `1d4c66af-e7e1-4292-8ab6-017029951cc2`: `association_mode=LOCAL`, `identity_verification_state=UNVERIFIED`, `AUTHORIZE=SUCCESS`, `SIGN_AND_SEND_TRANSACTIONS=SUCCESS`, `failure_source=NONE`, `injected_fault_id=null`, `DEAUTHORIZE=SUCCESS`.
- Injected session `061865bb-0bb2-4a02-852f-4506cb0785b9`: `association_mode=LOCAL`, `identity_verification_state=UNVERIFIED`, `AUTHORIZE=SUCCESS`, `SIGN_AND_SEND_TRANSACTIONS=FAILURE`, `protocol_error_code=-3`, `failure_source=INJECTED`, `injected_fault_id=FAULT_SIGN_REJECT`, `DEAUTHORIZE=SUCCESS`. No signing approval tap or submission occurred.
- Earlier cancelled UI-harness sessions were `322b618f-006e-4790-9e21-55434cc9f2dd`, `bf35d60f-77c2-4063-a00f-f1293e6ad003`, and `c83d81ea-c74b-4b4c-baf5-2dd32a10c773`; none is counted as a NORMAL pass.

The temporary snapshot was removed. The app-private fault preference was restored and verified as `NORMAL`. A confirmed balance after the successful submissions was **799,989,999 lamports** at slot `507235435`.

## Source and evidence audit

Current source, Room schema 4, reports, README, Phase 9 evidence, manifest, and dependency catalog were searched for private-key/seed, mnemonic, authorization/association token, raw Remote URI, reflector ID/token, association public key, and encryption/ciphertext surfaces. Reviewed matches were protected signing internals, transient client token use, negative safety assertions, redaction rules, or explanatory copy. No new raw Remote secret column or report field was found. This search cannot prove the absence of unknown secret values in every runtime artifact.

The Receive Test SOL QR encoder takes the public address only and is explicitly not an MWA connection QR. Direct Test Wallet Send uses its wallet service, not the MWA protocol recorder. The fixed submission endpoint is Devnet; no editable RPC or mainnet submission path was found. No Remote release button, CAMERA permission, or scanner dependency is present. Remote remains `BLOCKED_HIDDEN` without end-to-end evidence; scanner `OMITTED`; Identity Reset `UNEXPOSED_OPTIONAL_P1`; production-wallet compatibility `NOT_VERIFIED`.
