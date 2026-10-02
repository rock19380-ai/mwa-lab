# Phase 7 sanitized diagnostic report evidence

This directory records the Phase 7 implementation and freeze basis.

- `phase7-start.md` — clean Phase 6 predecessor and branch start.
- `phase7-baseline.sha256` — canonical Phase 6 Git-blob hashes guarded by the Phase 7 static gate.
- `phase7-core-verification.md` — Phase 7.0–7.10 canonical report-core verification.
- `phase7-export-verification.md` — Phase 7.11–7.21 export/UI/device checkpoint.
- `phase7-final-local-verification.md` — final local, build and sequential connected regression.
- `phase7-design-verification.md` — final design-freeze verification.
- `phase7-report-domain-verification.md` — canonical report-model verification.
- `phase7-sanitization-verification.md` — final sanitization/security verification.
- `phase7-live-normal-report.md` — final NORMAL Devnet sign-and-send/report acceptance.
- `phase7-live-injected-report.md` — final `FAULT_SIGN_REJECT` acceptance.
- `phase7-markdown-json-parity.md` — final Markdown/JSON/Room parity.
- `phase7-restart-export-acceptance.md` — persisted export after process restart.
- `phase7-share-provider-security.md` — Share Sheet/provider acceptance.
- `phase7-prefreeze-verification.md` — complete local/device gate before the closeout commit.
- `phase7-local-final-state.json` and `phase7-live-final-state.json` — machine-readable safe summaries used to build closeout evidence.

The exact-head GitHub Actions run and immutable freeze provenance are recorded
in the annotated Phase 7 tag and the external freeze receipt. Evidence here
does not contain private keys, seeds, authorization tokens, association secrets,
raw transactions/messages or raw signatures.
