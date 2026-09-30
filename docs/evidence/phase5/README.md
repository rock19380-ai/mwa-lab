# Phase 5 evidence

These files document local implementation checks and the open live-acceptance
prerequisite. They contain no private keys, seeds, raw authorization or
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
Devnet results. The Phase 5 freeze tag remains uncreated pending real PASS,
runtime FAIL, parent/child and restart acceptance.

The device runner is intentionally evidence-limited: successful UI and demo
wire checks alone produce `UI_ONLY_PENDING_DATABASE_AND_RESTART_VERIFICATION`,
not a complete Phase 5 acceptance receipt. Its parser self-test is part of the
current Phase 5 static gate.
