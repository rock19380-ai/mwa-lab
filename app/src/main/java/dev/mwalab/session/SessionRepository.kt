package dev.mwalab.session

import dev.mwalab.protocol.ProtocolEvent
import kotlinx.coroutines.flow.Flow

interface SessionRepository {
    suspend fun createSession(session: MwaSession)

    suspend fun finishSession(
        sessionId: SessionId,
        completedAtEpochMillis: Long,
        closeReason: SessionCloseReason,
    )

    suspend fun updateDappIdentity(sessionId: SessionId, dappIdentityName: String?)

    suspend fun recordProtocolEvent(event: ProtocolEvent)

    fun observeSessions(): Flow<List<SessionSummary>>

    fun observeSession(sessionId: SessionId): Flow<SessionSummary?>

    suspend fun getSession(sessionId: SessionId): SessionSummary?
}
