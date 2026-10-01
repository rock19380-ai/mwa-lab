package dev.mwalab.faults

import dev.mwalab.protocol.ProtocolMethod

/** Immutable request binding; contains no callback, payload, token, or secret. */
data class FaultRequestSnapshot(
    val profile: FaultProfile,
    val method: ProtocolMethod,
    val sessionId: String,
    val generation: Long,
) {
    init {
        require(sessionId.isNotBlank() && generation >= 0)
    }

    fun belongsTo(sessionId: String, generation: Long): Boolean =
        this.sessionId == sessionId && this.generation == generation
}

class DeterministicFaultEngine {
    /** The sole selection read for this observable request. */
    fun capture(
        selection: FaultSelectionRepository,
        method: ProtocolMethod,
        sessionId: String,
        generation: Long,
    ): FaultRequestSnapshot = FaultRequestSnapshot(selection.selected.value, method, sessionId, generation)

    fun evaluate(snapshot: FaultRequestSnapshot, hook: FaultHook): FaultDecision {
        val profile = snapshot.profile
        if (profile.id == FaultId.NORMAL || profile.hook != hook || snapshot.method !in profile.targetMethods) {
            return FaultDecision.Continue
        }
        return when (profile.id) {
            FaultId.NORMAL -> FaultDecision.Continue
            FaultId.AUTH_REJECT -> FaultDecision.RejectAuthorization
            FaultId.SIGN_REJECT -> FaultDecision.RejectSigning
            FaultId.DELAY_5S -> FaultDecision.Delay(5_000L)
            FaultId.UNSUPPORTED_CHAIN -> FaultDecision.UnsupportedChain
            FaultId.INVALID_PAYLOAD -> FaultDecision.InvalidPayload
            FaultId.TOO_MANY_PAYLOADS -> FaultDecision.TooManyPayloads
            FaultId.RPC_UNAVAILABLE -> FaultDecision.RpcUnavailable
            FaultId.SUBMISSION_FAILURE -> FaultDecision.SubmissionFailure
            FaultId.STALE_BLOCKHASH -> FaultDecision.StaleBlockhash
        }
    }
}
