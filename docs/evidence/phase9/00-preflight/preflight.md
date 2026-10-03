# Phase 9 preflight receipt

Date: 2026-10-03 (Asia/Yangon).

## Frozen predecessor and origin

- Starting branch: `phase8-world-class-ux-positioning`
- Starting HEAD: `7cb9c0da5839ee14f4ef5f45391449b5060e7ebc`
- Frozen tag: `phase8-world-class-ux-positioning-2026-10-03`
- Tag object: `821201070a0731eda9f7c1f203dbfbdaee467e44`
- Peeled tag target: `7cb9c0da5839ee14f4ef5f45391449b5060e7ebc`
- `origin/phase8-world-class-ux-positioning`: `7cb9c0da5839ee14f4ef5f45391449b5060e7ebc`
- Phase 8 exact-head CI: run `37093366636`, completed successfully
- Worktree before branch creation: clean
- Phase 9 branch: `phase9-first-run-connection-ux`
- Phase 9 start HEAD: `7cb9c0da5839ee14f4ef5f45391449b5060e7ebc`

`git fetch --all --tags --prune` completed before the refs above were checked. No Phase 9 local or remote branch existed, so the Phase 9 branch was created directly from the peeled frozen tag.

## Reproducible build state

- Gradle wrapper: `9.6.0`; distribution SHA-256 `bbaeb2fef8710818cf0e261201dab964c572f92b942812df0c3620d62a529a01`
- Android Gradle Plugin: `9.4.1`
- Kotlin plugin: `2.2.10`
- KSP: `2.3.12`
- Launcher JDK: OpenJDK `21.0.12.1`
- Requested Gradle daemon toolchain: Java `25`
- Compile SDK: `37`
- Target SDK: `37`
- Installed platforms: Android `36` and `37.0`
- Installed build tools: `36.0.0`
- SDK path: `/home/abbaas/Android/Sdk`
- Emulator used for connected baseline: `emulator-5554`, Android 16 / API 36

## Pinned dependency and schema state

- walletlib: `com.solanamobile:mobile-wallet-adapter-walletlib:2.0.7`
- clientlib: `com.solanamobile:mobile-wallet-adapter-clientlib:2.0.7`
- walletlib AAR SHA-256: `e5c639964c5740e187cacf31776f0e46b9a2433e26b50fc027ab8ffb481b177c`
- Room: `2.8.5`
- Room application schema: `3`
- Compose BOM: `2026.02.01`
- Coroutines: `1.11.0`
- Bouncy Castle: `1.85.2`
- multimult: `0.2.6`

No dependency was changed during preflight.

## Phase 8 continuation gate

| Command | Result |
|---|---|
| `./scripts/phase8_static.sh` | PASS |
| `./gradlew lint test assembleDebug :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest --offline` | PASS; 165 actionable tasks |
| `./gradlew :app:connectedDebugAndroidTest --offline` | PASS; 136 tests, 0 failures/errors/skips |
| reinstall exact `app-debug.apk` | PASS |
| `./gradlew :demo-client:connectedDebugAndroidTest --offline` | PASS; 1 test, 0 failures/errors/skips |
| reinstall exact `demo-client-debug.apk` | PASS |
| app JVM XML | 287 tests, 0 failures/errors/skips |
| demo-client JVM XML | 10 tests, 0 failures/errors/skips |
| `git diff --check` | PASS |

The static gate covered the frozen Phase 5 simulation checks, Phase 6 fault/security checks, Phase 7 report/export checks, and Phase 8 presentation/frozen-authority checks.

## walletlib 2.0.7 Remote MWA API inspection

The cached pinned AAR was inspected directly. It contains:

- `AssociationUri.parse(Uri)`
- `LocalAssociationUri`
- `RemoteAssociationUri`
- public `RemoteAssociationUri.reflectorHostAuthority: String`
- public `RemoteAssociationUri.reflectorIdBytes: ByteArray`
- `RemoteAssociationUri.createScenario(Context, MobileWalletAdapterConfig, AuthIssuerConfig, Scenario.Callbacks): Scenario`
- `RemoteWebSocketServerScenario`
- `Scenario.start()` and `Scenario.close()`
- lifecycle callbacks `onScenarioReady`, `onScenarioServingClients`, `onScenarioServingComplete`, `onScenarioComplete`, `onScenarioError`, and `onScenarioTeardownComplete`

The pinned artifact does not expose `Scenario.startAsync()`. Remote class presence establishes only API availability; it does not establish reflector interoperability or release readiness.

## Preflight result

**PASS.** The immutable Phase 8 predecessor, local continuation gates, dependency/API surface, schema, and build environment were verified before Phase 9 implementation began.
