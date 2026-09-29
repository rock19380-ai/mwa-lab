package dev.mwalab.storage

import dev.mwalab.capabilities.CapabilitySnapshot
import dev.mwalab.capabilities.CapabilitySnapshotRepository
import dev.mwalab.session.SessionId
import dev.mwalab.storage.capabilities.CapabilitySnapshotDao
import dev.mwalab.storage.capabilities.toDomain
import dev.mwalab.storage.capabilities.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCapabilitySnapshotRepository(
    private val capabilitySnapshotDao: CapabilitySnapshotDao,
) : CapabilitySnapshotRepository {
    override suspend fun recordSnapshot(snapshot: CapabilitySnapshot) {
        capabilitySnapshotDao.insertOnce(snapshot.toEntity())
    }

    override suspend fun getSnapshot(sessionId: SessionId): CapabilitySnapshot? =
        capabilitySnapshotDao.get(sessionId)?.toDomain()

    override fun observeSnapshot(sessionId: SessionId): Flow<CapabilitySnapshot?> =
        capabilitySnapshotDao.observe(sessionId).map { it?.toDomain() }
}
