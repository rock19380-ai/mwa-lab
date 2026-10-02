# Phase 8 implementation checkpoint: Steps 8.0–8.13

This is an implementation checkpoint, not a Phase 8 freeze or completion claim. It starts from the exact Phase 7 frozen commit `d4fbe20ff2d4b63d6f531f2a9cd7d6fadbe8a7e1` on `phase8-world-class-ux-positioning`. The Phase 7 annotated tag still dereferences to that commit. The canonical frozen-file baseline is recorded in `phase8-baseline.sha256`.

## Implemented in this pass

- Step 8.0–8.1: exact predecessor/branch/baseline and UX positioning design freeze.
- Step 8.2–8.3: explicit light/dark design system, reusable diagnostic components, five typed top-level destinations, and Session Detail as a child destination.
- Step 8.4–8.5: first-run onboarding and a Devnet-only Home dashboard built from persisted identity, fault, and session state.
- Step 8.6–8.8: scannable session history, failure-first Session Detail hero and timeline, subordinate transaction/simulation diagnostics, and sanitized details collapsed by default.
- Step 8.9–8.10: visible active-fault warning and Return to Normal action; presentation polish around the existing signing approval authority.
- Step 8.11–8.12: read-only Devnet public identity and safe appearance/settings information. UI preferences store only `onboarding_seen_v1` and `theme_mode`.
- Step 8.13: intentional loading, missing, and error states with retry where the current repository exposes a retry operation; child diagnostic failures remain separate from parent protocol outcomes.

Identity reset is deliberately **not exposed**. Existing code/tests do not prove a reset cannot leave stale authorization or token state, so the public identity screen shows address and copy only.

## Verification on this checkpoint

- `./gradlew test --offline`: PASS.
- `./gradlew lint --offline`: PASS.
- `./gradlew assembleDebug --offline`: PASS.
- `./gradlew test lint assembleDebug :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest --offline`: PASS.
- `./gradlew :app:connectedDebugAndroidTest --offline`: PASS, 135 tests, 0 failures/errors/skips, after final source edits.
- `./gradlew :demo-client:connectedDebugAndroidTest --offline`: PASS, 1 test. Its first attempt failed because the app connected runner had removed the MWA Lab wallet package from the emulator. Reinstalling `app-debug.apk` restored the required endpoint and the rerun passed; no code change was made for this setup failure.
- Phase 6 design/fault-vector/security and Phase 7 design/static/export-security scripts: PASS.
- `git diff --check`: PASS.
- Canonical Phase 7 tag, 14 Git-blob baseline hashes, frozen authority-file worktree hashes, Room schema 3, and walletlib 2.0.7: PASS.

No Phase 8 canonical live wallet regression, production-wallet compatibility claim, screenshot acceptance, accessibility device matrix, CI routing, or freeze tag is claimed here. Steps 8.14 onward remain for later work.
