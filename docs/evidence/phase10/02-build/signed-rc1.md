# Operator-signed RC1 — 2026-10-04

Built from clean branch `phase10-release-candidate-compatibility-evidence`
at `cc6c7dfc1e28ae666e7955e88ea8301171cce231` with all four external
signing environment variables present. `./gradlew :app:assembleRelease`
returned `BUILD SUCCESSFUL` and produced `app-release.apk`. The keystore is
outside the repository; no credential value or private key is stored here.

Verified with SDK build-tools `36.0.0` before and after copying the byte-for-byte
identical APK to
`/home/abbaas/Downloads/MWA_LAB_RC1_2026-10-04/MWA-Lab-v0.1.0-clockin-rc1.apk`.
The neighboring `.apk.sha256` file passed `sha256sum -c`.

- APK SHA-256: `a48372f422ddae239d698062db77601b6d06651ba135ef8eb8dc43146bb519ec`.
- APK size: `12239526` bytes.
- Signer certificate SHA-256: `a3745b48d28baac2a230360f32dcf9671d5abeb9a6ca81f458eb4b8667ecc4e4`.
- `apksigner verify --verbose --print-certs`: verifies; v1 and v2 schemes true.
- `aapt dump badging`: package `dev.mwalab`, versionCode `1`, versionName
  `0.1.0-clockin`, compileSdk `37`, targetSdk `37`, minSdk `23`.
- `aapt dump permissions`: INTERNET present, CAMERA absent.
- `aapt dump xmltree`: exported MWA association activity with VIEW,
  BROWSABLE, and `solana-wallet` scheme; FileProvider non-exported.
- Phase 10 static: fixed Devnet RPC remains, mainnet production authority and
  Remote scanner control/dependency absent. Remote MWA remains blocked/hidden;
  production-wallet compatibility remains NOT_VERIFIED.

This is a signed candidate, **not** a completed release-acceptance claim.
Runtime evidence and user-supplied Devnet funding are separate follow-ups.
