# MWA Lab — Phase 9 live acceptance report

Status: **LIVE_ACCEPTED** (2026-10-04). The annotated freeze tag and exact-head CI run are the authority for frozen provenance.

Frozen predecessor: Phase 8 tag `phase8-world-class-ux-positioning-2026-10-03` at `7cb9c0da5839ee14f4ef5f45391449b5060e7ebc`. Phase 9 Batch 4 remains `4047dbece34715f3ab88f0be8dc401f7c0df429e`. Room schema is 4 with a forward-only 3-to-4 migration; walletlib/clientlib remain 2.0.7.

## Accepted paths

The installed protected disposable Test Wallet was verified as `B2AFEixuhw9g4rYVk7Wv4qv2XoNz44n5Geb6JaiWWvJG` before any live submission. User-provided Devnet funding gave a confirmed balance of **800,000,000 lamports** at slot `507231647`. No airdrop or faucet was called in this continuation. The live test harness now fails closed on insufficient manual funding and requires an explicit canonical recipient.

| Gate | Result |
|---|---|
| Send Test SOL deterministic safety tests | PASS |
| Direct Test Wallet 1-lamport System Program transfer | PASS, finalized at slot `507232918` |
| Direct Send MWA session count unchanged | PASS |
| Canonical NORMAL Local MWA memo sign-and-send | PASS, finalized at slot `507234746` |
| Explicit Local authorization and NORMAL signing approval | PASS |
| Injected Local `FAULT_SIGN_REJECT`, without signing approval | PASS, `ERROR_NOT_SIGNED (-3)` |
| Final persisted fault | NORMAL |
| Remote MWA | BLOCKED_HIDDEN |
| Remote QR scanner | OMITTED |
| Identity Reset | UNEXPOSED_OPTIONAL_P1 |
| Production-wallet compatibility | NOT_VERIFIED |

The direct send used preflight, a fresh blockhash, the protected Lab signer, and confirmed commitment. The canonical Demo Client path used a real cross-package Local association. The wallet required separate CONNECT DAPP and SIGNING APPROVAL taps; the Demo Client verified the returned signature and Devnet confirmation. The injected run required authorization approval, no signing tap, and no submission. Room schema 4 records the NORMAL session as `LOCAL / UNVERIFIED / SIGN_AND_SEND_TRANSACTIONS SUCCESS / NONE` and the injected session as `LOCAL / UNVERIFIED / FAILURE / -3 / INJECTED / FAULT_SIGN_REJECT`.

The first direct attempt was rejected by Devnet preflight with `-32002` against a generated recipient with no existing account. Its balance remained unchanged, so no transfer was accepted. The harness was repaired to require an explicit canonical recipient; the successful 1-lamport recipient was the already funded public Devnet account that sent the manual top-up. Early NORMAL UI automation attempts timed out while the real signing approval screen was visible and left cancelled, unsubmitted Room events. Walking the accessibility node tree repaired the opt-in test harness; the final run passed. These failed attempts are retained as evidence rather than counted as passes.

Remote API class presence is not Remote end-to-end evidence. No Remote release button, CAMERA permission, or scanner dependency ships. The Receive Test SOL QR is a public address, not an MWA connection QR. The internal Demo Client is not a production wallet.

Detailed transaction, Room, and audit receipts: [Phase 9 live and security evidence](docs/evidence/phase9/09-adversarial-audit/live-and-security.md) and [live closeout](docs/evidence/phase9/10-closeout/live-acceptance-closeout.md).

## Final ordinary fresh-AVD regression

A fresh disposable API-36 AVD exposed five viewport-sensitive Compose test
assertions. Stable onboarding semantics tags and explicit LazyColumn scrolling
repair only the testability/test-harness boundary; protocol, wallet, signing,
authorization, transport, Room, and live acceptance behavior are unchanged.

Final ordinary regression is green:

- affected tests: **5 / 5 PASS**
- app connected: **149 / 149 PASS**
- Demo Client connected: **1 / 1 PASS**
- failures/errors/skips: **0**
- opt-in live classes excluded from ordinary discovery
- no live transaction repeated
- Phase 9 static: PASS
- frozen Phase 8 static: PASS
