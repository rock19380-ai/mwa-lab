package dev.mwalab.ui.sessions

import dev.mwalab.protocol.ProtocolEvidence
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.session.MwaSession
import dev.mwalab.session.SessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectedFaultPresentationTest {
    private fun event(outcome: ProtocolOutcome, source: ProtocolFailureSource,
        code: Int?, fault: String?) = ProtocolEvidence(
        sessionId = "s", eventId = "s:1", sequence = 1, method = ProtocolMethod.SIGN_MESSAGES,
        startedAtEpochMillis = 100, completedAtEpochMillis = 150, outcome = outcome,
        protocolErrorCode = code, failureSource = source, injectedFaultId = fault)

    @Test fun normalSuccessAndManualRejectionNeverAcquireAnInjectedBadgeFromErrorCode() {
        val success = event(ProtocolOutcome.SUCCESS, ProtocolFailureSource.NONE, null, null)
        val manual = event(ProtocolOutcome.FAILURE, ProtocolFailureSource.OBSERVED_PROTOCOL, -3, null)
        assertEquals(null, injectedConditionText(success))
        assertEquals(null, injectedConditionText(manual))
        assertFalse(sessionHasInjectedCondition(SessionSummary(MwaSession("s", 100), listOf(manual))))
        assertTrue(protocolErrorText(manual.protocolErrorCode).contains("ERROR_NOT_SIGNED"))
    }

    @Test fun syntheticSigningRejectionShowsConditionSeparatelyFromItsFailureSource() {
        val injected = event(ProtocolOutcome.FAILURE, ProtocolFailureSource.INJECTED, -3, "FAULT_SIGN_REJECT")
        assertEquals("Injected condition: Reject signing (FAULT_SIGN_REJECT)", injectedConditionText(injected))
        assertEquals(ProtocolFailureSource.INJECTED, injected.failureSource)
        assertTrue(sessionHasInjectedCondition(SessionSummary(MwaSession("s", 100), listOf(injected))))
    }

    @Test fun delaySuccessAndLaterObservedRejectionKeepTheSameAppliedCondition() {
        val success = event(ProtocolOutcome.SUCCESS, ProtocolFailureSource.NONE, null, "FAULT_DELAY_5S")
        val rejected = event(ProtocolOutcome.FAILURE, ProtocolFailureSource.OBSERVED_PROTOCOL, -3,
            "FAULT_DELAY_5S")
        assertEquals(injectedConditionText(success), injectedConditionText(rejected))
        assertEquals(ProtocolFailureSource.NONE, success.failureSource)
        assertEquals(ProtocolFailureSource.OBSERVED_PROTOCOL, rejected.failureSource)
    }

    @Test fun submissionFaultsStayDistinctEvenWithSameProtocolCode() {
        val unavailable = event(ProtocolOutcome.FAILURE, ProtocolFailureSource.INJECTED, -4,
            "FAULT_RPC_UNAVAILABLE")
        val failed = event(ProtocolOutcome.FAILURE, ProtocolFailureSource.INJECTED, -4,
            "FAULT_SUBMISSION_FAILURE")
        assertTrue(checkNotNull(injectedConditionText(unavailable)).contains("RPC unavailable"))
        assertTrue(checkNotNull(injectedConditionText(failed)).contains("Submission failure"))
        assertTrue(injectedConditionText(unavailable) != injectedConditionText(failed))
    }
}
