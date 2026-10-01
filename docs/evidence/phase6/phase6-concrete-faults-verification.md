# Phase 6.6–6.10 concrete fault verification

Starting branch and HEAD: `phase6-deterministic-fault-engine` at `b40b11c6ff0f8df813df465b83cf42a999ff5371`. Implementation commit: `66ea942` (`feat(phase6): implement deterministic protocol fault profiles`). This receipt covers the local emulator and JVM runs on 2026-10-01. Phase 6 remains in progress; no freeze tag or Phase 7 exporter was created.

## Frozen callback and evidence contract

All decisions come from the request-start `FaultRequestSnapshot` and the explicit `DeterministicFaultEngine` hook. The host annotates the pending canonical event before a synthetic callback. A terminal synthetic result returns immediately; no subsequent approval, signing, or submission runs. Request summaries retain real chain and payload count.

| Fault ID | Hook and target | Pinned walletlib 2.0.7 callback | Protocol code | Applied-result evidence |
| --- | --- | --- | --- | --- |
| `FAULT_AUTH_REJECT` | AUTHORIZATION_DECISION; AUTHORIZE, observable REAUTHORIZE | `completeWithDecline` | `ERROR_AUTHORIZATION_FAILED (-1)` | FAILURE / INJECTED / matching ID |
| `FAULT_UNSUPPORTED_CHAIN` | AUTHORIZATION_DECISION; AUTHORIZE | `completeWithClusterNotSupported` | `ERROR_CLUSTER_NOT_SUPPORTED (-7)` | FAILURE / INJECTED / matching ID; requested Devnet chain retained |
| `FAULT_INVALID_PAYLOAD` | SIGNING_VALIDATION; M, T, S | M/T `completeWithInvalidPayloads`; S `completeWithInvalidSignatures` | `ERROR_INVALID_PAYLOADS (-2)` | FAILURE / INJECTED / matching ID; false vector has actual payload count |
| `FAULT_TOO_MANY_PAYLOADS` | SIGNING_VALIDATION; M, T, S | `completeWithTooManyPayloads` | `ERROR_TOO_MANY_PAYLOADS (-6)` | FAILURE / INJECTED / matching ID; real count retained |
| `FAULT_SIGN_REJECT` | SIGNING_PRE_APPROVAL; M, T, S | `completeWithDecline` | `ERROR_NOT_SIGNED (-3)` | FAILURE / INJECTED / matching ID |
| `FAULT_DELAY_5S` | SIGNING_PRE_APPROVAL; M, T, S | normal callback after cancellable `delay(5000)` | determined by later action | applied ID retained; terminal outcome/source independent |
| `FAULT_STALE_BLOCKHASH` | TRANSACTION_BLOCKHASH_CHECK; T, S | T `completeWithInvalidPayloads`; S `completeWithInvalidSignatures` | `ERROR_INVALID_PAYLOADS (-2)` | FAILURE / INJECTED / matching ID; parsed first, no blockhash RPC |
| `FAULT_RPC_UNAVAILABLE` | SUBMISSION_PRE_RPC; S | `completeWithNotSubmitted` | `ERROR_NOT_SUBMITTED (-4)` | FAILURE / INJECTED / matching ID; injected_rpc_unavailable |
| `FAULT_SUBMISSION_FAILURE` | SUBMISSION_PRE_RPC; S | `completeWithNotSubmitted` | `ERROR_NOT_SUBMITTED (-4)` | FAILURE / INJECTED / matching ID; injected_submission_failure |

M = SIGN_MESSAGES, T = SIGN_TRANSACTIONS, S = SIGN_AND_SEND_TRANSACTIONS. The Android callback fixture verified the actual clientlib responses and Room event fields for each row. Synthetic rejection and NORMAL manual rejection both return the not-signed family, but the former persisted INJECTED plus `FAULT_SIGN_REJECT`, while the latter persisted OBSERVED_PROTOCOL plus a null ID.

The counting signer recorded zero sign calls for synthetic authorization, validation, sign-reject, and stale faults. For the two pre-submission faults it recorded one sign call per explicitly approved request and zero `sendTransaction` calls; the following NORMAL sign-and-send made one submission call through the fake gateway. The stale test recorded zero real blockhash-validation calls, then a NORMAL transaction made a real gateway validation call. No test submitted an on-chain transaction.

The delay test recorded at least 4900 ms in the canonical event before successful approval, with SUCCESS/NONE and the delay ID. A later manual rejection retained the delay ID with FAILURE/OBSERVED_PROTOCOL. Changing selection during a delayed request left its snapshot unchanged and affected the next request. Closing the association, explicitly closing the host (the boundary called by `MobileWalletAdapterActivity.onDestroy`), revoking authorization, and replacing the association during delay produced no signing or submission and preserved the old event's fault ID. A separate visual Activity recreation test was not run.

## Executed gates

| Command | Actual result |
| --- | --- |
| `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.mwalab.mwa.MwaTransactionApprovalInstrumentedTest#phase6AuthorizationFaultsUsePinnedCallbacksAndPreserveDevnetRequest` | PASS, 1 test |
| `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.mwalab.mwa.MwaTransactionApprovalInstrumentedTest` | PASS, 23 tests before the later host-destruction test; the new host-destruction test passed alone |
| `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.mwalab.mwa.MwaTransactionApprovalInstrumentedTest#phase6HostDestroyDuringDelayCancelsWithoutSigning` | PASS, 1 test |
| `./gradlew test lint assembleDebug` | PASS, 260 app JVM tests, 9 demo JVM tests, lint and debug APKs |
| `./gradlew :app:testDebugUnitTest --tests 'dev.mwalab.simulation.*'` | PASS, 19 Phase 5 simulation JVM tests |
| `./gradlew :app:connectedDebugAndroidTest lint` | PASS on final rerun, 122/122 emulator tests and lint |
| `./gradlew :app:installDebug :demo-client:connectedDebugAndroidTest` | PASS, 1/1 cross-app smoke; wallet installed explicitly after app suite |
| `sha256sum -c docs/evidence/phase6/phase6-baseline.sha256` | PASS, 13/13 protected hashes |
| `git diff bcd42c11adbe18abdbea18da3f29ae302c9518be -- app/schemas app/src/main/java/dev/mwalab/storage/MwaLabDatabase.kt gradle/libs.versions.toml app/src/main/java/dev/mwalab/security/NetworkPolicy.kt app/src/main/java/dev/mwalab/signing/LabSigningService.kt app/src/main/java/dev/mwalab/transaction/LegacyTransactionCodec.kt` | empty diff |

One earlier 121-test full emulator attempt had 1 failure: the first capability-capture test could not connect to the local websocket server before reaching its assertions. That test passed alone on immediate rerun, and the next full run passed 121/121. After adding the host-destruction case, the last full run passed 122/122 with lint. The failure was retained here rather than reported as an initial green run.

Historical static gates: Phase 1 PASS. Phase 2 reports `per-association callbacks missing` against its pre-freeze source pattern. Phase 3 reports four predecessor hash mismatches in files changed by later historical phases. Phase 4 expects Room schema 2 and therefore rejects current schema 3. Phase 5 design/vector/device-parser subchecks PASS, then its frozen `PersistentProtocolRecorder.kt` hash fails because Phase 6.4 intentionally extended that file. No historical gate script was edited; Phase 6 routing remains scheduled for 6.20.

## Security and phase boundaries

Room remains version 3 with only schema exports 1/2/3; migrations 1→2 and 2→3 are unchanged. Walletlib remains 2.0.7. The unchanged `NetworkPolicy` rejects mainnet. Only `MwaSessionHost` and an Android test file were changed by this implementation; no key, token, payload, transaction, RPC endpoint, manifest export, or external fault-control API was added. Phase 5 simulation remains diagnostic-only and its source was untouched. Phase 6.11 UI and Phase 7 export were not started.
