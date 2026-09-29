package dev.mwalab.transaction

import dev.mwalab.protocol.*
import dev.mwalab.protocol.recorder.*
import dev.mwalab.session.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class TransactionDiagnosticSettlementTest {
    @Test fun persistedEventPrecedesChildWriteAndRepeatedFinishIsIdempotent() = runTest {
        val f = Fixture(this)
        val context = f.context()
        val result = f.recorder.complete(context!!.handle, ProtocolOutcome.SUCCESS)
        f.settlement.completed(result)
        f.settlement.finish(context)
        f.settlement.finish(context)
        assertTrue(f.saved.isEmpty())
        runCurrent()
        assertEquals(1, f.saved.size)
        assertEquals(result.event.eventId, f.saved.single().single().eventId)
        assertEquals(result.event, f.sessions.getSession("original")!!.events.single())
        assertEquals(0, f.failures)
    }

    @Test fun nullHandleAndFailedParentWriteNeverProduceChildren() = runTest {
        val f = Fixture(this)
        assertNull(f.settlement.begin(null, f.summaries(null)) { f.failures++ })
        val context = f.context()!!
        val result = ProtocolRecorder.CompletionResult.PersistenceFailed(f.event(context.handle))
        f.settlement.completed(result)
        f.settlement.finish(context)
        runCurrent()
        assertTrue(f.saved.isEmpty())
        assertEquals(0, f.failures)
    }

    @Test fun alreadyCompletedClaimWaitsForActualDelayedRowWithoutCompletingRecorderAgain() = runTest {
        val f = Fixture(this)
        val context = f.context()!!
        val release = CompletableDeferred<Unit>()
        f.sessions.release = release
        val first = async { f.recorder.complete(context.handle, ProtocolOutcome.FAILURE, -3) }
        runCurrent()
        assertFalse(first.isCompleted)
        val duplicate = f.recorder.complete(context.handle, ProtocolOutcome.SUCCESS)
        assertTrue(duplicate is ProtocolRecorder.CompletionResult.AlreadyCompleted)
        f.settlement.completed(duplicate)
        f.settlement.finish(context)
        runCurrent()
        assertTrue(f.saved.isEmpty())
        release.complete(Unit)
        runCurrent()
        assertEquals(1, f.saved.size)
        assertEquals(ProtocolOutcome.FAILURE, f.sessions.getSession("original")!!.events.single().outcome)
        assertEquals(1, f.sessions.writes)
        first.await()
    }

    @Test fun missingClaimExpiresAfterFiveSecondsWithoutRetryOrOrphan() = runTest {
        val f = Fixture(this)
        val context = f.context()!!
        f.settlement.completed(ProtocolRecorder.CompletionResult.AlreadyCompleted(f.event(context.handle)))
        f.settlement.finish(context)
        runCurrent()
        advanceTimeBy(TransactionDiagnosticSettlement.PARENT_WAIT_MILLIS - 1)
        runCurrent()
        assertEquals(0, f.failures)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, f.failures)
        f.sessions.recordProtocolEvent(f.event(context.handle))
        advanceTimeBy(10_000); runCurrent()
        assertTrue(f.saved.isEmpty())
    }

    @Test fun closeSettlementWithoutHostCompletionUsesOriginalSessionAndSurvivesReplacement() = runTest {
        val f = Fixture(this)
        val context = f.context()!!
        f.recorder.begin("replacement", ProtocolMethod.AUTHORIZE)
        f.recorder.cancelPendingForSession("original")
        f.settlement.finish(context) // Authorization coroutine can be cancelled; worker is independent.
        runCurrent()
        assertEquals("original", f.saved.single().single().sessionId)
        assertEquals(context.handle.eventId, f.saved.single().single().eventId)
        assertEquals(ProtocolOutcome.CANCELLED, f.sessions.getSession("original")!!.events.single().outcome)
        assertTrue(f.sessions.getSession("replacement")!!.events.isEmpty())
    }

    @Test fun childFailureAndFailureEvidenceExceptionCannotChangeCanonicalResultOrOtherWorkers() = runTest {
        val f = Fixture(this)
        val context = f.context { f.failures++; error("untrusted failure sink") }!!
        f.failChildren = true
        val result = f.recorder.complete(context.handle, ProtocolOutcome.SUCCESS)
        f.settlement.completed(result)
        f.settlement.finish(context)
        runCurrent()
        assertEquals(1, f.failures)
        assertTrue(f.saved.isEmpty())
        assertEquals(result.event, f.sessions.getSession("original")!!.events.single())
        f.failChildren = false
        val next = f.context()!!
        f.settlement.completed(f.recorder.complete(next.handle, ProtocolOutcome.FAILURE))
        f.settlement.finish(next); runCurrent()
        assertEquals(next.handle.eventId, f.saved.single().single().eventId)
    }

    @Test fun wrongBindingOrParentMetadataNeverAcquiresChildAuthority() = runTest {
        val f = Fixture(this)
        val handle = f.recorder.begin("original", ProtocolMethod.SIGN_TRANSACTIONS)
        assertNull(f.settlement.begin(handle, f.summaries(TransactionDiagnosticBinding("other", handle.eventId))) { f.failures++ })
        val context = f.settlement.begin(handle, f.summaries(TransactionDiagnosticBinding(handle.sessionId, handle.eventId))) { f.failures++ }!!
        f.sessions.recordProtocolEvent(f.event(handle).copy(method = ProtocolMethod.SIGN_MESSAGES))
        f.settlement.completed(ProtocolRecorder.CompletionResult.Persisted(f.event(handle)))
        f.settlement.finish(context)
        runCurrent()
        advanceTimeBy(TransactionDiagnosticSettlement.PARENT_WAIT_MILLIS); runCurrent()
        assertTrue(f.saved.isEmpty())
        assertEquals(2, f.failures)
    }

    private class Fixture(scope: TestScope) {
        val sessions = Sessions()
        val recorder = PersistentProtocolRecorder(sessions)
        val saved = mutableListOf<List<TransactionSummary>>()
        var failures = 0
        var failChildren = false
        private val repository = object : TransactionDiagnosticRepository {
            override suspend fun recordForEvent(sessionId: SessionId, eventId: EventId, summaries: List<TransactionSummary>) {
                check(sessions.getSession(sessionId)!!.events.any { it.eventId == eventId })
                if (failChildren) error("SECRET_DAO_ERROR_MUST_NOT_ESCAPE")
                saved += summaries
            }
            override suspend fun getForEvent(sessionId: SessionId, eventId: EventId) = saved.flatten().filter { it.eventId == eventId }
            override fun observeForEvent(sessionId: SessionId, eventId: EventId): Flow<List<TransactionSummary>> = flowOf(emptyList())
        }
        val settlement = TransactionDiagnosticSettlement(sessions, repository, scope.backgroundScope)
        fun summaries(binding: TransactionDiagnosticBinding?) = TransactionApprovalDiagnostics(1,
            listOf(TransactionInspector().inspect(byteArrayOf(1), 0, binding)))
        suspend fun context(onFailure: () -> Unit = { failures++ }): TransactionDiagnosticSettlement.Context? {
            val handle = recorder.begin("original", ProtocolMethod.SIGN_TRANSACTIONS)
            return settlement.begin(handle, summaries(TransactionDiagnosticBinding(handle.sessionId, handle.eventId)), onFailure)
        }
        fun event(handle: ProtocolEventHandle) = ProtocolEvent(handle.sessionId, handle.eventId, handle.sequence,
            handle.method, handle.startedAtEpochMillis, handle.startedAtEpochMillis + 1, ProtocolOutcome.SUCCESS,
            requestSummary = handle.requestSummary)
    }

    private class Sessions : SessionRepository {
        private val events = MutableStateFlow<List<ProtocolEvent>>(emptyList())
        var writes = 0
        var release: CompletableDeferred<Unit>? = null
        override suspend fun recordProtocolEvent(event: ProtocolEvent) { writes++; release?.await(); events.value += event }
        override suspend fun createSession(session: MwaSession) = Unit
        override suspend fun finishSession(sessionId: SessionId, completedAtEpochMillis: Long, closeReason: SessionCloseReason) = Unit
        override suspend fun updateDappIdentity(sessionId: SessionId, dappIdentityName: String?) = Unit
        override fun observeSessions(): Flow<List<SessionSummary>> = flowOf(emptyList())
        override fun observeSession(sessionId: SessionId) = events.map { summary(sessionId, it) }
        override suspend fun getSession(sessionId: SessionId) = summary(sessionId, events.value)
        private fun summary(sessionId: String, events: List<ProtocolEvent>) =
            SessionSummary(MwaSession(sessionId, 0), events.filter { it.sessionId == sessionId })
    }
}
