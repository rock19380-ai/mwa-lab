# Current signed RC1 revision 2 clean install — 2026-10-05

The October 4 funded/older `MWA_Lab_API_36` AVD was shut down cleanly, not
wiped or deleted. A separate `MWA_Lab_RC_API_36` AVD was created from the
already-installed Android 16/API 36 Google APIs x86_64 image. It ran alone as
`emulator-5554` with emulator snapshots disabled. No connected Gradle test
was run against this acceptance AVD.

The current signed candidate, SHA-256
`0b17ccac5180d0bd6919f3f24c0c8a03efebebf9b42bd07f4909a2894e35bd21`,
was installed on the clean acceptance AVD. `dumpsys package dev.mwalab`
reported versionCode `1` and versionName `0.1.0-clockin`. Its manual first
launch showed **MWA Protocol Debugger**, **DEVNET ONLY**, and **NO REAL FUNDS**;
Home exposed HOW TO CONNECT, and How to Connect showed **SAME-DEVICE MWA**.
The Test Wallet screen showed adjacent `Copy Address` and `REFRESH` text with
identical vertical bounds `[832,885]` in the UI hierarchy, both on one line.
The existing weighted Row/button styles were unchanged. The Receive Test SOL
section and address QR were present; no live Request Devnet SOL action was
invoked. Screenshots `../09-screenshots/current-rc1-r2/01-manual-first-run.png`
through `04-how-to-connect.png` record this pass.

The unfunded app was then uninstalled/reinstalled **only on the dedicated RC
AVD** to establish a truly clean dApp-first state. The earlier manual-run
address `4GnVgH69iG2MDJmzYWsDsuEHjxWwypHHJk5Nuh6Q2hrk` was discarded
before any funding and must not be used. Demo Client was launched first, and
Android cold-launched the signed RC's Local MWA activity. The real wallet
authorization UI showed `SOLANA DEVNET`, `Connection: SAME-DEVICE MWA`, the
test-only Demo Client name, `Identity: UNVERIFIED`, and visible APPROVE/REJECT
controls. Tapping APPROVE returned Demo Client `Result: PASS` with CONNECTION,
AUTHORIZE, CAPABILITIES, and DEAUTHORIZE all PASS. A separate real REJECT
returned `PHASE1_CANONICAL: FAIL`, as expected. Screenshots
`05-cold-dapp-first-authorization.png` through
`07-authorization-reject.png` in the current candidate screenshot directory
record these states.

After final clean install and cold consent, the **current installed signed-RC
Test Wallet** public address appeared identically on onboarding and twice on
the Test Wallet screen:
`FLAU1DewjS2jNpmf5eAWjC8h1BjQvzs8JNrDCBX2P8ja`.
The final Test Wallet capture is `08-final-identity.png`. This is not the
older `B2AFEix...` versionName-`1.0` AVD identity and not the historical
October 4 signed-RC identity `8AgfEQ8...`. Do not uninstall, clear data, or
run a connected Gradle task against this final identity.
