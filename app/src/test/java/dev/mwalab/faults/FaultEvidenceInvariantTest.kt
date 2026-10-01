package dev.mwalab.faults

import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import org.junit.Assert.assertTrue
import org.junit.Test

class FaultEvidenceInvariantTest {
    private val normal = ProtocolEvent(
        sessionId = "session-a",
        eventId = "session-a:1",
        sequence = 1,
        method = ProtocolMethod.SIGN_MESSAGES,
        startedAtEpochMillis = 100,
        completedAtEpochMillis = 110,
        outcome = ProtocolOutcome.SUCCESS,
    )

    @Test
    fun normalAndAppliedNonFailureConditionAreValid() {
        FaultEvidenceInvariant.requireValid(normal)
        FaultEvidenceInvariant.requireValid(normal.copy(injectedFaultId = FaultId.DELAY_5S.stableId))
        FaultEvidenceInvariant.requireValid(normal.copy(
            outcome = ProtocolOutcome.FAILURE,
            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
            injectedFaultId = FaultId.DELAY_5S.stableId,
        ))
    }

    @Test
    fun injectedTerminalFailureRequiresKnownAppliedId() {
        val injected = normal.copy(outcome = ProtocolOutcome.FAILURE,
            failureSource = ProtocolFailureSource.INJECTED)
        assertTrue(runCatching { FaultEvidenceInvariant.requireValid(injected) }
            .exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { FaultEvidenceInvariant.requireValid(
            injected.copy(injectedFaultId = FaultId.NORMAL.stableId)) }
            .exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { FaultEvidenceInvariant.requireValid(
            injected.copy(injectedFaultId = "FAULT_NOT_DEFINED")) }
            .exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { FaultEvidenceInvariant.requireValid(
            normal.copy(failureSource = ProtocolFailureSource.INJECTED,
                injectedFaultId = FaultId.SIGN_REJECT.stableId)) }
            .exceptionOrNull() is IllegalArgumentException)
        FaultEvidenceInvariant.requireValid(injected.copy(injectedFaultId = FaultId.SIGN_REJECT.stableId))
    }
}
