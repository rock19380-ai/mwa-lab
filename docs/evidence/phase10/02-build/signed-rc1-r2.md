# Current signed RC1 revision 2 — 2026-10-05

Exact app source commit: `945295a3e0124af11a5d75a76c7444f09586339e`
on `phase10-release-candidate-compatibility-evidence`. It changes only the
Test Wallet adjacent refresh button label from `REFRESH BALANCE` to `REFRESH`;
the package, versionCode, versionName, equal Row weights, button style and
behavior are unchanged. The prior signed RC1 artifact is preserved as
historical evidence in `signed-rc1.md`.

After the source commit, `./gradlew :app:assembleRelease` returned
`BUILD SUCCESSFUL`; the four operator signing inputs were available and the
same external keystore was used. The signed `app-release.apk` was copied to
`/home/abbaas/Downloads/MWA_LAB_RC1_2026-10-05/MWA-Lab-v0.1.0-clockin-rc1-r2.apk`.
The neighboring `.apk.sha256` file passed `sha256sum -c`. No keystore or
credential was copied into the repository or recorded in evidence.

- APK SHA-256: `0b17ccac5180d0bd6919f3f24c0c8a03efebebf9b42bd07f4909a2894e35bd21`.
- APK size: `12239522` bytes.
- Signer certificate SHA-256: `a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4` (same release certificate as historical RC1).
- Installed SDK build-tools `36.0.0`: `apksigner verify --verbose --print-certs`
  returned `Verifies`, v1 true, v2 true.
- `aapt dump badging`: package `dev.mwalab`, versionCode `1`, versionName
  `0.1.0-clockin`, compileSdk `37`, targetSdk `37`, minSdk `23`.
- `aapt dump permissions`: INTERNET present, CAMERA absent.
- `aapt dump xmltree AndroidManifest.xml`: exported Local MWA association
  activity with BROWSABLE `solana-wallet` filters; FileProvider non-exported.
- Phase 10 static gate passes for fixed Devnet RPC and absent mainnet/Remote
  scanner controls and dependencies. Remote MWA remains blocked/hidden and
  production-wallet compatibility remains NOT_VERIFIED.

Before the source commit, `./scripts/phase10_static.sh`, `./gradlew lint`,
`./gradlew test`, `./gradlew assembleDebug`, `./gradlew assembleRelease`,
`./gradlew :app:assembleDebugAndroidTest`, and
`./gradlew :demo-client:assembleDebugAndroidTest` all exited successfully.
The post-commit release rebuild also passed. This is signed RC build evidence,
**not** a completed runtime/Devnet acceptance claim.
