# Phase 7 sanitized diagnostic reports: design freeze

Baseline: Phase 6 commit `f5eaec53d911fc2423efdb3051e8316ce9e1032d` and its annotated tag. Phase 7.0–7.10 only: canonical domain, snapshot, sanitization, renderers, and bounds. Cache files, FileProvider, Share Sheet, clipboard, and live device acceptance are later batches.

## Authority and sources

Every exported diagnosis must be derivable from structured persisted protocol/session state. No export surface may scrape UI text or serialize arbitrary runtime objects. `SessionRepository.getSession`, `CapabilitySnapshotRepository.getSnapshot`, `TransactionDiagnosticRepository.getForEvent`, and `SimulationRepository.getForEvent` are read-only sources. Reports are derived artifacts; there is no report table. Room schema 3, migrations 1→2 and 2→3, walletlib 2.0.7, Devnet-only execution, and Phase 6 authorization, signing, parser, simulation, fault and error authorities remain frozen.

## Canonical report v1

`DiagnosticReport` has format `mwa-lab-diagnostic-report`, version `1`, generated epoch milliseconds, application and environment, one session, optional capability snapshot, ordered events, and warnings. The typed event contains independent outcome, protocol error code, failure source, and injected fault ID; an active fault marker never determines failure source. Transactions and simulation attempts are child diagnostic evidence. A simulation PASS is diagnostic evidence only, never a future submission guarantee. Unknown instruction semantics remain `UNKNOWN`.

Completeness is `COMPLETE` only for a closed session whose two bounded full reads agree. Open sessions and unstable reads are `PARTIAL` with a warning. The assembler retries at most three snapshot pairs; it does not mutate records. It orders events by sequence, transactions by payload index, simulations by payload index then attempt number, summary entries by key, capability lists and warnings lexically. `generatedAt` comes from an injected clock and does not affect the consistency comparison.

## Export sanitization allowlist

The export policy copies validated app-generated IDs, bounded safe display label, fixed Devnet cluster, numeric timing/counts, enum names, known fault IDs, known summary keys with typed value validation, known capability values, canonical SHA-256 fingerprints, bounded wire lengths, verified public program IDs, parser-provided decoded labels and exact amounts, and simulation classification/compute units. Simulation logs are projected again through the existing structural `SimulationLimits.publicLogLine` policy.

Private keys, seeds and mnemonics, authorization and association tokens/secrets, encryption material, raw transactions and messages, raw signatures, arbitrary wallet requests, raw RPC bodies, and exception or stack trace text are forbidden. Unknown summary keys and invalid values are omitted with an explicit truncation/omission warning. No post-hoc regex of an unsafe object dump is the primary safety mechanism.

## Renderer and bounds contract

Markdown and JSON take the same immutable canonical report. Markdown uses fixed sections, LF newlines, UTF-8 and escaped untrusted inline text. JSON uses fixed snake-case keys and stable ordering; no renderer reads a repository. The report records `truncated=true` and a warning when any collection or string is omitted or shortened. Limits: 64 events, 10 transactions per event and 40 per report, 20 simulation attempts per event and 80 per report, 8 instructions per transaction, 24 summary fields per map, 128 characters per free-text field, 16 log lines of at most 128 characters, and 1 MiB per rendered output. Renderer byte limits fail closed if a caller constructs an oversized report outside the assembler.

Future file sharing may use only internal cache artifacts and a cache-scoped non-exported FileProvider with temporary read grants. That layer has no protocol or database authority.

## Verification

JVM tests cover normal/failed/injected/delayed outcomes, observed later failures, capabilities, known and unknown transaction semantics, all simulation classifications, multiple children, open and empty sessions, ordering, escaping, Unicode/control characters, hostile summary fields, bounds and renderer parity. The design checker and baseline hash manifest guard frozen decisions and predecessor source files. Full `test`, `lint`, and `assembleDebug` are required before a green implementation checkpoint. No Phase 7 freeze tag is created in this batch.
