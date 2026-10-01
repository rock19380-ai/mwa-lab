# Phase 6.11–6.15 UI, vectors, and acceptance verification

Starting HEAD: `92a42c3d9abcb7a66be37264c04be8080f1b802f` on `phase6-deterministic-fault-engine`.

This checkpoint completes the Phase 6 Fault Lab presentation layer, global active-fault visibility,
request-snapshot warning, persisted session fault presentation, the client-side Phase 6 acceptance
runner, and safe machine-readable fault vectors. The production demo client cannot set wallet fault
state; the developer must select the matching profile in MWA Lab. Wallet-side `ProtocolEvent`
remains the evidence authority.

## Truthfulness contracts

- NORMAL/manual rejection is not inferred as injected from ERROR_NOT_SIGNED.
- `injectedFaultId` is shown independently from terminal `failureSource`.
- `FAULT_DELAY_5S` can display SUCCESS/NONE or a later OBSERVED_PROTOCOL failure while retaining
  the applied delay ID.
- RPC unavailable and submission failure remain distinct IDs even though both map to -4.
- The approval banner uses the immutable request-start fault snapshot, not a later global selection.

## Executed gates

- focused Phase 6 vector/presentation/demo JVM tests: PASS
- full `./gradlew test lint assembleDebug`: PASS
- AndroidTest APK assembly: PASS
- full app connected instrumentation: PASS
- explicit wallet reinstall followed by demo-client connected cross-app smoke: PASS
- concrete `MwaTransactionApprovalInstrumentedTest` regression: PASS
- focused Phase 5 simulation JVM regression: PASS
- Phase 1 static gate: PASS
- Phase 5 design/vector/device-parser checks: PASS
- historical Phase 5 full static gate: expected frozen `PersistentProtocolRecorder.kt` hash rejection only
- Phase 6 baseline manifest: PASS
- Room schema/migrations, walletlib, network/signing/codec frozen boundary: unchanged

Test-count snapshot from this run:

```json
{
  "app_jvm": {
    "tests": 19,
    "failures": 0,
    "errors": 0,
    "skipped": 0,
    "files": 5
  },
  "demo_jvm": {
    "tests": 10,
    "failures": 0,
    "errors": 0,
    "skipped": 0,
    "files": 4
  },
  "app_connected": {
    "tests": 24,
    "failures": 0,
    "errors": 0,
    "skipped": 0,
    "files": 1
  },
  "demo_connected": {
    "tests": 1,
    "failures": 0,
    "errors": 0,
    "skipped": 0,
    "files": 1
  }
}
```

No Phase 7 Markdown/JSON exporter or Android Share Sheet was introduced. Phase 6 remains in progress;
6.16–6.20 and exact-head CI/freeze are still pending.
