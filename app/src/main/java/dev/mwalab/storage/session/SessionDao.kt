package dev.mwalab.storage.session

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(session: SessionEntity)

    @Query(
        """
        UPDATE sessions
        SET completed_at_ms = :completedAtEpochMillis,
            close_reason = :closeReason
        WHERE session_id = :sessionId
          AND completed_at_ms IS NULL
        """,
    )
    suspend fun finish(
        sessionId: String,
        completedAtEpochMillis: Long,
        closeReason: String,
    ): Int

    @Query(
        """
        UPDATE sessions
        SET dapp_identity_name = :dappIdentityName
        WHERE session_id = :sessionId
        """,
    )
    suspend fun updateDappIdentity(sessionId: String, dappIdentityName: String?): Int

    @Transaction
    @Query("SELECT * FROM sessions ORDER BY started_at_ms DESC")
    fun observeAllWithEvents(): Flow<List<SessionWithEventsRecord>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE session_id = :sessionId LIMIT 1")
    fun observeWithEvents(sessionId: String): Flow<SessionWithEventsRecord?>

    @Transaction
    @Query("SELECT * FROM sessions WHERE session_id = :sessionId LIMIT 1")
    suspend fun getWithEvents(sessionId: String): SessionWithEventsRecord?

    @Query("SELECT * FROM sessions WHERE session_id = :sessionId LIMIT 1")
    suspend fun get(sessionId: String): SessionEntity?
}
