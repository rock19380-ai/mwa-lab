package dev.mwalab.storage.protocol

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProtocolEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: ProtocolEventEntity)

    @Query(
        """
        SELECT * FROM protocol_events
        WHERE session_id = :sessionId
        ORDER BY sequence ASC
        """,
    )
    fun observeForSession(sessionId: String): Flow<List<ProtocolEventEntity>>

    @Query(
        """
        SELECT * FROM protocol_events
        WHERE session_id = :sessionId
        ORDER BY sequence ASC
        """,
    )
    suspend fun getForSession(sessionId: String): List<ProtocolEventEntity>
}
