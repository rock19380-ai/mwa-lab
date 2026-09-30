package dev.mwalab.rpc

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

class SignAndSendSubmissionExecutorTest {
    @Test
    fun `successful mocked submit returns exact signatures`() = runBlocking {
        val first = transaction(1)
        val second = transaction(2)
        val gateway = FakeGateway(
            sendResults = listOf(
                DevnetRpcResult.Success(first.expectedSignature),
                DevnetRpcResult.Success(second.expectedSignature),
            ),
        )

        val result = SignAndSendSubmissionExecutor(gateway).execute(
            transactions = listOf(first, second),
            options = options(),
            isRequestCurrent = { true },
        )

        assertTrue(result is SignAndSendSubmissionResult.Submitted)
        result as SignAndSendSubmissionResult.Submitted
        assertEquals(2, result.signatures.size)
        assertTrue(result.signatures[0].contentEquals(first.expectedSignature))
        assertTrue(result.signatures[1].contentEquals(second.expectedSignature))
        assertEquals(listOf("send", "send"), gateway.calls)
    }

    @Test
    fun `rpc transport rpc-error and malformed send failures map to not-submitted without retry`() = runBlocking {
        val failures = listOf<DevnetRpcResult<ByteArray>>(
            DevnetRpcResult.TransportFailure(TransportFailureReason.IO),
            DevnetRpcResult.TransportFailure(TransportFailureReason.TIMEOUT),
            DevnetRpcResult.RpcError(-32002),
            DevnetRpcResult.MalformedResponse,
        )

        for (failure in failures) {
            val gateway = FakeGateway(sendResults = listOf(failure))
            val result = SignAndSendSubmissionExecutor(gateway).execute(
                transactions = listOf(transaction(3)),
                options = options(),
                isRequestCurrent = { true },
            )

            assertTrue(result is SignAndSendSubmissionResult.NotSubmitted)
            result as SignAndSendSubmissionResult.NotSubmitted
            assertEquals(1, result.signatures.size)
            assertNull(result.signatures.single())
            assertEquals(1, gateway.sendCount)
            assertEquals(0, gateway.commitmentCount)
        }
    }

    @Test
    fun `partial submission preserves successful signature and does not resubmit`() = runBlocking {
        val first = transaction(4)
        val second = transaction(5)
        val gateway = FakeGateway(
            sendResults = listOf(
                DevnetRpcResult.Success(first.expectedSignature),
                DevnetRpcResult.TransportFailure(TransportFailureReason.IO),
            ),
        )

        val result = SignAndSendSubmissionExecutor(gateway).execute(
            transactions = listOf(first, second),
            options = options(),
            isRequestCurrent = { true },
        )

        assertTrue(result is SignAndSendSubmissionResult.NotSubmitted)
        result as SignAndSendSubmissionResult.NotSubmitted
        assertTrue(result.signatures[0]!!.contentEquals(first.expectedSignature))
        assertNull(result.signatures[1])
        assertEquals(2, gateway.sendCount)
    }

    @Test
    fun `unexpected rpc signature fails closed`() = runBlocking {
        val tx = transaction(6)
        val gateway = FakeGateway(
            sendResults = listOf(DevnetRpcResult.Success(ByteArray(64) { 99.toByte() })),
        )

        val result = SignAndSendSubmissionExecutor(gateway).execute(
            transactions = listOf(tx),
            options = options(),
            isRequestCurrent = { true },
        )

        assertEquals(
            SignAndSendSubmissionResult.Fatal(
                SignAndSendFatalReason.SIGNATURE_MISMATCH,
                submittedCount = 0,
            ),
            result,
        )
        assertEquals(1, gateway.sendCount)
    }

    @Test
    fun `requested commitment must succeed before successful response`() = runBlocking {
        val tx = transaction(7)
        val gateway = FakeGateway(
            sendResults = listOf(DevnetRpcResult.Success(tx.expectedSignature)),
            commitmentResults = listOf(DevnetRpcResult.Success(true)),
        )

        val result = SignAndSendSubmissionExecutor(gateway).execute(
            transactions = listOf(tx),
            options = options(commitment = "confirmed"),
            isRequestCurrent = { true },
        )

        assertTrue(result is SignAndSendSubmissionResult.Submitted)
        assertEquals(listOf("send", "commit:confirmed"), gateway.calls)
    }

    @Test
    fun `requested commitment false and unavailable fail closed`() = runBlocking {
        val tx = transaction(8)
        val cases = listOf(
            DevnetRpcResult.Success(false) to SignAndSendFatalReason.COMMITMENT_NOT_REACHED,
            DevnetRpcResult.RpcError(-32001) to SignAndSendFatalReason.COMMITMENT_UNAVAILABLE,
            DevnetRpcResult.TransportFailure(TransportFailureReason.TIMEOUT) to
                SignAndSendFatalReason.COMMITMENT_UNAVAILABLE,
            DevnetRpcResult.MalformedResponse to SignAndSendFatalReason.COMMITMENT_UNAVAILABLE,
        )

        for ((confirmation, expectedReason) in cases) {
            val gateway = FakeGateway(
                sendResults = listOf(DevnetRpcResult.Success(tx.expectedSignature)),
                commitmentResults = listOf(confirmation),
            )
            val result = SignAndSendSubmissionExecutor(gateway).execute(
                transactions = listOf(tx),
                options = options(commitment = "confirmed"),
                isRequestCurrent = { true },
            )
            assertEquals(
                SignAndSendSubmissionResult.Fatal(expectedReason, submittedCount = 1),
                result,
            )
        }
    }

    @Test
    fun `wait-for-commitment gates next transaction`() = runBlocking {
        val first = transaction(9)
        val second = transaction(10)
        val gateway = FakeGateway(
            sendResults = listOf(
                DevnetRpcResult.Success(first.expectedSignature),
                DevnetRpcResult.Success(second.expectedSignature),
            ),
            commitmentResults = listOf(DevnetRpcResult.Success(true)),
        )

        val result = SignAndSendSubmissionExecutor(gateway).execute(
            transactions = listOf(first, second),
            options = options(waitForCommitment = true),
            isRequestCurrent = { true },
        )

        assertTrue(result is SignAndSendSubmissionResult.Submitted)
        assertEquals(listOf("send", "commit:confirmed", "send"), gateway.calls)
    }

    @Test
    fun `cancellation before first submission performs zero rpc sends`() = runBlocking {
        val gateway = FakeGateway()

        val result = SignAndSendSubmissionExecutor(gateway).execute(
            transactions = listOf(transaction(11)),
            options = options(),
            isRequestCurrent = { false },
        )

        assertEquals(SignAndSendSubmissionResult.Cancelled, result)
        assertEquals(0, gateway.sendCount)
        assertEquals(0, gateway.commitmentCount)
    }

    @Test
    fun `cancellation after first rpc completion stops remaining submissions and never resubmits`() = runBlocking {
        val first = transaction(12)
        val second = transaction(13)
        var current = true
        val gateway = FakeGateway(
            sendResults = listOf(
                DevnetRpcResult.Success(first.expectedSignature),
                DevnetRpcResult.Success(second.expectedSignature),
            ),
            onSend = { sendCount ->
                if (sendCount == 1) current = false
            },
        )

        val result = SignAndSendSubmissionExecutor(gateway).execute(
            transactions = listOf(first, second),
            options = options(),
            isRequestCurrent = { current },
        )

        assertEquals(SignAndSendSubmissionResult.Cancelled, result)
        assertEquals(1, gateway.sendCount)
        assertEquals(0, gateway.commitmentCount)
    }

    @Test
    fun `cancellation after commitment response prevents next transaction submission`() = runBlocking {
        val first = transaction(14)
        val second = transaction(15)
        var current = true
        val gateway = FakeGateway(
            sendResults = listOf(
                DevnetRpcResult.Success(first.expectedSignature),
                DevnetRpcResult.Success(second.expectedSignature),
            ),
            commitmentResults = listOf(DevnetRpcResult.Success(true)),
            onCommitment = { current = false },
        )

        val result = SignAndSendSubmissionExecutor(gateway).execute(
            transactions = listOf(first, second),
            options = options(waitForCommitment = true),
            isRequestCurrent = { current },
        )

        assertEquals(SignAndSendSubmissionResult.Cancelled, result)
        assertEquals(1, gateway.sendCount)
        assertEquals(1, gateway.commitmentCount)
    }

    private fun transaction(seed: Int): SignAndSendSubmission = SignAndSendSubmission(
        payload = ByteArray(100) { seed.toByte() },
        expectedSignature = ByteArray(64) { index -> (seed + index).toByte() },
    )

    private fun options(
        commitment: String? = null,
        waitForCommitment: Boolean? = false,
    ) = DevnetSendOptions(
        minContextSlot = null,
        commitment = commitment,
        skipPreflight = false,
        maxRetries = 3,
        waitForCommitmentToSendNextTransaction = waitForCommitment,
    )

    private class FakeGateway(
        sendResults: List<DevnetRpcResult<ByteArray>> = emptyList(),
        commitmentResults: List<DevnetRpcResult<Boolean>> = emptyList(),
        private val onSend: (Int) -> Unit = {},
        private val onCommitment: () -> Unit = {},
    ) : DevnetRpcGateway {
        override suspend fun simulateTransaction(
            transaction: ByteArray,
            options: DevnetSimulationOptions,
        ): DevnetRpcResult<SimulationRpcValue> = error("Submission must never invoke simulation")

        private val sendResults = ArrayDeque(sendResults)
        private val commitmentResults = ArrayDeque(commitmentResults)
        val calls = mutableListOf<String>()
        var sendCount = 0
            private set
        var commitmentCount = 0
            private set

        override suspend fun isBlockhashValid(
            blockhash: ByteArray,
            minContextSlot: Int?,
        ): DevnetRpcResult<Boolean> = DevnetRpcResult.Success(true)

        override suspend fun sendTransaction(
            signedTransaction: ByteArray,
            options: DevnetSendOptions,
        ): DevnetRpcResult<ByteArray> {
            sendCount += 1
            calls += "send"
            onSend(sendCount)
            check(sendResults.isNotEmpty()) { "Unexpected extra sendTransaction call" }
            return sendResults.removeFirst()
        }

        override suspend fun awaitCommitment(
            signature: ByteArray,
            commitment: String,
            timeoutMillis: Long,
        ): DevnetRpcResult<Boolean> {
            commitmentCount += 1
            calls += "commit:$commitment"
            onCommitment()
            check(commitmentResults.isNotEmpty()) { "Unexpected extra awaitCommitment call" }
            return commitmentResults.removeFirst()
        }
    }
}
