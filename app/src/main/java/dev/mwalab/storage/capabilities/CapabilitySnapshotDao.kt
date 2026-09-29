package dev.mwalab.storage.capabilities

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CapabilitySnapshotDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(snapshot: CapabilitySnapshotEntity)

    @Transaction
    open suspend fun insertOnce(snapshot: CapabilitySnapshotEntity) {
        snapshot.toDomain() // The same strict metadata contract applies to direct DAO callers.
        val existing = get(snapshot.sessionId)
        if (existing == null) {
            insert(snapshot)
        } else {
            require(existing == snapshot) { "A session capability snapshot is immutable" }
        }
    }

    @Query("SELECT * FROM capability_snapshots WHERE session_id = :sessionId LIMIT 1")
    abstract suspend fun get(sessionId: String): CapabilitySnapshotEntity?

    @Query("SELECT * FROM capability_snapshots WHERE session_id = :sessionId LIMIT 1")
    abstract fun observe(sessionId: String): Flow<CapabilitySnapshotEntity?>
}
