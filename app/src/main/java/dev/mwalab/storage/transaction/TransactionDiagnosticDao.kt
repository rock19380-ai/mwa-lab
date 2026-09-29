package dev.mwalab.storage.transaction

import androidx.room.Dao
import androidx.room.Query

/** Read-only schema shape; guarded population belongs to Step 4.9. */
@Dao
interface TransactionDiagnosticDao {
    @Query("SELECT * FROM transaction_diagnostics WHERE event_id = :eventId ORDER BY payload_index ASC")
    suspend fun getForEvent(eventId: String): List<TransactionDiagnosticEntity>
}
