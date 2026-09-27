#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-$(pwd)}"
cd "$ROOT"

fail() {
  echo "PHASE 2 STATIC GATE ERROR: $*" >&2
  exit 1
}

echo "=== MWA LAB PHASE 2 STATIC / CI GATE — BATCH 2.0–2.12 PRE-FREEZE ==="

./scripts/phase1_static.sh "$ROOT"

test -f app/build/outputs/apk/debug/app-debug.apk || fail "primary debug APK missing"
test -f demo-client/build/outputs/apk/debug/demo-client-debug.apk || fail "demo-client debug APK missing"

grep -Fq 'mwaWalletlib = "2.0.7"' gradle/libs.versions.toml || fail "walletlib 2.0.7 pin changed"
grep -Fq 'AUTH_ISSUER_NAME = "mwa-lab-phase1"' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "Phase 1 auth issuer compatibility changed"
grep -Fq '"mwa-lab:phase1:devnet:v1"' app/src/main/java/dev/mwalab/mwa/authorization/LabAuthorizationPolicy.kt || fail "authorization scope compatibility changed"
grep -Fq 'MAX_TRANSACTIONS_PER_SIGNING_REQUEST = 10' app/src/main/java/dev/mwalab/mwa/capabilities/MwaCapabilityProfile.kt || fail "transaction request limit not explicit"
grep -Fq 'MAX_MESSAGES_PER_SIGNING_REQUEST = 10' app/src/main/java/dev/mwalab/mwa/capabilities/MwaCapabilityProfile.kt || fail "message request limit not explicit"
grep -Fq 'ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS' app/src/main/java/dev/mwalab/mwa/capabilities/MwaCapabilityProfile.kt || fail "sign_transactions optional feature is not advertised to walletlib 2.0.7"
grep -Fq 'MwaLabComposition.signingService' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "protected signing service not wired"
grep -Fq 'approvalCoordinator.requestApproval' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "approval coordinator not wired"
grep -Fq 'SolanaTransactionMessageDetector.isTransactionMessage' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "transaction-message rejection not wired"
grep -Fq 'request.completeWithReauthorize()' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "reauthorization path missing"
grep -Fq 'request.completeWithSignedPayloads' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "message signing success path missing"
grep -Fq 'ProtocolMethod.SIGN_MESSAGES' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "sign_messages protocol evidence missing"
grep -Fq 'private val sessionGeneration = AtomicLong(0)' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "session generation guard missing"
grep -Fq 'private val activeAuthorizationGeneration = AtomicLong(NO_AUTHORIZATION_GENERATION)' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "session-local active authorization gate missing"
grep -Fq 'invalidateAuthorization(generation)' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "deauthorization does not invalidate session-local authority"
grep -Fq 'authorization_revoked_during_approval' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "post-approval authorization recheck missing"
grep -Fq 'private fun createCallbacks(generation: Long)' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "per-association callbacks missing"
grep -Fq 'if (!isCurrentGeneration(generation)) return' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "stale lifecycle callback guard missing"
test -f app/src/main/java/dev/mwalab/transaction/LegacyTransactionCodec.kt || fail "legacy transaction codec missing"
test -f app/src/main/java/dev/mwalab/rpc/DevnetRpcGateway.kt || fail "Devnet RPC boundary missing"
grep -Fq 'const val MAX_TRANSACTION_BYTES = 1232' app/src/main/java/dev/mwalab/transaction/LegacyTransactionCodec.kt || fail "legacy transaction size bound missing"
grep -Fq 'DEVNET_RPC_URL = "https://api.devnet.solana.com"' app/src/main/java/dev/mwalab/rpc/DevnetRpcGateway.kt || fail "fixed Devnet RPC endpoint missing"
grep -Fq 'LegacyTransactionCodec.parseForSigner' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "bounded legacy transaction parsing not wired"
grep -Fq 'rpcGateway.isBlockhashValid' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "Devnet blockhash validation not wired"
grep -Fq 'handleSignTransactions(' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "sign_transactions handler missing"
grep -Fq 'handleSignAndSendTransactions(' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "sign_and_send handler missing"
grep -Fq 'request.completeWithNotSubmitted' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "ERROR_NOT_SUBMITTED mapping missing"
grep -Fq 'request.completeWithSignatures' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "sign_and_send success mapping missing"
if grep -Fq 'record(MwaSessionEvent.SIGN_TRANSACTIONS_DECLINED_PHASE_1)' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt; then
  fail "sign_transactions is still wired to Phase 1 decline path"
fi
if grep -Fq 'record(MwaSessionEvent.SIGN_AND_SEND_DECLINED_PHASE_1)' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt; then
  fail "sign_and_send is still wired to Phase 1 decline path"
fi
test -f docs/evidence/phase2/phase2-step2.11a-hostile-contract.txt || fail "Step 2.11A hostile contract evidence missing"
grep -Fq 'association_token' app/src/main/java/dev/mwalab/security/DiagnosticSanitizer.kt || fail "association-token sanitizer guard missing"
grep -Fq 'authorization_token' app/src/main/java/dev/mwalab/security/DiagnosticSanitizer.kt || fail "authorization-token sanitizer guard missing"
grep -Fq 'raw_message_payload' app/src/main/java/dev/mwalab/security/DiagnosticSanitizer.kt || fail "raw-message sanitizer guard missing"
grep -Fq 'raw_transaction_payload' app/src/main/java/dev/mwalab/security/DiagnosticSanitizer.kt || fail "raw-transaction sanitizer guard missing"
grep -Fq 'raw_signature' app/src/main/java/dev/mwalab/security/DiagnosticSanitizer.kt || fail "raw-signature sanitizer guard missing"
grep -Fq 'concurrentApprovalIsBusyAndCannotStealDecision' app/src/test/java/dev/mwalab/approval/ApprovalCoordinatorTest.kt || fail "approval concurrency hostile test missing"
grep -Fq 'parsed transaction is bound to immutable snapshot of approved payload' app/src/test/java/dev/mwalab/transaction/LegacyTransactionCodecTest.kt || fail "transaction approval-binding hostile test missing"
grep -Fq 'missingTestnetAndUnknownChainsFailClosed' app/src/test/java/dev/mwalab/mwa/authorization/LabAuthorizationPolicyTest.kt || fail "authorization chain hostile test missing"
grep -Fq 'MWA LAB TEST ENDPOINT' app/src/main/java/dev/mwalab/mwa/MobileWalletAdapterActivity.kt || fail "Lab warning missing from approval Activity"
grep -Fq 'SOLANA DEVNET' app/src/main/java/dev/mwalab/mwa/MobileWalletAdapterActivity.kt || fail "Devnet warning missing from approval Activity"
grep -Fq 'NO REAL FUNDS' app/src/main/java/dev/mwalab/mwa/MobileWalletAdapterActivity.kt || fail "no-real-funds warning missing from approval Activity"
test -f app/src/main/java/dev/mwalab/rpc/SignAndSendSubmissionExecutor.kt || fail "sign-and-send submission executor missing"
test -f app/src/test/java/dev/mwalab/rpc/SignAndSendSubmissionExecutorTest.kt || fail "sign-and-send submission executor tests missing"
test -f app/src/androidTest/java/dev/mwalab/rpc/SolanaDevnetRpcGatewayInstrumentedTest.kt || fail "mocked Devnet RPC matrix instrumentation tests missing"
grep -Fq 'SignAndSendSubmissionExecutor(rpcGateway).execute' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "session host is not using tested submission executor"
grep -Fq 'isRequestCurrent = { isAuthorizationActive(generation) }' app/src/main/java/dev/mwalab/mwa/MwaSessionHost.kt || fail "submission executor lacks active-authorization cancellation guard"
grep -Fq 'internal fun interface DevnetHttpTransport' app/src/main/java/dev/mwalab/rpc/DevnetRpcGateway.kt || fail "deterministic Devnet transport seam missing"
grep -Fq 'FixedDevnetHttpTransport()' app/src/main/java/dev/mwalab/rpc/DevnetRpcGateway.kt || fail "fixed production Devnet transport default missing"
grep -Fq 'cancellation after first rpc completion stops remaining submissions and never resubmits' app/src/test/java/dev/mwalab/rpc/SignAndSendSubmissionExecutorTest.kt || fail "cancellation/no-resubmission hostile test missing"
grep -Fq 'dnsOrConnectionStyleIoFailureStaysClassifiedAsTransportIo' app/src/androidTest/java/dev/mwalab/rpc/SolanaDevnetRpcGatewayInstrumentedTest.kt || fail "DNS/connection failure RPC test missing"
grep -Fq 'jsonRpcErrorCodeIsPreservedWithoutResponseBodyLeakage' app/src/androidTest/java/dev/mwalab/rpc/SolanaDevnetRpcGatewayInstrumentedTest.kt || fail "JSON-RPC error matrix test missing"
test -f docs/evidence/phase2/phase2-step2.11b1-rpc-submission-contract.txt || fail "Step 2.11B1 RPC/submission contract evidence missing"
grep -Fq 'REVOKED_AUTH_REJECT("REVOKED_AUTH_REJECT")' demo-client/src/main/java/dev/mwalab/democlient/Phase2AcceptanceRunner.kt || fail "revoked authorization live scenario missing"
grep -Fq 'SINGLE_OUTSTANDING_REQUEST_ISOLATION("SINGLE_OUTSTANDING_REQUEST_ISOLATION")' demo-client/src/main/java/dev/mwalab/democlient/Phase2AcceptanceRunner.kt || fail "single-outstanding-request live isolation scenario missing"
grep -Fq 'assertErrorAuthorizationFailed' demo-client/src/main/java/dev/mwalab/democlient/Phase2AcceptanceRunner.kt || fail "revoked authorization error assertion missing"
grep -Fq 'Only a single request may be outstanding' demo-client/src/main/java/dev/mwalab/democlient/Phase2AcceptanceRunner.kt || fail "clientlib single-outstanding-request assertion missing"
grep -Fq 'Outstanding signing response crossed request payload boundaries' demo-client/src/main/java/dev/mwalab/democlient/Phase2AcceptanceRunner.kt || fail "outstanding-request payload isolation assertion missing"
test -f docs/evidence/phase2/phase2-step2.11b2-live-lifecycle-contract.txt || fail "Step 2.11B2 live lifecycle contract evidence missing"

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

test -f PHASE_2_REPORT.md || fail "Phase 2 report missing"
grep -Fq 'Phase 2 — Complete Core Request / Signing Path' PHASE_2_REPORT.md || fail "Phase 2 report title/scope marker missing"
grep -Fq 'Phase 2 verified boundary' README.md || fail "README not reconciled to Phase 2"
grep -Fq 'sign_and_send_transactions | VERIFIED' docs/PROTOCOL_SUPPORT.md || fail "protocol support does not record verified sign_and_send"
grep -Fq 'session-local active-authorization generation' docs/ARCHITECTURE.md || fail "architecture missing active-authorization boundary"
grep -Fq 'S6 — Submission fail-closed behavior' docs/SECURITY.md || fail "security doc missing submission fail-closed invariant"
grep -Fq 'Clientlib concurrency truth' docs/TESTING.md || fail "testing doc missing clientlib concurrency truth"
test -f docs/evidence/phase2/phase2-step2.11-closeout.txt || fail "Step 2.11 final closeout evidence missing"
grep -Fq 'STEP 2.11 COMPLETE / PASS' docs/evidence/phase2/phase2-step2.11-closeout.txt || fail "Step 2.11 final closeout is not PASS"
test -f docs/evidence/phase2/phase2-step2.12-documentation-contract.txt || fail "Step 2.12 documentation contract missing"
if grep -RInE 'androidx\.room|Room\.databaseBuilder|@Entity|@Database' app/src/main demo-client/src/main; then
  fail "Phase 3 persistence introduced before Phase 2 freeze"
fi

echo "PHASE 2 STATIC / CI GATE — BATCH 2.0–2.12 PRE-FREEZE: PASS"
