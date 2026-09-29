package dev.mwalab.capabilities

import dev.mwalab.session.SessionId
import kotlinx.coroutines.flow.Flow

interface CapabilitySnapshotRepository {
    /** Insert once; identical replay is allowed, replacement is forbidden. */
    suspend fun recordSnapshot(snapshot: CapabilitySnapshot)

    suspend fun getSnapshot(sessionId: SessionId): CapabilitySnapshot?

    fun observeSnapshot(sessionId: SessionId): Flow<CapabilitySnapshot?>
}
