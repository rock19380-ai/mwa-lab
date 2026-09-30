package dev.mwalab.storage.simulation

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.mwalab.storage.protocol.ProtocolEventEntity
import dev.mwalab.storage.session.SessionEntity

@Entity(
    tableName = "simulation_results",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["session_id"], childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ProtocolEventEntity::class, parentColumns = ["event_id"], childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["session_id"]), Index(value = ["event_id"]),
        Index(value = ["event_id", "payload_index", "attempt_number"], unique = true)],
)
data class SimulationResultEntity(
    @PrimaryKey @ColumnInfo(name = "simulation_id") val simulationId: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "event_id") val eventId: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "payload_index") val payloadIndex: Int,
    @ColumnInfo(name = "attempt_number") val attemptNumber: Int,
    @ColumnInfo(name = "fingerprint_sha256") val fingerprintSha256: String,
    @ColumnInfo(name = "started_at_ms") val startedAtMillis: Long,
    @ColumnInfo(name = "completed_at_ms") val completedAtMillis: Long,
    @ColumnInfo(name = "duration_ms") val durationMillis: Long,
    @ColumnInfo(name = "outcome") val outcome: String,
    @ColumnInfo(name = "failure_source") val failureSource: String,
    @ColumnInfo(name = "commitment") val commitment: String,
    @ColumnInfo(name = "context_slot") val contextSlot: Long?,
    @ColumnInfo(name = "error_kind") val errorKind: String?,
    @ColumnInfo(name = "instruction_index") val instructionIndex: Int?,
    @ColumnInfo(name = "instruction_error_kind") val instructionErrorKind: String?,
    @ColumnInfo(name = "custom_program_error_code") val customProgramErrorCode: Long?,
    @ColumnInfo(name = "rpc_error_code") val rpcErrorCode: Int?,
    @ColumnInfo(name = "availability_reason") val availabilityReason: String?,
    @ColumnInfo(name = "units_consumed") val unitsConsumed: Long?,
    @ColumnInfo(name = "logs_json") val logsJson: String,
    @ColumnInfo(name = "logs_truncated") val logsTruncated: Boolean,
)
