package dev.mwalab.simulation

import kotlinx.coroutines.flow.Flow

/** Durable child evidence only; the canonical parent event must already exist. */
interface SimulationRepository {
    suspend fun recordForEvent(sessionId: String, eventId: String, results: List<SimulationResult>)
    suspend fun getForEvent(sessionId: String, eventId: String): List<SimulationResult>
    fun observeForEvent(sessionId: String, eventId: String): Flow<List<SimulationResult>>
}
