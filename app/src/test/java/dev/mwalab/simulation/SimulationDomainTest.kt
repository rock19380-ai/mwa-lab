package dev.mwalab.simulation

import dev.mwalab.protocol.ProtocolFailureSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationDomainTest {
    private val target = SimulationTargetRef("session", "event", "request", 0, "a".repeat(64))

    @Test fun targetRequiresCompleteSafeBinding() {
        for (bad in listOf("", " ", "bad\nidentity")) {
            rejects { target.copy(sessionId = bad) }
            rejects { target.copy(eventId = bad) }
            rejects { target.copy(requestId = bad) }
        }
        rejects { target.copy(payloadIndex = -1) }
        rejects { target.copy(transactionFingerprintSha256 = "A".repeat(64)) }
        rejects { target.copy(transactionFingerprintSha256 = "a".repeat(63)) }
    }

    @Test fun outcomeAndFailureSourceMustAgree() {
        assertEquals(ProtocolFailureSource.NONE, SimulationClassification.source(SimulationOutcome.PASS))
        assertEquals(ProtocolFailureSource.SIMULATION, SimulationClassification.source(SimulationOutcome.FAIL))
        assertEquals(ProtocolFailureSource.RPC_NETWORK,
            SimulationClassification.source(SimulationOutcome.UNAVAILABLE, SimulationAvailabilityReason.TIMEOUT))
        assertEquals(ProtocolFailureSource.LOCAL_PARSER,
            SimulationClassification.source(SimulationOutcome.UNAVAILABLE, SimulationAvailabilityReason.LOCAL_INPUT))
        assertEquals(ProtocolFailureSource.UNKNOWN,
            SimulationClassification.source(SimulationOutcome.UNAVAILABLE, SimulationAvailabilityReason.UNKNOWN))
        rejects { result(outcome = SimulationOutcome.PASS, source = ProtocolFailureSource.SIMULATION) }
        rejects { result(outcome = SimulationOutcome.FAIL, source = ProtocolFailureSource.NONE) }
        rejects { result(outcome = SimulationOutcome.FAIL, source = ProtocolFailureSource.SIMULATION) }
        rejects { result(outcome = SimulationOutcome.UNAVAILABLE, source = ProtocolFailureSource.NONE) }
    }

    @Test fun numericAndStateBoundsRejectUnsafeResults() {
        rejects { result(attempt = 0) }
        rejects { result(start = -1) }
        rejects { result(start = 3, finish = 2) }
        rejects { result(duration = -1) }
        rejects { result(slot = -1) }
        rejects { result(units = -1) }
        rejects { result(commitment = "root") }
        rejects { result(outcome = SimulationOutcome.PASS, source = ProtocolFailureSource.NONE,
            error = SimulationErrorSummary(SimulationErrorKind.BLOCKHASH_NOT_FOUND)) }
        rejects { result(outcome = SimulationOutcome.UNAVAILABLE, source = ProtocolFailureSource.RPC_NETWORK,
            reason = SimulationAvailabilityReason.TIMEOUT, slot = 1) }
        rejects { result(outcome = SimulationOutcome.UNAVAILABLE, source = ProtocolFailureSource.RPC_NETWORK,
            reason = SimulationAvailabilityReason.TIMEOUT, rpcCode = -32002) }
    }

    @Test fun structuredErrorCannotInventCustomMeaningOrOverflow() {
        rejects { SimulationErrorSummary(SimulationErrorKind.INSTRUCTION_ERROR, -1) }
        rejects { SimulationErrorSummary(SimulationErrorKind.INSTRUCTION_ERROR, 256) }
        rejects { SimulationErrorSummary(SimulationErrorKind.INSTRUCTION_ERROR, 0, "Custom", -1) }
        rejects { SimulationErrorSummary(SimulationErrorKind.INSTRUCTION_ERROR, 0, "Custom", 0x1_0000_0000L) }
        rejects { SimulationErrorSummary(SimulationErrorKind.INSTRUCTION_ERROR, 0, "Custom") }
        rejects { SimulationErrorSummary(SimulationErrorKind.INSTRUCTION_ERROR, 0, "InventedProgramError") }
        rejects { SimulationErrorSummary(SimulationErrorKind.BLOCKHASH_NOT_FOUND, 0) }
        assertEquals(0xffff_ffffL, SimulationErrorSummary(
            SimulationErrorKind.INSTRUCTION_ERROR, 255, "Custom", 0xffff_ffffL).customProgramErrorCode)
    }

    @Test fun logsAreBoundedSanitizedAndDefensivelyOwned() {
        val raw = MutableList(70) { "line\u0000\u202e" + "x".repeat(600) }
        val bounded = SimulationLimits.sanitizeLogs(raw)
        assertEquals(64, bounded.lines.size)
        assertTrue(bounded.truncated)
        assertTrue(bounded.lines.all { SimulationLimits.safeText(it) })
        assertTrue(bounded.lines.all { it.codePointCount(0, it.length) <= 512 })
        raw.clear()
        assertEquals(64, bounded.lines.size)
        rejects { BoundedLogs(listOf("bad\nline"), false) }
        rejects { BoundedLogs(List(65) { "safe" }, false) }
        rejects { BoundedLogs(listOf("x".repeat(513)), false) }
        val pass = result(logs = bounded)
        assertTrue(pass.logsTruncated)
        assertFalse(pass.programLogs.any { it.contains('\u0000') || it.contains('\u202e') })
    }

    @Test fun arbitraryRpcLogTextCannotReachPublicOrDurableResult() {
        val program = "11111111111111111111111111111111"
        val logs = SimulationLimits.sanitizeLogs(listOf(
            "Program $program invoke [1]",
            "Program log: AUTH_TOKEN_SENTINEL_PRIVATE_PAYLOAD",
            "Program $program consumed 42 of 200 compute units",
            "Program $program success",
            "RAW_RPC_BODY_SENTINEL",
        ))
        assertEquals(listOf("Program invocation, depth 1", SimulationLimits.REDACTED_LOG,
            "Program consumed 42 of 200 compute units", "Program success",
            SimulationLimits.REDACTED_LOG), logs.lines)
        val pass = result(logs = logs)
        assertFalse(pass.programLogs.joinToString().contains("AUTH_TOKEN_SENTINEL"))
        assertFalse(pass.programLogs.joinToString().contains("RAW_RPC_BODY_SENTINEL"))
        assertEquals(logs.lines, BoundedLogs(logs.lines, false).lines)
    }

    private fun result(
        outcome: SimulationOutcome = SimulationOutcome.PASS,
        source: ProtocolFailureSource = ProtocolFailureSource.NONE,
        attempt: Int = 1,
        start: Long = 1,
        finish: Long = 2,
        duration: Long = 1,
        slot: Long? = null,
        units: Long? = null,
        commitment: String = "processed",
        error: SimulationErrorSummary? = null,
        reason: SimulationAvailabilityReason? = null,
        rpcCode: Int? = null,
        logs: BoundedLogs = BoundedLogs(emptyList(), false),
    ) = SimulationResult("simulation", target, attempt, start, finish, duration, outcome, source,
        commitment, slot, error, rpcCode, reason, units, logs)

    private fun rejects(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected invalid simulation model to fail")
        } catch (_: IllegalArgumentException) {
            // Construction must fail closed.
        }
    }
}
