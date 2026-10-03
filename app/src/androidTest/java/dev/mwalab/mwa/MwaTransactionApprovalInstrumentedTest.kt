package dev.mwalab.mwa

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterServer
import com.solana.mobilewalletadapter.walletlib.scenario.DeauthorizedEvent
import com.solana.mobilewalletadapter.walletlib.scenario.LocalScenario
import dev.mwalab.app.MwaLabComposition
import dev.mwalab.faults.FaultCatalog
import dev.mwalab.faults.FaultId
import dev.mwalab.faults.FaultProfile
import dev.mwalab.faults.FaultSelectionRepository
import dev.mwalab.approval.*
import dev.mwalab.identity.TestEndpointIdentity
import dev.mwalab.mwa.evidence.MwaSessionEvidence
import dev.mwalab.mwa.evidence.MwaSessionEvent
import dev.mwalab.mwa.association.AssociationOpenResult
import dev.mwalab.protocol.*
import dev.mwalab.protocol.recorder.*
import dev.mwalab.rpc.*
import dev.mwalab.security.DiagnosticSanitizer
import dev.mwalab.session.*
import dev.mwalab.signing.LabSigningService
import dev.mwalab.storage.*
import dev.mwalab.transaction.*
import dev.mwalab.simulation.*
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.bouncycastle.math.ec.rfc8032.Ed25519
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MwaTransactionApprovalInstrumentedTest {
    @Test
    fun signTransactionsShowsCorrectBoundSummariesBeforeSigningAndPreservesSignatureSlots() {
        Fixture().use { f ->
            val payloads = arrayOf(f.transaction(), f.transaction(knownProgram = false))
            val pending = f.sign(false, payloads)
            val request = f.awaitApproval().request
            val summaries = request.transactionSummaries!!.summaries.map { it!! }
            assertEquals(listOf(0, 1), summaries.map { it.payloadIndex })
            assertEquals(payloads.map { DiagnosticSanitizer.sha256(it) }, request.payloadFingerprints)
            assertEquals(request.payloadFingerprints, summaries.map { it.fingerprintSha256 })
            assertEquals(listOf(1, 1), summaries.map { it.requiredSignerCount })
            assertTrue(summaries[0].instructions!!.single().decodedInstruction is DecodedInstruction.SystemTransfer)
            assertEquals(DecodedInstruction.Unknown, summaries[1].instructions!!.single().decodedInstruction)
            assertEquals(listOf(request.sessionId, request.sessionId), summaries.map { it.sessionId })
            assertEquals(listOf(request.sessionId + ":2", request.sessionId + ":2"), summaries.map { it.eventId })
            assertEquals(0, f.signCount)
            assertTrue(f.submitted.isEmpty())
            assertEquals(listOf("begin:AUTHORIZE", "begin:SIGN_TRANSACTIONS", "inspect:0", "inspect:1",
                "blockhash", "blockhash"), f.order.toList())
            assertTrue(f.approvals.approve(request.requestId))
            val signed = (pending.get(10, TimeUnit.SECONDS) as MobileWalletAdapterClient.SignPayloadsResult).signedPayloads
            signed.forEachIndexed { index, bytes -> f.verifySigned(payloads[index], bytes) }
            f.assertEvent(ProtocolMethod.SIGN_TRANSACTIONS, ProtocolOutcome.SUCCESS, null, "signed")
            assertTrue(f.submitted.isEmpty())
            f.assertDiagnosticRowCount(2)
            f.assertNoAuthTokensInStorage()
        }
    }

    @Test
    fun signAndSendRetainsSignedPayloadsSignatureVerificationOptionsAndCommitmentOrder() {
        Fixture().use { f ->
            val payloads = arrayOf(f.transaction(), f.transaction(knownProgram = false))
            val pending = f.sign(true, payloads)
            val request = f.awaitApproval().request
            assertEquals(listOf(0, 1), request.transactionSummaries!!.summaries.map { it!!.payloadIndex })
            assertEquals(0, f.signCount); assertTrue(f.submitted.isEmpty())
            assertTrue(f.approvals.approve(request.requestId))
            val result = pending.get(10, TimeUnit.SECONDS) as MobileWalletAdapterClient.SignAndSendTransactionsResult
            assertEquals(2, result.signatures.size)
            assertEquals(2, f.submitted.size)
            f.submitted.forEachIndexed { index, bytes ->
                f.verifySigned(payloads[index], bytes)
                assertArrayEquals(bytes.copyOfRange(1, 65), result.signatures[index])
            }
            assertEquals(listOf("send", "commitment:confirmed", "send", "commitment:confirmed"),
                f.order.filter { it == "send" || it.startsWith("commitment:") })
            assertTrue(f.options.all { it == DevnetSendOptions(17, "confirmed", false, 2, true) })
            f.assertEvent(ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS, ProtocolOutcome.SUCCESS, null, "submitted")
            f.assertDiagnosticRowCount(2)
            f.assertNoAuthTokensInStorage()
        }
    }

    @Test
    fun transactionApprovalRejectionKeepsOriginalNotSignedErrorsAndNeverSignsOrSubmits() {
        Fixture().use { f ->
            for (send in listOf(false, true)) {
                val pending = f.sign(send, arrayOf(f.transaction()))
                assertNotNull(f.awaitApproval().request.transactionSummaries)
                assertTrue(f.approvals.reject(f.awaitApproval().request.requestId))
                assertRemoteError(pending, ProtocolContract.ERROR_NOT_SIGNED)
                f.assertEvent(method(send), ProtocolOutcome.FAILURE, ProtocolContract.ERROR_NOT_SIGNED, "rejected")
            }
            assertEquals(0, f.signCount); assertTrue(f.submitted.isEmpty())
            f.assertOrderedEvents(listOf(ProtocolMethod.AUTHORIZE, ProtocolMethod.SIGN_TRANSACTIONS,
                ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS))
        }
    }

    @Test
    fun malformedWrongSignerAndV0RequestsRetainAuthoritativeRejectionWithoutApproval() {
        Fixture().use { f ->
            val invalid = listOf(byteArrayOf(1, 2, 3, 4, 5),
                TransactionApprovalTestVectors.systemTransfer(ByteArray(32) { 0x33 }),
                f.transaction(versioned = true))
            for (send in listOf(false, true)) {
                for ((index, bytes) in invalid.withIndex()) {
                    assertRemoteError(f.sign(send, arrayOf(bytes)), ProtocolContract.ERROR_INVALID_PAYLOADS)
                    f.assertEvent(method(send), ProtocolOutcome.FAILURE, ProtocolContract.ERROR_INVALID_PAYLOADS,
                        "invalid_legacy_transaction")
                    assertEquals(ApprovalState.Idle, f.approvals.state.value)
                    val diagnostic = f.inspected.last()
                    assertEquals(when (index) { 0 -> TransactionInspectionStatus.MALFORMED
                        1 -> TransactionInspectionStatus.PARSED
                        else -> TransactionInspectionStatus.PARTIAL }, diagnostic.inspectionStatus)
                    if (index == 2) assertEquals(TransactionVersion.V0, diagnostic.transactionVersion)
                }
            }
            assertEquals(0, f.signCount)
            assertFalse(f.order.contains("blockhash")); assertTrue(f.submitted.isEmpty())
        }
    }

    @Test
    fun malformedDiagnosticHeaderCannotRejectARequestAcceptedByFrozenCodec() {
        Fixture().use { f ->
            for (send in listOf(false, true)) {
                val pending = f.sign(send, arrayOf(f.transaction(readonlyPayer = true)))
                val request = f.awaitApproval().request
                assertEquals(TransactionInspectionStatus.MALFORMED,
                    request.transactionSummaries!!.summaries.single()!!.inspectionStatus)
                assertTrue(f.approvals.approve(request.requestId))
                assertNotNull(pending.get(10, TimeUnit.SECONDS))
                f.assertEvent(method(send), ProtocolOutcome.SUCCESS, null, if (send) "submitted" else "signed")
            }
        }
    }

    @Test
    fun inspectorExceptionPreservesBothTransactionResultsAndMessageSigningNeverUsesInspector() {
        var inspected = 0
        val throwing = object : TransactionInspection {
            override fun inspect(transaction: ByteArray, payloadIndex: Int, binding: TransactionDiagnosticBinding?): TransactionSummary {
                inspected++
                error("RAW_DIAGNOSTIC_EXCEPTION_NEVER_DISPLAY")
            }
        }
        Fixture(throwing).use { f ->
            for (send in listOf(false, true)) {
                val pending = f.sign(send, arrayOf(f.transaction()))
                val request = f.awaitApproval().request
                assertNull(request.transactionSummaries!!.summaries.single())
                assertTrue(f.approvals.approve(request.requestId))
                assertNotNull(pending.get(10, TimeUnit.SECONDS))
                f.assertEvent(method(send), ProtocolOutcome.SUCCESS, null, if (send) "submitted" else "signed")
                assertRemoteError(f.sign(send, arrayOf(byteArrayOf(1, 2, 3))), ProtocolContract.ERROR_INVALID_PAYLOADS)
            }
            val before = inspected
            val message = "PHASE48_MESSAGE_BEHAVIOR_UNCHANGED".encodeToByteArray()
            @Suppress("DEPRECATION")
            val pending = f.client.signMessages(arrayOf(message), arrayOf(f.publicKey))
            val request = f.awaitApproval().request
            assertNull(request.transactionSummaries)
            assertEquals(listOf(DiagnosticSanitizer.sha256(message)), request.payloadFingerprints)
            assertTrue(f.approvals.approve(request.requestId))
            val signed = pending.get(10, TimeUnit.SECONDS).signedPayloads.single()
            assertArrayEquals(message, signed.copyOfRange(0, message.size))
            assertTrue(Ed25519.verify(signed.copyOfRange(message.size, signed.size), 0,
                f.publicKey, 0, message, 0, message.size))
            assertEquals(before, inspected)
            f.assertEvent(ProtocolMethod.SIGN_MESSAGES, ProtocolOutcome.SUCCESS, null, "signed")
            f.assertNoDiagnosticWrites()
        }
    }

    @Test
    fun authorizationRevokedDuringTransactionApprovalStillWinsOverApprove() {
        Fixture().use { f ->
            for (send in listOf(false, true)) {
                if (send) f.authorize()
                val pending = f.sign(send, arrayOf(f.transaction()))
                val request = f.awaitApproval().request
                f.revokeThroughPinnedWalletlibCallback(request.sessionId)
                assertTrue(f.approvals.approve(request.requestId))
                assertRemoteError(pending, ProtocolContract.ERROR_AUTHORIZATION_FAILED)
                f.assertEvent(method(send), ProtocolOutcome.FAILURE, ProtocolContract.ERROR_AUTHORIZATION_FAILED,
                    "authorization_revoked_during_approval")
            }
            assertEquals(0, f.signCount); assertTrue(f.submitted.isEmpty())
            f.assertOrderedEvents(listOf(ProtocolMethod.AUTHORIZE, ProtocolMethod.SIGN_TRANSACTIONS,
                ProtocolMethod.DEAUTHORIZE, ProtocolMethod.AUTHORIZE, ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
                ProtocolMethod.DEAUTHORIZE))
        }
    }

    @Test
    fun inspectorCannotBypassDevnetBlockhashValidationOrChangeItsErrorMapping() {
        Fixture().use { f ->
            f.blockhashValid = false
            for (send in listOf(false, true)) {
                assertRemoteError(f.sign(send, arrayOf(f.transaction())), ProtocolContract.ERROR_INVALID_PAYLOADS)
                assertEquals(TransactionInspectionStatus.PARSED, f.inspected.last().inspectionStatus)
                f.assertEvent(method(send), ProtocolOutcome.FAILURE, ProtocolContract.ERROR_INVALID_PAYLOADS,
                    "blockhash_not_valid_on_devnet", ProtocolFailureSource.OBSERVED_PROTOCOL)
            }
            assertEquals(ApprovalState.Idle, f.approvals.state.value)
            assertEquals(0, f.signCount); assertTrue(f.submitted.isEmpty())
        }
    }

    @Test
    fun diagnosticDaoFailureCannotChangeSigningSubmissionRejectionOrMalformedWireResults() {
        Fixture().use { f ->
            f.failDiagnosticInserts()
            for (send in listOf(false, true)) {
                val approved = f.sign(send, arrayOf(f.transaction()))
                assertTrue(f.approvals.approve(f.awaitApproval().request.requestId))
                assertNotNull(approved.get(10, TimeUnit.SECONDS))
                f.assertEvent(method(send), ProtocolOutcome.SUCCESS, null, if (send) "submitted" else "signed")
                val rejected = f.sign(send, arrayOf(f.transaction()))
                assertTrue(f.approvals.reject(f.awaitApproval().request.requestId))
                assertRemoteError(rejected, ProtocolContract.ERROR_NOT_SIGNED)
                f.assertEvent(method(send), ProtocolOutcome.FAILURE, ProtocolContract.ERROR_NOT_SIGNED, "rejected")
                assertRemoteError(f.sign(send, arrayOf(byteArrayOf(1))), ProtocolContract.ERROR_INVALID_PAYLOADS)
                f.assertEvent(method(send), ProtocolOutcome.FAILURE, ProtocolContract.ERROR_INVALID_PAYLOADS, "invalid_legacy_transaction")
            }
            f.awaitFailures("transaction_diagnostics", 6)
            assertEquals(2, f.signCount); assertEquals(1, f.submitted.size)
            f.assertNoDiagnosticWrites()
            f.assertNoAuthTokensInStorage()
        }
    }

    @Test
    fun canonicalEventWriteFailurePreservesWireResultsAndNeverCreatesOrphanDiagnostics() {
        Fixture().use { f ->
            f.failTransactionEventInserts()
            for (send in listOf(false, true)) {
                val pending = f.sign(send, arrayOf(f.transaction()))
                assertTrue(f.approvals.approve(f.awaitApproval().request.requestId))
                assertNotNull(pending.get(10, TimeUnit.SECONDS))
                f.awaitFailures("protocol_complete_" + method(send).wireName, 1)
            }
            f.assertOnlyAuthorizationPersisted()
            f.assertNoDiagnosticWrites()
            assertFalse(f.evidence.any { it.detail == "transaction_diagnostics" })
            assertEquals(2, f.signCount); assertEquals(1, f.submitted.size)
        }
    }

    @Test
    fun simulationPassRejectAndFailApproveKeepBothWireMethodsAuthoritative() {
        Fixture().use { f ->
            for (send in listOf(false, true)) {
                f.simulationResponse = DevnetRpcResult.Success(SimulationRpcValue(91, null,
                    BoundedLogs(listOf("Program log: bounded"), false), 42))
                val signedBeforeSimulation = f.signCount
                val submissionsBeforeSimulation = f.submitted.size
                var pending = f.sign(send, arrayOf(f.transaction()))
                var request = f.awaitApproval().request
                var ref = request.simulationTargets.single()!!
                assertTrue(f.simulator.simulate(ref))
                assertEquals(SimulationOutcome.PASS, f.awaitSimulation(ref).outcome)
                assertEquals(signedBeforeSimulation, f.signCount)
                assertEquals(submissionsBeforeSimulation, f.submitted.size)
                assertTrue(f.approvals.reject(request.requestId))
                assertRemoteError(pending, ProtocolContract.ERROR_NOT_SIGNED)
                f.assertEvent(method(send), ProtocolOutcome.FAILURE, ProtocolContract.ERROR_NOT_SIGNED, "rejected")
                assertEquals(SimulationOutcome.PASS, f.awaitStoredSimulation(ref).outcome)

                f.simulationResponse = DevnetRpcResult.Success(SimulationRpcValue(92,
                    SimulationErrorSummary(SimulationErrorKind.INSTRUCTION_ERROR, 0, "Custom", 42),
                    BoundedLogs(emptyList(), false), 43))
                pending = f.sign(send, arrayOf(f.transaction()))
                request = f.awaitApproval().request
                ref = request.simulationTargets.single()!!
                assertTrue(f.simulator.simulate(ref))
                assertEquals(SimulationOutcome.FAIL, f.awaitSimulation(ref).outcome)
                val submittedBeforeApproval = f.submitted.size
                assertTrue(f.approvals.approve(request.requestId))
                assertNotNull(pending.get(10, TimeUnit.SECONDS))
                f.assertEvent(method(send), ProtocolOutcome.SUCCESS, null, if (send) "submitted" else "signed")
                assertEquals(submittedBeforeApproval + if (send) 1 else 0, f.submitted.size)
                assertEquals(SimulationOutcome.FAIL, f.awaitStoredSimulation(ref).outcome)
            }
            assertEquals(4, f.simulationCalls.size)
            f.assertNoAuthTokensInStorage()
        }
    }

    @Test
    fun unavailableSimulationAndLateInFlightResultNeverChangeApprovalOrParent() {
        Fixture().use { f ->
            f.simulationResponse = DevnetRpcResult.TransportFailure(TransportFailureReason.HTTP)
            var pending = f.sign(false, arrayOf(f.transaction()))
            var request = f.awaitApproval().request
            var ref = request.simulationTargets.single()!!
            assertTrue(f.simulator.simulate(ref))
            assertEquals(SimulationOutcome.UNAVAILABLE, f.awaitSimulation(ref).outcome)
            assertTrue(f.approvals.approve(request.requestId))
            assertNotNull(pending.get(10, TimeUnit.SECONDS))
            f.assertEvent(ProtocolMethod.SIGN_TRANSACTIONS, ProtocolOutcome.SUCCESS, null, "signed")
            assertEquals(ProtocolFailureSource.RPC_NETWORK, f.awaitStoredSimulation(ref).failureSource)

            val gate = CompletableDeferred<Unit>()
            f.simulationGate = gate
            f.simulationResponse = DevnetRpcResult.Success(SimulationRpcValue(99,
                SimulationErrorSummary(SimulationErrorKind.BLOCKHASH_NOT_FOUND),
                BoundedLogs(emptyList(), false), null))
            pending = f.sign(false, arrayOf(f.transaction()))
            request = f.awaitApproval().request
            ref = request.simulationTargets.single()!!
            assertTrue(f.simulator.simulate(ref))
            f.awaitSimulationCallCount(2)
            assertFalse(f.simulator.simulate(ref))
            assertTrue(f.approvals.reject(request.requestId))
            assertRemoteError(pending, ProtocolContract.ERROR_NOT_SIGNED)
            f.assertEvent(ProtocolMethod.SIGN_TRANSACTIONS, ProtocolOutcome.FAILURE,
                ProtocolContract.ERROR_NOT_SIGNED, "rejected")
            gate.complete(Unit)
            assertEquals(SimulationOutcome.FAIL, f.awaitStoredSimulation(ref).outcome)
            assertFalse(f.simulator.simulate(ref))
            assertTrue(f.submitted.isEmpty())
        }
    }

    @Test
    fun simulationPersistenceAndParentFailureAreIsolatedFromWireResult() {
        Fixture().use { f ->
            f.failSimulationInserts()
            var pending = f.sign(false, arrayOf(f.transaction()))
            var request = f.awaitApproval().request
            var ref = request.simulationTargets.single()!!
            assertTrue(f.simulator.simulate(ref))
            f.awaitSimulation(ref)
            assertTrue(f.approvals.approve(request.requestId))
            assertNotNull(pending.get(10, TimeUnit.SECONDS))
            f.assertEvent(ProtocolMethod.SIGN_TRANSACTIONS, ProtocolOutcome.SUCCESS, null, "signed")
            f.awaitFailures("simulation_results", 1)

            f.failTransactionEventInserts()
            pending = f.sign(false, arrayOf(f.transaction()))
            request = f.awaitApproval().request
            ref = request.simulationTargets.single()!!
            assertTrue(f.simulator.simulate(ref))
            f.awaitSimulation(ref)
            assertTrue(f.approvals.reject(request.requestId))
            assertRemoteError(pending, ProtocolContract.ERROR_NOT_SIGNED)
            f.awaitFailures("protocol_complete_sign_transactions", 1)
            assertTrue(runBlocking { f.simulationRepository.getForEvent(ref.sessionId, ref.eventId) }.isEmpty())
        }
    }

    @Test
    fun phase6AuthorizationFaultsUsePinnedCallbacksAndPreserveDevnetRequest() {
        Fixture().use { f ->
            f.selectFault(FaultId.AUTH_REJECT)
            assertRemoteError(f.authorizeAgain(), ProtocolContract.ERROR_AUTHORIZATION_FAILED)
            f.assertInjectedEvent(ProtocolMethod.AUTHORIZE, FaultId.AUTH_REJECT,
                ProtocolContract.ERROR_AUTHORIZATION_FAILED, "injected_auth_reject")
            f.selectFault(FaultId.UNSUPPORTED_CHAIN)
            assertRemoteError(f.authorizeAgain(), ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED)
            f.assertInjectedEvent(ProtocolMethod.AUTHORIZE, FaultId.UNSUPPORTED_CHAIN,
                ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED, "injected_unsupported_chain")
            assertEquals(ProtocolContract.CHAIN_SOLANA_DEVNET,
                f.awaitLatestEvent(ProtocolMethod.AUTHORIZE, "injected_unsupported_chain").requestSummary["chain"])
            f.selectFault(FaultId.AUTH_REJECT)
            assertRemoteError(f.authorizeAgain(f.latestAuthToken()), ProtocolContract.ERROR_AUTHORIZATION_FAILED)
            f.assertInjectedEvent(ProtocolMethod.REAUTHORIZE, FaultId.AUTH_REJECT,
                ProtocolContract.ERROR_AUTHORIZATION_FAILED, "injected_auth_reject")
            f.selectFault(FaultId.NORMAL)
            val normalAuthorization = f.authorizeAgain()
            AuthorizationApprovalTestDriver.approveNext()
            assertNotNull(normalAuthorization.get(10, TimeUnit.SECONDS))
            assertEquals(0, f.signCount)
            assertTrue(f.submitted.isEmpty())
        }
    }

    @Test
    fun phase6ValidationFaultsKeepRealPayloadCountAcrossAllSigningMethods() {
        Fixture().use { f ->
            for ((id, code, result) in listOf(
                Triple(FaultId.INVALID_PAYLOAD, ProtocolContract.ERROR_INVALID_PAYLOADS, "injected_invalid_payload"),
                Triple(FaultId.TOO_MANY_PAYLOADS, ProtocolContract.ERROR_TOO_MANY_PAYLOADS, "injected_too_many_payloads"),
            )) {
                f.selectFault(id)
                val message = "phase6-valid-message".encodeToByteArray()
                @Suppress("DEPRECATION")
                val messageRequest = f.client.signMessages(arrayOf(message), arrayOf(f.publicKey))
                assertRemoteError(messageRequest, code)
                f.assertInjectedEvent(ProtocolMethod.SIGN_MESSAGES, id, code, result, 1)
                for (send in listOf(false, true)) {
                    assertRemoteError(f.sign(send, arrayOf(f.transaction())), code)
                    f.assertInjectedEvent(method(send), id, code, result, 1)
                }
                assertEquals(ApprovalState.Idle, f.approvals.state.value)
            }
            assertEquals(0, f.signCount)
            assertTrue(f.submitted.isEmpty())
            f.selectFault(FaultId.NORMAL)
            @Suppress("DEPRECATION")
            val normal = f.client.signMessages(arrayOf("normal".encodeToByteArray()), arrayOf(f.publicKey))
            assertTrue(f.approvals.approve(f.awaitApproval().request.requestId))
            assertNotNull(normal.get(10, TimeUnit.SECONDS))
            assertNull(f.awaitLatestEvent(ProtocolMethod.SIGN_MESSAGES, "signed").injectedFaultId)
        }
    }

    @Test
    fun phase6SyntheticAndManualSigningRejectionHaveDifferentEvidence() {
        Fixture().use { f ->
            f.selectFault(FaultId.SIGN_REJECT)
            @Suppress("DEPRECATION")
            val message = f.client.signMessages(arrayOf("valid".encodeToByteArray()), arrayOf(f.publicKey))
            assertRemoteError(message, ProtocolContract.ERROR_NOT_SIGNED)
            f.assertInjectedEvent(ProtocolMethod.SIGN_MESSAGES, FaultId.SIGN_REJECT,
                ProtocolContract.ERROR_NOT_SIGNED, "injected_sign_reject")
            for (send in listOf(false, true)) {
                assertRemoteError(f.sign(send, arrayOf(f.transaction())), ProtocolContract.ERROR_NOT_SIGNED)
                f.assertInjectedEvent(method(send), FaultId.SIGN_REJECT,
                    ProtocolContract.ERROR_NOT_SIGNED, "injected_sign_reject")
            }
            assertEquals(0, f.signCount)
            assertTrue(f.submitted.isEmpty())
            f.selectFault(FaultId.NORMAL)
            @Suppress("DEPRECATION")
            val manual = f.client.signMessages(arrayOf("valid".encodeToByteArray()), arrayOf(f.publicKey))
            assertTrue(f.approvals.reject(f.awaitApproval().request.requestId))
            assertRemoteError(manual, ProtocolContract.ERROR_NOT_SIGNED)
            val observed = f.awaitLatestEvent(ProtocolMethod.SIGN_MESSAGES, "rejected")
            assertEquals(ProtocolFailureSource.OBSERVED_PROTOCOL, observed.failureSource)
            assertNull(observed.injectedFaultId)
        }
    }

    @Test
    fun phase6StaleBlockhashRejectsParsedTransactionsBeforeRpcOrSigning() {
        Fixture().use { f ->
            f.selectFault(FaultId.STALE_BLOCKHASH)
            for (send in listOf(false, true)) {
                assertRemoteError(f.sign(send, arrayOf(f.transaction())), ProtocolContract.ERROR_INVALID_PAYLOADS)
                f.assertInjectedEvent(method(send), FaultId.STALE_BLOCKHASH,
                    ProtocolContract.ERROR_INVALID_PAYLOADS, "injected_stale_blockhash", 1)
            }
            assertFalse(f.order.contains("blockhash"))
            assertEquals(0, f.signCount)
            assertTrue(f.submitted.isEmpty())
            f.selectFault(FaultId.NORMAL)
            val normal = f.sign(false, arrayOf(f.transaction()))
            assertTrue(f.approvals.approve(f.awaitApproval().request.requestId))
            assertNotNull(normal.get(10, TimeUnit.SECONDS))
            assertTrue(f.order.contains("blockhash"))
        }
    }

    @Test
    fun phase6SubmissionFaultsSignAfterApprovalButNeverSend() {
        Fixture().use { f ->
            for ((id, result) in listOf(
                FaultId.RPC_UNAVAILABLE to "injected_rpc_unavailable",
                FaultId.SUBMISSION_FAILURE to "injected_submission_failure",
            )) {
                f.selectFault(id)
                val pending = f.sign(true, arrayOf(f.transaction()))
                assertTrue(f.approvals.approve(f.awaitApproval().request.requestId))
                assertRemoteError(pending, ProtocolContract.ERROR_NOT_SUBMITTED)
                f.assertInjectedEvent(ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS, id,
                    ProtocolContract.ERROR_NOT_SUBMITTED, result, 1)
                assertTrue(f.submitted.isEmpty())
            }
            assertEquals(2, f.signCount)
            assertFalse(f.order.contains("send"))
            f.selectFault(FaultId.NORMAL)
            val normal = f.sign(true, arrayOf(f.transaction()))
            assertTrue(f.approvals.approve(f.awaitApproval().request.requestId))
            assertNotNull(normal.get(10, TimeUnit.SECONDS))
            assertEquals(1, f.submitted.size)
        }
    }

    @Test
    fun phase6DelayRetainsAppliedIdForSuccessAndObservedManualRejection() {
        Fixture().use { f ->
            f.selectFault(FaultId.DELAY_5S)
            @Suppress("DEPRECATION")
            val success = f.client.signMessages(arrayOf("delay success".encodeToByteArray()), arrayOf(f.publicKey))
            assertTrue(f.approvals.approve(f.awaitApproval().request.requestId))
            assertNotNull(success.get(10, TimeUnit.SECONDS))
            val succeeded = f.awaitLatestEvent(ProtocolMethod.SIGN_MESSAGES, "signed")
            assertEquals(FaultId.DELAY_5S.stableId, succeeded.injectedFaultId)
            assertEquals(ProtocolOutcome.SUCCESS, succeeded.outcome)
            assertEquals(ProtocolFailureSource.NONE, succeeded.failureSource)
            assertTrue(succeeded.completedAtEpochMillis - succeeded.startedAtEpochMillis >= 4_900)
            @Suppress("DEPRECATION")
            val rejected = f.client.signMessages(arrayOf("delay reject".encodeToByteArray()), arrayOf(f.publicKey))
            assertTrue(f.approvals.reject(f.awaitApproval().request.requestId))
            assertRemoteError(rejected, ProtocolContract.ERROR_NOT_SIGNED)
            val observed = f.awaitLatestEvent(ProtocolMethod.SIGN_MESSAGES, "rejected")
            assertEquals(FaultId.DELAY_5S.stableId, observed.injectedFaultId)
            assertEquals(ProtocolFailureSource.OBSERVED_PROTOCOL, observed.failureSource)
        }
    }

    @Test
    fun phase6DelayKeepsRequestSnapshotWhenSelectionChangesAndNextRequestIsNormal() {
        Fixture().use { f ->
            f.selectFault(FaultId.DELAY_5S)
            @Suppress("DEPRECATION")
            val delayed = f.client.signMessages(arrayOf("snapshot first".encodeToByteArray()), arrayOf(f.publicKey))
            f.awaitFaultMark(FaultId.DELAY_5S)
            f.selectFault(FaultId.NORMAL)
            assertTrue(f.approvals.approve(f.awaitApproval().request.requestId))
            assertNotNull(delayed.get(10, TimeUnit.SECONDS))
            assertEquals(FaultId.DELAY_5S.stableId,
                f.awaitLatestEvent(ProtocolMethod.SIGN_MESSAGES, "signed").injectedFaultId)
            @Suppress("DEPRECATION")
            val next = f.client.signMessages(arrayOf("snapshot next".encodeToByteArray()), arrayOf(f.publicKey))
            assertTrue(f.approvals.approve(f.awaitApproval().request.requestId))
            assertNotNull(next.get(10, TimeUnit.SECONDS))
            assertNull(f.awaitLatestEvent(ProtocolMethod.SIGN_MESSAGES, "signed").injectedFaultId)
        }
    }

    @Test
    fun phase6SessionCloseDuringDelayPreservesFaultOnSingleCancelledEvent() {
        Fixture().use { f ->
            f.selectFault(FaultId.DELAY_5S)
            @Suppress("DEPRECATION")
            val pending = f.client.signMessages(arrayOf("close during delay".encodeToByteArray()), arrayOf(f.publicKey))
            f.awaitFaultMark(FaultId.DELAY_5S)
            f.closeAssociation()
            runCatching { pending.get(10, TimeUnit.SECONDS) }
            val event = f.awaitLastEvent(ProtocolMethod.SIGN_MESSAGES)
            assertEquals(FaultId.DELAY_5S.stableId, event.injectedFaultId)
            assertEquals(ProtocolOutcome.CANCELLED, event.outcome)
            assertEquals(0, f.signCount)
            assertTrue(f.submitted.isEmpty())
            assertEquals(ApprovalState.Idle, f.approvals.state.value)
        }
    }

    @Test
    fun phase6HostDestroyDuringDelayCancelsWithoutSigning() {
        Fixture().use { f ->
            f.selectFault(FaultId.DELAY_5S)
            @Suppress("DEPRECATION")
            val pending = f.client.signMessages(arrayOf("destroy during delay".encodeToByteArray()), arrayOf(f.publicKey))
            f.awaitFaultMark(FaultId.DELAY_5S)
            f.closeHost()
            runCatching { pending.get(10, TimeUnit.SECONDS) }
            val event = f.awaitLastEvent(ProtocolMethod.SIGN_MESSAGES)
            assertEquals(FaultId.DELAY_5S.stableId, event.injectedFaultId)
            assertEquals(ProtocolOutcome.CANCELLED, event.outcome)
            assertEquals(0, f.signCount)
            assertTrue(f.submitted.isEmpty())
        }
    }

    @Test
    fun phase6AuthorizationRevocationDuringDelayCannotReachApprovalOrSigning() {
        Fixture().use { f ->
            f.selectFault(FaultId.DELAY_5S)
            @Suppress("DEPRECATION")
            val pending = f.client.signMessages(arrayOf("revoke during delay".encodeToByteArray()), arrayOf(f.publicKey))
            f.awaitFaultMark(FaultId.DELAY_5S)
            f.revokeThroughPinnedWalletlibCallback(f.currentSessionId())
            assertRemoteError(pending, ProtocolContract.ERROR_AUTHORIZATION_FAILED)
            val event = f.awaitLatestEvent(ProtocolMethod.SIGN_MESSAGES, "authorization_revoked_after_fault_delay")
            assertEquals(FaultId.DELAY_5S.stableId, event.injectedFaultId)
            assertEquals(ProtocolFailureSource.OBSERVED_PROTOCOL, event.failureSource)
            assertEquals(0, f.signCount)
            assertTrue(f.submitted.isEmpty())
            assertEquals(ApprovalState.Idle, f.approvals.state.value)
        }
    }

    @Test
    fun phase6ReplacedAssociationCannotReceiveOldDelayedRequest() {
        Fixture().use { f ->
            f.selectFault(FaultId.DELAY_5S)
            @Suppress("DEPRECATION")
            val old = f.client.signMessages(arrayOf("old association".encodeToByteArray()), arrayOf(f.publicKey))
            f.awaitFaultMark(FaultId.DELAY_5S)
            f.selectFault(FaultId.NORMAL)
            val replacement = f.replaceAssociation()
            val replacementAuthorization = replacement.authorize(
                Uri.parse("https://phase6-replacement.invalid"),
                Uri.parse("icon.png"),
                "Phase 6 replacement",
                ProtocolContract.CHAIN_SOLANA_DEVNET,
                null,
                null,
                null,
                null,
            )
            AuthorizationApprovalTestDriver.approveNext()
            val newAuth = replacementAuthorization.get(10, TimeUnit.SECONDS)
            assertTrue(newAuth.authToken.isNotEmpty())
            runCatching { old.get(10, TimeUnit.SECONDS) }
            val oldEvent = f.awaitFaultEvent(ProtocolMethod.SIGN_MESSAGES, FaultId.DELAY_5S)
            assertEquals(ProtocolOutcome.CANCELLED, oldEvent.outcome)
            assertEquals(0, f.signCount)
            assertTrue(f.submitted.isEmpty())
            assertEquals(ApprovalState.Idle, f.approvals.state.value)
            assertTrue(f.sessionIds().size >= 2)
        }
    }

    private fun method(send: Boolean) = if (send) ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS else ProtocolMethod.SIGN_TRANSACTIONS

    private fun assertRemoteError(future: Future<*>, code: Int) {
        try { future.get(10, TimeUnit.SECONDS); fail("Expected unchanged MWA rejection") }
        catch (error: ExecutionException) {
            val cause = error.cause
            if (cause is MobileWalletAdapterClient.InvalidPayloadsException) {
                assertEquals(ProtocolContract.ERROR_INVALID_PAYLOADS, code)
                assertFalse(cause.validPayloads.any { it })
            } else {
                assertTrue("Expected a protocol error", cause is JsonRpc20Client.JsonRpc20RemoteException)
                assertEquals(code, (cause as JsonRpc20Client.JsonRpc20RemoteException).code)
            }
        }
    }

    private inner class Fixture(inspector: TransactionInspection = TransactionInspector()) : java.io.Closeable {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val name = "phase48-approval-" + UUID.randomUUID() + ".db"
        private val db = Room.databaseBuilder(context, MwaLabDatabase::class.java, name).build()
        private val repo = RoomSessionRepository(db.sessionDao(), db.protocolEventDao())
        private val sessionFinished = CountDownLatch(1)
        private val diagnosticWorkerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val simulationWorkerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val approvals = ApprovalCoordinator()
        private val selectedFault = MutableStateFlow(FaultCatalog.get(FaultId.NORMAL))
        private val faultSelection = object : FaultSelectionRepository {
            override val selected: StateFlow<FaultProfile> = selectedFault
            override fun select(id: FaultId) { selectedFault.value = FaultCatalog.get(id) }
        }
        fun selectFault(id: FaultId) = faultSelection.select(id)
        val order = CopyOnWriteArrayList<String>()
        val inspected = CopyOnWriteArrayList<TransactionSummary>()
        val submitted = CopyOnWriteArrayList<ByteArray>()
        val options = CopyOnWriteArrayList<DevnetSendOptions>()
        val simulationCalls = CopyOnWriteArrayList<DevnetSimulationOptions>()
        @Volatile var simulationResponse: DevnetRpcResult<SimulationRpcValue> =
            DevnetRpcResult.Success(SimulationRpcValue(1, null, BoundedLogs(emptyList(), false), null))
        @Volatile var simulationGate: CompletableDeferred<Unit>? = null
        val evidence = CopyOnWriteArrayList<MwaSessionEvidence>()
        private val authTokens = CopyOnWriteArrayList<String>()
        private var diagnosticWritesFail = false
        @Volatile var signCount = 0
        @Volatile var blockhashValid = true
        private val signer = MwaLabComposition.signingService(context)
        private val countingSigner = object : LabSigningService {
            override suspend fun publicIdentity(): TestEndpointIdentity = signer.publicIdentity()
            override suspend fun sign(message: ByteArray): ByteArray {
                signCount++
                return signer.sign(message)
            }
        }
        private val delegateRecorder = PersistentProtocolRecorder(repo)
        private val recorder = object : ProtocolRecorder by delegateRecorder {
            override suspend fun begin(sessionId: SessionId, method: ProtocolMethod,
                requestSummary: Map<String, String>): ProtocolEventHandle {
                order += "begin:" + method.name
                return delegateRecorder.begin(sessionId, method, requestSummary)
            }
            override suspend fun markInjectedFault(handle: ProtocolEventHandle, faultId: FaultId) {
                delegateRecorder.markInjectedFault(handle, faultId)
                order += "fault:" + faultId.stableId
            }
        }
        private val rpc = object : DevnetRpcGateway {
            override suspend fun simulateTransaction(
                transaction: ByteArray,
                options: dev.mwalab.rpc.DevnetSimulationOptions,
            ): DevnetRpcResult<dev.mwalab.rpc.SimulationRpcValue> {
                simulationCalls += options
                simulationGate?.await()
                return simulationResponse
            }
            override suspend fun isBlockhashValid(blockhash: ByteArray, minContextSlot: Int?): DevnetRpcResult<Boolean> {
                order += "blockhash"
                assertArrayEquals(ByteArray(32) { 0x77 }, blockhash)
                return DevnetRpcResult.Success(blockhashValid)
            }
            override suspend fun sendTransaction(signedTransaction: ByteArray, options: DevnetSendOptions): DevnetRpcResult<ByteArray> {
                order += "send"; submitted += signedTransaction.copyOf(); this@Fixture.options += options
                return DevnetRpcResult.Success(signedTransaction.copyOfRange(1, 65))
            }
            override suspend fun awaitCommitment(signature: ByteArray, commitment: String, timeoutMillis: Long): DevnetRpcResult<Boolean> {
                order += "commitment:" + commitment
                return DevnetRpcResult.Success(true)
            }
        }
        val simulationRepository = RoomSimulationRepository(db.simulationResultDao())
        private val simulationSettlement = SimulationDiagnosticSettlement(repo, simulationRepository,
            simulationWorkerScope)
        val simulator = TransactionSimulationCoordinator(TransactionSimulationService(rpc),
            { result -> simulationSettlement.record(result) }, simulationWorkerScope)
        private val host = MwaSessionHost(context, signingService = countingSigner, approvalCoordinator = approvals,
            rpcGateway = rpc, evidenceSink = { evidence += it }, protocolEvidenceSink = {}, protocolRecorder = recorder,
            faultSelectionRepository = faultSelection,
            sessionLifecycleCoordinator = SessionLifecycleCoordinator(repo, recorder),
            capabilitySnapshotRepository = RoomCapabilitySnapshotRepository(db.capabilitySnapshotDao()),
            transactionDiagnosticSettlement = TransactionDiagnosticSettlement(repo, RoomTransactionDiagnosticRepository(db.transactionDiagnosticDao()), diagnosticWorkerScope),
            simulationCoordinator = simulator, simulationDiagnosticSettlement = simulationSettlement,
            transactionInspector = object : TransactionInspection {
                override fun inspect(transaction: ByteArray, payloadIndex: Int, binding: TransactionDiagnosticBinding?): TransactionSummary {
                    order += "inspect:" + payloadIndex
                    return inspector.inspect(transaction, payloadIndex, binding).also { inspected += it }
                }
            }, onSessionFinished = { sessionFinished.countDown() })
        private val association = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        private val replacementAssociations = CopyOnWriteArrayList<LocalAssociationScenario>()
        private var hostClosedExplicitly = false
        val client: MobileWalletAdapterClient
        lateinit var publicKey: ByteArray
        init {
            val uri = LocalAssociationIntentCreator.createAssociationIntent(null, association.port, association.session).data
            assertTrue(host.openAssociation(uri) is AssociationOpenResult.Accepted)
            client = association.start().get(30, TimeUnit.SECONDS)
            authorize()
        }
        fun authorize() {
            val future = client.authorize(Uri.parse("https://phase48-test.invalid"), Uri.parse("icon.png"),
                "Phase 4.8 approval fixture", ProtocolContract.CHAIN_SOLANA_DEVNET,
                null, null, null, null)
            AuthorizationApprovalTestDriver.approveNext()
            val authorization = future.get(10, TimeUnit.SECONDS)
            publicKey = authorization.accounts.single().publicKey
            authTokens += authorization.authToken
        }
        /** Clientlib is single-flight. Exercise the real frozen revocation callback while its wire request is pending. */
        fun revokeThroughPinnedWalletlibCallback(sessionId: String) {
            val constructor = MobileWalletAdapterServer.DeauthorizeRequest::class.java.declaredConstructors.single {
                it.parameterTypes.contentEquals(arrayOf(Any::class.java, String::class.java))
            }
                .also { it.isAccessible = true }
            val request = constructor.newInstance(17, "walletlib-test-only-token") as MobileWalletAdapterServer.DeauthorizeRequest
            val eventConstructor = DeauthorizedEvent::class.java.declaredConstructors.single { it.parameterTypes.size == 6 }.also { it.isAccessible = true }
            val event = eventConstructor.newInstance(request, "Phase 4.8 approval fixture",
                Uri.parse("https://phase48-test.invalid"), Uri.parse("icon.png"), ProtocolContract.CHAIN_SOLANA_DEVNET,
                byteArrayOf()) as DeauthorizedEvent
            val generationField = MwaSessionHost::class.java.getDeclaredField("sessionGeneration").also { it.isAccessible = true }
            val generation = (generationField.get(host) as java.util.concurrent.atomic.AtomicLong).get()
            val callbackFactory = MwaSessionHost::class.java.getDeclaredMethod("createCallbacks",
                Long::class.javaPrimitiveType, String::class.java).also { it.isAccessible = true }
            val callbacks = callbackFactory.invoke(host, generation, sessionId) as LocalScenario.Callbacks
            callbacks.onDeauthorizedEvent(event)
            assertTrue(request.isDone)
        }

        fun transaction(knownProgram: Boolean = true, versioned: Boolean = false, readonlyPayer: Boolean = false) =
            TransactionApprovalTestVectors.systemTransfer(publicKey, knownProgram, versioned, readonlyPayer)
        @Suppress("DEPRECATION")
        fun sign(send: Boolean, payloads: Array<ByteArray>): Future<*> =
            if (send) client.signAndSendTransactions(payloads, 17, "confirmed", false, 2, true)
            else client.signTransactions(payloads)
        fun awaitApproval(): ApprovalState.Pending = await {
            approvals.state.value as? ApprovalState.Pending
        }
        fun assertEvent(method: ProtocolMethod, outcome: ProtocolOutcome, code: Int?, result: String,
            source: ProtocolFailureSource? = null) {
            val event = await {
                runBlocking { repo.observeSessions().first() }.flatMap { it.events }.lastOrNull {
                    it.method == method && it.responseSummary["result"] == result
                }
            }
            assertEquals(outcome, event.outcome); assertEquals(code, event.protocolErrorCode)
            source?.let { assertEquals(it, event.failureSource) }
            assertFalse(event.requestSummary.values.any { it.contains("RAW_DIAGNOSTIC_EXCEPTION") })
            val expected = inspected.filter { it.eventId == event.eventId }
            if (expected.isNotEmpty() && !diagnosticWritesFail) {
                val stored = await {
                    runBlocking { RoomTransactionDiagnosticRepository(db.transactionDiagnosticDao())
                        .getForEvent(event.sessionId, event.eventId) }.takeIf { it.size == expected.size }
                }
                assertEquals(expected, stored)
                assertEquals(event, runBlocking { repo.getSession(event.sessionId) }!!.events.single { it.eventId == event.eventId })
            }

        }
        fun assertInjectedEvent(method: ProtocolMethod, id: FaultId, code: Int, result: String,
            payloadCount: Int? = null) {
            val event = await {
                runBlocking { repo.observeSessions().first() }.single().events.lastOrNull {
                    it.method == method && it.responseSummary["result"] == result
                }
            }
            assertEquals(ProtocolOutcome.FAILURE, event.outcome)
            assertEquals(code, event.protocolErrorCode)
            assertEquals(ProtocolFailureSource.INJECTED, event.failureSource)
            assertEquals(id.stableId, event.injectedFaultId)
            payloadCount?.let { assertEquals(it.toString(), event.requestSummary["payload_count"]) }
            assertEquals(1, runBlocking { repo.getSession(event.sessionId) }!!.events.count { it.eventId == event.eventId })
        }
        fun awaitLatestEvent(method: ProtocolMethod, result: String): ProtocolEvent = await {
            runBlocking { repo.observeSessions().first() }.single().events.lastOrNull {
                it.method == method && it.responseSummary["result"] == result
            }
        }
        fun authorizeAgain(token: String? = null): Future<*> =
            client.authorize(Uri.parse("https://phase48-test.invalid"), Uri.parse("icon.png"),
                "Phase 4.8 approval fixture", ProtocolContract.CHAIN_SOLANA_DEVNET,
                token, null, null, null)
        fun latestAuthToken(): String = authTokens.last()
        fun awaitFaultMark(id: FaultId) {
            await { order.lastOrNull { it == "fault:" + id.stableId } }
        }
        fun currentSessionId(): String = runBlocking { repo.observeSessions().first() }.single().session.id
        fun sessionIds(): Set<String> = runBlocking { repo.observeSessions().first() }.map { it.session.id }.toSet()
        fun awaitFaultEvent(method: ProtocolMethod, id: FaultId): ProtocolEvent = await {
            runBlocking { repo.observeSessions().first() }.flatMap { it.events }.singleOrNull {
                it.method == method && it.injectedFaultId == id.stableId
            }
        }
        fun replaceAssociation(): MobileWalletAdapterClient {
            val replacement = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
            replacementAssociations += replacement
            val uri = LocalAssociationIntentCreator.createAssociationIntent(
                null, replacement.port, replacement.session,
            ).data
            assertTrue(host.openAssociation(uri) is AssociationOpenResult.Accepted)
            return replacement.start().get(30, TimeUnit.SECONDS)
        }
        fun closeAssociation() {
            association.close().get(10, TimeUnit.SECONDS)
            assertTrue(sessionFinished.await(10, TimeUnit.SECONDS))
        }
        fun closeHost() {
            hostClosedExplicitly = true
            host.close()
        }
        fun awaitLastEvent(method: ProtocolMethod): ProtocolEvent = await {
            runBlocking { repo.observeSessions().first() }.single().events.lastOrNull { it.method == method }
        }
        fun awaitSimulation(ref: SimulationTargetRef): SimulationResult = await {
            (simulator.state.value[ref] as? SimulationUiState.Completed)?.result
        }
        fun awaitStoredSimulation(ref: SimulationTargetRef): SimulationResult = await {
            runBlocking { simulationRepository.getForEvent(ref.sessionId, ref.eventId) }
                .singleOrNull { it.target == ref }
        }
        fun awaitSimulationCallCount(count: Int) {
            await { simulationCalls.size.takeIf { it >= count } }
        }
        fun failSimulationInserts() {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_simulations BEFORE INSERT ON simulation_results " +
                "BEGIN SELECT RAISE(ABORT, 'controlled simulation failure'); END")
        }
        fun assertOrderedEvents(methods: List<ProtocolMethod>) {
            val events = await { runBlocking { repo.observeSessions().first() }.single().events.takeIf { it.size == methods.size } }
            assertEquals(methods, events.map { it.method })
            assertEquals((1L..methods.size.toLong()).toList(), events.map { it.sequence })
            assertFalse(events.any { it.method == ProtocolMethod.GET_CAPABILITIES })
        }
        fun verifySigned(original: ByteArray, signed: ByteArray) {
            assertArrayEquals(original.copyOfRange(65, original.size), signed.copyOfRange(65, signed.size))
            assertEquals(original.size, signed.size)
            val message = signed.copyOfRange(65, signed.size)
            assertTrue(Ed25519.verify(signed.copyOfRange(1, 65), 0, publicKey, 0, message, 0, message.size))
        }
        fun assertDiagnosticRowCount(expected: Int) {
            val count = await {
                db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transaction_diagnostics").use {
                    assertTrue(it.moveToFirst()); it.getInt(0).takeIf { value -> value == expected }
                }
            }
            assertEquals(expected, count)
        }
        fun failDiagnosticInserts() {
            diagnosticWritesFail = true
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_diagnostics BEFORE INSERT ON transaction_diagnostics " +
                "BEGIN SELECT RAISE(ABORT, 'controlled diagnostic failure'); END")
        }
        fun failTransactionEventInserts() {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_transaction_events BEFORE INSERT ON protocol_events " +
                "WHEN NEW.method IN ('SIGN_TRANSACTIONS', 'SIGN_AND_SEND_TRANSACTIONS') " +
                "BEGIN SELECT RAISE(ABORT, 'controlled parent failure'); END")
        }
        fun awaitFailures(detail: String, count: Int) {
            await { evidence.count { it.event == MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED && it.detail == detail }
                .takeIf { it >= count } }
            assertTrue(evidence.filter { it.event == MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED }
                .all { it.detail == "transaction_diagnostics" || it.detail == "simulation_results" ||
                    it.detail!!.startsWith("protocol_complete_") })
        }
        fun assertOnlyAuthorizationPersisted() {
            assertEquals(listOf(ProtocolMethod.AUTHORIZE), runBlocking { repo.observeSessions().first() }.single().events.map { it.method })
        }
        fun assertNoAuthTokensInStorage() {
            assertTrue(authTokens.all { it.isNotBlank() })
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { while (it.moveToNext()) {} }
            listOf("", "-wal", "-shm").forEach { suffix ->
                val file = context.getDatabasePath(name + suffix)
                if (file.exists()) {
                    val text = file.readBytes().toString(Charsets.ISO_8859_1)
                    authTokens.forEach { assertFalse(text.contains(it)) }
                }
            }
        }
        fun assertNoDiagnosticWrites() {
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transaction_diagnostics").use {
                assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0))
            }
        }
        private fun <T : Any> await(block: () -> T?): T {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < end) { block()?.let { return it }; Thread.sleep(20) }
            throw AssertionError("Timed out awaiting request-bound state")
        }
        override fun close() {
            replacementAssociations.forEach { runCatching { it.close().get(10, TimeUnit.SECONDS) } }
            runCatching { association.close().get(10, TimeUnit.SECONDS) }
            if (!hostClosedExplicitly) {
                assertTrue("Natural walletlib teardown did not finish", sessionFinished.await(10, TimeUnit.SECONDS))
            }
            host.close()
            // Drain handler-finally and diagnostic jobs before closing their disposable Room database.
            // A visible committed row does not prove its background coroutine has returned yet.
            val field = MwaSessionHost::class.java.getDeclaredField("authorizationScope").also { it.isAccessible = true }
            val authorizationScope = field.get(host) as CoroutineScope
            runBlocking {
                withTimeout(7_000) {
                    authorizationScope.coroutineContext[Job]!!.join()
                    diagnosticWorkerScope.coroutineContext[Job]!!.children.toList().joinAll()
                }
            }
            diagnosticWorkerScope.cancel()
            simulationWorkerScope.cancel()
            db.close()
            context.deleteDatabase(name)
        }
    }
}
