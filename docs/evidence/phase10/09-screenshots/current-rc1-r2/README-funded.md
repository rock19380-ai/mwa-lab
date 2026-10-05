# Funded continuation screenshot index

All images 09–38 were captured from the dedicated `MWA_Lab_RC_API_36` current signed RC1 revision-2 install after user funding. No old versionName-1.0 AVD screenshot is used as current release evidence.

- 09–10: visible sign-message approval and Demo Client PASS; no transaction.
- 11: pre-signing Demo Client UnknownHostException under Android APP_BACKGROUND network block.
- 12: fixed-HTTPS RPC reached sign-only review after Demo Client-only emulator network allowlist; that diagnostic request was rejected.
- 13–16: canonical NORMAL transaction review, diagnostics, explicit signing approval, and Demo Client PASS. On-chain signature/finalization is separately verified in `../../04-local-mwa/funded-rc1-r2.md`.
- 17–21: synthetic FAULT_SIGN_REJECT warning, client-verified `-3`, restored NORMAL, before-send session list, and persisted fault detail.
- 22–24: fault Markdown/JSON Android Share Sheets and Copy Summary control. Screenshot 24 was taken before its asynchronous copied status appeared; the subsequent UI dump did show `Sanitized summary copied.`
- 25–28: canonical session persisted detail, Markdown/JSON Share Sheets, and verified Copy Summary status.
- 29–33: invalid recipient, zero, over-precision, negative, and over-balance non-submitting UI rejection.
- 34–36: one-lamport direct Test Wallet review and confirmed/refreshed result. Screenshot 35 precedes the below-fold confirmation visible in 36.
- 37–38: unchanged latest MWA sessions after direct send and NORMAL fault state after process restart.

The screenshot sequence alone is not proof of Devnet confirmation; read-only RPC signatures/status, generated report bytes, and Room read-only counts are recorded in the corresponding evidence directories. No share target was selected.
