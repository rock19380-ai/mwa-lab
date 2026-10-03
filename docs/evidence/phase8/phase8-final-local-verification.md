# Phase 8 final local verification

Date: 2026-10-03 (Asia/Yangon). Branch: `phase8-world-class-ux-positioning`.
Pre-closeout HEAD: `8c14ded3219fb03e169f6cd9c93e6898a5d72187`.

After the presentation-only approval status-bar inset repair, these commands passed:

```text
./scripts/phase8_static.sh
./gradlew lint test assembleDebug :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest --offline
git diff --check
```

The Phase 8 script ran Phase 5 design/vector/device-parser, Phase 6 design/fault/security, Phase 7 design/static/export-security, and Phase 8 design/static checks. Every applicable check passed. Gradle reported `BUILD SUCCESSFUL`; lint and both debug and AndroidTest APK builds completed.

Counts parsed from the generated XML, rather than inferred from Gradle's summary:

| Suite | XML files | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| App JVM | 43 | 287 | 0 | 0 | 0 |
| Demo-client JVM | 4 | 10 | 0 | 0 | 0 |

The local gate was rerun after `SigningApprovalScreen.kt` gained `statusBarsPadding()`. No protocol or report implementation changed. Connected and canonical live results are recorded separately.

After closeout `b70c4c0e21eee5566bc903e8e8ca893915bdc3ab` exposed an obsolete Phase 1 CI source-location assertion, a CI-only routing repair was made. The complete offline Gradle command above passed again (`BUILD SUCCESSFUL`, 165 actionable tasks: 2 executed, 163 up-to-date), as did `./scripts/phase8_static.sh`, shell syntax validation, and `git diff --check`. Fresh XML parsing again found app 287 and demo-client 10, with zero failures, errors, or skips. No app or demo code changed in this repair; connected and canonical live receipts remain for the same APKs.
