package dev.mwalab.storage.protocol

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.mwalab.storage.session.SessionEntity

@Entity(
    tableName = "protocol_events",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["session_id", "sequence"], unique = true),
    ],
)
data class ProtocolEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "sequence")
    val sequence: Long,
    @ColumnInfo(name = "method")
    val method: String,
    @ColumnInfo(name = "started_at_ms")
    val startedAtEpochMillis: Long,
    @ColumnInfo(name = "completed_at_ms")
    val completedAtEpochMillis: Long,
    @ColumnInfo(name = "outcome")
    val outcome: String,
    @ColumnInfo(name = "protocol_error_code")
    val protocolErrorCode: Int?,
    @ColumnInfo(name = "failure_source")
    val failureSource: String,
    @ColumnInfo(name = "injected_fault_id")
    val injectedFaultId: String?,
    @ColumnInfo(name = "request_summary_json")
    val requestSummaryJson: String,
    @ColumnInfo(name = "response_summary_json")
    val responseSummaryJson: String,
    @ColumnInfo(name = "capability_context_json")
    val capabilityContextJson: String?,
)
