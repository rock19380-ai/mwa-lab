package dev.mwalab.session

import dev.mwalab.mwa.association.AssociationMode
import dev.mwalab.mwa.association.DappVerificationState
import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.protocol.recorder.EventClock
import dev.mwalab.protocol.recorder.ProtocolEventHandle
import dev.mwalab.protocol.recorder.ProtocolRecorder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLifecycleCoordinatorTest {
    @Test
    fun createSessionPersistsDevnetSessionAtClockTime() = runBlocking {
        val repository = FakeSessionRepository()
        val recorder = FakeProtocolRecorder()
        val coordinator = SessionLifecycleCoordinator(
            sessionRepository = repository,
            protocolRecorder = recorder,
            clock = FixedClock(1234L),
        )

        val result = coordinator.createSession("session-a")

        assertEquals(SessionLifecycleCoordinator.PersistenceResult.Persisted, result)
        assertEquals("session-a", repository.created.single().id)
        assertEquals(1234L, repository.created.single().startedAtEpochMillis)
        assertEquals("solana:devnet", repository.created.single().cluster)
        assertEquals(AssociationMode.LOCAL, repository.created.single().associationMode)
        assertEquals(DappVerificationState.NOT_AVAILABLE, repository.created.single().identityVerificationState)
        assertNull(repository.created.single().completedAtEpochMillis)
    }

    @Test
    fun remoteSessionAndVerificationStateUseTheSameLifecycleBoundary() = runBlocking {
        val repository = FakeSessionRepository()
        val coordinator = SessionLifecycleCoordinator(
            sessionRepository = repository,
            protocolRecorder = FakeProtocolRecorder(),
            clock = FixedClock(55L),
        )

        assertEquals(
            SessionLifecycleCoordinator.PersistenceResult.Persisted,
            coordinator.createSession("remote", AssociationMode.REMOTE),
        )
        assertEquals(AssociationMode.REMOTE, repository.created.single().associationMode)
        assertEquals(
            DappVerificationState.REMOTE_UNVERIFIED,
            repository.created.single().identityVerificationState,
        )

        assertEquals(
            SessionLifecycleCoordinator.PersistenceResult.Persisted,
            coordinator.updateIdentityVerificationState(
                "remote",
                DappVerificationState.REMOTE_UNVERIFIED,
            ),
        )
        assertEquals(
            "remote" to DappVerificationState.REMOTE_UNVERIFIED,
            repository.verificationStates.single(),
        )
    }

    @Test
    fun finishSettlesPendingRecorderBeforePersistingClose() = runBlocking {
        val order = mutableListOf<String>()
        val repository = FakeSessionRepository(order)
        val recorder = FakeProtocolRecorder(order)
        val coordinator = SessionLifecycleCoordinator(
            sessionRepository = repository,
            protocolRecorder = recorder,
            clock = FixedClock(2222L),
        )

        coordinator.finishSession("session-a", SessionCloseReason.SERVING_COMPLETE)

        assertEquals(listOf("recorder-cancel", "repository-finish"), order)
        assertEquals(
            Triple("session-a", 2222L, SessionCloseReason.SERVING_COMPLETE),
            repository.finished.single(),
        )
    }

    @Test
    fun firstCloseReasonRemainsAuthoritativeAcrossLaterCallbacks() = runBlocking {
        val repository = FakeSessionRepository()
        val recorder = FakeProtocolRecorder()
        val coordinator = SessionLifecycleCoordinator(
            sessionRepository = repository,
            protocolRecorder = recorder,
            clock = FixedClock(3000L),
        )

        coordinator.finishSession("session-a", SessionCloseReason.REPLACED_BY_ASSOCIATION_ATTEMPT)
        coordinator.finishSession("session-a", SessionCloseReason.TEARDOWN_COMPLETE)

        assertEquals(
            listOf(
                SessionCloseReason.REPLACED_BY_ASSOCIATION_ATTEMPT,
                SessionCloseReason.REPLACED_BY_ASSOCIATION_ATTEMPT,
            ),
            repository.finished.map { it.third },
        )
    }

    @Test
    fun dappIdentityIsTrimmedBoundedAndControlCharactersAreRejected() = runBlocking {
        val repository = FakeSessionRepository()
        val coordinator = SessionLifecycleCoordinator(
            sessionRepository = repository,
            protocolRecorder = FakeProtocolRecorder(),
        )

        val longName = "  " + "x".repeat(MwaSession.MAX_DAPP_DISPLAY_NAME_LENGTH + 20) + "  "
        val stored = coordinator.updateDappIdentity("session-a", longName)
        val rejected = coordinator.updateDappIdentity("session-a", "bad\u0000name")
        val skipped = coordinator.updateDappIdentity("session-a", "   ")

        assertEquals(SessionLifecycleCoordinator.PersistenceResult.Persisted, stored)
        assertEquals(SessionLifecycleCoordinator.PersistenceResult.Skipped, rejected)
        assertEquals(SessionLifecycleCoordinator.PersistenceResult.Skipped, skipped)
        assertEquals(MwaSession.MAX_DAPP_DISPLAY_NAME_LENGTH, repository.identities.single().second!!.length)
    }

    @Test
    fun persistenceFailuresAreExplicitAndDoNotThrow() = runBlocking {
        val repository = FakeSessionRepository(failWrites = true)
        val coordinator = SessionLifecycleCoordinator(
            sessionRepository = repository,
            protocolRecorder = FakeProtocolRecorder(failCancellation = true),
        )

        assertEquals(
            SessionLifecycleCoordinator.PersistenceResult.PersistenceFailed,
            coordinator.createSession("session-a"),
        )
        assertEquals(
            SessionLifecycleCoordinator.PersistenceResult.PersistenceFailed,
            coordinator.finishSession("session-a", SessionCloseReason.HOST_CLOSED),
        )
    }

    @Test
    fun firstCloseTimestampAndReasonWinWhenDuplicateCloseFinishesFirst() = runBlocking {
        val repository = FakeSessionRepository()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var first = true
        val recorder = object : ProtocolRecorder by FakeProtocolRecorder() {
            override suspend fun cancelPendingForSession(sessionId: SessionId,
                responseSummary: Map<String, String>): List<ProtocolRecorder.CompletionResult> {
                if (first) { first = false; entered.complete(Unit); release.await() }
                return emptyList()
            }
        }
        var now = 100L
        val coordinator = SessionLifecycleCoordinator(repository, recorder, EventClock { now })
        val original = async { coordinator.finishSession("a", SessionCloseReason.HOST_CLOSED) }
        entered.await()
        now = 200L
        coordinator.finishSession("a", SessionCloseReason.TEARDOWN_COMPLETE)
        release.complete(Unit)
        original.await()
        assertEquals(listOf(100L, 100L), repository.finished.map { it.second })
        assertTrue(repository.finished.all { it.third == SessionCloseReason.HOST_CLOSED })
    }

    private class FixedClock(private val value: Long) : EventClock {
        override fun nowEpochMillis(): Long = value
    }

    private class FakeSessionRepository(
        private val order: MutableList<String>? = null,
        private val failWrites: Boolean = false,
    ) : SessionRepository {
        val created = mutableListOf<MwaSession>()
        val finished = mutableListOf<Triple<SessionId, Long, SessionCloseReason>>()
        val identities = mutableListOf<Pair<SessionId, String?>>()
        val verificationStates = mutableListOf<Pair<SessionId, DappVerificationState>>()

        override suspend fun createSession(session: MwaSession) {
            if (failWrites) throw IllegalStateException("synthetic persistence failure")
            created += session
        }

        override suspend fun finishSession(
            sessionId: SessionId,
            completedAtEpochMillis: Long,
            closeReason: SessionCloseReason,
        ) {
            order?.add("repository-finish")
            if (failWrites) throw IllegalStateException("synthetic persistence failure")
            finished += Triple(sessionId, completedAtEpochMillis, closeReason)
        }

        override suspend fun updateDappIdentity(sessionId: SessionId, dappIdentityName: String?) {
            if (failWrites) throw IllegalStateException("synthetic persistence failure")
            identities += sessionId to dappIdentityName
        }

        override suspend fun updateIdentityVerificationState(
            sessionId: SessionId,
            identityVerificationState: DappVerificationState,
        ) {
            if (failWrites) throw IllegalStateException("synthetic persistence failure")
            verificationStates += sessionId to identityVerificationState
        }

        override suspend fun recordProtocolEvent(event: ProtocolEvent) = Unit
        override fun observeSessions(): Flow<List<SessionSummary>> = emptyFlow()
        override fun observeSession(sessionId: SessionId): Flow<SessionSummary?> = emptyFlow()
        override suspend fun getSession(sessionId: SessionId): SessionSummary? = null
    }

    private class FakeProtocolRecorder(
        private val order: MutableList<String>? = null,
        private val failCancellation: Boolean = false,
    ) : ProtocolRecorder {
        override suspend fun begin(
            sessionId: SessionId,
            method: ProtocolMethod,
            requestSummary: Map<String, String>,
        ): ProtocolEventHandle = throw UnsupportedOperationException()

        override suspend fun complete(
            handle: ProtocolEventHandle,
            outcome: ProtocolOutcome,
            protocolErrorCode: Int?,
            failureSource: ProtocolFailureSource,
            responseSummary: Map<String, String>,
        ): ProtocolRecorder.CompletionResult = throw UnsupportedOperationException()

        override suspend fun cancelPendingForSession(
            sessionId: SessionId,
            responseSummary: Map<String, String>,
        ): List<ProtocolRecorder.CompletionResult> {
            order?.add("recorder-cancel")
            if (failCancellation) throw IllegalStateException("synthetic recorder failure")
            assertTrue(responseSummary["reason"]?.startsWith("session_closed_") == true)
            return emptyList()
        }
    }
}
