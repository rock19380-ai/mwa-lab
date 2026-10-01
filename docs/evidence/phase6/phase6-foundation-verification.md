# Phase 6.0–6.5 foundation verification

Baseline: `bcd42c11adbe18abdbea18da3f29ae302c9518be`, tag `phase5-simulation-diagnostic-classification-2026-09-30`. GitHub Actions run [36707197361](https://github.com/rock19380-ai/mwa-lab/actions/runs/36707197361) reports `completed/success` at that exact SHA. The Phase 6 branch was created from the clean freeze commit; no `gradlew.bat` drift was present.

The protected-source check `sha256sum -c docs/evidence/phase6/phase6-baseline.sha256` passed for all 13 listed paths. `git diff bcd42c11adbe18abdbea18da3f29ae302c9518be -- app/schemas app/src/main/java/dev/mwalab/storage/MwaLabDatabase.kt gradle/libs.versions.toml app/src/main/java/dev/mwalab/security/NetworkPolicy.kt app/src/main/java/dev/mwalab/signing/LabSigningService.kt app/src/main/java/dev/mwalab/transaction/LegacyTransactionCodec.kt` was empty. Room is version 3, schemas 1/2/3 and both historical migrations are unchanged, and walletlib is 2.0.7. The unchanged network policy rejects mainnet.

| Check | Actual result |
| --- | --- |
| `./gradlew :app:testDebugUnitTest --tests 'dev.mwalab.protocol.recorder.PersistentProtocolRecorderTest' --tests 'dev.mwalab.faults.*'` | PASS; 32 tests, zero failures |
| `./gradlew :app:testDebugUnitTest --tests 'dev.mwalab.simulation.*'` | PASS; focused Phase 5 simulation tests |
| `./gradlew test lint assembleDebug` | PASS; 260 app JVM tests and 9 demo JVM tests, zero failures; lint and both debug APKs passed |
| `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.mwalab.faults.FaultSelectionPersistenceInstrumentedTest,dev.mwalab.storage.FaultEvidencePersistenceInstrumentedTest` | PASS; Gradle selected only the first class, so one emulator test ran |
| `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.mwalab.storage.FaultEvidencePersistenceInstrumentedTest` | PASS; one emulator test |
| `./gradlew :app:connectedDebugAndroidTest` | PASS; 111 emulator tests, zero failures |
| `./scripts/phase1_static.sh` | PASS |
| `./scripts/phase5_static.sh` | Historical design/vector/device-parser subchecks PASS; overall FAIL at its frozen `PersistentProtocolRecorder.kt` SHA because Phase 6 intentionally extends that file. The historical script is unchanged. Phase 6 CI routing belongs to 6.20. |

The first demo-client connected run failed before association because the wallet package was absent after app instrumentation. `adb shell pm list packages dev.mwalab` returned no package. `./gradlew :app:installDebug` restored the wallet; the rerun of `./gradlew :demo-client:connectedDebugAndroidTest` passed its one cross-package test. This was an emulator setup prerequisite, not a product-code repair.

Phase 6.5 deliberately captures/evaluates typed decisions without applying them to walletlib callbacks. No synthetic protocol behavior is claimed here. Applied-fault annotation is available to the later callback adapters; selecting a profile alone does not mark an event. Phase 6.6–6.10 must translate decisions and annotate only when the condition is actually applied.
