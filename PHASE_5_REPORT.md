# MWA Lab — Phase 5 simulation diagnostic closeout checkpoint

**Status: implementation and required live acceptance VERIFIED.** The earlier
funding blocker is resolved. Real cross-package PASS-approve, runtime FAIL-approve,
PASS-reject, canonical child binding and restart checks passed on 2026-09-30.
This continuation changes only documentation and sanitized evidence.
Freeze remains conditional on successful CI for this final evidence commit,
a clean synchronized branch and the final invariant audit. Its exact CI run and
freeze SHA belong in the annotated tag/final closeout receipt, which are produced
after the commit exists; they are not fabricated as pre-commit facts here.

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

## Historical funding blocker

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

At that checkpoint GOOD_PASS_APPROVE was **BLOCKED before simulation**;
BAD_FAIL_APPROVE, GOOD_PASS_REJECT and restart were **NOT RUN**. A live
simulation PASS, runtime FAIL, parent wire outcome and live zero-submission
result were **not proven** by that historical attempt. The controlled
RPC-unavailable case and FAIL-approve/PASS-reject authority behavior pass
instrumentation, but those fixtures are not live Devnet receipts.

## Funded live acceptance (2026-09-30)

Continuation began from clean local/remote
`fc131ae7ef7a876cce85994233cffae68511e067`. Exact-head
[CI 36700891045](https://github.com/rock19380-ai/mwa-lab/actions/runs/36700891045)
was reverified completed/success, including lint, JVM tests, debug/AndroidTest
APKs, APK checks and the Phase 5 static gate.

The historical address was confirmed funded, but the installed wallet's current
public identity differed. Real authorization and Home independently showed
`5amADFiYY9UsNQnwoATm3W3f4YCCDqJnbhZvbqoK5EWV`; it survived a normal
force-stop/relaunch unchanged. After resolving a case-sensitive funding-address
mismatch, the user funded that exact payer. Confirmed balance was
**1,000,000,000 lamports at slot 505877125**. Fresh fee quotes for both existing
transaction templates were **5,000 lamports**; every actual demo request also
obtained a fresh blockhash, fee and balance through its existing RPC path.
No faucet request or product/validation change was made in this continuation.

The existing debug APKs ran on `emulator-5554`, `MWA_Lab_API_36`, Android 16 /
API 36, through the actual cross-package MWA local association. The acceptance
runner triggered SIMULATE and then an explicit APPROVE or REJECT action.

| Scenario | Simulation child | Canonical parent | Slot | Units | Safe logs | Submissions reported |
| --- | --- | --- | --- | --- | --- | --- |
| GOOD_PASS_APPROVE | PASS / NONE | SUCCESS / NONE, signature verified | 505877227 | 14864 | 4 | 0 |
| BAD_FAIL_APPROVE | FAIL / SIMULATION, instruction 0 InvalidInstructionData | SUCCESS / NONE, signature verified | 505877329 | 150 | 2 | 0 |
| GOOD_PASS_REJECT | PASS / NONE | FAILURE / OBSERVED_PROTOCOL, ERROR_NOT_SIGNED (-3) | 505877434 | 14864 | 4 | 0 |

APPROVE and REJECT remained enabled after simulation; PASS remained on the
approval screen until the explicit decision. PASS warnings were visible. The
bad transaction used the existing structurally valid unknown System Program
opcode, producing a real runtime failure without product fault injection.
The fixed production gateway mapped err=null to PASS and the safe runtime error
to FAIL; raw RPC bodies were not captured as evidence.

The demo's zero-submission field is corroborated by the audited sign_transactions
path and canonical method timeline (AUTHORIZE, SIGN_TRANSACTIONS, DEAUTHORIZE).
It is not a packet-capture counter. Balance increased during the run because
further funding arrived; balance change is not used as submission proof.

`phase5-device-acceptance.json` remains explicitly UI/wire-only. The independent
`phase5-live-devnet-acceptance.json` records exact session/event/request/fingerprint
bindings, payload index 0 and attempt 1, safe classifications, slots, units,
ordered-log hashes and canonical outcomes. All sessions ended normally after
deauthorization, exercising completed child settlement during real session close.

## Live restart and persistence security

Read-only SQLite checks verified canonical parent/transaction/simulation joins,
foreign keys, schema 3 and integrity. Force-stop ended the process; relaunch
created a different process. All three sessions' records remained identical,
including identities, fingerprints, payload/attempt ordering and parent/child
classification. The last session's persisted UI showed the rejected parent,
legacy diagnostic and independent PASS/NONE child with matching fingerprint,
expandable redacted logs and disclaimer. Screenshots/XML are under the
`phase5-device-restart-*` names.

The actual `mwa_lab.db` (98,304 bytes), WAL (420,272 bytes) and SHM (32,768 bytes)
were scanned in memory. All three fingerprints were found as positive controls.
Exact reconstructed unsigned payloads and signed-message bodies, checked raw/
hex/base64 forms, memo text, raw RPC envelope markers and synthetic secret
sentinels were absent. No DB snapshot or raw payload was saved in evidence.
Real individual signatures/tokens/protected keys were not extracted for literal
matching; schema/source controls and existing instrumented sentinel tests cover
that boundary. See `phase5-live-audit-method.md` for scope and limitations.

One temporary host audit query produced non-JSON output during relaunch; a strict
repeat passed all comparisons. A UI assertion initially checked a field below
the viewport; scrolling that actual field into view completed the check. These
were audit-command/viewport issues; no product code or acceptance invariant was
changed. The historical blocked evidence remains unchanged.

## Final closeout gates

Design, deterministic vectors, the device parser self-test, static security,
Phase 5 static and diff checks are rerun for the new evidence. The unchanged
product retains the previously verified 245 app + 9 demo JVM tests, 109 app + 1
demo connected tests, lint and APK builds. Controlled RPC_UNAVAILABLE evidence
remains instrumentation-based: RPC_NETWORK leaves APPROVE/REJECT independent.
Phase 6 and Phase 7 remain NOT STARTED. Final evidence-only CI must match the
new exact HEAD before the annotated Phase 5 freeze tag is created.
