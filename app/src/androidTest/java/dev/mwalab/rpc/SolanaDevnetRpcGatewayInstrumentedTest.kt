package dev.mwalab.rpc

import com.funkatronics.encoders.Base58
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.ArrayDeque

class SolanaDevnetRpcGatewayInstrumentedTest {
    @Test
    fun successfulMockedSubmitReturnsDecoded64ByteSignature() = runBlocking {
        val signature = ByteArray(64) { index -> (index + 1).toByte() }
        val transport = QueueTransport(
            DevnetHttpTransportResult.Response(
                200,
                jsonResult(Base58.encodeToString(signature)),
            ),
        )
        val gateway = SolanaDevnetRpcGateway(transport)

        val result = gateway.sendTransaction(ByteArray(32) { 9 }, defaultOptions())

        assertTrue(result is DevnetRpcResult.Success)
        result as DevnetRpcResult.Success
        assertTrue(result.value.contentEquals(signature))
        val request = JSONObject(String(transport.requestBodies.single(), StandardCharsets.UTF_8))
        assertEquals("sendTransaction", request.getString("method"))
    }

    @Test
    fun dnsOrConnectionStyleIoFailureStaysClassifiedAsTransportIo() = runBlocking {
        val gateway = SolanaDevnetRpcGateway(
            QueueTransport(
                DevnetHttpTransportResult.Failure(TransportFailureReason.IO),
            ),
        )

        assertEquals(
            DevnetRpcResult.TransportFailure(TransportFailureReason.IO),
            gateway.sendTransaction(ByteArray(32) { 1 }, defaultOptions()),
        )
    }

    @Test
    fun timeoutStaysClassifiedAsTransportTimeout() = runBlocking {
        val gateway = SolanaDevnetRpcGateway(
            QueueTransport(
                DevnetHttpTransportResult.Failure(TransportFailureReason.TIMEOUT),
            ),
        )

        assertEquals(
            DevnetRpcResult.TransportFailure(TransportFailureReason.TIMEOUT),
            gateway.sendTransaction(ByteArray(32) { 1 }, defaultOptions()),
        )
    }

    @Test
    fun nonSuccessHttpStatusFailsClosed() = runBlocking {
        val gateway = SolanaDevnetRpcGateway(
            QueueTransport(
                DevnetHttpTransportResult.Response(503, "unavailable".encodeToByteArray()),
            ),
        )

        assertEquals(
            DevnetRpcResult.TransportFailure(TransportFailureReason.HTTP),
            gateway.sendTransaction(ByteArray(32) { 1 }, defaultOptions()),
        )
    }

    @Test
    fun jsonRpcErrorCodeIsPreservedWithoutResponseBodyLeakage() = runBlocking {
        val gateway = SolanaDevnetRpcGateway(
            QueueTransport(
                DevnetHttpTransportResult.Response(
                    200,
                    """{"jsonrpc":"2.0","id":1,"error":{"code":-32002,"message":"secret-like remote detail"}}"""
                        .encodeToByteArray(),
                ),
            ),
        )

        assertEquals(
            DevnetRpcResult.RpcError(-32002),
            gateway.sendTransaction(ByteArray(32) { 1 }, defaultOptions()),
        )
    }

    @Test
    fun malformedMissingResultAndOversizedResponsesFailClosed() = runBlocking {
        val responses = listOf(
            "not-json".encodeToByteArray(),
            """{"jsonrpc":"2.0","id":1}""".encodeToByteArray(),
            ByteArray(64 * 1024 + 1) { 'x'.code.toByte() },
        )

        for (body in responses) {
            val gateway = SolanaDevnetRpcGateway(
                QueueTransport(DevnetHttpTransportResult.Response(200, body)),
            )
            assertEquals(
                DevnetRpcResult.MalformedResponse,
                gateway.sendTransaction(ByteArray(32) { 1 }, defaultOptions()),
            )
        }
    }

    @Test
    fun malformedSignatureResultFailsClosed() = runBlocking {
        val gateway = SolanaDevnetRpcGateway(
            QueueTransport(
                DevnetHttpTransportResult.Response(200, jsonResult("not-base58-0OIl")),
            ),
        )

        assertEquals(
            DevnetRpcResult.MalformedResponse,
            gateway.sendTransaction(ByteArray(32) { 1 }, defaultOptions()),
        )
    }

    @Test
    fun confirmedStatusSatisfiesConfirmedCommitment() = runBlocking {
        val signature = ByteArray(64) { 7 }
        val response = """
            {"jsonrpc":"2.0","id":1,"result":{"value":[{"err":null,"confirmationStatus":"confirmed","confirmations":1}]}}
        """.trimIndent().encodeToByteArray()
        val gateway = SolanaDevnetRpcGateway(
            QueueTransport(DevnetHttpTransportResult.Response(200, response)),
        )

        assertEquals(
            DevnetRpcResult.Success(true),
            gateway.awaitCommitment(signature, "confirmed", timeoutMillis = 1_000),
        )
    }

    @Test
    fun transactionErrorStatusIsNotConsideredCommittedSuccess() = runBlocking {
        val signature = ByteArray(64) { 7 }
        val response = """
            {"jsonrpc":"2.0","id":1,"result":{"value":[{"err":{"InstructionError":[0,"Custom"]},"confirmationStatus":"confirmed","confirmations":1}]}}
        """.trimIndent().encodeToByteArray()
        val gateway = SolanaDevnetRpcGateway(
            QueueTransport(DevnetHttpTransportResult.Response(200, response)),
        )

        assertEquals(
            DevnetRpcResult.Success(false),
            gateway.awaitCommitment(signature, "confirmed", timeoutMillis = 1_000),
        )
    }

    private fun defaultOptions() = DevnetSendOptions(
        minContextSlot = null,
        commitment = null,
        skipPreflight = false,
        maxRetries = 0,
        waitForCommitmentToSendNextTransaction = false,
    )

    private fun jsonResult(value: String): ByteArray =
        JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", 1)
            .put("result", value)
            .toString()
            .encodeToByteArray()

    private class QueueTransport(
        vararg responses: DevnetHttpTransportResult,
    ) : DevnetHttpTransport {
        private val responses = ArrayDeque(responses.toList())
        val requestBodies = mutableListOf<ByteArray>()

        override suspend fun post(body: ByteArray): DevnetHttpTransportResult {
            requestBodies += body.copyOf()
            check(responses.isNotEmpty()) { "Unexpected extra RPC request" }
            return responses.removeFirst()
        }
    }
}
