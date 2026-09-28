package dev.mwalab.storage.session

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "started_at_ms")
    val startedAtEpochMillis: Long,
    @ColumnInfo(name = "completed_at_ms")
    val completedAtEpochMillis: Long?,
    @ColumnInfo(name = "dapp_identity_name")
    val dappIdentityName: String?,
    @ColumnInfo(name = "cluster")
    val cluster: String,
    @ColumnInfo(name = "close_reason")
    val closeReason: String?,
)
