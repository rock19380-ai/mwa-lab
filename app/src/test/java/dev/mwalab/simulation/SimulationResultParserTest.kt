package dev.mwalab.simulation

import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.rpc.DevnetRpcResult
import dev.mwalab.rpc.DevnetSimulationOptions
import dev.mwalab.rpc.SimulationRpcValue
import dev.mwalab.rpc.TransportFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SimulationResultParserTest {
    private val target = SimulationTargetRef("session", "event", "request", 0, "b".repeat(64))
    private val options = DevnetSimulationOptions.forSignTransactions()

    @Test fun passHasNoFailureSourceAndPreservesSafeMeasurements() {
        val result = parse(DevnetRpcResult.Success(SimulationRpcValue(44, null,
            SimulationLimits.sanitizeLogs(listOf("Program success")), 777)))
        assertEquals(SimulationOutcome.PASS, result.outcome)
        assertEquals(ProtocolFailureSource.NONE, result.failureSource)
        assertEquals(44L, result.contextSlot)
        assertEquals(777L, result.unitsConsumed)
        assertEquals(15L, result.durationMillis)
        assertNull(result.error)
    }

    @Test fun runtimeAndUnknownFailureStaySimulationFailures() {
        for (kind in listOf(SimulationErrorKind.BLOCKHASH_NOT_FOUND,
            SimulationErrorKind.UNKNOWN_SIMULATION_ERROR)) {
            val result = parse(DevnetRpcResult.Success(SimulationRpcValue(44,
                SimulationErrorSummary(kind), BoundedLogs(emptyList(), false), null)))
            assertEquals(SimulationOutcome.FAIL, result.outcome)
            assertEquals(ProtocolFailureSource.SIMULATION, result.failureSource)
            assertEquals(kind, result.error?.kind)
        }
        val custom = parse(DevnetRpcResult.Success(SimulationRpcValue(44,
            SimulationErrorSummary(SimulationErrorKind.INSTRUCTION_ERROR, 3, "Custom", 42),
            BoundedLogs(emptyList(), false), null)))
        assertEquals(3, custom.error?.instructionIndex)
        assertEquals(42L, custom.error?.customProgramErrorCode)
    }

    @Test fun availabilityFailuresDoNotBecomeProtocolOrRuntimeFailures() {
        val cases = listOf(
            DevnetRpcResult.RpcError(-32002) to SimulationAvailabilityReason.JSON_RPC,
            DevnetRpcResult.TransportFailure(TransportFailureReason.TIMEOUT) to SimulationAvailabilityReason.TIMEOUT,
            DevnetRpcResult.TransportFailure(TransportFailureReason.IO) to SimulationAvailabilityReason.IO,
            DevnetRpcResult.TransportFailure(TransportFailureReason.HTTP) to SimulationAvailabilityReason.HTTP,
            DevnetRpcResult.TransportFailure(TransportFailureReason.RATE_LIMITED) to SimulationAvailabilityReason.HTTP,
            DevnetRpcResult.MalformedResponse to SimulationAvailabilityReason.MALFORMED_RESPONSE,
        )
        for ((rpc, reason) in cases) {
            val result = parse(rpc)
            assertEquals(SimulationOutcome.UNAVAILABLE, result.outcome)
            assertEquals(ProtocolFailureSource.RPC_NETWORK, result.failureSource)
            assertEquals(reason, result.availabilityReason)
            assertNull(result.error)
        }
        assertEquals(-32002, parse(DevnetRpcResult.RpcError(-32002)).rpcErrorCode)
        val local = SimulationResultParser.localUnavailable("simulation", target, 1, 100, 115, 15, "processed")
        assertEquals(ProtocolFailureSource.LOCAL_PARSER, local.failureSource)
    }

    private fun parse(rpc: DevnetRpcResult<SimulationRpcValue>) =
        SimulationResultParser.parse("simulation", target, 1, 100, 115, 15, options, rpc)
}
