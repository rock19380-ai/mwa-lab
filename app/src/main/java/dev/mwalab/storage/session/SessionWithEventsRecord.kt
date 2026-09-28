package dev.mwalab.storage.session

import androidx.room.Embedded
import androidx.room.Relation
import dev.mwalab.storage.protocol.ProtocolEventEntity

data class SessionWithEventsRecord(
    @Embedded
    val session: SessionEntity,
    @Relation(
        parentColumn = "session_id",
        entityColumn = "session_id",
    )
    val events: List<ProtocolEventEntity>,
)
