package dev.mwalab.storage

import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.session.MwaSession
import dev.mwalab.session.SessionCloseReason
import dev.mwalab.session.SessionId
import dev.mwalab.session.SessionRepository
import dev.mwalab.session.SessionSummary
import dev.mwalab.storage.protocol.ProtocolEventDao
import dev.mwalab.storage.session.SessionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSessionRepository(
    private val sessionDao: SessionDao,
    private val protocolEventDao: ProtocolEventDao,
) : SessionRepository {
    override suspend fun createSession(session: MwaSession) {
        sessionDao.insert(session.toEntity())
    }

    override suspend fun finishSession(
        sessionId: SessionId,
        completedAtEpochMillis: Long,
        closeReason: SessionCloseReason,
    ) {
        require(completedAtEpochMillis >= 0) { "Completion must be an epoch timestamp" }
        sessionDao.finish(sessionId, completedAtEpochMillis, closeReason.name)
    }

    override suspend fun updateDappIdentity(sessionId: SessionId, dappIdentityName: String?) {
        val normalized = dappIdentityName?.trim()?.takeIf(String::isNotBlank)
        require(normalized == null || normalized.length <= MwaSession.MAX_DAPP_DISPLAY_NAME_LENGTH) {
            "dApp display label is too long"
        }
        require(normalized == null || normalized.none { it.isISOControl() }) {
            "dApp display label contains control characters"
        }
        sessionDao.updateDappIdentity(sessionId, normalized)
    }

    override suspend fun recordProtocolEvent(event: ProtocolEvent) {
        require(event.sessionId.isNotBlank() && event.sessionId != "unknown") {
            "Protocol event requires an assigned session"
        }
        require(event.eventId.isNotBlank() && event.eventId != "unknown:0") {
            "Protocol event requires an assigned event identity"
        }
        require(event.sequence > 0) { "Protocol event sequence must be positive" }
        protocolEventDao.insert(event.toEntity())
    }

    override fun observeSessions(): Flow<List<SessionSummary>> =
        sessionDao.observeAllWithEvents().map { rows -> rows.map { it.toDomain() } }

    override fun observeSession(sessionId: SessionId): Flow<SessionSummary?> =
        sessionDao.observeWithEvents(sessionId).map { it?.toDomain() }

    override suspend fun getSession(sessionId: SessionId): SessionSummary? =
        sessionDao.getWithEvents(sessionId)?.toDomain()
}
