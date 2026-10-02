# Phase 8 evidence

This directory records Phase 8 UX and positioning work after the frozen Phase 7 predecessor. The baseline manifest hashes canonical Git blobs at the predecessor commit. Device, live, screenshot, compatibility, and freeze evidence must be recorded only after those checks actually run.

Current implementation/hardening evidence:

- `phase8-start.md` — exact frozen predecessor and branch start.
- `phase8-baseline.sha256` — canonical protected-authority baseline.
- `phase8-implementation-checkpoint.md` — Steps 8.0–8.13 checkpoint.
- `phase8-accessibility-device-audit.md` — 1.3× font/light/dark/layout audit.
- `phase8-positioning-audit.md` — public-copy and product-identity audit.
- `phase8-five-second-audit.md` — Home first-impression comprehension audit.
- `phase8-production-wallet-smoke.md` — current compatibility status; no unsupported compatibility claim.
- `phase8-screenshot-demo-readiness.md` — screenshot/readiness policy and observed UI audit.
- `phase8-hardening-checkpoint.md` — created only after the full Prompt-2 local/connected verification script passes.

Phase 8 remains open until the later canonical live regression, security closeout, documentation closeout, pre-freeze audit, exact-head CI success, and annotated freeze tag are complete.
