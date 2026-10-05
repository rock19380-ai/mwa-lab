# Clean-checkout reproducibility

Verified on 2026-10-05 in a separate detached worktree at `/tmp/mwa-lab-phase11-clean-94b2828`, checked out from the committed Phase 11 checkpoint `94b2828a1e948d02e742f4e72dbb1c76b0fea640`. The checkout began clean, without `local.properties` or an `app/build` directory. No untracked source, local patches, copied signing secrets, or local servers were used. The system Android SDK and shared Gradle dependency cache were available; this does **not** prove fresh-network dependency retrieval.

| Command/check | Result |
| --- | --- |
| `git status --short --branch` | `## HEAD (no branch)` before and after build; no tracked/untracked changes |
| `bash -n scripts/phase0_gate.sh … scripts/phase11_static.sh` | PASS |
| `./scripts/phase11_static.sh` | Phase 9 PASS, Phase 10 PASS, six Phase 11 unit tests PASS, Phase 11 PASS |
| `./gradlew lint test assembleDebug assembleRelease :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest --console=plain` | PASS, `BUILD SUCCESSFUL in 5m 23s`; 254 actionable tasks, **254 executed** |
| Output checks | app and Demo Client debug APKs, unsigned app release APK, and both debug AndroidTest APKs present |

Non-blocking workstation/toolchain messages: Gradle reported `Already watching path: /home/abbaas/mwa-lab` while starting the separate checkout; Android tooling could not strip the prebuilt `libandroidx.graphics.path.so`; compiler reported existing deprecated API uses in Demo Client and one instrumented test. None stopped lint/test/build. No product code or dependency was changed to suppress these warnings. This validates the committed engineering candidate (not a signed RC2 or on-device run). The final evidence-only update after this checkout does not change source/CI/gate behavior; recheck the final commit separately.
