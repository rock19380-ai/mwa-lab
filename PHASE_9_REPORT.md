# MWA Lab — Phase 9 pre-freeze report

Status: **BLOCKED_EXTERNAL_FUNDING / PRE-FREEZE** (2026-10-04).

Frozen predecessor: Phase 8 tag
`phase8-world-class-ux-positioning-2026-10-03`, commit
`7cb9c0da5839ee14f4ef5f45391449b5060e7ebc`. Phase 9 Batch 4 committed
checkpoint: `4047dbece34715f3ab88f0be8dc401f7c0df429e`.
walletlib/clientlib remain `2.0.7`; Room schema is `4` with a forward-only
3-to-4 migration.

## Implemented and deterministically verified

- Demo Client is labeled as a test dApp; manual first run and Home explain
  same-device Local MWA and the disposable Devnet Test Wallet.
- Supported normal authorization requires explicit human Approve/Reject;
  injected authorization faults remain deterministic. Normal signing still
  requires explicit approval.
- Test Wallet shows public address/balance, public-address-only Receive QR,
  Devnet airdrop status, and a reviewed native SOL Send path using the protected
  signer and fixed Devnet RPC. Direct wallet actions stay outside MWA history.
- Room schema 4 persists coarse `association_mode` and
  `identity_verification_state`; reports retain sanitized coarse metadata.
- Batch 5 main/unit/android-test Kotlin compilation, targeted JVM tests,
  lint/test/debug APK/AndroidTest APK assembly, and Gradle connected tasks
  passed before live funding stopped the run. A later XML review found that
  Android counted the opt-in guards as failures; ordinary test discovery was
  repaired without changing the live assertions. Final separate connected
  receipts are app 149/0 and Demo Client 1/0 (tests/failures).
- The current-source Phase 9 static gate passed. The injected live Local MWA
  `FAULT_SIGN_REJECT` path passed independently, including persisted
  `ERROR_NOT_SIGNED (-3) / INJECTED / FAULT_SIGN_REJECT` Room evidence.

## Release decisions and limits

| Item | Status |
|---|---|
| Send Test SOL deterministic safety gates | PASS |
| Send Test SOL live confirmation | BLOCKED_EXTERNAL_FUNDING |
| Canonical NORMAL Local sign-and-send | BLOCKED_EXTERNAL_FUNDING |
| Injected Local `FAULT_SIGN_REJECT` | PASS |
| Remote MWA | BLOCKED_HIDDEN |
| Remote QR scanner | OMITTED |
| Identity Reset | UNEXPOSED_OPTIONAL_P1 |
| Production-wallet compatibility | NOT_VERIFIED |

The interrupted live run used
`FnYk3SiU9aUqg5wdPuDPDxWn9GX4fPL9NS7Ru2aPNUZs` at 0 lamports. Its in-app
airdrop returned RPC `-32603`; two later bounded official Devnet RPC attempts
returned `429`. No live transaction was submitted. Final connected testing
uninstalled/reinstalled the disposable wallet app, rotating its local identity.
The **currently installed** Test Wallet address is
`B2AFEixuhw9g4rYVk7Wv4qv2XoNz44n5Geb6JaiWWvJG`; confirmed Devnet
`getBalance` at slot `507225904` returned **0 lamports**. A manual top-up of
at least **100,000 Devnet lamports (0.0001 Devnet SOL)** to this current public
address is required to resume the opt-in live gates. Recheck the address before
funding and do not uninstall the app between funding and live acceptance.
Use Devnet only.

No Remote end-to-end reflector/counterparty evidence exists; API class presence
does not make Remote shippable. No Remote button, camera permission, or scanner
dependency is included. The internal Demo Client is not a production wallet.

Phase 9 is not fully live accepted. Exact-head CI and an annotated Phase 9
freeze tag remain pending; the tag must not be created until both live
submission gates and all remaining mandatory gates truly pass.

Detailed receipts: [Phase 9 evidence](docs/evidence/phase9/).
