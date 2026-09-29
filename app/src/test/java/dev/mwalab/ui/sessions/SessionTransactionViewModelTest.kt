package dev.mwalab.ui.sessions

import dev.mwalab.capabilities.*
import dev.mwalab.protocol.*
import dev.mwalab.session.*
import dev.mwalab.transaction.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionTransactionViewModelTest {
    @Test fun persistedPayloadsAreOrderedAndAttachedOnlyToMatchingRealEvents() = runTest {
        val history = History().apply { rows.value = listOf(session("a")) }
        val diagnostics = Diagnostics().apply { rows.value = mapOf("a:1" to listOf(payload("a", 1), payload("a", 0))) }
        val model = SessionDetailViewModel(history, Capabilities, backgroundScope, diagnostics)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a"); runCurrent()
        val ready = model.state.value as SessionDetailUiState.Ready
        assertSame(history.rows.value.single(), ready.summary)
        assertEquals(listOf(0, 1), (ready.transactions["a:1"] as SessionTransactionUiState.Recorded).summaries.map { it.payloadIndex })
        assertEquals(listOf("a" to "a:1"), diagnostics.reads)
        assertEquals(listOf(1L), ready.summary.events.map { it.sequence })
        assertFalse(ready.summary.events.any { it.method == ProtocolMethod.GET_CAPABILITIES })
    }

    @Test fun historicalAbsenceDoesNotWriteOrHideTimeline() = runTest {
        val history = History().apply { rows.value = listOf(session("old")) }
        val diagnostics = Diagnostics()
        val model = SessionDetailViewModel(history, Capabilities, backgroundScope, diagnostics)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("old"); runCurrent()
        val ready = model.state.value as SessionDetailUiState.Ready
        assertEquals(SessionCapabilityUiState.Missing, ready.capabilities)
        assertEquals(SessionTransactionUiState.Missing, ready.transactions["old:1"])
        assertEquals(1, ready.summary.eventCount)
    }

    @Test fun loadingIsIndependentAndLatePersistenceUpdatesDiagnostics() = runTest {
        val history = History().apply { rows.value = listOf(session("a")) }
        val diagnostics = Diagnostics().apply { held = true }
        val model = SessionDetailViewModel(history, Capabilities, backgroundScope, diagnostics)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a"); runCurrent()
        assertEquals(SessionTransactionUiState.Loading, (model.state.value as SessionDetailUiState.Ready).transactions["a:1"])
        diagnostics.rows.value = mapOf("a:1" to listOf(payload("a", 0)))
        diagnostics.release.value = true; runCurrent()
        assertTrue((model.state.value as SessionDetailUiState.Ready).transactions["a:1"] is SessionTransactionUiState.Recorded)
    }

    @Test fun oneEventReadFailureDoesNotHideOthersAndRetryOnlyReloadsThatEvent() = runTest {
        val history = History().apply { rows.value = listOf(session("a", twoEvents = true)) }
        val diagnostics = Diagnostics().apply {
            failures += "a:1"
            rows.value = mapOf("a:2" to listOf(payload("a", 0, "a:2")))
        }
        val model = SessionDetailViewModel(history, Capabilities, backgroundScope, diagnostics)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a"); runCurrent()
        val before = model.state.value as SessionDetailUiState.Ready
        assertEquals(SessionTransactionUiState.Unavailable, before.transactions["a:1"])
        assertTrue(before.transactions["a:2"] is SessionTransactionUiState.Recorded)
        assertFalse(before.toString().contains("SECRET_DIAGNOSTIC_EXCEPTION"))
        diagnostics.failures.clear()
        diagnostics.rows.value = diagnostics.rows.value + ("a:1" to listOf(payload("a", 0)))
        model.retryTransactions("a:1"); runCurrent()
        assertTrue((model.state.value as SessionDetailUiState.Ready).transactions["a:1"] is SessionTransactionUiState.Recorded)
        assertEquals(1, history.subscriptions)
        assertEquals(1, diagnostics.reads.count { it.second == "a:2" })
        assertEquals(2, diagnostics.reads.count { it.second == "a:1" })
    }

    @Test fun mismatchedBindingAndDuplicatePayloadsBecomeUnavailable() = runTest {
        val history = History().apply { rows.value = listOf(session("a")) }
        val diagnostics = Diagnostics().apply { rows.value = mapOf("a:1" to listOf(payload("b", 0))) }
        val model = SessionDetailViewModel(history, Capabilities, backgroundScope, diagnostics)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a"); runCurrent()
        assertEquals(SessionTransactionUiState.Unavailable, (model.state.value as SessionDetailUiState.Ready).transactions["a:1"])
        diagnostics.rows.value = mapOf("a:1" to listOf(payload("a", 0), payload("a", 0)))
        model.retryTransactions("a:1"); runCurrent()
        assertEquals(SessionTransactionUiState.Unavailable, (model.state.value as SessionDetailUiState.Ready).transactions["a:1"])
        assertEquals("a", (model.state.value as SessionDetailUiState.Ready).summary.session.id)
    }

    @Test fun replacementSessionCannotReceiveOldDiagnosticsAndNewEventsAreObserved() = runTest {
        val history = History().apply { rows.value = listOf(session("a"), session("b")) }
        val diagnostics = Diagnostics().apply { rows.value = mapOf("a:1" to listOf(payload("a", 0))) }
        val model = SessionDetailViewModel(history, Capabilities, backgroundScope, diagnostics)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a"); runCurrent()
        model.selectSession("b"); runCurrent()
        diagnostics.rows.value = mapOf("a:1" to listOf(payload("a", 1)))
        runCurrent()
        val ready = model.state.value as SessionDetailUiState.Ready
        assertEquals("b", ready.summary.session.id)
        assertEquals(setOf("b:1"), ready.transactions.keys)
        assertEquals(SessionTransactionUiState.Missing, ready.transactions["b:1"])
        history.rows.value = listOf(session("a"), session("b", twoEvents = true))
        diagnostics.rows.value = mapOf("b:2" to listOf(payload("b", 0, "b:2")))
        runCurrent()
        assertTrue((model.state.value as SessionDetailUiState.Ready).transactions["b:2"] is SessionTransactionUiState.Recorded)
    }

    @Test fun messageEventsNeverSubscribeToTransactionDiagnostics() = runTest {
        val row = SessionSummary(MwaSession("a", 100), listOf(event("a", 1).copy(method = ProtocolMethod.SIGN_MESSAGES)))
        val history = History().apply { rows.value = listOf(row) }
        val diagnostics = Diagnostics()
        val model = SessionDetailViewModel(history, Capabilities, backgroundScope, diagnostics)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a"); runCurrent()
        assertTrue((model.state.value as SessionDetailUiState.Ready).transactions.isEmpty())
        assertTrue(diagnostics.reads.isEmpty())
        assertSame(row, (model.state.value as SessionDetailUiState.Ready).summary)
    }

    private fun payload(session: String, index: Int, event: String = "$session:1") =
        TransactionInspector().inspect(TransactionWireFixtures.legacy().bytes, index, TransactionDiagnosticBinding(session, event))
    private fun event(id: String, sequence: Long) = ProtocolEvent(id, "$id:$sequence", sequence,
        if (sequence == 1L) ProtocolMethod.SIGN_TRANSACTIONS else ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
        110, 150, ProtocolOutcome.SUCCESS)
    private fun session(id: String, twoEvents: Boolean = false) = SessionSummary(MwaSession(id, 100),
        if (twoEvents) listOf(event(id, 1), event(id, 2)) else listOf(event(id, 1)))

    private class Diagnostics : TransactionDiagnosticRepository {
        val rows = MutableStateFlow<Map<String, List<TransactionSummary>>>(emptyMap())
        val reads = mutableListOf<Pair<String, String>>()
        val failures = mutableSetOf<String>()
        val release = MutableStateFlow(false)
        var held = false
        override fun observeForEvent(sessionId: SessionId, eventId: EventId): Flow<List<TransactionSummary>> = flow {
            reads += sessionId to eventId
            if (eventId in failures) error("SECRET_DIAGNOSTIC_EXCEPTION")
            if (held) release.first { it }
            emitAll(rows.map { it[eventId] ?: emptyList() })
        }
        override suspend fun getForEvent(sessionId: SessionId, eventId: EventId) = error("Observe stored diagnostics")
        override suspend fun recordForEvent(sessionId: SessionId, eventId: EventId, summaries: List<TransactionSummary>) =
            error("UI must never write diagnostics")
    }

    private object Capabilities : CapabilitySnapshotRepository {
        override fun observeSnapshot(sessionId: SessionId) = flowOf<CapabilitySnapshot?>(null)
        override suspend fun getSnapshot(sessionId: SessionId) = error("unused")
        override suspend fun recordSnapshot(snapshot: CapabilitySnapshot) = error("UI must never write capabilities")
    }

    private class History : SessionRepository {
        val rows = MutableStateFlow<List<SessionSummary>>(emptyList())
        var subscriptions = 0
        override fun observeSessions() = rows
        override fun observeSession(sessionId: SessionId): Flow<SessionSummary?> = flow {
            subscriptions++
            emitAll(rows.map { it.firstOrNull { row -> row.session.id == sessionId } })
        }
        override suspend fun getSession(sessionId: SessionId) = error("unused")
        override suspend fun createSession(session: MwaSession) = error("UI must never create history")
        override suspend fun finishSession(sessionId: SessionId, completedAtEpochMillis: Long, closeReason: SessionCloseReason) = error("unused")
        override suspend fun updateDappIdentity(sessionId: SessionId, dappIdentityName: String?) = error("unused")
        override suspend fun recordProtocolEvent(event: ProtocolEvent) = error("UI must never create events")
    }
}
