package dev.mwalab.storage.transaction

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.storage.SafeSummaryJson
import dev.mwalab.storage.protocol.ProtocolEventEntity
import dev.mwalab.transaction.TransactionInspectionLimits
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TransactionDiagnosticDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(rows: List<TransactionDiagnosticEntity>)

    @Query("SELECT * FROM protocol_events WHERE event_id = :eventId LIMIT 1")
    protected abstract suspend fun parent(eventId: String): ProtocolEventEntity?

    /** Parent/session/method checks and immutable batch replay share the insertion transaction. */
    @Transaction
    open suspend fun insertForEvent(sessionId: String, eventId: String, rows: List<TransactionDiagnosticEntity>) {
        require(rows.size <= TransactionInspectionLimits.MAX_ACCOUNTS)
        val event = requireNotNull(parent(eventId)) { "Diagnostic parent event is unavailable" }
        require(event.sessionId == sessionId && event.method in setOf(
            ProtocolMethod.SIGN_TRANSACTIONS.name, ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS.name))
        val request = SafeSummaryJson.decode(event.requestSummaryJson)
        val payloadCount = requireNotNull(request["payload_count"]?.toIntOrNull())
        val sorted = rows.sortedBy { it.payloadIndex }
        require(sorted.map { it.payloadIndex }.distinct().size == sorted.size)
        sorted.forEach { row ->
            val summary = row.toDomain() // Direct DAO callers obey the same strict typed contract.
            require(summary.sessionId == sessionId && summary.eventId == eventId)
            require(summary.payloadIndex < payloadCount)
            require(request["payload_${summary.payloadIndex}_sha256"] == summary.fingerprintSha256)
            require(request["payload_${summary.payloadIndex}_length"] == summary.wireLength.toString())
        }
        val existing = getForEvent(eventId)
        if (existing.isEmpty()) insert(sorted)
        else require(existing == sorted) { "A diagnostic batch is immutable" }
    }

    @Query("SELECT * FROM transaction_diagnostics WHERE event_id = :eventId ORDER BY payload_index ASC")
    abstract suspend fun getForEvent(eventId: String): List<TransactionDiagnosticEntity>

    @Query("SELECT * FROM transaction_diagnostics WHERE event_id = :eventId ORDER BY payload_index ASC")
    abstract fun observeForEvent(eventId: String): Flow<List<TransactionDiagnosticEntity>>
}
