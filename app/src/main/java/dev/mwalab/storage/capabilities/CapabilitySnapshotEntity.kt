package dev.mwalab.storage.capabilities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import dev.mwalab.storage.session.SessionEntity

@Entity(
    tableName = "capability_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CapabilitySnapshotEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "captured_at_ms")
    val capturedAtEpochMillis: Long,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "max_transactions")
    val maxTransactionsPerSigningRequest: Int,
    @ColumnInfo(name = "max_messages")
    val maxMessagesPerSigningRequest: Int,
    @ColumnInfo(name = "supported_versions_json")
    val supportedVersionsJson: String,
    @ColumnInfo(name = "optional_features_json")
    val optionalFeaturesJson: String,
)
