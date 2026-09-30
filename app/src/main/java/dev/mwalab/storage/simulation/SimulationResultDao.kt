package dev.mwalab.storage.simulation

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.storage.SafeSummaryJson
import dev.mwalab.storage.protocol.ProtocolEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SimulationResultDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(rows: List<SimulationResultEntity>)

    @Query("SELECT * FROM protocol_events WHERE event_id = :eventId LIMIT 1")
    protected abstract suspend fun parent(eventId: String): ProtocolEventEntity?

    @Transaction
    open suspend fun insertForEvent(sessionId: String, eventId: String, rows: List<SimulationResultEntity>) {
        require(rows.size <= 256)
        val event = requireNotNull(parent(eventId)) { "Simulation parent event unavailable" }
        require(event.sessionId == sessionId && event.method in setOf(
            ProtocolMethod.SIGN_TRANSACTIONS.name, ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS.name))
        val request = SafeSummaryJson.decode(event.requestSummaryJson)
        val count = requireNotNull(request["payload_count"]?.toIntOrNull())
        val keys = rows.map { it.payloadIndex to it.attemptNumber }
        require(keys.size == keys.distinct().size)
        val existing = getForEvent(eventId).associateBy { it.payloadIndex to it.attemptNumber }
        val newRows = ArrayList<SimulationResultEntity>()
        for (row in rows) {
            val result = row.toDomain()
            require(result.target.sessionId == sessionId && result.target.eventId == eventId)
            require(result.target.payloadIndex < count)
            require(request["payload_${result.target.payloadIndex}_sha256"] == result.target.transactionFingerprintSha256)
            val previous = existing[row.payloadIndex to row.attemptNumber]
            if (previous == null) newRows += row else require(previous == row) { "Simulation attempt is immutable" }
        }
        if (newRows.isNotEmpty()) insert(newRows)
    }

    @Query("SELECT * FROM simulation_results WHERE event_id = :eventId ORDER BY payload_index ASC, attempt_number ASC")
    abstract suspend fun getForEvent(eventId: String): List<SimulationResultEntity>

    @Query("SELECT * FROM simulation_results WHERE event_id = :eventId ORDER BY payload_index ASC, attempt_number ASC")
    abstract fun observeForEvent(eventId: String): Flow<List<SimulationResultEntity>>
}
