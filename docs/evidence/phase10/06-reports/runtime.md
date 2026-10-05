# Signed-RC report controls — October 4, 2026

The RC persisted a genuine LOCAL AUTHORIZE-success timeline and a separate
LOCAL `FAULT_SIGN_REJECT` SIGN_MESSAGES failure timeline. On the success
session, the real **Share Markdown** and **Share JSON** actions each opened
Android's `com.android.intentresolver` Share Sheet with `Sharing 1 file`;
**Copy Summary** was exercised. On the fault session, both report actions
again opened the Android Share Sheet, the UI reported `Share Sheet opened for
JSON`, and **Copy Summary** reported `Sanitized summary copied.` The fault
report UI showed `INTENTIONAL TEST CONDITION`, `FAULT_SIGN_REJECT`, and
`Recorded failure source: INJECTED`, matching its persisted timeline. No
share target was selected and no report was transmitted. Screenshots 11–13
and 17–18 in `../09-screenshots/` show these interactions.

The UI explicitly reported `Report is truncated; see warnings in the full
report.` Do **not** claim byte-for-byte comparison of the actual generated
Markdown/JSON files or direct inspection of their complete contents: the
chooser's files were not captured. Canonical NORMAL sign-and-send and
sign-and-send injected-fault session exports remain unverified. Existing
deterministic `DiagnosticReportTest` covers persisted-state projection,
Markdown/JSON rendering, clipboard derivation, rejection of hostile secret
sentinels, and bounded sanitization; `DiagnosticReportExportInstrumentedTest`
covers restart-backed Room export and FileProvider grants. Runtime content
secret audit remains incomplete; no secret was observed in the captured UI.
The October 5 app/Demo Client unit-test tasks completed successfully without
connected-device mutation.
