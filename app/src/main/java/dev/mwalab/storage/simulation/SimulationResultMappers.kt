package dev.mwalab.storage.simulation

import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.simulation.SimulationAvailabilityReason
import dev.mwalab.simulation.SimulationErrorKind
import dev.mwalab.simulation.SimulationErrorSummary
import dev.mwalab.simulation.SimulationOutcome
import dev.mwalab.simulation.SimulationResult
import dev.mwalab.simulation.SimulationTargetRef

internal fun SimulationResult.toEntity() = SimulationResultEntity(
    simulationId, target.sessionId, target.eventId, target.requestId, target.payloadIndex,
    attemptNumber, target.transactionFingerprintSha256, startedAtEpochMillis, completedAtEpochMillis,
    durationMillis, outcome.name, failureSource.name, commitment, contextSlot, error?.kind?.name,
    error?.instructionIndex, error?.instructionErrorKind, error?.customProgramErrorCode,
    rpcErrorCode, availabilityReason?.name, unitsConsumed, SimulationResultJson.encode(programLogs),
    logsTruncated,
)

internal fun SimulationResultEntity.toDomain(): SimulationResult {
    val error = errorKind?.let { SimulationErrorSummary(SimulationErrorKind.valueOf(it), instructionIndex,
        instructionErrorKind, customProgramErrorCode) }
    require(error != null || (instructionIndex == null && instructionErrorKind == null && customProgramErrorCode == null))
    return SimulationResult(simulationId,
        SimulationTargetRef(sessionId, eventId, requestId, payloadIndex, fingerprintSha256),
        attemptNumber, startedAtMillis, completedAtMillis, durationMillis,
        SimulationOutcome.valueOf(outcome), ProtocolFailureSource.valueOf(failureSource), commitment,
        contextSlot, error, rpcErrorCode, availabilityReason?.let(SimulationAvailabilityReason::valueOf),
        unitsConsumed, SimulationResultJson.decode(logsJson, logsTruncated))
}
