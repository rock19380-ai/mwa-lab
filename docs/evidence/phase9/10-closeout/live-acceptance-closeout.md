# Phase 9 live acceptance closeout

Date: 2026-10-04. Device: `emulator-5554`, Android 16 / API 36. Branch: `phase9-first-run-connection-ux`.

The installed protected Test Wallet public identity was verified as `B2AFEixuhw9g4rYVk7Wv4qv2XoNz44n5Geb6JaiWWvJG` before submission. User manual Devnet funding produced a confirmed balance of 800,000,000 lamports at slot 507231647. This continuation made **no requestAirdrop or faucet call**.

- Direct native SOL send: 1 lamport, System Program transfer, preflight enabled, confirmed and later finalized at slot 507232918; public signature `5eeswxUrvCkZeveYc5tYQ52Q6kenXMNjLCHtnzJVyQomWtFbZbzMpigaN7yfH5e3fZ9sY3JmbQ4KofXni3UefxhX`. The recipient was an existing funded Devnet System account. MWA session count did not change.
- Canonical NORMAL Local MWA: real cross-package association, explicit CONNECT DAPP and SIGNING APPROVAL taps, verified returned signature, memo submission confirmed and later finalized at slot 507234746; public signature `h6PrjH49yxWYpu5BiqvrqfeM6d1pcMGQNEWZmM2avrix6auhoo95HBcRZ81HTvq3CwjdxqwoggrJ7xXZeFmMMBS`.
- Current NORMAL Room session `1d4c66af-e7e1-4292-8ab6-017029951cc2`: `LOCAL / UNVERIFIED`; `AUTHORIZE SUCCESS`; `SIGN_AND_SEND_TRANSACTIONS SUCCESS / NONE / null injected fault`; `DEAUTHORIZE SUCCESS`.
- Current injected Room session `061865bb-0bb2-4a02-852f-4506cb0785b9`: `LOCAL / UNVERIFIED`; `AUTHORIZE SUCCESS`; `SIGN_AND_SEND_TRANSACTIONS FAILURE / ERROR_NOT_SIGNED (-3) / INJECTED / FAULT_SIGN_REJECT`; `DEAUTHORIZE SUCCESS`. The test made no signing approval tap and no submission.
- Final persisted fault: `NORMAL`. Confirmed wallet balance after both successful submissions: 799,989,999 lamports at slot 507235435.

A first direct attempt failed preflight with RPC `-32002` against an uncreated generated recipient; confirmed wallet balance stayed unchanged. Three earlier NORMAL UI-harness attempts timed out despite a visible signing approval screen and left cancelled, unsubmitted Room events. The final opt-in harness requires an explicit canonical recipient and uses accessibility node traversal for the signing approval control. No production signing or protocol behavior was weakened.

Remote remains `BLOCKED_HIDDEN`; scanner `OMITTED`; Identity Reset `UNEXPOSED_OPTIONAL_P1`; production-wallet compatibility `NOT_VERIFIED`. This receipt records live acceptance; exact-head CI and annotated tag provenance must be verified separately before calling the phase frozen.

## Final fresh-AVD ordinary regression

A fresh disposable API-36 AVD originally exposed five viewport-sensitive
Compose assertions. The product/protocol path did not fail. The repair added
stable `onboarding-list` / `enter-lab` semantics tags and made the affected
tests explicitly scroll existing LazyColumn roots before asserting
below-the-fold content.

The first repair-resume wrapper then stopped because one combined
method-qualified instrumentation selector produced only one XML test result;
that selected test itself passed. The final gate therefore ran the five
method-qualified selectors independently.

Final evidence:

- five previously failing fresh-AVD tests: **5 / 5 PASS**
- ordinary app connected suite: **149 / 149 PASS**
- ordinary Demo Client connected suite: **1 / 1 PASS**
- failures/errors/skips: **0**
- opt-in live acceptance classes: absent from ordinary discovery
- no live Devnet transaction repeated during final regression
- JVM tests: PASS
- lint: PASS
- debug + AndroidTest APK assembly: PASS
- Phase 9 current-source static: PASS
- frozen Phase 8 static from detached tag: PASS
