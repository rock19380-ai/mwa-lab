# Phase 8 evidence index

This directory records Phase 8 UX, positioning, final local/device/live/security checks, and the prefreeze audit after the immutable Phase 7 predecessor. Freeze provenance belongs to the annotated tag created only after exact-head GitHub Actions succeeds.

## Baseline and implementation

- `phase8-start.md` — frozen predecessor and branch start.
- `phase8-baseline.sha256` — canonical protected-authority baseline.
- `phase8-implementation-checkpoint.md` — Steps 8.0–8.13.
- `phase8-hardening-checkpoint.md` — Steps 8.14–8.21 and Prompt-2 verification.
- `phase8-accessibility-device-audit.md` — light/dark, 1.3× font, and layout audit.
- `phase8-five-second-audit.md` — Home comprehension audit.
- `phase8-positioning-audit.md` — product wording audit.
- `phase8-production-wallet-smoke.md` — **NOT VERIFIED IN THIS RELEASE**.

## Final candidate verification

- `phase8-final-local-verification.md` — final repaired-build static, lint, JVM, and APK gate.
- `phase8-final-device-verification.md` — sequential app/demo connected suites and device baseline.
- `phase8-live-normal.md` — canonical NORMAL sign-and-send, retry disclosure, and exports.
- `phase8-live-fault-sign-reject.md` — injected rejection, independent persisted fields, exports, and NORMAL restoration.
- `phase8-final-security.md` — current security continuation, historical scanner interpretation, and actual export scan.
- `phase8-screenshot-demo-readiness.md` — six final-build screenshots under `screenshots/phase8/`.
- `phase8-prefreeze-verification.md` — final hostile audit before closeout commit.

The screenshots show only real Devnet/test state and explicitly label injected evidence. Production-wallet compatibility is not inferred from MWA Lab's own endpoint. No Phase 8 freeze tag or exact-head CI result is claimed inside the freeze-candidate source commit.
