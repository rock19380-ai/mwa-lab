package dev.mwalab.simulation

import dev.mwalab.protocol.*
import dev.mwalab.protocol.recorder.*
import dev.mwalab.session.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimulationDiagnosticSettlementTest {
    @Test fun childBeforeParentWaitsAndKeepsCanonicalFailure() = runTest {
        val f = Fixture(this)
        val handle = f.recorder.begin("session", ProtocolMethod.SIGN_TRANSACTIONS, f.requestSummary)
        val context = f.settlement.begin(handle, "request")!!
        assertTrue(f.settlement.record(f.result(handle, 1)))
        f.settlement.finish(context)
        runCurrent()
        assertTrue(f.saved.isEmpty())
        val parent = f.recorder.complete(handle, ProtocolOutcome.FAILURE, -32003,
            ProtocolFailureSource.OBSERVED_PROTOCOL)
        f.settlement.completed(parent)
        runCurrent()
        assertEquals(1, f.saved.size)
        assertEquals(ProtocolOutcome.FAILURE, f.sessions.getSession("session")!!.events.single().outcome)
        assertEquals(ProtocolFailureSource.OBSERVED_PROTOCOL,
            f.sessions.getSession("session")!!.events.single().failureSource)
    }

    @Test fun parentBeforeChildAndLateAttemptPreserveOrderWithoutDelayingParent() = runTest {
        val f = Fixture(this)
        val handle = f.recorder.begin("session", ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS, f.requestSummary)
        val context = f.settlement.begin(handle, "request")!!
        val parent = f.recorder.complete(handle, ProtocolOutcome.SUCCESS)
        f.settlement.completed(parent)
        f.settlement.finish(context)
        assertTrue(f.settlement.record(f.result(handle, 1)))
        assertTrue(f.settlement.record(f.result(handle, 2)))
        assertFalse(f.settlement.record(f.result(handle, 2)))
        runCurrent()
        assertEquals(listOf(1, 2), f.saved.map { it.attemptNumber })
        assertEquals(parent.event, f.sessions.getSession("session")!!.events.single())
        assertEquals(1, f.sessions.writes)
    }

    @Test fun completedAttemptSurvivesImmediateSessionCloseAfterDurableParent() = runTest {
        val f = Fixture(this)
        val handle = f.recorder.begin("session", ProtocolMethod.SIGN_TRANSACTIONS, f.requestSummary)
        val context = f.settlement.begin(handle, "request")!!
        val accepted = f.result(handle, 1)
        assertTrue(f.settlement.record(accepted))
        val parent = f.recorder.complete(handle, ProtocolOutcome.SUCCESS)
        f.settlement.completed(parent)
        f.settlement.finish(context)
        // Serving-complete can close the session before the scheduled child write runs.
        f.settlement.invalidateSession("session")
        assertFalse(f.settlement.record(f.result(handle, 2)))
        runCurrent()
        assertEquals(listOf(accepted), f.saved)
        assertEquals(parent.event, f.sessions.getSession("session")!!.events.single())
    }

    @Test fun failedParentWrongIdentityAndInvalidationNeverInsert() = runTest {
        val f = Fixture(this)
        val handle = f.recorder.begin("session", ProtocolMethod.SIGN_TRANSACTIONS, f.requestSummary)
        val context = f.settlement.begin(handle, "request")!!
        assertFalse(f.settlement.record(f.result(handle, 1, request = "other")))
        assertFalse(f.settlement.record(f.result(handle, 1, session = "other")))
        assertFalse(f.settlement.record(f.result(handle, 1, event = "other:1")))
        f.settlement.completed(ProtocolRecorder.CompletionResult.PersistenceFailed(f.event(handle)))
        assertFalse(f.settlement.record(f.result(handle, 1)))
        f.settlement.finish(context)
        runCurrent()
        assertTrue(f.saved.isEmpty())

        val next = f.recorder.begin("session", ProtocolMethod.SIGN_TRANSACTIONS, f.requestSummary)
        val nextContext = f.settlement.begin(next, "next")!!
        assertTrue(f.settlement.record(f.result(next, 1, request = "next")))
        f.settlement.invalidateSession("session")
        f.recorder.complete(next, ProtocolOutcome.CANCELLED)
        f.settlement.finish(nextContext)
        runCurrent()
        assertTrue(f.saved.isEmpty())
    }

    @Test fun childWriteFailureIsIsolatedAndDelayedClaimWaitsForRealParent() = runTest {
        val f = Fixture(this)
        val handle = f.recorder.begin("session", ProtocolMethod.SIGN_TRANSACTIONS, f.requestSummary)
        val context = f.settlement.begin(handle, "request") { f.failures++ }!!
        val release = CompletableDeferred<Unit>()
        f.sessions.release = release
        val pending = async { f.recorder.complete(handle, ProtocolOutcome.SUCCESS) }
        runCurrent()
        val already = f.recorder.complete(handle, ProtocolOutcome.FAILURE)
        assertTrue(already is ProtocolRecorder.CompletionResult.AlreadyCompleted)
        f.settlement.completed(already)
        f.failChild = true
        f.settlement.record(f.result(handle, 1))
        f.settlement.finish(context)
        runCurrent()
        assertTrue(f.saved.isEmpty())
        release.complete(Unit)
        runCurrent()
        assertEquals(1, f.failures)
        assertEquals(ProtocolOutcome.SUCCESS, pending.await().event.outcome)
        assertTrue(f.saved.isEmpty())
    }

    private class Fixture(scope: TestScope) {
        val sessions = Sessions()
        val recorder = PersistentProtocolRecorder(sessions)
        val saved = mutableListOf<SimulationResult>()
        var failChild = false
        var failures = 0
        val requestSummary = mapOf("payload_count" to "1", "payload_0_sha256" to HASH)
        private val repository = object : SimulationRepository {
            override suspend fun recordForEvent(sessionId: String, eventId: String, results: List<SimulationResult>) {
                check(sessions.getSession(sessionId)!!.events.any { it.eventId == eventId })
                if (failChild) error("controlled storage failure")
                saved += results
            }
            override suspend fun getForEvent(sessionId: String, eventId: String) =
                saved.filter { it.target.sessionId == sessionId && it.target.eventId == eventId }
            override fun observeForEvent(sessionId: String, eventId: String): Flow<List<SimulationResult>> =
                flowOf(emptyList())
        }
        val settlement = SimulationDiagnosticSettlement(sessions, repository, scope.backgroundScope)
        fun result(handle: ProtocolEventHandle, attempt: Int, session: String = handle.sessionId,
            event: String = handle.eventId, request: String = "request") = SimulationResult(
            "$event:simulation:0:$attempt", SimulationTargetRef(session, event, request, 0, HASH),
            attempt, 1, 2, 1, SimulationOutcome.PASS, ProtocolFailureSource.NONE, "processed")
        fun event(handle: ProtocolEventHandle) = ProtocolEvent(handle.sessionId, handle.eventId,
            handle.sequence, handle.method, handle.startedAtEpochMillis, handle.startedAtEpochMillis + 1,
            ProtocolOutcome.SUCCESS, requestSummary = handle.requestSummary)
    }

    private class Sessions : SessionRepository {
        private val events = MutableStateFlow<List<ProtocolEvent>>(emptyList())
        var release: CompletableDeferred<Unit>? = null
        var writes = 0
        override suspend fun recordProtocolEvent(event: ProtocolEvent) {
            writes++
            release?.await()
            events.value += event
        }
        override suspend fun createSession(session: MwaSession) = Unit
        override suspend fun finishSession(sessionId: SessionId, completedAtEpochMillis: Long,
            closeReason: SessionCloseReason) = Unit
        override suspend fun updateDappIdentity(sessionId: SessionId, dappIdentityName: String?) = Unit
        override fun observeSessions(): Flow<List<SessionSummary>> = flowOf(emptyList())
        override fun observeSession(sessionId: SessionId) = events.map { summary(sessionId, it) }
        override suspend fun getSession(sessionId: SessionId) = summary(sessionId, events.value)
        private fun summary(id: String, all: List<ProtocolEvent>) =
            SessionSummary(MwaSession(id, 0), all.filter { it.sessionId == id })
    }

    private companion object { const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" }
}
