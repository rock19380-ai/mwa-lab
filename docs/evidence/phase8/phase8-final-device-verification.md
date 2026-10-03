# Phase 8 final connected-device verification

Date: 2026-10-03 (Asia/Yangon). Device: `emulator-5554`, `sdk_gphone64_x86_64`, Android 16 / API 36. One healthy emulator was attached. System font scale was `1.0` before and after the final suites.

After the approval inset repair, the suites ran sequentially:

```text
./gradlew :app:connectedDebugAndroidTest --offline
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew :demo-client:connectedDebugAndroidTest --offline
adb -s emulator-5554 install -r demo-client/build/outputs/apk/debug/demo-client-debug.apk
```

The app runner removed the wallet package, so the exact verified app APK was reinstalled before the demo suite. The demo runner removed its package, so the exact verified demo APK was reinstalled before live acceptance. No concurrent connected suites ran.

| Suite | XML files | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| App connected | 1 | 136 | 0 | 0 | 0 |
| Demo-client connected | 1 | 1 | 0 | 0 | 0 |

The final app suite includes the constrained approval UI test at 360×640 dp, 1.3× font scale, and dark theme. The preceding Phase 8 accessibility audit records the light/dark and large-font device matrix. The only final source repair was approval top inset handling; a real final-build approval screenshot was visually checked after that repair and the title cleared the Android status bar.

The connected test teardown produced a new Lab test identity. It was funded with 0.01 test-only Devnet SOL from an already configured Devnet CLI identity. No private key was imported into MWA Lab or included in evidence. Final live acceptance used the newly installed exact APKs and that new public test address.
