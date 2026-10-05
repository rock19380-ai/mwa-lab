# Regression/RC environment separation — 2026-10-04

`adb devices -l` found one running `emulator-5554`, AVD `MWA_Lab_API_36`,
Android 16/API 36, x86_64 (`getprop`); 1080 × 2424 pixels. `emulator
-list-avds` listed an additional `MWA_Lab_API_36_Regression` definition,
but its `.ini` points to `/tmp/mwa_lab_api36_regression.avd`, which did not
exist. The host had ~1 GiB available memory, making simultaneous AVDs
impractical. **Single-emulator operating rule:** all debug/connected tests
run first; remove the debug wallet; install signed RC1 and perform acceptance;
after final funding, no connected Gradle task may uninstall the wallet.

Initial combined `:app:connectedDebugAndroidTest
:demo-client:connectedDebugAndroidTest`: the app suite's JUnit XML showed
149 tests, 0 failures, 0 errors, 0 skipped. Demo Client showed 1 failure,
0 errors: `canonicalSequenceIsRealCrossPackageAndRepeatable` timed out
waiting for the wallet authorization approval. `adb shell pm path dev.mwalab`
then found no installed wallet: the app connected-test lifecycle had removed
the debug wallet before the subsequent Demo Client suite. This failure is
reported, not masked or counted as a clean combined run.

Explicitly running `:app:installDebug` followed by
`:demo-client:connectedDebugAndroidTest` returned `BUILD SUCCESSFUL`.
Fresh JUnit XML: ordinary app suite **149 / 0 failures / 0 errors / 0 skipped**
(timestamp `2026-10-04T10:07:54`); ordinary Demo Client suite **1 / 0 / 0 / 0**
(timestamp `2026-10-04T10:10:02`). No opt-in live test arguments were supplied;
both Gradle build files exclude their Phase 9 live classes, and the
instrumentation airdrop transport test uses `QueueTransport` rather than
network RPC. No live airdrop was invoked.

From this point onward, do not run `connectedDebugAndroidTest` on the
emulator used for signed-RC acceptance.
