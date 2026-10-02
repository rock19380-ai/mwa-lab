# Phase 7 final live injected signing-rejection acceptance

Timestamp (UTC): `2026-10-02T08:02:58Z`
Device: `emulator-5554`
Session: `340ce109-f6bb-45d2-a492-b069f3742ecb`

Result: **PASS**.

The test harness selected `Reject signing` through the visible Fault Lab UI and
launched `SIGN_AND_SEND_REJECT`. The demo client verified
`ERROR_NOT_SIGNED (-3)`. After force-stop/relaunch, Session Detail and both
exports preserved the synthetic condition independently from the terminal
classification:

```text
outcome = FAILURE
failureSource = INJECTED
injectedFaultId = FAULT_SIGN_REJECT
protocolError = -3
```

Visible approval taps driven by the test harness: `0`.

Markdown and JSON both opened the Android Share Sheet. Copy Summary passed. The
read-only device verifier matched both formats to Room. Fault Lab was returned
to NORMAL after the scenario.
