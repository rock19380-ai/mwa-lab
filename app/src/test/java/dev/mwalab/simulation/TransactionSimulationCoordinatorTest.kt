package dev.mwalab.simulation

import dev.mwalab.rpc.DevnetRpcGateway
import dev.mwalab.rpc.DevnetRpcResult
import dev.mwalab.rpc.DevnetSendOptions
import dev.mwalab.rpc.DevnetSimulationOptions
import dev.mwalab.rpc.SimulationRpcValue
import dev.mwalab.security.DiagnosticSanitizer
import dev.mwalab.transaction.TransactionWireFixtures
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionSimulationCoordinatorTest {
    private val legacy = TransactionWireFixtures.legacy().bytes
    private val v0 = TransactionWireFixtures.v0().bytes
    private val options = DevnetSimulationOptions.forSignTransactions()

    @Test fun duplicateTapRetryAndReleaseKeepOneExactAttempt() = runTest {
        val rpc = PendingGateway()
        val results = mutableListOf<SimulationResult>()
        val coordinator = TransactionSimulationCoordinator(TransactionSimulationService(rpc),
            results::add, this)
        val ref = ref("session-a", "event-a", "request-a", legacy)
        coordinator.activateSession(ref.sessionId, 1)
        assertTrue(coordinator.register(ref, 1, legacy, options))
        assertEquals(SimulationUiState.NotRun, coordinator.state.value[ref])
        assertTrue(coordinator.simulate(ref))
        assertFalse(coordinator.simulate(ref))
        runCurrent()
        assertEquals(1, rpc.pending.size)
        assertEquals(SimulationUiState.Running, coordinator.state.value[ref])
        rpc.pending[0].complete(pass())
        runCurrent()
        assertEquals(SimulationOutcome.PASS, (coordinator.state.value[ref] as SimulationUiState.Completed).result.outcome)
        assertEquals(1, results.size)
        assertTrue(coordinator.simulate(ref))
        runCurrent()
        assertEquals(2, rpc.pending.size)
        coordinator.releaseRequest(ref.sessionId, ref.eventId, ref.requestId)
        assertFalse(coordinator.simulate(ref))
        rpc.pending[1].complete(fail())
        runCurrent()
        assertEquals(2, results.size)
        assertEquals(SimulationOutcome.FAIL, results.last().outcome)
        assertFalse(coordinator.state.value.containsKey(ref))
    }

    @Test fun rejectsMismatchedFingerprintV0AndStaleIdentity() = runTest {
        val rpc = PendingGateway()
        val coordinator = TransactionSimulationCoordinator(TransactionSimulationService(rpc), scope = this)
        val ref = ref("session-a", "event-a", "request-a", legacy)
        coordinator.activateSession(ref.sessionId, 5)
        assertFalse(coordinator.register(ref, 4, legacy, options))
        assertFalse(coordinator.register(ref.copy(transactionFingerprintSha256 = "f".repeat(64)), 5, legacy, options))
        assertFalse(coordinator.register(ref.copy(transactionFingerprintSha256 = DiagnosticSanitizer.sha256(v0)),
            5, v0, options))
        assertTrue(coordinator.register(ref, 5, legacy, options))
        assertFalse(coordinator.simulate(ref.copy(requestId = "stale-request")))
        assertFalse(coordinator.simulate(ref.copy(eventId = "other-event")))
        assertFalse(coordinator.simulate(ref.copy(payloadIndex = 1)))
        coordinator.invalidateSession(ref.sessionId, 4)
        assertTrue(coordinator.simulate(ref))
        runCurrent()
        coordinator.invalidateSession(ref.sessionId, 5)
        assertFalse(coordinator.simulate(ref))
        assertTrue(coordinator.state.value.isEmpty())
        assertEquals(1, rpc.pending.size)
    }

    @Test fun sessionReplacementCannotRedirectLateResultAtSamePayloadIndex() = runTest {
        val rpc = PendingGateway()
        val results = mutableListOf<SimulationResult>()
        val coordinator = TransactionSimulationCoordinator(TransactionSimulationService(rpc),
            results::add, this)
        val a = ref("session-a", "event-a", "request-a", legacy)
        coordinator.activateSession(a.sessionId, 1)
        assertTrue(coordinator.register(a, 1, legacy, options))
        assertTrue(coordinator.simulate(a))
        runCurrent()
        val b = ref("session-b", "event-b", "request-b", legacy)
        coordinator.activateSession(b.sessionId, 2)
        assertTrue(coordinator.register(b, 2, legacy, options))
        assertTrue(coordinator.simulate(b))
        runCurrent()
        rpc.pending[0].complete(pass())
        rpc.pending[1].complete(fail())
        runCurrent()
        assertEquals(1, results.size)
        assertEquals(b, results.single().target)
        assertEquals(SimulationOutcome.FAIL, results.single().outcome)
        assertFalse(coordinator.state.value.containsKey(a))
    }

    private fun ref(session: String, event: String, request: String, bytes: ByteArray) =
        SimulationTargetRef(session, event, request, 0, DiagnosticSanitizer.sha256(bytes))

    private fun pass(): DevnetRpcResult<SimulationRpcValue> =
        DevnetRpcResult.Success(SimulationRpcValue(10, null, BoundedLogs(emptyList(), false), 5))

    private fun fail(): DevnetRpcResult<SimulationRpcValue> =
        DevnetRpcResult.Success(SimulationRpcValue(10,
            SimulationErrorSummary(SimulationErrorKind.BLOCKHASH_NOT_FOUND), BoundedLogs(emptyList(), false), null))

    private class PendingGateway : DevnetRpcGateway {
        val pending = mutableListOf<CompletableDeferred<DevnetRpcResult<SimulationRpcValue>>>()
        override suspend fun simulateTransaction(transaction: ByteArray, options: DevnetSimulationOptions):
            DevnetRpcResult<SimulationRpcValue> {
            val next = CompletableDeferred<DevnetRpcResult<SimulationRpcValue>>()
            pending += next
            return next.await()
        }
        override suspend fun isBlockhashValid(blockhash: ByteArray, minContextSlot: Int?) =
            DevnetRpcResult.Success(true)
        override suspend fun sendTransaction(signedTransaction: ByteArray, options: DevnetSendOptions):
            DevnetRpcResult<ByteArray> = error("Simulation cannot submit")
        override suspend fun awaitCommitment(signature: ByteArray, commitment: String, timeoutMillis: Long):
            DevnetRpcResult<Boolean> = error("Simulation cannot await submission")
    }
}
