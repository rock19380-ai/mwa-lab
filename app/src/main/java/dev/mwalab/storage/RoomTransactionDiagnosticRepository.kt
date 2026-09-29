package dev.mwalab.storage

import dev.mwalab.protocol.EventId
import dev.mwalab.session.SessionId
import dev.mwalab.storage.transaction.TransactionDiagnosticDao
import dev.mwalab.storage.transaction.toDomain
import dev.mwalab.storage.transaction.toEntity
import dev.mwalab.transaction.TransactionDiagnosticRepository
import dev.mwalab.transaction.TransactionSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomTransactionDiagnosticRepository(private val dao: TransactionDiagnosticDao) : TransactionDiagnosticRepository {
    override suspend fun recordForEvent(sessionId: SessionId, eventId: EventId, summaries: List<TransactionSummary>) {
        dao.insertForEvent(sessionId, eventId, summaries.map { it.toEntity() })
    }

    override suspend fun getForEvent(sessionId: SessionId, eventId: EventId): List<TransactionSummary> =
        decode(sessionId, dao.getForEvent(eventId))

    override fun observeForEvent(sessionId: SessionId, eventId: EventId): Flow<List<TransactionSummary>> =
        dao.observeForEvent(eventId).map { decode(sessionId, it) }

    private fun decode(sessionId: SessionId, rows: List<dev.mwalab.storage.transaction.TransactionDiagnosticEntity>) =
        rows.map { row -> require(row.sessionId == sessionId); row.toDomain() }
}
