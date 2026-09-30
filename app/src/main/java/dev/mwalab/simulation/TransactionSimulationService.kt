package dev.mwalab.simulation

import dev.mwalab.rpc.DevnetRpcGateway
import dev.mwalab.rpc.DevnetSimulationOptions
import kotlinx.coroutines.CancellationException

/** Runs one diagnostic RPC attempt. The caller owns and clears the transient byte array. */
class TransactionSimulationService(private val gateway: DevnetRpcGateway) {
    suspend fun simulate(
        target: SimulationTargetRef,
        attempt: Int,
        transaction: ByteArray,
        options: DevnetSimulationOptions,
    ): SimulationResult {
        val startWall = System.currentTimeMillis().coerceAtLeast(0)
        val startNanos = System.nanoTime()
        val rpc = try {
            gateway.simulateTransaction(transaction, options)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        val completed = System.currentTimeMillis().coerceAtLeast(startWall)
        val elapsed = ((System.nanoTime() - startNanos).coerceAtLeast(0) / 1_000_000L)
        val id = "${target.eventId}:simulation:${target.payloadIndex}:$attempt"
        return if (rpc == null) {
            SimulationResult(id, target, attempt, startWall, completed, elapsed,
                SimulationOutcome.UNAVAILABLE, SimulationClassification.source(
                    SimulationOutcome.UNAVAILABLE, SimulationAvailabilityReason.UNKNOWN),
                options.commitment, availabilityReason = SimulationAvailabilityReason.UNKNOWN)
        } else SimulationResultParser.parse(id, target, attempt, startWall, completed, elapsed, options, rpc)
    }
}
