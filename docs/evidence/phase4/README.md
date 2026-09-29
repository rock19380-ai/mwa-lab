# Phase 4 Evidence

This directory contains executed evidence for **Phase 4 — Capability Snapshot +
Transaction Diagnostics**.

The authoritative frozen predecessor is Phase 3 commit
`913642d30c4445602b5ff7f72995e826a196938b` and tag
`phase3-protocol-recorder-timeline-2026-09-28`.

## Step 4.11 acceptance

`phase4-step4.11-local-gates.json` records the completed local build/test gates at
commit `e1529dcdc990f0f08b34127fd8eb953ec7877b84`.

`phase4-step4.11-device-acceptance.json` records the real cross-package Android
emulator acceptance on `sdk_gphone64_x86_64`, API 36. It verifies:

- System Program Transfer inspection and successful legacy signing;
- explicit Unknown Program rendering without invented semantics;
- v0 partial diagnostics followed by the unchanged legacy-only authoritative
  rejection;
- no synthetic `GET_CAPABILITIES` protocol event;
- force-stop/restart preservation of sessions, protocol events, capability
  snapshots, and transaction diagnostics.

PNG files in this directory are the corresponding pre-approval and post-restart
product screenshots.

## Step 4.14 static/doc preflight

`phase4-step4.14-preflight.json` records the frozen Phase 3 static-gate hash and
the Phase 4.11 implementation base used to build the Phase 4 static/doc gate.

`phase4-protected-sources.json` is a reviewed source-hash receipt for the
completed Phase 4.11 runtime. It is used by `scripts/phase4_static.py` to detect
unreviewed authority-boundary changes during the documentation/static-gate
closeout.

This evidence does **not** claim the final Phase 4 freeze/tag or exact-head
remote CI. Those belong to the later pre-freeze audit/final closeout.

## Pre-freeze hostile audit

- `phase4-prefreeze-hostile-audit-summary.json` records the final read/test-only hostile audit against candidate `387183f183564f885c963cac7ba72b41817101b5`.
- Audit summary SHA-256: `d5d716836c0f56a5aa920a8a62631d68b93e869dc68b698005700cc82ac0de55`.
- The audit verified fresh real cross-app discovery/execution, persisted Phase 4 acceptance evidence, static/schema/authority/security gates, and a clean tracked worktree.
- No Phase 4 product source repair was required after the hostile audit.
- The freeze tag is created only after GitHub Actions succeeds for the exact post-evidence closeout commit.
