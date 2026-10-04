# Phase 10 unsigned buildability — 2026-10-04

Source checkpoint at post-commit rebuild:
`1e178bfeff3e2e8c092baf62c5a6213c72e9438d` on
`phase10-release-candidate-compatibility-evidence`. `git status --short
--branch` showed no changes at this point. After the commit,
`./gradlew assembleRelease` returned `BUILD SUCCESSFUL` and produced
`app/build/outputs/apk/release/app-release-unsigned.apk`. This is a locally
generated **unsigned buildability artifact**, not a signed RC or a distributable
competition APK; it is not copied to a release directory.

- Unsigned APK SHA-256 (post-commit build):
  `fca1370f711db8ac18b19276ccb33c025f7e7113ed5113de1d135abdad2382ac`.
- Size: `12216846` bytes (`stat -c %s`).
- SDK discovered from `local.properties`: `/home/abbaas/Android/Sdk`;
  highest installed build-tools directory: `36.0.0`.
- `aapt dump badging`: package `dev.mwalab`, versionCode `1`, versionName
  `0.1.0-clockin`, minSdk `23`, targetSdk `37`, compileSdk `37`.
- `aapt dump badging` permissions: `android.permission.INTERNET`,
  `android.permission.ACCESS_NETWORK_STATE`, and
  `dev.mwalab.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; no CAMERA.
- `aapt dump xmltree`: `dev.mwalab.mwa.MobileWalletAdapterActivity` is
  exported; VIEW, BROWSABLE, and `solana-wallet` association filters remain.
- `apksigner verify` returned nonzero with `DOES NOT VERIFY` and
  `Missing META-INF/MANIFEST.MF`: expected for this unsigned artifact.
  Public signing certificate SHA-256: **not available**. Signed RC1 path and
  RC1 SHA-256: **not available**.
- Phase 10 static gate passed, including fixed Devnet authority and absence
  of a mainnet production path, scanner control/dependency, and CAMERA.

Validation after implementation (each exited 0; the commands were chained
with `set -e`): `./scripts/phase10_static.sh`, `./gradlew lint`,
`./gradlew test`, `./gradlew assembleDebug`, `./gradlew assembleRelease`,
`./gradlew :app:assembleDebugAndroidTest`, and
`./gradlew :demo-client:assembleDebugAndroidTest`. Debug app and both
instrumentation APKs were confirmed to exist. A separate signing-negative
test with only `MWALAB_RELEASE_KEY_ALIAS` set failed at configuration with
the three other variable names identified as missing, as intended; it
exposed no credential values.

The first attempted Phase 10 `assembleRelease` found a Gradle Kotlin DSL
`java.io.File` name-resolution error. The source was corrected to import
`java.io.File`, after which the full release build and subsequent gates passed.
No Phase 10 exact-head CI run is claimed: the branch was committed locally,
not pushed. No live Devnet transaction or airdrop was attempted.

**Gate:** `WAITING_FOR_RELEASE_SIGNING_SECRET`. A stable externally stored
operator keystore and all four `MWALAB_RELEASE_*` variables are needed before
building, verifying, checksumming, and exporting a signed RC1. Do not rename
or distribute this unsigned APK as RC1. Production-wallet compatibility
remains `NOT_VERIFIED`; Remote MWA remains `BLOCKED_HIDDEN` and unreleased.
