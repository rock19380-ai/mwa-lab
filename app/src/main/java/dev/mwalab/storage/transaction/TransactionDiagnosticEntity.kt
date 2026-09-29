package dev.mwalab.storage.transaction

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.mwalab.storage.protocol.ProtocolEventEntity
import dev.mwalab.storage.session.SessionEntity

/** Sanitized child metadata for an already persisted terminal protocol event. */
@Entity(
    tableName = "transaction_diagnostics",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ProtocolEventEntity::class,
            parentColumns = ["event_id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["session_id"]),
        Index(value = ["event_id"]),
        Index(value = ["event_id", "payload_index"], unique = true),
    ],
)
data class TransactionDiagnosticEntity(
    @PrimaryKey
    @ColumnInfo(name = "transaction_id")
    val diagnosticId: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "event_id")
    val eventId: String,
    @ColumnInfo(name = "payload_index")
    val payloadIndex: Int,
    @ColumnInfo(name = "fingerprint_sha256")
    val fingerprintSha256: String,
    @ColumnInfo(name = "wire_length")
    val wireLength: Int,
    @ColumnInfo(name = "version")
    val version: String,
    @ColumnInfo(name = "inspection_status")
    val inspectionStatus: String,
    @ColumnInfo(name = "summary_json")
    val summaryJson: String,
)
