#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(pwd)}"
cd "$ROOT"

fail() {
  echo "PHASE 2 STATIC GATE ERROR: $*" >&2
  exit 1
}

echo "=== MWA LAB PHASE 2 STATIC / CI GATE — BATCH 2.0–2.5 ==="

./scripts/phase1_static.sh "$ROOT"

test -f app/build/outputs/apk/debug/app-debug.apk || fail "primary debug APK missing"
test -f demo-client/build/outputs/apk/debug/demo-client-debug.apk || fail "demo-client debug APK missing"

grep -Fq 'mwaWalletlib = "2.0.7"' gradle/libs.versions.toml || fail "walletlib 2.0.7 pin changed"
grep -Fq 'AUTH_ISSUER_NAME = "mwa-lab-phase1"' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "Phase 1 auth issuer compatibility changed"
grep -Fq '"mwa-lab:phase1:devnet:v1"' app/src/main/java/dev/mwalab/mwa/authorization/LabAuthorizationPolicy.kt || fail "authorization scope compatibility changed"
grep -Fq 'MAX_TRANSACTIONS_PER_SIGNING_REQUEST = 10' app/src/main/java/dev/mwalab/mwa/capabilities/MwaCapabilityProfile.kt || fail "transaction request limit not explicit"
grep -Fq 'MAX_MESSAGES_PER_SIGNING_REQUEST = 10' app/src/main/java/dev/mwalab/mwa/capabilities/MwaCapabilityProfile.kt || fail "message request limit not explicit"
grep -Fq 'MwaLabComposition.signingService' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "protected signing service not wired"
grep -Fq 'approvalCoordinator.requestApproval' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "approval coordinator not wired"
grep -Fq 'SolanaTransactionMessageDetector.isTransactionMessage' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "transaction-message rejection not wired"
grep -Fq 'request.completeWithReauthorize()' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "reauthorization path missing"
grep -Fq 'request.completeWithSignedPayloads' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "message signing success path missing"
grep -Fq 'ProtocolMethod.SIGN_MESSAGES' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "sign_messages protocol evidence missing"
grep -Fq 'private val sessionGeneration = AtomicLong(0)' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "session generation guard missing"
grep -Fq 'private fun createCallbacks(generation: Long)' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "per-association callbacks missing"
grep -Fq 'if (!isCurrentGeneration(generation)) return' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "stale lifecycle callback guard missing"
grep -Fq 'MWA LAB TEST ENDPOINT' app/src/main/java/dev/mwalab/mwa/MobileWalletAdapterActivity.kt || fail "Lab warning missing from approval Activity"
grep -Fq 'SOLANA DEVNET' app/src/main/java/dev/mwalab/mwa/MobileWalletAdapterActivity.kt || fail "Devnet warning missing from approval Activity"
grep -Fq 'NO REAL FUNDS' app/src/main/java/dev/mwalab/mwa/MobileWalletAdapterActivity.kt || fail "no-real-funds warning missing from approval Activity"

if grep -RInE '(Log\.(d|i|v|w|e)|println\().*(authToken|auth_token|privateKey|private_key|mnemonic|seed|payload)' \
  app/src/main/java demo-client/src/main/java; then
  fail "possible sensitive ad-hoc log found"
fi

if grep -RInE "(private_key|mnemonic|auth_token)[[:space:]]*[:=][[:space:]]*['\"]?[A-Za-z0-9+/=_-]{20,}" \
  app/src/main/java demo-client/src/main/java docs/evidence/phase2; then
  fail "possible raw secret value found"
fi

if grep -RIn 'api.mainnet-beta.solana.com\|solana:mainnet\|mainnet-beta' \
  app/src/main/java/dev/mwalab | grep -v 'NetworkPolicy.kt'; then
  fail "unexpected mainnet implementation path introduced"
fi

echo "PHASE 2 STATIC / CI GATE — BATCH 2.0–2.5: PASS"
