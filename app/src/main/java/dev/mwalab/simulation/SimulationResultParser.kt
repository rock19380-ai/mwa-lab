package dev.mwalab.simulation

import dev.mwalab.rpc.DevnetRpcResult
import dev.mwalab.rpc.DevnetSimulationOptions
import dev.mwalab.rpc.SimulationRpcValue
import dev.mwalab.rpc.TransportFailureReason

/**
 * Converts the fixed Devnet gateway's safe RPC representation into public diagnostic evidence.
 * No approval, signing, submission, recorder, or persistence dependency is present.
 */
object SimulationResultParser {
    fun parse(
        simulationId: String,
        target: SimulationTargetRef,
        attemptNumber: Int,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long,
        durationMillis: Long,
        options: DevnetSimulationOptions,
        rpc: DevnetRpcResult<SimulationRpcValue>,
    ): SimulationResult {
        val base = Base(simulationId, target, attemptNumber, startedAtEpochMillis,
            completedAtEpochMillis, durationMillis, options.commitment)
        return when (rpc) {
            is DevnetRpcResult.Success -> {
                val value = rpc.value
                val outcome = if (value.error == null) SimulationOutcome.PASS else SimulationOutcome.FAIL
                base.result(outcome, contextSlot = value.contextSlot, error = value.error,
                    unitsConsumed = value.unitsConsumed, logs = value.logs)
            }
            is DevnetRpcResult.RpcError -> base.result(SimulationOutcome.UNAVAILABLE,
                reason = SimulationAvailabilityReason.JSON_RPC, rpcErrorCode = rpc.code)
            is DevnetRpcResult.TransportFailure -> base.result(SimulationOutcome.UNAVAILABLE,
                reason = when (rpc.reason) {
                    TransportFailureReason.TIMEOUT -> SimulationAvailabilityReason.TIMEOUT
                    TransportFailureReason.IO -> SimulationAvailabilityReason.IO
                    TransportFailureReason.HTTP,
                    TransportFailureReason.RATE_LIMITED -> SimulationAvailabilityReason.HTTP
                })
            DevnetRpcResult.MalformedResponse -> base.result(SimulationOutcome.UNAVAILABLE,
                reason = SimulationAvailabilityReason.MALFORMED_RESPONSE)
        }
    }

    fun localUnavailable(
        simulationId: String,
        target: SimulationTargetRef,
        attemptNumber: Int,
        startedAtEpochMillis: Long,
        completedAtEpochMillis: Long,
        durationMillis: Long,
        commitment: String,
    ): SimulationResult = Base(simulationId, target, attemptNumber, startedAtEpochMillis,
        completedAtEpochMillis, durationMillis, commitment).result(
        SimulationOutcome.UNAVAILABLE, reason = SimulationAvailabilityReason.LOCAL_INPUT)

    private class Base(
        val id: String,
        val target: SimulationTargetRef,
        val attempt: Int,
        val started: Long,
        val completed: Long,
        val duration: Long,
        val commitment: String,
    ) {
        fun result(
            outcome: SimulationOutcome,
            contextSlot: Long? = null,
            error: SimulationErrorSummary? = null,
            unitsConsumed: Long? = null,
            logs: BoundedLogs = BoundedLogs(emptyList(), false),
            reason: SimulationAvailabilityReason? = null,
            rpcErrorCode: Int? = null,
        ) = SimulationResult(
            simulationId = id,
            target = target,
            attemptNumber = attempt,
            startedAtEpochMillis = started,
            completedAtEpochMillis = completed,
            durationMillis = duration,
            outcome = outcome,
            failureSource = SimulationClassification.source(outcome, reason),
            commitment = commitment,
            contextSlot = contextSlot,
            error = error,
            rpcErrorCode = rpcErrorCode,
            availabilityReason = reason,
            unitsConsumed = unitsConsumed,
            logs = logs,
        )
    }
}
