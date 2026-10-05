# Local deterministic gates

Run on `phase11-hard-code-freeze` on 2026-10-05 (working candidate, before checkpoint commit).

| Gate | Result |
| --- | --- |
| `bash -n` for phase0–phase11 shell scripts | PASS |
| `./scripts/phase11_static.sh` (includes Phase 9, Phase 10, six Phase 11 guard unit tests, Phase 11) | PASS |
| `./gradlew lint test assembleDebug assembleRelease :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest --console=plain` | PASS, `BUILD SUCCESSFUL in 1m 48s`; 254 tasks, 6 executed and 248 UP-TO-DATE |

This local invocation used existing Gradle build caches; clean-checkout verification is recorded separately. No tests were weakened, no dependency changed, and no release-signing credentials were used. The release build here verifies unsigned buildability, **not** an RC2 signed artifact. Device instrumentation/runtime acceptance and GitHub Actions for the future exact candidate HEAD are not claimed.
