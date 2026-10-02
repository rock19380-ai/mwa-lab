# Phase 7 sanitized diagnostic reports: design freeze

Baseline: Phase 6 commit `f5eaec53d911fc2423efdb3051e8316ce9e1032d` and its annotated tag. Phase 7.0–7.10 froze the canonical domain, snapshot, sanitization, renderers, and bounds. The Phase 7.11–7.21 export boundary is specified below.

## Authority and sources

Every exported diagnosis must be derivable from structured persisted protocol/session state. No export surface may scrape UI text or serialize arbitrary runtime objects. `SessionRepository.getSession`, `CapabilitySnapshotRepository.getSnapshot`, `TransactionDiagnosticRepository.getForEvent`, and `SimulationRepository.getForEvent` are read-only sources. Reports are derived artifacts; there is no report table. Room schema 3, migrations 1→2 and 2→3, walletlib 2.0.7, Devnet-only execution, and Phase 6 authorization, signing, parser, simulation, fault and error authorities remain frozen.

## Canonical report v1

`DiagnosticReport` has format `mwa-lab-diagnostic-report`, version `1`, generated epoch milliseconds, application and environment, one session, optional capability snapshot, ordered events, and warnings. The typed event contains independent outcome, protocol error code, failure source, and injected fault ID; an active fault marker never determines failure source. Transactions and simulation attempts are child diagnostic evidence. A simulation PASS is diagnostic evidence only, never a future submission guarantee. Unknown instruction semantics remain `UNKNOWN`.

Completeness is `COMPLETE` only for a closed session whose two bounded full reads agree. Open sessions and unstable reads are `PARTIAL` with a warning. The assembler retries at most three snapshot pairs; it does not mutate records. It orders events by sequence, transactions by payload index, simulations by payload index then attempt number, summary entries by key, capability lists and warnings lexically. `generatedAt` comes from an injected clock and does not affect the consistency comparison.

## Export sanitization allowlist

The export policy copies validated app-generated IDs, bounded safe display label, fixed Devnet cluster, numeric timing/counts, enum names, known fault IDs, known summary keys with typed value validation, known capability values, canonical SHA-256 fingerprints, bounded wire lengths, verified public program IDs, parser-provided decoded labels and exact amounts, and simulation classification/compute units. Simulation logs are projected again through the existing structural `SimulationLimits.publicLogLine` policy.

Private keys, seeds and mnemonics, authorization and association tokens/secrets, encryption material, raw transactions and messages, raw signatures, arbitrary wallet requests, raw RPC bodies, and exception or stack trace text are forbidden. Unknown summary keys and invalid values are omitted with an explicit truncation/omission warning. No post-hoc regex of an unsafe object dump is the primary safety mechanism.

## Renderer and bounds contract

Markdown and JSON take the same immutable canonical report. Markdown uses fixed sections, LF newlines, UTF-8 and escaped untrusted inline text. It shows UTC alongside epoch timestamps and a reproduction-context section derived only from recorded method, dApp, cluster, outcome and fault marker; it explicitly says exact request bytes cannot be reconstructed from the sanitized report. JSON uses fixed snake-case keys and stable ordering; no renderer reads a repository. The report records `truncated=true` and a warning when any collection or string is omitted or shortened. Limits: 64 events, 10 transactions per event and 40 per report, 20 simulation attempts per event and 80 per report, 8 instructions per transaction, 24 summary fields per map, 128 characters per free-text field, 16 log lines of at most 128 characters, and 1 MiB per rendered output. Renderer byte limits fail closed if a caller constructs an oversized report outside the assembler.

File sharing uses only internal cache artifacts and a cache-scoped non-exported FileProvider with temporary read grants. That layer has no protocol or database authority.

## Verification

JVM tests cover normal/failed/injected/delayed outcomes, observed later failures, capabilities, known and unknown transaction semantics, all simulation classifications, multiple children, open and empty sessions, ordering, escaping, Unicode/control characters, hostile summary fields, bounds and renderer parity. The design checker and baseline hash manifest guard frozen decisions and predecessor source files. Full `test`, `lint`, `assembleDebug`, both AndroidTest APK builds, connected device checks where available, and the Phase 7 static/security gate are required before freeze. The annotated freeze tag requires exact-head GitHub Actions success.

## Phase 7.11–7.21 Android export boundary

The Markdown reproduction section and clipboard summary focus the first failed method when one exists; otherwise they focus the last method other than DEAUTHORIZE, falling back to the last event for an authorization-only session. The application use case rereads repositories through the snapshot assembler for each export or copy action. The UI owns only action state and platform delivery. It never serializes report data or queries diagnostic child repositories. An action is cancelled on session selection change, and delivered effects carry the session ID so stale effects cannot share another session's artifact.

The cache writer renders a validated canonical report to UTF-8 bytes capped at 1 MiB, writes a temporary file under `cacheDir/diagnostic_reports/`, flushes and syncs it, then renames it to a generated `mwa-lab-<safe token>-<timestamp>.md|json` artifact. A UUID is used when already canonical; otherwise a SHA-256 prefix is used. No user string becomes a directory component. Cleanup selects only generated names and limits deletion work per write. Cache artifacts have no authority after the persisted repositories change.

The sole FileProvider has authority `${applicationId}.diagnosticreports`, `exported=false`, `grantUriPermissions=true`, and one `cache-path` mapping for `diagnostic_reports/`. The Share Sheet uses one `ACTION_SEND` content URI with temporary read permission, text/plain for Markdown or application/json for JSON. The Intent contains no report body or raw wallet data. Clipboard text comes from `DiagnosticReportSummaryRenderer`, which validates the same canonical report and is bounded to 4096 UTF-8 bytes.

Session Detail offers an export entry point, both file formats, and Copy Summary. It labels sanitized reports, intentional injected conditions, partial sessions, and the diagnostic-only status of simulation. The existing Session Detail timeline remains the persisted authority for on-screen facts.

Phase 7 security verification includes source checks for the exact provider path, JVM hostile sentinel tests across model/renderers/files/clipboard, Android provider and content URI tests, Room close/reopen export tests, UI action tests, and live cross-app acceptance where the device can execute it. Device acceptance records distinguish automation from visible user approval; no synthetic failure is described as organic. Final freeze is permitted only after complete local, device, security, documentation, and exact-head CI evidence.

## Phase 7.22–7.28 gate routing and freeze

The historical Phase 6 gate still rejects `ACTION_SEND` because sharing was out
of scope at that freeze. Phase 7 CI selects `phase7_static.sh` when its tracked
marker exists; Phase 6 CI selects `phase6_static.sh` only without that marker.
The Phase 7 gate reuses applicable Phase 5 and Phase 6 assertions, checks frozen
predecessor hashes and exact ancestry, and validates report, export, provider,
security, and evidence boundaries. It never changes the Phase 6 script or its
historical meaning.

The implementation/evidence commit must include docs and prefreeze results.
After pushing that exact commit, its GitHub Actions run must finish successfully
with a matching head SHA before an annotated Phase 7 tag is created or pushed.
The tag records CI provenance. Phase 8 remains outside this freeze.
