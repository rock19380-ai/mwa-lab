# Phase 5 evidence

These files document local implementation checks, the historical funding blocker,
and the subsequently completed real Devnet acceptance. They contain no private keys, seeds, raw authorization or
association tokens, raw transaction payloads, raw signatures, or raw RPC
request/response bodies.

- `phase5-step5.0-baseline.json` and the protected-source hashes establish the
  frozen Phase 4 predecessor.
- The Phase 5 prefreeze local logs/JSON and `phase5-closeout-local-gates.json`
  record design, vector, static, lint/JVM/build, and connected checks.
- `phase5-ci-36671496660-failure.txt` contains selected original CI log lines
  proving the shallow-checkout ancestry failure.
- `phase5-ci-36693030227-exact-head.json` records successful CI on the
  checkout-depth repair; `phase5-ci-36698868948-exact-head.json` records
  successful CI on the later settlement race repair.
- `phase5-settlement-close-race.txt` records the failed-before/fixed-after
  child-evidence teardown race.
- `phase5-security-scan.txt`, `phase5-protected-sources-final.json`,
  `phase5-prefreeze-hostile-audit-summary.json`, and
  `phase5-database-secret-scan.txt` record local static, hostile, and
  disposable Room SQLite/WAL/SHM verification. The database scan has a safe
  fingerprint positive control; it is not a live-request DB receipt.
- `phase5-device-parser.txt` records the deterministic fail-closed UI parser
  check and its real-device funding-prerequisite confirmation.
- `phase5-device-acceptance-blocked.json` records the real cross-package
  Devnet attempt that stopped before simulation because the lab identity had
  zero lamports against a 5,000-lamport fee. It is not a PASS or FAIL simulation
  receipt.

`test-vectors/simulation/` contains deterministic synthetic fixtures. Those
fixtures and controlled RPC-unavailable tests must never be presented as live
Devnet results. Real PASS, runtime FAIL, parent/child binding and restart acceptance subsequently
passed. The final tag is permitted only after exact-head CI for the evidence
commit and the final invariant audit.

The device runner is intentionally evidence-limited: successful UI and demo
wire checks alone produce `UI_ONLY_PENDING_DATABASE_AND_RESTART_VERIFICATION`,
not a complete Phase 5 acceptance receipt. Its parser self-test is part of the
current Phase 5 static gate.

## Subsequent funded live acceptance

- `phase5-live-devnet-acceptance.json` is the independent canonical database,
  process-restart and persisted-UI receipt for all three funded live scenarios.
- `phase5-device-acceptance.json` remains the runner's preliminary UI/wire receipt;
  its limited status is intentional, and the independent receipt completes it.
- `phase5-device-{scenario}-{simulation,parent}.{xml,png}` shows real simulation
  classification and the independent dApp wire response, with zero submissions.
- `phase5-device-restart-*.{xml,png}` shows the actual persisted parent,
  transaction diagnostic, child simulation and expanded sanitized logs.
- `phase5-live-audit-method.md` explains independent binding/restart checks, the
  actual DB/WAL/SHM byte scan and its limits. No raw DB files were retained.
- `phase5-live-security-scan.txt` and `phase5-live-local-gates.json` record the
  post-acceptance deterministic checks and clearly distinguish retained suites.
- `phase5-ci-36700891045-exact-head.json` records the successful starting checkpoint
  CI. The final evidence commit requires its own CI, recorded in the subsequent
  annotated tag/final closeout receipt rather than a circular self-reference.

Historical blocked-funding and earlier test receipts remain unchanged. Funding
of the historical address did not fund the then-current installed identity;
the exact current payer was verified and funded before the successful scenarios.
