package dev.mwalab.rpc

import android.util.Base64
import dev.mwalab.simulation.SimulationErrorKind
import dev.mwalab.simulation.SimulationLimits
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class SolanaDevnetSimulationRpcInstrumentedTest {
    private val payload = byteArrayOf(1, 2, 3, 4)
    private val options = DevnetSimulationOptions.forSignTransactions()

    @Test fun exactRequestAndContextPolicy() = runBlocking {
        val first = SingleResponse(response(value()))
        assertTrue(SolanaDevnetRpcGateway(first).simulateTransaction(payload, options) is DevnetRpcResult.Success)
        val request = JSONObject(String(first.body!!, StandardCharsets.UTF_8))
        assertEquals("simulateTransaction", request.getString("method"))
        val params = request.getJSONArray("params")
        assertTrue(Base64.decode(params.getString(0), Base64.DEFAULT).contentEquals(payload))
        val config = params.getJSONObject(1)
        assertEquals("base64", config.getString("encoding"))
        assertFalse(config.getBoolean("sigVerify"))
        assertFalse(config.getBoolean("replaceRecentBlockhash"))
        assertFalse(config.getBoolean("innerInstructions"))
        assertEquals("processed", config.getString("commitment"))
        assertFalse(config.has("minContextSlot"))
        assertFalse(config.has("accounts"))

        val second = SingleResponse(response(value()))
        val send = DevnetSendOptions(123, "confirmed", false, 0, false)
        SolanaDevnetRpcGateway(second).simulateTransaction(payload, DevnetSimulationOptions.forSignAndSend(send))
        val configured = JSONObject(String(second.body!!, StandardCharsets.UTF_8))
            .getJSONArray("params").getJSONObject(1)
        assertEquals("confirmed", configured.getString("commitment"))
        assertEquals(123, configured.getInt("minContextSlot"))
    }

    @Test fun nullLogsAbsentUnitsAndRuntimeErrors() = runBlocking {
        val success = call(response(value(logs = JSONObject.NULL, units = null)))
        assertTrue(success is DevnetRpcResult.Success)
        val safe = (success as DevnetRpcResult.Success).value
        assertEquals(52L, safe.contextSlot)
        assertNull(safe.error)
        assertTrue(safe.logs.lines.isEmpty())
        assertNull(safe.unitsConsumed)

        val instruction = JSONObject().put("InstructionError", JSONArray().put(2).put("InvalidArgument"))
        val custom = JSONObject().put("InstructionError",
            JSONArray().put(3).put(JSONObject().put("Custom", 4294967295L)))
        val cases = listOf(
            instruction to SimulationErrorKind.INSTRUCTION_ERROR,
            custom to SimulationErrorKind.INSTRUCTION_ERROR,
            JSONObject().put("InstructionError", JSONArray().put(1).put("Custom")) to SimulationErrorKind.UNKNOWN_SIMULATION_ERROR,
            "BlockhashNotFound" to SimulationErrorKind.BLOCKHASH_NOT_FOUND,
            JSONObject().put("FutureError", "unverified") to SimulationErrorKind.UNKNOWN_SIMULATION_ERROR,
        )
        for ((error, kind) in cases) {
            val result = call(response(value(err = error))) as DevnetRpcResult.Success
            assertEquals(kind, result.value.error?.kind)
            if (error === custom) {
                assertEquals(3, result.value.error?.instructionIndex)
                assertEquals(4294967295L, result.value.error?.customProgramErrorCode)
            }
        }
    }

    @Test fun logsAreBoundedAndControlsAreReplaced() = runBlocking {
        val logs = JSONArray()
        repeat(66) { logs.put("line\u0000\u202e" + "x".repeat(600)) }
        val result = call(response(value(logs = logs))) as DevnetRpcResult.Success
        assertEquals(64, result.value.logs.lines.size)
        assertTrue(result.value.logs.truncated)
        assertTrue(result.value.logs.lines.all(SimulationLimits::safeText))
        assertTrue(result.value.logs.lines.all { it.codePointCount(0, it.length) <= 512 })
    }

    @Test fun availabilityAndMalformedResponsesFailClosed() = runBlocking {
        assertEquals(DevnetRpcResult.RpcError(-32002), call(DevnetHttpTransportResult.Response(
            200, """{"jsonrpc":"2.0","id":1,"error":{"code":-32002,"message":"untrusted"}}""".encodeToByteArray())))
        assertEquals(DevnetRpcResult.TransportFailure(TransportFailureReason.HTTP),
            call(DevnetHttpTransportResult.Response(503, byteArrayOf())))
        for (reason in listOf(TransportFailureReason.IO, TransportFailureReason.TIMEOUT)) {
            assertEquals(DevnetRpcResult.TransportFailure(reason),
                call(DevnetHttpTransportResult.Failure(reason)))
        }
        val malformed = listOf(
            "not-json",
            """{"jsonrpc":"2.0","id":1}""",
            response(value()).body.decodeToString().replace(""""value":""", """"missing":"""),
            response(value()).body.decodeToString().replace(""""slot":52""", """"slot":"52""""),
            response(value()).body.decodeToString().replace(""""logs":[]""", """"logs":[42]"""),
            response(value()).body.decodeToString().replace(""""unitsConsumed":123""", """"unitsConsumed":-1"""),
        )
        for (body in malformed) {
            assertEquals(DevnetRpcResult.MalformedResponse,
                call(DevnetHttpTransportResult.Response(200, body.encodeToByteArray())))
        }
        assertEquals(DevnetRpcResult.MalformedResponse,
            call(DevnetHttpTransportResult.Response(200,
                ByteArray(SimulationLimits.MAX_RPC_RESPONSE_BYTES + 1) { 'x'.code.toByte() })))
        assertEquals(DevnetRpcResult.MalformedResponse,
            SolanaDevnetRpcGateway(SingleResponse(response(value())))
                .simulateTransaction(ByteArray(1233), options))
    }

    private suspend fun call(response: DevnetHttpTransportResult) =
        SolanaDevnetRpcGateway(SingleResponse(response)).simulateTransaction(payload, options)

    private fun value(
        err: Any = JSONObject.NULL,
        logs: Any = JSONArray(),
        units: Long? = 123,
    ): JSONObject = JSONObject().put("context", JSONObject().put("slot", 52))
        .put("value", JSONObject().put("err", err).put("logs", logs).also {
            if (units != null) it.put("unitsConsumed", units)
        })

    private fun response(result: JSONObject) = DevnetHttpTransportResult.Response(200,
        JSONObject().put("jsonrpc", "2.0").put("id", 1).put("result", result).toString().encodeToByteArray())

    private class SingleResponse(private val response: DevnetHttpTransportResult) : DevnetHttpTransport {
        var body: ByteArray? = null
        override suspend fun post(body: ByteArray): DevnetHttpTransportResult {
            check(this.body == null)
            this.body = body.copyOf()
            return response
        }
    }
}
