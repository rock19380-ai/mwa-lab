#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

echo '=== MWA LAB PHASE 0 LOCAL GATE ==='

git diff --check

required=(
  README.md
  LICENSE
  CHANGELOG.md
  docs/PRODUCT_POSITIONING.md
  docs/ARCHITECTURE.md
  docs/SECURITY.md
  docs/PROTOCOL_SUPPORT.md
  docs/PRIVACY.md
  docs/THREAT_MODEL.md
  docs/TESTING.md
  docs/COMPATIBILITY.md
  docs/DEMO.md
  docs/RELEASE.md
  docs/COMPETITION.md
  .github/workflows/android.yml
)

for file in "${required[@]}"; do
  test -f "$file" || {
    echo "MISSING: $file" >&2
    exit 1
  }
done

grep -q 'namespace = "dev.mwalab"' app/build.gradle.kts
grep -q 'applicationId = "dev.mwalab"' app/build.gradle.kts
grep -q 'minSdk = 23' app/build.gradle.kts

./gradlew lint
./gradlew test
./gradlew assembleDebug

APK='app/build/outputs/apk/debug/app-debug.apk'
test -f "$APK"

echo
echo '=== DEBUG APK ==='
sha256sum "$APK"

echo
echo 'PHASE 0 LOCAL GATE: PASS'
