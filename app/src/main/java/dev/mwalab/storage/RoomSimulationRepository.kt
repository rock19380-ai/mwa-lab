package dev.mwalab.storage

import dev.mwalab.simulation.SimulationRepository
import dev.mwalab.simulation.SimulationResult
import dev.mwalab.storage.simulation.SimulationResultDao
import dev.mwalab.storage.simulation.toDomain
import dev.mwalab.storage.simulation.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomSimulationRepository(private val dao: SimulationResultDao) : SimulationRepository {
    override suspend fun recordForEvent(sessionId: String, eventId: String, results: List<SimulationResult>) {
        dao.insertForEvent(sessionId, eventId, results.map { it.toEntity() })
    }

    override suspend fun getForEvent(sessionId: String, eventId: String): List<SimulationResult> =
        decode(sessionId, eventId, dao.getForEvent(eventId))

    override fun observeForEvent(sessionId: String, eventId: String): Flow<List<SimulationResult>> =
        dao.observeForEvent(eventId).map { decode(sessionId, eventId, it) }

    private fun decode(sessionId: String, eventId: String,
        rows: List<dev.mwalab.storage.simulation.SimulationResultEntity>): List<SimulationResult> = rows.map { row ->
        require(row.sessionId == sessionId && row.eventId == eventId)
        row.toDomain()
    }
}
