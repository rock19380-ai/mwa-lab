# Phase 8 final canonical NORMAL live regression

Date: 2026-10-03 (Asia/Yangon). Device: `emulator-5554`. Final repaired build from pre-closeout HEAD `8c14ded3219fb03e169f6cd9c93e6898a5d72187` plus the scoped approval inset change.

The visible Fault Lab selection and persisted `active_fault_id` were `NORMAL`. The established demo-client `mwa_phase2_scenario=SIGN_AND_SEND_APPROVE` launched a real cross-package MWA request. The approval screen visibly showed `MWA LAB TEST ENDPOINT`, `SOLANA DEVNET`, `NO REAL FUNDS`, `sign_and_send_transactions`, transaction diagnostics, `REJECT`, and `APPROVE`. One visible `APPROVE` control was activated. The demo client reported `PHASE2 SIGN_AND_SEND_APPROVE: PASS`, whose canonical client path checks the returned signature and confirmed Devnet submission. The Lab address balance moved from 0.01 to 0.009995 Devnet SOL, consistent with a 5,000-lamport fee.

Final successful session: `20671410-5a3a-4f1e-b28e-5166d542462d`. Read-only Room inspection after completion found three events, including:

```text
SIGN_AND_SEND_TRANSACTIONS
outcome = SUCCESS
protocolError = null
failureSource = NONE
injectedFaultId = null
```

After force-stop/relaunch, Home and Session Detail showed the persisted PASS session and Devnet context. Share Markdown and Share JSON each opened Android Share Sheet and created a bounded app-private cache artifact; Copy Summary displayed `Sanitized summary copied.` The existing read-only `phase7_device_report_verify.py` checked Markdown/JSON/Room parity for this COMPLETE three-event session. The final semantic export scan found no forbidden raw/secret JSON keys or secret sentinels.

A preceding attempt on the same repaired build, session `9a0fd4d1-ed65-4935-9e16-ae788c959b3b`, returned client code `-4` and persisted `FAILURE / RPC_NETWORK / null injected fault`, with `one_or_more_not_submitted` and no Devnet fee charged. The fixed public RPC subsequently answered health and blockhash queries. The canonical retry above passed without a code change. This transient attempt is not counted as a successful submission and remains part of the device history.

This is a Devnet test endpoint result, not production-wallet compatibility evidence.
