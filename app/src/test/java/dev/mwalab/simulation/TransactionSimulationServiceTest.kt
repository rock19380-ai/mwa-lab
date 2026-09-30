package dev.mwalab.simulation

import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.rpc.DevnetRpcGateway
import dev.mwalab.rpc.DevnetRpcResult
import dev.mwalab.rpc.DevnetSendOptions
import dev.mwalab.rpc.DevnetSimulationOptions
import dev.mwalab.rpc.SimulationRpcValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionSimulationServiceTest {
    private val target = SimulationTargetRef("session", "event", "request", 0, "a".repeat(64))
    private val options = DevnetSimulationOptions.forSignTransactions()

    @Test fun unexpectedGatewayExceptionBecomesUnknownUnavailableWithoutLeakingText() = runTest {
        val service = TransactionSimulationService(Gateway { throw IllegalStateException("RAW_SECRET_SENTINEL") })
        val result = service.simulate(target, 1, byteArrayOf(1, 2, 3), options)
        assertEquals(SimulationOutcome.UNAVAILABLE, result.outcome)
        assertEquals(ProtocolFailureSource.UNKNOWN, result.failureSource)
        assertEquals(SimulationAvailabilityReason.UNKNOWN, result.availabilityReason)
        assertEquals(null, result.rpcErrorCode)
        assertEquals(emptyList<String>(), result.programLogs)
    }

    @Test fun cancellationRemainsCancellationAndCannotBeReclassified() = runTest {
        val service = TransactionSimulationService(Gateway { throw CancellationException("cancel") })
        try {
            service.simulate(target, 1, byteArrayOf(1), options)
            throw AssertionError("Expected CancellationException")
        } catch (cancelled: CancellationException) {
            assertEquals("cancel", cancelled.message)
        }
    }

    private class Gateway(
        private val block: suspend () -> DevnetRpcResult<SimulationRpcValue>,
    ) : DevnetRpcGateway {
        override suspend fun simulateTransaction(transaction: ByteArray, options: DevnetSimulationOptions) = block()
        override suspend fun isBlockhashValid(blockhash: ByteArray, minContextSlot: Int?) = DevnetRpcResult.Success(true)
        override suspend fun sendTransaction(signedTransaction: ByteArray, options: DevnetSendOptions): DevnetRpcResult<ByteArray> =
            error("simulation test must not submit")
        override suspend fun awaitCommitment(signature: ByteArray, commitment: String, timeoutMillis: Long): DevnetRpcResult<Boolean> =
            error("simulation test must not await submission")
    }
}
