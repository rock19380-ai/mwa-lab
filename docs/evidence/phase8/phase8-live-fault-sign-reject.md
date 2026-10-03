# Phase 8 final canonical injected signing-rejection regression

Date: 2026-10-03 (Asia/Yangon). Device: `emulator-5554`. Final repaired build.

The visible Fault Lab selected `FAULT_SIGN_REJECT` and displayed `FAULT ACTIVE`, `INTENTIONAL TEST CONDITION`, `ERROR_NOT_SIGNED (-3)`, and `RETURN TO NORMAL`. The established demo-client `mwa_phase2_scenario=SIGN_AND_SEND_REJECT` then exercised the same sign-and-send request category. It completed unaided, with zero approval taps, and reported `PHASE2 SIGN_AND_SEND_REJECT: PASS` for the expected `ERROR_NOT_SIGNED` response.

Final injected session: `d34d35dd-78e4-47fa-8e83-8eb458d0ad1a`. Read-only Room inspection found three events, including:

```text
SIGN_AND_SEND_TRANSACTIONS
outcome = FAILURE
protocolError = -3 (ERROR_NOT_SIGNED)
failureSource = INJECTED
injectedFaultId = FAULT_SIGN_REJECT
```

After force-stop/relaunch, Home showed an active intentional fault and failed latest session. Session Detail showed `SESSION FAILED`, failed method, `ERROR_NOT_SIGNED`, `INJECTED`, and `Reject signing (FAULT_SIGN_REJECT)` in its first viewport. Its export section separately displayed `INTENTIONAL TEST CONDITION`, `FAULT_SIGN_REJECT`, and `Recorded failure source: INJECTED`.

Share Markdown and Share JSON each opened Android Share Sheet; Copy Summary displayed `Sanitized summary copied.` The existing read-only `phase7_device_report_verify.py` confirmed both formats match persisted Room for this COMPLETE three-event session. The final semantic scan found no forbidden raw/secret JSON keys or secret sentinels. The unit report test also requires the canonical clipboard renderer, Markdown, and JSON to contain `FAULT_SIGN_REJECT`, `ERROR_NOT_SIGNED`, and `INJECTED` from the same report.

After capture/export, the visible `RETURN TO NORMAL` action was used. The UI showed `NORMAL · no intentional fault selected`, and app-private persisted `active_fault_id` was read back as `NORMAL`. The frozen application was not left in an injected-fault state.
