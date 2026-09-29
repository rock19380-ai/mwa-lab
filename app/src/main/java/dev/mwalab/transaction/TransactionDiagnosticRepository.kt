package dev.mwalab.transaction

import dev.mwalab.protocol.EventId
import dev.mwalab.session.SessionId
import kotlinx.coroutines.flow.Flow

/** Sanitized diagnostics only; the canonical parent event must already exist. */
interface TransactionDiagnosticRepository {
    suspend fun recordForEvent(sessionId: SessionId, eventId: EventId, summaries: List<TransactionSummary>)
    suspend fun getForEvent(sessionId: SessionId, eventId: EventId): List<TransactionSummary>
    fun observeForEvent(sessionId: SessionId, eventId: EventId): Flow<List<TransactionSummary>>
}
