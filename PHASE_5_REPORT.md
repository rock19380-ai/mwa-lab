# MWA Lab — Phase 5 simulation diagnostic closeout checkpoint

**Status: NOT FROZEN.** Local implementation checks and exact-head implementation
CI pass. Required real cross-package Devnet PASS/FAIL and persistence/restart
acceptance are blocked by the unfunded lab identity. No Phase 5 freeze tag exists.
The exact-head CI result for this documentation/test-harness commit can only be
recorded after that commit exists; its run ID belongs in the external closeout
receipt and final response, not as a fabricated pre-commit fact here.

## Baseline and implementation

Phase 5 descends from frozen Phase 4 commit
`6028375636251eb3071f0a5240a2ad1efc78131e`. Historical Phase 3/4 static
scripts, Room schema exports 1/2, and `MIGRATION_1_2` remain unchanged. Room is
exactly schema 3 with additive `MIGRATION_2_3` and no historical backfill.

Supported legacy transactions have user-triggered, diagnostic-only Devnet
simulation. The fixed RPC request uses `simulateTransaction`, base64,
`sigVerify=false`, and `replaceRecentBlockhash=false`. Safe child evidence
includes PASS/FAIL/UNAVAILABLE, failure source, allowlisted error category,
slot, compute units, duration, and bounded/redacted program-log structure.
Transient target bytes are bound to session, event, request, payload index,
fingerprint, and generation; the Room child is inserted only after the real
parent `ProtocolEvent` is durable.

`ApprovalCoordinator`, the existing signing service, fixed Devnet submission,
and `ProtocolRecorder` remain authoritative. Simulation never approves,
rejects, signs, submits, or completes a request. PASS does not guarantee later
submission or confirmation. FAIL and UNAVAILABLE do not disable APPROVE/REJECT
or rewrite the parent event. No synthetic `ProtocolMethod.SIMULATE` exists.
Mainnet and v0 signing remain unavailable. Phase 6 fault injection and Phase 7
report/Share Sheet export have not started.

## CI failure and narrow repair

Run [36671496660](https://github.com/rock19380-ai/mwa-lab/actions/runs/36671496660)
failed on implementation SHA `cf7b0bc37be65b0bab8300143699b53ff2d11eef`.
Its checkout fetched only one commit (`fetch-depth: 1`), so the Phase 5 static
ancestry assertion could not resolve frozen Phase 4 and Git reported
`Not a valid commit name`. Lint, JVM tests, APK builds, APK checks, and Phase 1
had passed before that assertion. The selected original log lines are in
`docs/evidence/phase5/phase5-ci-36671496660-failure.txt`.

Commit `ad0b584980a8322705070d20de59c978021cd47a` changed CI checkout to
`fetch-depth: 0` and made the Phase 5 gate require that setting. The ancestry
assertion itself was retained. The gate passed in a clean full-history local
clone. Exact-head [run 36693030227](https://github.com/rock19380-ai/mwa-lab/actions/runs/36693030227)
completed successfully on that SHA, including lint, unit tests, both APK builds,
APK checks, Phase 1, and the Phase 5 static gate. Historical Phase 2/3/4 gates
were skipped by schema-aware routing.

## Session-close child settlement repair

A new deterministic regression test reproduced a diagnostic-loss race: after an
attempt had completed and the canonical parent was durable, immediate session
close cancelled the scheduled child write. Commit
`7d7f709ebeea42fa454c9009557311f4a291fe28` keeps only already accepted,
finished attempts alive for their bounded parent-then-child write while rejecting
all late callbacks, including a callback that looked up the context before the
close lock. It does not change the parent result, signing path, or MWA response.
The test failed on the previous implementation and the settlement suite passed
after the repair. The exact test receipt is
`docs/evidence/phase5/phase5-settlement-close-race.txt`.
Exact-head [run 36698868948](https://github.com/rock19380-ai/mwa-lab/actions/runs/36698868948)
completed successfully on the repair SHA, including lint, unit tests, both APK
builds, APK checks, Phase 1, and the Phase 5 gate.

## Local regression and security evidence

At the repaired implementation checkpoint:

| Check | Result |
| --- | --- |
| Phase 5 design, deterministic vectors, security scan, static gate | PASS |
| App JVM tests | 245, zero failures |
| Demo-client JVM tests | 9, zero failures |
| App connected tests on Android 16 emulator | 109, zero failures |
| Demo-client connected tests | 1, zero failures |
| Room simulation migration/byte-scan targeted connected tests | 5, zero failures |
| Host approval/signing/simulation targeted connected tests | 13, zero failures |
| Gradle lint, debug APKs, AndroidTest APKs | PASS |
| `git diff --check` | PASS |

The schema-3 disposable Room test inspected the database bytes and any WAL/SHM files present. It found
a stored SHA-256 fingerprint as a positive control and did not find synthetic
raw transaction, signature, auth/association token, private key/seed, or raw
RPC-body sentinels. This is a realistic persistence test, not a completed-live-
request database scan. Static security and protected-source hashes passed; see
`docs/evidence/phase5/phase5-security-scan.txt`,
`phase5-database-secret-scan.txt`, and `phase5-protected-sources-final.json`.
The targeted hostile/authority cases are recorded in
`phase5-prefreeze-hostile-audit-summary.json`.

## Device and live Devnet status

The Android 16 emulator `emulator-5554` ran the installed wallet and demo
client through the real cross-package MWA association. The GOOD_PASS_APPROVE
runner authorized the lab identity and fetched a fresh Devnet blockhash, then
stopped before the signing approval/SIMULATE step: public lab address
`9xioY2tZrmkgSdZXts9Ybqb7HWEwf7Ss48G3JvKuBXfm` had **0 lamports** and
its fee quote was **5,000 lamports**. A Devnet-only faucet request returned
JSON-RPC `-32603`; a smaller retry returned `429` (airdrop limit or empty
faucet). The blocked-attempt receipt is
`docs/evidence/phase5/phase5-device-acceptance-blocked.json`.
A repeat attempt with the same identity and fee failed promptly with the
same explicit funding prerequisite after the device runner was hardened. The
runner now labels even successful visible-UI checks as preliminary until
canonical parent/child database binding and restart are verified; its parser
self-test runs in the Phase 5 static gate.

GOOD_PASS_APPROVE: **BLOCKED before simulation**. BAD_FAIL_APPROVE and
GOOD_PASS_REJECT: **NOT RUN**. Completed-request persistence/restart: **NOT
RUN**. A live simulation PASS, live runtime FAIL, parent wire outcome, and live
`submittedTransactions=0` are **not proven** by this attempt. The controlled
RPC-unavailable case and FAIL-approve/PASS-reject authority behavior pass
instrumentation, but those fixtures are not live Devnet receipts.

The next prerequisite is at least 5,000 Devnet lamports on the same authorized
lab identity. After funding, rerun the real device scenarios, inspect canonical
parent/transaction/simulation child rows before and after force-stop/relaunch,
repeat the security scan, then obtain exact-head CI for the final closeout
commit. Only then may the Phase 5 freeze tag be considered.
