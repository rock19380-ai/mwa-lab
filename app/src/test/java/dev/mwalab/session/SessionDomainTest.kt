package dev.mwalab.session

import dev.mwalab.mwa.association.AssociationMode
import dev.mwalab.mwa.association.DappVerificationState
import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionDomainTest {
    @Test
    fun newSessionHasFixedDevnetAndUnknownDisplayIdentity() {
        assertEquals("solana:devnet", active.cluster)
        assertNull(active.dappIdentityName)
        assertNull(active.completedAtEpochMillis)
        assertNull(active.closeReason)
        assertNull(active.durationMillis)
        assertEquals(SessionStatus.ACTIVE, SessionSummary(active, emptyList()).status)
    }

    @Test
    fun completionRequiresBothTimestampAndCauseIncludingCopies() {
        assertThrows(IllegalArgumentException::class.java) { active.copy(completedAtEpochMillis = 200) }
        assertThrows(IllegalArgumentException::class.java) {
            active.copy(closeReason = SessionCloseReason.SERVING_COMPLETE)
        }
        assertThrows(IllegalArgumentException::class.java) { active.copy(id = "unknown") }
        assertThrows(IllegalArgumentException::class.java) { active.copy(startedAtEpochMillis = -1) }
    }

    @Test
    fun displayIdentityMustAlreadyBeBoundedAndNormalized() {
        assertEquals("Demo dApp", active.copy(dappIdentityName = "Demo dApp").dappIdentityName)
        for (name in listOf("", " ", "  Demo", "Demo\nClient", "x".repeat(129))) {
            assertThrows(IllegalArgumentException::class.java) { active.copy(dappIdentityName = name) }
        }
    }

    @Test
    fun transportAndVerificationStateCannotImplyFalseEvidence() {
        assertEquals(AssociationMode.LOCAL, active.associationMode)
        assertEquals(DappVerificationState.NOT_AVAILABLE, active.identityVerificationState)
        assertThrows(IllegalArgumentException::class.java) {
            active.copy(identityVerificationState = DappVerificationState.REMOTE_UNVERIFIED)
        }
        assertThrows(IllegalArgumentException::class.java) {
            active.copy(
                associationMode = AssociationMode.REMOTE,
                identityVerificationState = DappVerificationState.VERIFIED,
            )
        }
        val remote = active.copy(
            associationMode = AssociationMode.REMOTE,
            identityVerificationState = DappVerificationState.REMOTE_UNVERIFIED,
        )
        assertEquals(AssociationMode.REMOTE, remote.associationMode)
        assertEquals(DappVerificationState.REMOTE_UNVERIFIED, remote.identityVerificationState)
    }

    @Test
    fun sessionDurationDoesNotSumEventsOrBecomeNegativeOnClockRollback() {
        val closed = closed(SessionCloseReason.SERVING_COMPLETE)
        assertEquals(100L, SessionSummary(closed, listOf(event(1), event(2))).durationMillis)
        assertEquals(0L, closed.copy(completedAtEpochMillis = 90).durationMillis)
    }

    @Test
    fun activeSessionKeepsLifecycleStatusWhileFailedEventRemainsVisible() {
        val failure = event(1, ProtocolOutcome.FAILURE)
        val summary = SessionSummary(active, listOf(failure))
        assertEquals(SessionStatus.ACTIVE, summary.status)
        assertEquals(ProtocolOutcome.FAILURE, summary.events.single().outcome)
    }

    @Test
    fun normalClosedObservedSuccessPassesButEmptyHistoryIsUnknown() {
        for (reason in listOf(SessionCloseReason.SERVING_COMPLETE, SessionCloseReason.SCENARIO_COMPLETE)) {
            assertEquals(SessionStatus.PASS, SessionSummary(closed(reason), listOf(event(1))).status)
            assertEquals(SessionStatus.UNKNOWN, SessionSummary(closed(reason), emptyList()).status)
        }
    }

    @Test
    fun observedFailureDominatesCancellationAndKeepsExactError() {
        val failure = event(2, ProtocolOutcome.FAILURE).copy(
            protocolErrorCode = -12345,
            failureSource = ProtocolFailureSource.RPC_NETWORK,
        )
        val summary = SessionSummary(
            closed(SessionCloseReason.HOST_CLOSED),
            listOf(failure, event(1, ProtocolOutcome.CANCELLED)),
        )
        assertEquals(SessionStatus.FAIL, summary.status)
        assertEquals(-12345, summary.events.last().protocolErrorCode)
        assertEquals(ProtocolFailureSource.RPC_NETWORK, summary.events.last().failureSource)
    }

    @Test
    fun lifecycleErrorFailsWithoutInventingAMethodOrProtocolCode() {
        for (reason in listOf(SessionCloseReason.START_FAILED, SessionCloseReason.SCENARIO_ERROR)) {
            val summary = SessionSummary(closed(reason), emptyList())
            assertEquals(SessionStatus.FAIL, summary.status)
            assertEquals(0, summary.eventCount)
        }
    }

    @Test
    fun interruptionNeverBecomesPassEvenAfterSuccessfulEvents() {
        val reasons = listOf(
            SessionCloseReason.HOST_CLOSED,
            SessionCloseReason.REPLACED_BY_ASSOCIATION_ATTEMPT,
            SessionCloseReason.TEARDOWN_COMPLETE,
            SessionCloseReason.LOW_POWER_NO_CONNECTION,
        )
        for (reason in reasons) {
            assertEquals(SessionStatus.CANCELLED, SessionSummary(closed(reason), listOf(event(1))).status)
        }
        assertEquals(SessionStatus.CANCELLED, SessionSummary(
            closed(SessionCloseReason.SERVING_COMPLETE), listOf(event(1, ProtocolOutcome.CANCELLED)),
        ).status)
    }

    @Test
    fun summarySortsByReservedSequenceNotCompletionTimeAndSnapshotsMembership() {
        val first = event(1).copy(completedAtEpochMillis = 190)
        val second = event(2).copy(completedAtEpochMillis = 150)
        val supplied = mutableListOf(second, first)
        val summary = SessionSummary(active, supplied)
        supplied.clear()
        assertEquals(listOf(first, second), summary.events)
        assertEquals(2, summary.eventCount)
    }

    @Test
    fun lateOldSessionEventCannotBeIncludedInReplacementSummary() {
        val replacement = active.copy(id = "session-b")
        assertThrows(IllegalArgumentException::class.java) {
            SessionSummary(replacement, listOf(event(1)))
        }
        assertEquals("session-a", event(1).sessionId)
    }

    @Test
    fun duplicateIdsSequencesAndUnassignedEventsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            SessionSummary(active, listOf(event(1), event(1).copy(eventId = "other")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SessionSummary(active, listOf(event(1), event(2).copy(eventId = "session-a:1")))
        }
        assertThrows(IllegalArgumentException::class.java) { SessionSummary(active, listOf(event(0))) }
    }

    private val active = MwaSession(id = "session-a", startedAtEpochMillis = 100)

    private fun closed(reason: SessionCloseReason) = active.copy(
        completedAtEpochMillis = 200,
        closeReason = reason,
    )

    private fun event(sequence: Long, outcome: ProtocolOutcome = ProtocolOutcome.SUCCESS) = ProtocolEvent(
        sessionId = active.id,
        eventId = "${active.id}:$sequence",
        sequence = sequence,
        method = ProtocolMethod.AUTHORIZE,
        startedAtEpochMillis = 110,
        completedAtEpochMillis = 120,
        outcome = outcome,
    )
}
