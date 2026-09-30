package dev.mwalab.simulation

import dev.mwalab.protocol.ProtocolFailureSource

enum class SimulationOutcome { PASS, FAIL, UNAVAILABLE }

enum class SimulationErrorKind {
    BLOCKHASH_NOT_FOUND,
    ACCOUNT_NOT_FOUND,
    INSUFFICIENT_FUNDS_FOR_FEE,
    INSTRUCTION_ERROR,
    UNKNOWN_SIMULATION_ERROR,
}

data class SimulationErrorSummary(
    val kind: SimulationErrorKind,
    val instructionIndex: Int? = null,
    val instructionErrorKind: String? = null,
    val customProgramErrorCode: Long? = null,
) {
    init {
        require(instructionIndex == null || instructionIndex in 0..SimulationLimits.MAX_INSTRUCTION_INDEX)
        require(customProgramErrorCode == null || customProgramErrorCode in 0..SimulationLimits.MAX_CUSTOM_ERROR_CODE)
        if (kind == SimulationErrorKind.INSTRUCTION_ERROR) {
            require(instructionIndex != null)
            require(instructionErrorKind == null || instructionErrorKind in KNOWN_INSTRUCTION_ERRORS)
            require((instructionErrorKind == "Custom") == (customProgramErrorCode != null))
        } else {
            require(instructionIndex == null && instructionErrorKind == null && customProgramErrorCode == null)
        }
    }

    companion object {
        val KNOWN_INSTRUCTION_ERRORS = setOf("Custom", "InvalidArgument", "InvalidInstructionData",
            "InvalidAccountData", "InsufficientFunds", "IncorrectProgramId", "MissingRequiredSignature")
    }
}

data class SimulationTargetRef(
    val sessionId: String,
    val eventId: String,
    val requestId: String,
    val payloadIndex: Int,
    val transactionFingerprintSha256: String,
) {
    init {
        require(SimulationLimits.safeIdentity(sessionId))
        require(SimulationLimits.safeIdentity(eventId))
        require(SimulationLimits.safeIdentity(requestId))
        require(payloadIndex >= 0)
        require(transactionFingerprintSha256.matches(Regex("[0-9a-f]{64}")))
    }
}

enum class SimulationAvailabilityReason {
    TIMEOUT, IO, HTTP, JSON_RPC, MALFORMED_RESPONSE, LOCAL_INPUT, UNKNOWN
}

class SimulationResult(
    val simulationId: String,
    val target: SimulationTargetRef,
    val attemptNumber: Int,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val durationMillis: Long,
    val outcome: SimulationOutcome,
    val failureSource: ProtocolFailureSource,
    val commitment: String,
    val contextSlot: Long? = null,
    val error: SimulationErrorSummary? = null,
    val rpcErrorCode: Int? = null,
    val availabilityReason: SimulationAvailabilityReason? = null,
    val unitsConsumed: Long? = null,
    logs: BoundedLogs = BoundedLogs(emptyList(), false),
) {
    val programLogs: List<String> = java.util.Collections.unmodifiableList(ArrayList(logs.lines))
    val logsTruncated: Boolean = logs.truncated

    init {
        require(SimulationLimits.safeIdentity(simulationId))
        require(attemptNumber > 0)
        require(startedAtEpochMillis >= 0 && completedAtEpochMillis >= startedAtEpochMillis)
        require(durationMillis >= 0)
        require(commitment in setOf("processed", "confirmed", "finalized"))
        require(contextSlot == null || contextSlot >= 0)
        require(unitsConsumed == null || unitsConsumed >= 0)
        when (outcome) {
            SimulationOutcome.PASS -> require(failureSource == ProtocolFailureSource.NONE && error == null &&
                availabilityReason == null && rpcErrorCode == null)
            SimulationOutcome.FAIL -> require(failureSource == ProtocolFailureSource.SIMULATION && error != null &&
                availabilityReason == null && rpcErrorCode == null)
            SimulationOutcome.UNAVAILABLE -> {
                require(failureSource in setOf(ProtocolFailureSource.RPC_NETWORK,
                    ProtocolFailureSource.LOCAL_PARSER, ProtocolFailureSource.UNKNOWN))
                require(error == null && contextSlot == null && unitsConsumed == null && programLogs.isEmpty())
                require(availabilityReason != null)
                require((rpcErrorCode != null) == (availabilityReason == SimulationAvailabilityReason.JSON_RPC))
                require(when (failureSource) {
                    ProtocolFailureSource.RPC_NETWORK -> availabilityReason in setOf(
                        SimulationAvailabilityReason.TIMEOUT, SimulationAvailabilityReason.IO,
                        SimulationAvailabilityReason.HTTP, SimulationAvailabilityReason.JSON_RPC,
                        SimulationAvailabilityReason.MALFORMED_RESPONSE)
                    ProtocolFailureSource.LOCAL_PARSER -> availabilityReason == SimulationAvailabilityReason.LOCAL_INPUT
                    else -> availabilityReason == SimulationAvailabilityReason.UNKNOWN
                })
            }
        }
    }
}

object SimulationClassification {
    fun source(outcome: SimulationOutcome, reason: SimulationAvailabilityReason? = null): ProtocolFailureSource =
        when (outcome) {
            SimulationOutcome.PASS -> ProtocolFailureSource.NONE
            SimulationOutcome.FAIL -> ProtocolFailureSource.SIMULATION
            SimulationOutcome.UNAVAILABLE -> when (reason) {
                SimulationAvailabilityReason.LOCAL_INPUT -> ProtocolFailureSource.LOCAL_PARSER
                SimulationAvailabilityReason.UNKNOWN -> ProtocolFailureSource.UNKNOWN
                SimulationAvailabilityReason.TIMEOUT, SimulationAvailabilityReason.IO,
                SimulationAvailabilityReason.HTTP, SimulationAvailabilityReason.JSON_RPC,
                SimulationAvailabilityReason.MALFORMED_RESPONSE -> ProtocolFailureSource.RPC_NETWORK
                null -> throw IllegalArgumentException("Unavailable simulation requires reason")
            }
        }
}
