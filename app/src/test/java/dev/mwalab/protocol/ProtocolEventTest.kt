package dev.mwalab.protocol

import dev.mwalab.security.DiagnosticSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ProtocolEventTest {
    @Test
    fun productEventAndLegacyEvidenceAreTheSameRecord() {
        val event: ProtocolEvent = event()
        val legacy: ProtocolEvidence = event

        assertSame(event, legacy)
        assertEquals("session-a", legacy.sessionId)
        assertEquals("session-a:1", legacy.eventId)
        assertEquals(1L, legacy.sequence)
        assertNull(event.injectedFaultId)
        assertNull(event.capabilityContext)
    }

    @Test
    fun durationUsesRecordedTimesAndClampsClockRollback() {
        assertEquals(40L, event().durationMillis)
        assertEquals(0L, event().copy(completedAtEpochMillis = 90).durationMillis)
    }

    @Test
    fun outcomesAndFailureSourcesRemainDistinctWithoutInventingErrorNames() {
        for (outcome in ProtocolOutcome.entries) {
            for (source in ProtocolFailureSource.entries) {
                val event = event().copy(
                    outcome = outcome,
                    failureSource = source,
                    protocolErrorCode = -12345,
                )
                assertEquals(outcome, event.outcome)
                assertEquals(source, event.failureSource)
                assertEquals(-12345, event.protocolErrorCode)
            }
        }
    }

    @Test
    fun realRejectionPreservesProtocolErrorAndObservedSource() {
        val event = event().copy(
            outcome = ProtocolOutcome.FAILURE,
            protocolErrorCode = -3,
            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
        )
        assertEquals(ProtocolOutcome.FAILURE, event.outcome)
        assertEquals(-3, event.protocolErrorCode)
        assertEquals(ProtocolFailureSource.OBSERVED_PROTOCOL, event.failureSource)
        assertNull(event.injectedFaultId)
    }

    @Test
    fun sanitizedSummariesAreRepresentableWithoutRawRequestObjects() {
        val request = DiagnosticSanitizer.sanitizeFields(mapOf(
            "auth_token" to "domain-test-token",
            "payload_count" to "1",
        ))
        val response = DiagnosticSanitizer.sanitizeFields(mapOf(
            "raw_message_payload" to "domain-test-payload",
            "result" to "rejected",
        ))
        val event = event().copy(requestSummary = request, responseSummary = response)
        assertEquals(DiagnosticSanitizer.REDACTED, event.requestSummary["auth_token"])
        assertEquals("1", event.requestSummary["payload_count"])
        assertEquals(DiagnosticSanitizer.REDACTED, event.responseSummary["raw_message_payload"])
        assertEquals("rejected", event.responseSummary["result"])
    }

    private fun event() = ProtocolEvent(
        sessionId = "session-a",
        eventId = "session-a:1",
        sequence = 1,
        method = ProtocolMethod.SIGN_MESSAGES,
        startedAtEpochMillis = 100,
        completedAtEpochMillis = 140,
        outcome = ProtocolOutcome.SUCCESS,
    )
}
