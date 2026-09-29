package dev.mwalab.ui.sessions

import dev.mwalab.capabilities.CapabilitySnapshot
import dev.mwalab.capabilities.CapabilitySnapshotRepository
import dev.mwalab.capabilities.snapshotForSession
import dev.mwalab.mwa.capabilities.MwaCapabilityProfile
import dev.mwalab.identity.IdentityRepository
import dev.mwalab.identity.TestEndpointIdentity
import dev.mwalab.protocol.*
import dev.mwalab.session.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelsTest {
    @Test
    fun sessionsLoadEmptyThenObserveActiveSuccessFailureAndInterruptedState() = runTest {
        val repo = Repository()
        val model = SessionsViewModel(repo, backgroundScope)
        assertEquals(SessionsUiState.Loading, model.state.value)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        runCurrent()
        assertEquals(SessionsUiState.Empty, model.state.value)
        repo.rows.value = listOf(summary("open"), summary("pass", SessionCloseReason.SERVING_COMPLETE),
            summary("fail", SessionCloseReason.SCENARIO_COMPLETE, ProtocolOutcome.FAILURE),
            summary("cancel", SessionCloseReason.HOST_CLOSED, ProtocolOutcome.CANCELLED))
        runCurrent()
        val state = model.state.value as SessionsUiState.Ready
        assertEquals(listOf(SessionStatus.ACTIVE, SessionStatus.PASS, SessionStatus.FAIL, SessionStatus.CANCELLED),
            state.sessions.map { it.status })
        assertNotNull(failureText(state.sessions[2]))
        assertEquals(1, state.sessions[0].eventCount)
    }

    @Test
    fun repositoryErrorBecomesGenericStateAndRetryReloadsHistory() = runTest {
        val repo = Repository().apply { failReads = true }
        val model = SessionsViewModel(repo, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        runCurrent()
        assertEquals(SessionsUiState.Error, model.state.value)
        assertFalse(model.state.value.toString().contains("SECRET_REPOSITORY_EXCEPTION"))
        repo.failReads = false
        repo.rows.value = listOf(summary("reloaded", SessionCloseReason.SERVING_COMPLETE))
        model.retry()
        runCurrent()
        assertEquals("reloaded", (model.state.value as SessionsUiState.Ready).sessions.single().session.id)
    }

    @Test
    fun timelineUsesSequenceAndPreservesErrorsSourcesAndSafeSummaries() = runTest {
        val first = event("a", 1).copy(completedAtEpochMillis = 500)
        val second = event("a", 2, ProtocolOutcome.FAILURE).copy(protocolErrorCode = -3,
            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
            requestSummary = mapOf("payload_count" to "1", "payload_0_sha256" to "a".repeat(64)),
            responseSummary = mapOf("result" to "rejected"), completedAtEpochMillis = 150)
        val repo = Repository().apply {
            rows.value = listOf(SessionSummary(MwaSession("a", 100, 500,
                closeReason = SessionCloseReason.SCENARIO_COMPLETE), listOf(second, first)))
        }
        val model = SessionDetailViewModel(repo, Capabilities(), backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a")
        runCurrent()
        val recorded = (model.state.value as SessionDetailUiState.Ready).summary
        assertEquals(listOf(1L, 2L), recorded.events.map { it.sequence })
        assertEquals(-3, recorded.events[1].protocolErrorCode)
        assertEquals(ProtocolFailureSource.OBSERVED_PROTOCOL, recorded.events[1].failureSource)
        assertEquals("rejected", recorded.events[1].responseSummary["result"])
        assertEquals("1", recorded.events[1].requestSummary["payload_count"])
        assertEquals(SessionStatus.FAIL, recorded.status)
    }

    @Test
    fun selectingReplacementSessionRejectsLateOldHistoryUpdates() = runTest {
        val repo = Repository().apply { rows.value = listOf(summary("a"), summary("b")) }
        val model = SessionDetailViewModel(repo, Capabilities(), backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a")
        runCurrent()
        model.selectSession("b")
        runCurrent()
        repo.rows.value = listOf(summary("a", SessionCloseReason.HOST_CLOSED), summary("b"))
        runCurrent()
        assertEquals("b", (model.state.value as SessionDetailUiState.Ready).summary.session.id)
        assertEquals(SessionStatus.ACTIVE, (model.state.value as SessionDetailUiState.Ready).summary.status)
    }

    @Test
    fun missingAndUnavailableDetailsAreDistinct() = runTest {
        val repo = Repository()
        val model = SessionDetailViewModel(repo, Capabilities(), backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("missing")
        runCurrent()
        assertEquals(SessionDetailUiState.Missing, model.state.value)
        repo.failReads = true
        model.retry()
        runCurrent()
        assertEquals(SessionDetailUiState.Error, model.state.value)
        repo.failReads = false
        repo.rows.value = listOf(summary("missing"))
        model.retry()
        runCurrent()
        assertTrue(model.state.value is SessionDetailUiState.Ready)
    }

    @Test
    fun newStateHoldersLoadExistingPersistedAggregateRatherThanProcessEvidenceStore() = runTest {
        val loaded = summary("persisted", SessionCloseReason.SERVING_COMPLETE)
        val repo = Repository().apply { rows.value = listOf(loaded) }
        val first = SessionsViewModel(repo, backgroundScope)
        val reopened = SessionsViewModel(repo, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { first.state.collect() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { reopened.state.collect() }
        runCurrent()
        assertSame(loaded, (reopened.state.value as SessionsUiState.Ready).sessions.single())
        assertEquals(SessionStatus.PASS, (reopened.state.value as SessionsUiState.Ready).sessions.single().status)
    }

    @Test
    fun homeLoadsOnlyPublicIdentityAndLatestPersistentSession() = runTest {
        val repo = Repository().apply { rows.value = listOf(summary("new"), summary("old")) }
        val identity = object : IdentityRepository {
            override suspend fun getOrCreate() = TestEndpointIdentity(ByteArray(32), "PUBLIC_DEVNET_ADDRESS")
            override suspend fun reset(): TestEndpointIdentity = error("Home must never reset identity")
        }
        val model = HomeViewModel(identity, repo, backgroundScope, StandardTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        runCurrent()
        assertEquals(IdentityUiState.Ready("PUBLIC_DEVNET_ADDRESS"), model.state.value.identity)
        assertEquals("new", model.state.value.lastSession?.session?.id)
    }

    @Test
    fun homeIdentityFailureDoesNotHideExistingHistoryOrExposeException() = runTest {
        val repo = Repository().apply { rows.value = listOf(summary("history")) }
        val identity = object : IdentityRepository {
            override suspend fun getOrCreate(): TestEndpointIdentity = error("SECRET_IDENTITY_EXCEPTION")
            override suspend fun reset(): TestEndpointIdentity = error("unused")
        }
        val model = HomeViewModel(identity, repo, backgroundScope, StandardTestDispatcher(testScheduler))
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        runCurrent()
        assertEquals(IdentityUiState.Unavailable, model.state.value.identity)
        assertEquals("history", model.state.value.lastSession?.session?.id)
        assertFalse(model.state.value.toString().contains("SECRET_IDENTITY_EXCEPTION"))
    }

    @Test
    fun presentationRetainsUnknownErrorsAndOpenHistoryUncertainty() {
        assertEquals("Protocol error: ERROR_NOT_SIGNED (-3)", protocolErrorText(-3))
        assertEquals("Protocol error: -12345 (Unknown protocol error)", protocolErrorText(-12345))
        assertEquals("No protocol error recorded", protocolErrorText(null))
        assertEquals("ACTIVE · recorded open", sessionStatusText(SessionStatus.ACTIVE))
        assertEquals("1970-01-01 00:00:00.000 UTC", timestampText(0))
    }

    @Test
    fun capabilityReadsUsePersistedSessionDataAndLeaveCanonicalTimelineUnchanged() = runTest {
        val row = summary("a")
        val repo = Repository().apply { rows.value = listOf(row) }
        val persisted = snapshot("a", 123)
        val capabilities = Capabilities().apply { rows.value = mapOf("a" to persisted) }
        val model = SessionDetailViewModel(repo, capabilities, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a")
        runCurrent()
        val ready = model.state.value as SessionDetailUiState.Ready
        assertSame(row, ready.summary)
        assertSame(persisted, (ready.capabilities as SessionCapabilityUiState.Recorded).snapshot)
        assertEquals(listOf("a"), capabilities.readIds)
        assertEquals(listOf(1L), ready.summary.events.map { it.sequence })
        assertFalse(ready.summary.events.any { it.method == ProtocolMethod.GET_CAPABILITIES })
    }

    @Test
    fun historicalMissingSnapshotStaysMissingWithoutAnyCaptureOrWrite() = runTest {
        val repo = Repository().apply { rows.value = listOf(summary("historical")) }
        val capabilities = Capabilities()
        val model = SessionDetailViewModel(repo, capabilities, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("historical")
        runCurrent()
        assertEquals(SessionCapabilityUiState.Missing,
            (model.state.value as SessionDetailUiState.Ready).capabilities)
        model.retryCapabilities()
        runCurrent()
        assertEquals(SessionCapabilityUiState.Missing,
            (model.state.value as SessionDetailUiState.Ready).capabilities)
        assertEquals(listOf("historical", "historical"), capabilities.readIds)
    }

    @Test
    fun capabilityLoadingDoesNotDelayTimelineAndLaterRecordedValueUpdatesOnlyDiagnostics() = runTest {
        val repo = Repository().apply { rows.value = listOf(summary("a")) }
        val capabilities = Capabilities().apply { holdReads = true }
        val model = SessionDetailViewModel(repo, capabilities, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a")
        runCurrent()
        val initial = model.state.value as SessionDetailUiState.Ready
        assertEquals(SessionCapabilityUiState.Loading, initial.capabilities)
        assertEquals(1, initial.summary.eventCount)
        capabilities.releaseReads.value = true
        capabilities.rows.value = mapOf("a" to snapshot("a", 234))
        runCurrent()
        val loaded = model.state.value as SessionDetailUiState.Ready
        assertSame(initial.summary, loaded.summary)
        assertTrue(loaded.capabilities is SessionCapabilityUiState.Recorded)
    }

    @Test
    fun capabilityReadFailureIsGenericAndIndependentRetryDoesNotRestartTimeline() = runTest {
        val repo = Repository().apply { rows.value = listOf(summary("a")) }
        val capabilities = Capabilities().apply { failReads = true }
        val model = SessionDetailViewModel(repo, capabilities, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a")
        runCurrent()
        val ready = model.state.value as SessionDetailUiState.Ready
        assertEquals(SessionCapabilityUiState.Unavailable, ready.capabilities)
        assertFalse(ready.toString().contains("SECRET_CAPABILITY_EXCEPTION"))
        assertEquals(1, repo.detailSubscriptions)
        capabilities.failReads = false
        capabilities.rows.value = mapOf("a" to snapshot("a", 345))
        model.retryCapabilities()
        runCurrent()
        assertTrue((model.state.value as SessionDetailUiState.Ready).capabilities is SessionCapabilityUiState.Recorded)
        assertEquals(1, repo.detailSubscriptions)
    }

    @Test
    fun selectingAnotherSessionCannotReuseOrReceiveLateOldSnapshot() = runTest {
        val repo = Repository().apply { rows.value = listOf(summary("a"), summary("b")) }
        val capabilities = Capabilities().apply { rows.value = mapOf("a" to snapshot("a", 100)) }
        val model = SessionDetailViewModel(repo, capabilities, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a")
        runCurrent()
        model.selectSession("b")
        runCurrent()
        assertEquals(SessionCapabilityUiState.Missing,
            (model.state.value as SessionDetailUiState.Ready).capabilities)
        capabilities.rows.value = mapOf("a" to snapshot("a", 999), "b" to snapshot("b", 200))
        runCurrent()
        val ready = model.state.value as SessionDetailUiState.Ready
        assertEquals("b", ready.summary.session.id)
        assertEquals("b", (ready.capabilities as SessionCapabilityUiState.Recorded).snapshot.sessionId)
        assertEquals(200L, ready.capabilities.snapshot.capturedAtEpochMillis)
    }

    @Test
    fun mismatchedStoredSessionBindingMakesCapabilitiesUnavailableWithoutHidingHistory() = runTest {
        val repo = Repository().apply { rows.value = listOf(summary("a")) }
        val capabilities = Capabilities().apply { rows.value = mapOf("a" to snapshot("b", 100)) }
        val model = SessionDetailViewModel(repo, capabilities, backgroundScope)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect() }
        model.selectSession("a")
        runCurrent()
        val ready = model.state.value as SessionDetailUiState.Ready
        assertEquals("a", ready.summary.session.id)
        assertEquals(SessionCapabilityUiState.Unavailable, ready.capabilities)
    }

    private fun snapshot(id: String, at: Long) = MwaCapabilityProfile.snapshotForSession(
        id, at, MwaCapabilityProfile.createWalletConfig())

    private class Capabilities : CapabilitySnapshotRepository {
        val rows = MutableStateFlow<Map<String, CapabilitySnapshot>>(emptyMap())
        val releaseReads = MutableStateFlow(false)
        val readIds = mutableListOf<String>()
        var failReads = false
        var holdReads = false
        override fun observeSnapshot(sessionId: SessionId): Flow<CapabilitySnapshot?> = flow {
            readIds += sessionId
            if (failReads) error("SECRET_CAPABILITY_EXCEPTION")
            if (holdReads) releaseReads.first { it }
            emitAll(rows.map { it[sessionId] })
        }
        override suspend fun getSnapshot(sessionId: SessionId): CapabilitySnapshot? =
            error("UI must use the observed persisted snapshot")
        override suspend fun recordSnapshot(snapshot: CapabilitySnapshot) =
            error("UI must never capture or write capabilities")
    }

    private fun summary(id: String, reason: SessionCloseReason? = null, outcome: ProtocolOutcome = ProtocolOutcome.SUCCESS) =
        SessionSummary(MwaSession(id, 100, if (reason == null) null else 200,
            dappIdentityName = "Test dApp $id", closeReason = reason), listOf(event(id, 1, outcome)))

    private fun event(id: String, sequence: Long, outcome: ProtocolOutcome = ProtocolOutcome.SUCCESS) =
        ProtocolEvent(id, "$id:$sequence", sequence, ProtocolMethod.SIGN_MESSAGES, 110, 150, outcome)

    private class Repository : SessionRepository {
        val rows = MutableStateFlow<List<SessionSummary>>(emptyList())
        var failReads = false
        var detailSubscriptions = 0
        override fun observeSessions(): Flow<List<SessionSummary>> = flow {
            if (failReads) error("SECRET_REPOSITORY_EXCEPTION")
            emitAll(rows)
        }
        override fun observeSession(sessionId: SessionId): Flow<SessionSummary?> = flow {
            detailSubscriptions++
            emitAll(observeSessions().map { it.firstOrNull { row -> row.session.id == sessionId } })
        }
        override suspend fun getSession(sessionId: SessionId) = rows.value.firstOrNull { it.session.id == sessionId }
        override suspend fun createSession(session: MwaSession) = error("UI must not write history")
        override suspend fun finishSession(sessionId: SessionId, completedAtEpochMillis: Long, closeReason: SessionCloseReason) = error("UI must not close sessions")
        override suspend fun updateDappIdentity(sessionId: SessionId, dappIdentityName: String?) = error("UI must not update dApp identity")
        override suspend fun recordProtocolEvent(event: ProtocolEvent) = error("UI must not invent events")
    }
}
