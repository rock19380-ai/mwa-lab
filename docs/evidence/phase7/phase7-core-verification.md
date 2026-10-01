# Phase 7.0–7.10 local verification

Checkpoint: phase7-sanitized-diagnostic-reports from Phase 6 commit f5eaec53d911fc2423efdb3051e8316ce9e1032d. No Phase 6 source or historical evidence was modified. Room remains schema 3; walletlib remains 2.0.7.

| Check | Result |
| --- | --- |
| python3 scripts/phase7_design_check.py | PASS |
| sha256sum -c docs/evidence/phase7/phase7-baseline.sha256 | PASS, seven files |
| python3 scripts/phase7_static.py | PASS |
| ./scripts/phase6_static.sh | PASS |
| ./gradlew :app:testDebugUnitTest --tests dev.mwalab.report.DiagnosticReportTest | PASS, eight Phase 7 test methods |
| ./gradlew test | PASS, 276 app and 10 demo-client JVM test cases |
| ./gradlew lint | PASS |
| ./gradlew assembleDebug | PASS |
| ./gradlew :app:assembleDebugAndroidTest :demo-client:assembleDebugAndroidTest | PASS, builds only |
| git diff --check | PASS |

The local JVM suite covers five independent failure truth cases, stable and changing snapshots, normal/empty sessions, capability projection, known and unknown transaction diagnostics, simulation PASS/FAIL/unavailable, ordering, Markdown/JSON parse-back, escaping, Unicode, secret sentinels, and truncation. Android instrumentation execution and live report export remain later Phase 7 work.
