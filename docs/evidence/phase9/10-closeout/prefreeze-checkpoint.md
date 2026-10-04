# Phase 9 pre-freeze checkpoint

Date: 2026-10-04. Branch: `phase9-first-run-connection-ux`.

Historical checkpoint: this funding block was resolved by the later [live acceptance closeout](live-acceptance-closeout.md). The statements below describe the earlier pre-freeze state only.

The work resumed from committed Batch 4 HEAD
`4047dbece34715f3ab88f0be8dc401f7c0df429e` with intentional uncommitted
Batch 5 live tests, current-source static gate, and CI routing. The Batch 5
changes were preserved. Its deterministic compilation, targeted JVM, full app
connected, Demo Client connected, lint, test, and APK-assembly gates had passed
before the Devnet funding stop. See the interrupted operator run log outside
the repository and the Phase 9 audit receipt for the exact external RPC result.

The Local injected rejection passed independently and persisted expected Room
provenance. The active fault was restored to `NORMAL`. Remote remains
`BLOCKED_HIDDEN`; scanner is `OMITTED`; Identity Reset is
`UNEXPOSED_OPTIONAL_P1`; production-wallet compatibility is `NOT_VERIFIED`.

The Phase 9 plan requires a real confirmed Devnet Send Test SOL when the Send
feature is shipped, plus the canonical NORMAL live Local sign-and-send. Both
remain `BLOCKED_EXTERNAL_FUNDING`. No Phase 9 freeze tag may be created from
this checkpoint. Exact final source commit and CI run, if obtained, belong in
the operator closeout receipt rather than a self-referential source commit.


## Ordinary connected-suite discovery repair

The first final connected rerun exposed an Android test-runner reporting defect:
Gradle reported task success, but XML counted the live tests' opt-in
`AssumptionViolatedException` as failures (app 1, Demo Client 2). Those tests
had no live side effects. Both module Gradle configurations now pass
`notClass` to the ordinary connected runner for their single opt-in live class.
The live test sources, assertions, and direct `adb am instrument -e class ...
-e mwa_phase9_live 1` path remain intact. The Phase 9 static gate asserts both
the exclusion and the in-test opt-in guards. Final connected XML must be checked
for zero failures after this repair.


The first combined app-plus-Demo connected invocation after the discovery
repair yielded app XML 149 tests / 0 failures. The Demo case timed out because
the app connected task had uninstalled the wallet APK; `pm path dev.mwalab`
confirmed its absence. This was a combined-task lifecycle/order issue. Running
the specified Demo Client task separately reinstalled the wallet APK and passed.
Final ordinary connected XML totals: app **149 tests, 0 failures/errors/skips**;
Demo Client **1 test, 0 failures/errors/skips**. The opt-in live classes are
absent from these ordinary suites and retain their explicit manual runner path.


## Current installed Test Wallet identity after connected suites

The app connected task removed the wallet package and its disposable identity;
the separate Demo Client task reinstalled it. A read-only check of the new
app-private **public key only** derived address
`B2AFEixuhw9g4rYVk7Wv4qv2XoNz44n5Geb6JaiWWvJG`. The fixed Devnet RPC
returned 0 lamports at slot `507225904`. The earlier
`FnYk3SiU9aUqg5wdPuDPDxWn9GX4fPL9NS7Ru2aPNUZs` address belongs to the
interrupted live run and is no longer the manual funding target for this
installed app instance. Recheck the address before any top-up; do not uninstall
the app between funding and live acceptance.
