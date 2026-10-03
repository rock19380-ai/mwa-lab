# Phase 9 Batch 1 — Local authorization consent and Demo Client disambiguation

Date: 2026-10-03 (Asia/Yangon)

## Predecessor

- Phase 8 frozen tag: `phase8-world-class-ux-positioning-2026-10-03`
- Phase 8 frozen commit: `7cb9c0da5839ee14f4ef5f45391449b5060e7ebc`
- Phase 9 branch: `phase9-first-run-connection-ux`

## Delivered

- Demo Client is explicitly identified as a test harness rather than the MWA Lab debugger.
- Demo Client exposes an explicit `OPEN MWA LAB` affordance without changing its canonical MWA acceptance role.
- Normal supported Devnet authorization crosses an explicit human Approve/Reject boundary.
- Policy/chain validation and deterministic injected authorization faults remain before human consent.
- Authorization decisions are request/session/generation bound and pending decisions are canceled on host/session replacement.
- Same-device asserted dApp metadata is not falsely presented as verified.
- Connected MWA tests explicitly drive authorization consent; production code does not auto-approve.

## Repairs discovered by device gates

1. A historical replacement-association test path was updated to drive the new explicit authorization boundary.
2. Phase 9's sanitized `human_consent` response-summary field was added to the recorder's explicit safe summary allowlist. Arbitrary and secret-bearing keys remain rejected.
3. The Demo Client cross-package acceptance helper now resolves the Compose test-tag accessibility node and/or the APPROVE text node, then walks to a clickable ancestor. This changes only instrumentation interaction; it adds no production auto-approval and no new dependency.

## Historical freeze handling

Historical Phase 7/8 static scripts remain unchanged and are executed only against a detached worktree at the frozen Phase 8 tag.

## Green gates

- targeted canonical recorder JVM regression: PASS
- targeted transaction-approval connected regression: PASS (V5)
- full `:app:connectedDebugAndroidTest`: PASS
- `:demo-client:connectedDebugAndroidTest`: PASS
- `lint test assembleDebug :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest --offline`: PASS
- frozen Phase 8 `./scripts/phase8_static.sh`: PASS
- `git diff --check`: PASS

## Status

**Batch 1 COMPLETE / GREEN.**

Next: Phase 9 Batch 2 — manual first-run UX, Home / How to Connect, Test Wallet balance,
Receive Test SOL, and Request Devnet SOL.
