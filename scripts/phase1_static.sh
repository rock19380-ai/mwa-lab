#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(pwd)}"
cd "$ROOT"

fail() {
  echo "PHASE 1 STATIC GATE ERROR: $*" >&2
  exit 1
}

echo "=== MWA LAB PHASE 1 STATIC / CI GATE ==="

test -f app/build/outputs/apk/debug/app-debug.apk \
  || fail "primary debug APK missing"

test -f demo-client/build/outputs/apk/debug/demo-client-debug.apk \
  || fail "demo-client debug APK missing"

test -f app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  || fail "primary AndroidTest APK missing"

test -f demo-client/build/outputs/apk/androidTest/debug/demo-client-debug-androidTest.apk \
  || fail "demo-client AndroidTest APK missing"

grep -Fq 'com.solanamobile:mobile-wallet-adapter-walletlib:2.0.7' \
  docs/evidence/phase1/dependency-api-pin.txt \
  || fail "walletlib 2.0.7 dependency pin evidence missing"

grep -Fq 'scenario_start=Scenario.start()' \
  docs/evidence/phase1/walletlib-2.0.7-api-authority.txt \
  || fail "pinned walletlib lifecycle authority missing"

grep -Fq 'scenario_start_async=ABSENT' \
  docs/evidence/phase1/walletlib-2.0.7-api-authority.txt \
  || fail "pinned startAsync absence missing"

grep -Fq 'android:scheme="solana-wallet"' app/src/main/AndroidManifest.xml \
  || fail "wallet association intent scheme missing"

grep -Fq 'MWA LAB TEST ENDPOINT' app/src/main/java/dev/mwalab/MainActivity.kt \
  || fail "Lab endpoint warning missing"

grep -Fq 'SOLANA DEVNET' app/src/main/java/dev/mwalab/MainActivity.kt \
  || fail "Devnet warning missing"

grep -Fq 'NO REAL FUNDS' app/src/main/java/dev/mwalab/MainActivity.kt \
  || fail "no-real-funds warning missing"

grep -Fq 'MWA Lab Demo Client — FOR TESTING ONLY' \
  demo-client/src/main/AndroidManifest.xml \
  || fail "demo-client test-only label missing"

grep -Fq 'include(":demo-client")' settings.gradle.kts \
  || fail "demo-client module missing"

grep -Fq 'MwaCapabilityProfile.createWalletConfig()' \
  app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt \
  || fail "central capability authority not wired"

grep -Fq 'DiagnosticSanitizer.sanitizeFields' \
  app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt \
  || fail "protocol evidence sanitizer not wired"

if grep -Fq 'implementation(libs.solana.mobile.clientlib)' app/build.gradle.kts; then
  fail "clientlib leaked into primary app production configuration"
fi

if grep -RInE \
  '(Log\.(d|i|v|w|e)|println\().*(authToken|auth_token|privateKey|private_key|mnemonic|seed)' \
  app/src/main/java demo-client/src/main/java
then
  fail "possible secret-bearing ad-hoc log found"
fi

if grep -RInE \
  '(auth_token|private_key|mnemonic|seed)[[:space:]]*[:=][[:space:]]*["'\'']?[A-Za-z0-9+/=_-]{20,}' \
  docs/evidence/phase1 app/src/main/java demo-client/src/main/java
then
  fail "possible raw secret value found"
fi

echo "PHASE 1 STATIC / CI GATE: PASS"
