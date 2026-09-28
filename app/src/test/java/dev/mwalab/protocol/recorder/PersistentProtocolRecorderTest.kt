package dev.mwalab.protocol.recorder

import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.session.MwaSession
import dev.mwalab.session.SessionCloseReason
import dev.mwalab.session.SessionId
import dev.mwalab.session.SessionRepository
import dev.mwalab.session.SessionSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import dev.mwalab.session.SessionLifecycleCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentProtocolRecorderTest {
    @Test
    fun closeWaitsForClaimedPersistenceBeforePublishingClosedSession() = runBlocking {
        val repository = BlockingSessionRepository()
        val recorder = PersistentProtocolRecorder(repository, FakeClock(100))
        val lifecycle = SessionLifecycleCoordinator(repository, recorder, FakeClock(200))
        val handle = recorder.begin("session-a", ProtocolMethod.SIGN_MESSAGES)
        val completion = async { recorder.complete(handle, ProtocolOutcome.FAILURE) }
        repository.started.await()
        val close = async { lifecycle.finishSession("session-a", SessionCloseReason.SCENARIO_COMPLETE) }
        yield()
        assertFalse("Close published before claimed event reached storage", close.isCompleted)
        repository.release.complete(Unit)
        completion.await()
        close.await()
        assertEquals(1, repository.events.size)
    }

    @Test
    fun invalidResponseCannotOrphanHandleAndCloseCanStillSettleIt() = runBlocking {
        val repository = FakeSessionRepository()
        val recorder = PersistentProtocolRecorder(repository, FakeClock(100))
        val handle = recorder.begin("session-a", ProtocolMethod.SIGN_MESSAGES)
        assertTrue(runCatching {
            recorder.complete(handle, ProtocolOutcome.SUCCESS, responseSummary = mapOf("raw_signature" to "SECRET"))
        }.exceptionOrNull() is IllegalArgumentException)
        recorder.cancelPendingForSession("session-a")
        assertEquals(ProtocolOutcome.CANCELLED, repository.events.single().outcome)
    }

    @Test
    fun beginReservesRequestStartSequenceIndependentlyPerSession() = runBlocking {
        val repository = FakeSessionRepository()
        val clock = FakeClock(100)
        val recorder = PersistentProtocolRecorder(repository, clock)

        val a1 = recorder.begin("session-a", ProtocolMethod.AUTHORIZE)
        clock.now = 101
        val a2 = recorder.begin("session-a", ProtocolMethod.SIGN_MESSAGES)
        clock.now = 102
        val b1 = recorder.begin("session-b", ProtocolMethod.AUTHORIZE)

        assertEquals(1L, a1.sequence)
        assertEquals("session-a:1", a1.eventId)
        assertEquals(2L, a2.sequence)
        assertEquals("session-a:2", a2.eventId)
        assertEquals(1L, b1.sequence)
        assertEquals("session-b:1", b1.eventId)
    }

    @Test
    fun reverseCompletionOrderDoesNotChangeReservedOrdering() = runBlocking {
        val repository = FakeSessionRepository()
        val clock = FakeClock(100)
        val recorder = PersistentProtocolRecorder(repository, clock)
        val first = recorder.begin("session-a", ProtocolMethod.AUTHORIZE)
        clock.now = 110
        val second = recorder.begin("session-a", ProtocolMethod.SIGN_MESSAGES)

        clock.now = 120
        recorder.complete(second, ProtocolOutcome.SUCCESS)
        clock.now = 130
        recorder.complete(first, ProtocolOutcome.SUCCESS)

        assertEquals(listOf(2L, 1L), repository.events.map { it.sequence })
        assertEquals("session-a", repository.events[1].sessionId)
        assertEquals(30L, repository.events[1].durationMillis)
        assertEquals(10L, repository.events[0].durationMillis)
    }

    @Test
    fun handleRemainsBoundToOriginalSessionAfterAnotherSessionBegins() = runBlocking {
        val repository = FakeSessionRepository()
        val clock = FakeClock(10)
        val recorder = PersistentProtocolRecorder(repository, clock)
        val old = recorder.begin("session-old", ProtocolMethod.SIGN_TRANSACTIONS)
        val replacement = recorder.begin("session-new", ProtocolMethod.AUTHORIZE)

        clock.now = 20
        recorder.complete(replacement, ProtocolOutcome.SUCCESS)
        clock.now = 30
        recorder.complete(
            old,
            outcome = ProtocolOutcome.FAILURE,
            protocolErrorCode = -3,
            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
        )

        val oldEvent = repository.events.single { it.eventId == "session-old:1" }
        val newEvent = repository.events.single { it.eventId == "session-new:1" }
        assertEquals("session-old", oldEvent.sessionId)
        assertEquals("session-new", newEvent.sessionId)
        assertEquals(1L, oldEvent.sequence)
        assertEquals(1L, newEvent.sequence)
    }

    @Test
    fun firstTerminalResultWinsAndRepeatedCompletionDoesNotPersistTwice() = runBlocking {
        val repository = FakeSessionRepository()
        val clock = FakeClock(100)
        val recorder = PersistentProtocolRecorder(repository, clock)
        val handle = recorder.begin("session-a", ProtocolMethod.SIGN_MESSAGES)

        clock.now = 120
        val first = recorder.complete(
            handle,
            outcome = ProtocolOutcome.FAILURE,
            protocolErrorCode = -3,
            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
        )
        clock.now = 999
        val duplicate = recorder.complete(handle, ProtocolOutcome.SUCCESS)

        assertTrue(first is ProtocolRecorder.CompletionResult.Persisted)
        assertTrue(duplicate is ProtocolRecorder.CompletionResult.AlreadyCompleted)
        assertEquals(1, repository.events.size)
        assertEquals(ProtocolOutcome.FAILURE, duplicate.event.outcome)
        assertEquals(-3, duplicate.event.protocolErrorCode)
        assertEquals(120L, duplicate.event.completedAtEpochMillis)
    }

    @Test
    fun concurrentRepeatedCompletionClaimsOnlyOnePersistenceWrite() = runBlocking {
        val repository = BlockingSessionRepository()
        val clock = FakeClock(100)
        val recorder = PersistentProtocolRecorder(repository, clock)
        val handle = recorder.begin("session-a", ProtocolMethod.SIGN_MESSAGES)

        val first = async {
            recorder.complete(
                handle,
                outcome = ProtocolOutcome.FAILURE,
                protocolErrorCode = -3,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
            )
        }
        repository.started.await()
        clock.now = 500
        val duplicate = recorder.complete(handle, ProtocolOutcome.SUCCESS)
        repository.release.complete(Unit)
        val firstResult = first.await()

        assertTrue(firstResult is ProtocolRecorder.CompletionResult.Persisted)
        assertTrue(duplicate is ProtocolRecorder.CompletionResult.AlreadyCompleted)
        assertEquals(1, repository.events.size)
        assertEquals(ProtocolOutcome.FAILURE, duplicate.event.outcome)
        assertEquals(-3, duplicate.event.protocolErrorCode)
    }

    @Test
    fun summariesAreDefensivelyCopiedAndBoundedBeforePersistence() = runBlocking {
        val repository = FakeSessionRepository()
        val clock = FakeClock(100)
        val recorder = PersistentProtocolRecorder(repository, clock)
        val request = linkedMapOf(
            "chain" to "solana:devnet",
            "payload_count" to "1",
        )
        val handle = recorder.begin("session-a", ProtocolMethod.SIGN_MESSAGES, request)
        request["payload_count"] = "999"
        request["result"] = "late-mutation"

        val response = linkedMapOf("result" to "rejected")
        clock.now = 110
        recorder.complete(
            handle,
            outcome = ProtocolOutcome.FAILURE,
            protocolErrorCode = -3,
            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
            responseSummary = response,
        )
        response["result"] = "mutated"

        val event = repository.events.single()
        assertEquals("solana:devnet", event.requestSummary["chain"])
        assertEquals("1", event.requestSummary["payload_count"])
        assertFalse(event.requestSummary.containsKey("result"))
        assertEquals("rejected", event.responseSummary["result"])
        assertEquals(null, event.injectedFaultId)
        assertEquals(null, event.capabilityContext)
    }

    @Test
    fun secretBearingSummaryKeysAreRejectedBeforePersistence() {
        runBlocking {
            val failure = runCatching {
                PersistentProtocolRecorder(FakeSessionRepository(), FakeClock(100)).begin(
                    "session-a",
                    ProtocolMethod.AUTHORIZE,
                    mapOf("auth_token" to "SECRET_AUTH_TOKEN"),
                )
            }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
        }
    }

    @Test
    fun persistenceFailureIsExplicitAndDoesNotThrowIntoProtocolCaller() = runBlocking {
        val repository = FakeSessionRepository(failWrites = true)
        val recorder = PersistentProtocolRecorder(repository, FakeClock(100))
        val handle = recorder.begin("session-a", ProtocolMethod.DEAUTHORIZE)

        val result = recorder.complete(handle, ProtocolOutcome.SUCCESS)
        val duplicate = recorder.complete(handle, ProtocolOutcome.FAILURE)

        assertTrue(result is ProtocolRecorder.CompletionResult.PersistenceFailed)
        assertTrue(duplicate is ProtocolRecorder.CompletionResult.AlreadyCompleted)
        assertEquals(ProtocolOutcome.SUCCESS, duplicate.event.outcome)
        assertTrue(repository.events.isEmpty())
    }

    @Test
    fun cancelPendingSettlesOnlyTargetSessionInSequenceOrder() = runBlocking {
        val repository = FakeSessionRepository()
        val clock = FakeClock(100)
        val recorder = PersistentProtocolRecorder(repository, clock)
        recorder.begin("session-a", ProtocolMethod.AUTHORIZE)
        recorder.begin("session-b", ProtocolMethod.AUTHORIZE)
        recorder.begin("session-a", ProtocolMethod.SIGN_MESSAGES)

        clock.now = 150
        val cancelled = recorder.cancelPendingForSession("session-a")

        assertEquals(listOf(1L, 2L), cancelled.map { it.event.sequence })
        assertEquals(listOf("session-a", "session-a"), cancelled.map { it.event.sessionId })
        assertTrue(cancelled.all { it.event.outcome == ProtocolOutcome.CANCELLED })
        assertTrue(cancelled.all { it.event.protocolErrorCode == null })
        assertTrue(cancelled.all { it.event.failureSource == ProtocolFailureSource.UNKNOWN })
        assertEquals(listOf("session-a", "session-a"), repository.events.map { it.sessionId })
    }

    @Test
    fun summaryKeysWithUnsupportedCharactersAreRejected() {
        runBlocking {
            val failure = runCatching {
                PersistentProtocolRecorder(FakeSessionRepository(), FakeClock(100)).begin(
                    "session-a",
                    ProtocolMethod.AUTHORIZE,
                    mapOf("bad key" to "value"),
                )
            }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
        }
    }

    @Test
    fun safeLookingButUnapprovedSummaryKeysAreRejected() {
        runBlocking {
            val failure = runCatching {
                PersistentProtocolRecorder(FakeSessionRepository(), FakeClock(100)).begin(
                    "session-a",
                    ProtocolMethod.AUTHORIZE,
                    mapOf("arbitrary_metadata" to "must-not-enter-persistence"),
                )
            }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
        }
    }

    @Test
    fun reconstructedValueEquivalentHandleIsRejected() = runBlocking {
        val repository = FakeSessionRepository()
        val recorder = PersistentProtocolRecorder(repository, FakeClock(100))
        val original = recorder.begin("session-a", ProtocolMethod.AUTHORIZE)
        val reconstructed = ProtocolEventHandle(
            sessionId = original.sessionId,
            eventId = original.eventId,
            sequence = original.sequence,
            method = original.method,
            startedAtEpochMillis = original.startedAtEpochMillis,
            requestSummary = original.requestSummary,
        )

        val failure = runCatching {
            recorder.complete(reconstructed, ProtocolOutcome.SUCCESS)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(repository.events.isEmpty())
        assertTrue(recorder.complete(original, ProtocolOutcome.SUCCESS) is ProtocolRecorder.CompletionResult.Persisted)
        assertEquals(1, repository.events.size)
    }

    @Test
    fun eventIdsStayUniqueAcrossSessionSpaces() = runBlocking {
        val recorder = PersistentProtocolRecorder(FakeSessionRepository(), FakeClock(100))
        val handles = listOf(
            recorder.begin("a", ProtocolMethod.AUTHORIZE),
            recorder.begin("a", ProtocolMethod.REAUTHORIZE),
            recorder.begin("b", ProtocolMethod.AUTHORIZE),
        )
        assertEquals(handles.size, handles.map { it.eventId }.toSet().size)
        assertNotEquals(handles[0].eventId, handles[2].eventId)
    }

    @Test
    fun callbackBeginningAfterCloseIsSettledInOriginalSession() = runBlocking {
        val repository = FakeSessionRepository()
        val recorder = PersistentProtocolRecorder(repository, FakeClock(100))
        recorder.cancelPendingForSession("a")
        val late = recorder.begin("a", ProtocolMethod.AUTHORIZE)
        val current = recorder.begin("b", ProtocolMethod.AUTHORIZE)
        recorder.complete(late, ProtocolOutcome.SUCCESS)
        recorder.complete(current, ProtocolOutcome.SUCCESS)
        assertEquals(ProtocolOutcome.CANCELLED, repository.events[0].outcome)
        assertEquals("a", repository.events[0].sessionId)
        assertEquals("b", repository.events[1].sessionId)
        assertEquals(1L, current.sequence)
    }

    @Test
    fun externalChainAndCommitmentCannotSmuggleSecretsThroughApprovedKeys() = runBlocking {
        val repository = FakeSessionRepository()
        val recorder = PersistentProtocolRecorder(repository, FakeClock(100))
        val handle = recorder.begin("a", ProtocolMethod.AUTHORIZE, mapOf("chain" to "SECRET_CHAIN"))
        recorder.complete(handle, ProtocolOutcome.FAILURE, responseSummary = mapOf("commitment" to "SECRET_COMMITMENT"))
        assertEquals("<unsupported>", repository.events.single().requestSummary["chain"])
        assertEquals("<unsupported>", repository.events.single().responseSummary["commitment"])
        assertTrue(runCatching { recorder.begin("a", ProtocolMethod.GET_CAPABILITIES) }.exceptionOrNull() is IllegalArgumentException)
    }

    private class FakeClock(var now: Long) : EventClock {
        override fun nowEpochMillis(): Long = now
    }

    private open class FakeSessionRepository(
        private val failWrites: Boolean = false,
    ) : SessionRepository {
        val events = mutableListOf<ProtocolEvent>()

        override suspend fun createSession(session: MwaSession) = Unit
        override suspend fun finishSession(
            sessionId: SessionId,
            completedAtEpochMillis: Long,
            closeReason: SessionCloseReason,
        ) = Unit
        override suspend fun updateDappIdentity(sessionId: SessionId, dappIdentityName: String?) = Unit
        override suspend fun recordProtocolEvent(event: ProtocolEvent) {
            if (failWrites) error("controlled persistence failure")
            events += event
        }
        override fun observeSessions(): Flow<List<SessionSummary>> = flowOf(emptyList())
        override fun observeSession(sessionId: SessionId): Flow<SessionSummary?> = flowOf(null)
        override suspend fun getSession(sessionId: SessionId): SessionSummary? = null
    }

    private class BlockingSessionRepository : FakeSessionRepository() {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun recordProtocolEvent(event: ProtocolEvent) {
            started.complete(Unit)
            release.await()
            super.recordProtocolEvent(event)
        }
    }
}
