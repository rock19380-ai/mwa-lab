package dev.mwalab.rpc

import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.simulation.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Synthetic RPC fixtures are acceptance vectors, never live Devnet receipts. */
class Phase5SimulationVectorInstrumentedTest {
    private val payload = byteArrayOf(1, 2, 3)
    private val target = SimulationTargetRef("fixture-session", "fixture-event",
        "fixture-request", 0, "a".repeat(64))
    private val options = DevnetSimulationOptions.forSignTransactions()

    @Test fun passFixturePreservesSafeRuntimeEvidenceWithoutSubmitting() = runBlocking {
        val transport = FixtureTransport("rpc-pass.json")
        val result = TransactionSimulationService(SolanaDevnetRpcGateway(transport))
            .simulate(target, 1, payload, options)
        assertEquals(SimulationOutcome.PASS, result.outcome)
        assertEquals(ProtocolFailureSource.NONE, result.failureSource)
        assertEquals(52L, result.contextSlot)
        assertEquals(42L, result.unitsConsumed)
        assertEquals(listOf("Program invocation, depth 1", SimulationLimits.REDACTED_LOG,
            "Program consumed 42 of 200 compute units", "Program success"), result.programLogs)
        assertFalse(result.programLogs.joinToString().contains("VECTOR_FREEFORM_SENTINEL"))
        val request = JSONObject(transport.onlyRequest())
        assertEquals("simulateTransaction", request.getString("method"))
        assertFalse(request.getJSONArray("params").getJSONObject(1).getBoolean("sigVerify"))
        assertFalse(request.getJSONArray("params").getJSONObject(1).getBoolean("replaceRecentBlockhash"))
        assertEquals(1, transport.calls)
    }

    @Test fun runtimeFailCustomBlockhashAndUnknownStayTruthful() = runBlocking {
        val expected = listOf(
            Triple("rpc-fail.json", SimulationErrorKind.INSTRUCTION_ERROR, null),
            Triple("rpc-custom.json", SimulationErrorKind.INSTRUCTION_ERROR, 42L),
            Triple("rpc-blockhash.json", SimulationErrorKind.BLOCKHASH_NOT_FOUND, null),
            Triple("rpc-unknown.json", SimulationErrorKind.UNKNOWN_SIMULATION_ERROR, null),
        )
        for ((name, kind, custom) in expected) {
            val result = TransactionSimulationService(SolanaDevnetRpcGateway(FixtureTransport(name)))
                .simulate(target, 1, payload, options)
            assertEquals(name, SimulationOutcome.FAIL, result.outcome)
            assertEquals(name, ProtocolFailureSource.SIMULATION, result.failureSource)
            assertEquals(name, kind, result.error?.kind)
            assertEquals(name, custom, result.error?.customProgramErrorCode)
            if (custom != null) assertEquals(0, result.error?.instructionIndex)
            assertFalse(result.programLogs.joinToString().contains("invalid instruction data"))
        }
    }

    @Test fun rpcUnavailableFixtureNeverBecomesRuntimeFailure() = runBlocking {
        val result = TransactionSimulationService(SolanaDevnetRpcGateway(
            FixtureTransport("rpc-unavailable.json"))).simulate(target, 1, payload, options)
        assertEquals(SimulationOutcome.UNAVAILABLE, result.outcome)
        assertEquals(ProtocolFailureSource.RPC_NETWORK, result.failureSource)
        assertEquals(SimulationAvailabilityReason.JSON_RPC, result.availabilityReason)
        assertEquals(-32005, result.rpcErrorCode)
        assertNull(result.error)
    }

    private class FixtureTransport(private val name: String) : DevnetHttpTransport {
        var calls = 0
        private var request: ByteArray? = null
        override suspend fun post(body: ByteArray): DevnetHttpTransportResult {
            calls++
            request = body.copyOf()
            val context = InstrumentationRegistry.getInstrumentation().context
            val response = context.assets.open(name).use { it.readBytes() }
            return DevnetHttpTransportResult.Response(200, response)
        }
        fun onlyRequest() = checkNotNull(request).decodeToString()
    }
}
