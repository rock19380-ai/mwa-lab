package dev.mwalab.ui.sessions

import dev.mwalab.capabilities.CapabilitySnapshot
import dev.mwalab.capabilities.CapabilitySnapshotRepository
import dev.mwalab.protocol.*
import dev.mwalab.session.*
import dev.mwalab.simulation.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionSimulationViewModelTest {
    @Test fun persistedAttemptsAreOrderedAndHistoricalAbsenceIsTruthful() = runTest {
        val history = History().apply { rows.value = listOf(session("a"), session("old")) }
        val simulations = Simulations().apply {
            rows.value = mapOf("a:1" to listOf(result("a", 2), result("a", 1)))
        }
        val model = SessionDetailViewModel(history, Capabilities, backgroundScope,
            simulationRepository = simulations)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a"); runCurrent()
        val ready = model.state.value as SessionDetailUiState.Ready
        assertEquals(listOf(1, 2), (ready.simulations["a:1"] as SessionSimulationUiState.Recorded)
            .attempts.map { it.attemptNumber })
        assertEquals(ProtocolOutcome.FAILURE, ready.summary.events.single().outcome)
        model.selectSession("old"); runCurrent()
        assertEquals(SessionSimulationUiState.Missing,
            (model.state.value as SessionDetailUiState.Ready).simulations["old:1"])
        assertEquals(listOf("a" to "a:1", "old" to "old:1"), simulations.reads)
    }

    @Test fun failureAndWrongBindingAreIndependentOfTimelineAndRetryable() = runTest {
        val history = History().apply { rows.value = listOf(session("a")) }
        val simulations = Simulations().apply { fail = true }
        val model = SessionDetailViewModel(history, Capabilities, backgroundScope,
            simulationRepository = simulations)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a"); runCurrent()
        var ready = model.state.value as SessionDetailUiState.Ready
        assertEquals(SessionSimulationUiState.Unavailable, ready.simulations["a:1"])
        assertEquals(ProtocolOutcome.FAILURE, ready.summary.events.single().outcome)
        assertFalse(ready.toString().contains("SECRET_SIMULATION_EXCEPTION"))
        simulations.fail = false
        simulations.rows.value = mapOf("a:1" to listOf(result("b", 1)))
        model.retrySimulations("a:1"); runCurrent()
        assertEquals(SessionSimulationUiState.Unavailable,
            (model.state.value as SessionDetailUiState.Ready).simulations["a:1"])
        simulations.rows.value = mapOf("a:1" to listOf(result("a", 1)))
        model.retrySimulations("a:1"); runCurrent()
        ready = model.state.value as SessionDetailUiState.Ready
        assertTrue(ready.simulations["a:1"] is SessionSimulationUiState.Recorded)
        assertEquals(1, history.subscriptions)
    }

    private fun result(id: String, attempt: Int) = SimulationResult(
        "$id:1:simulation:0:$attempt", SimulationTargetRef(id, "$id:1", "request", 0, "a".repeat(64)),
        attempt, 1, 2, 1, SimulationOutcome.PASS, ProtocolFailureSource.NONE, "processed")
    private fun session(id: String) = SessionSummary(MwaSession(id, 100),
        listOf(ProtocolEvent(id, "$id:1", 1, ProtocolMethod.SIGN_TRANSACTIONS, 110, 150,
            ProtocolOutcome.FAILURE, protocolErrorCode = -3,
            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL)))

    private class Simulations : SimulationRepository {
        val rows = MutableStateFlow<Map<String, List<SimulationResult>>>(emptyMap())
        val reads = mutableListOf<Pair<String, String>>()
        var fail = false
        override fun observeForEvent(sessionId: String, eventId: String): Flow<List<SimulationResult>> = flow {
            reads += sessionId to eventId
            if (fail) error("SECRET_SIMULATION_EXCEPTION")
            emitAll(rows.map { it[eventId] ?: emptyList() })
        }
        override suspend fun getForEvent(sessionId: String, eventId: String) = error("UI must observe")
        override suspend fun recordForEvent(sessionId: String, eventId: String, results: List<SimulationResult>) =
            error("UI must not write")
    }
    private object Capabilities : CapabilitySnapshotRepository {
        override fun observeSnapshot(sessionId: SessionId) = flowOf<CapabilitySnapshot?>(null)
        override suspend fun getSnapshot(sessionId: SessionId) = error("unused")
        override suspend fun recordSnapshot(snapshot: CapabilitySnapshot) = error("unused")
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
        override suspend fun createSession(session: MwaSession) = error("unused")
        override suspend fun finishSession(sessionId: SessionId, completedAtEpochMillis: Long,
            closeReason: SessionCloseReason) = error("unused")
        override suspend fun updateDappIdentity(sessionId: SessionId, dappIdentityName: String?) = error("unused")
        override suspend fun recordProtocolEvent(event: ProtocolEvent) = error("unused")
    }
}
