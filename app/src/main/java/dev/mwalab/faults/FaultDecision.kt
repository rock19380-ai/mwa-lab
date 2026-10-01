package dev.mwalab.faults

/** A pure decision. Only the protocol-boundary adapter may translate it to walletlib. */
sealed interface FaultDecision {
    data object Continue : FaultDecision
    data object RejectAuthorization : FaultDecision
    data object RejectSigning : FaultDecision
    data class Delay(val milliseconds: Long) : FaultDecision
    data object UnsupportedChain : FaultDecision
    data object InvalidPayload : FaultDecision
    data object TooManyPayloads : FaultDecision
    data object RpcUnavailable : FaultDecision
    data object SubmissionFailure : FaultDecision
    data object StaleBlockhash : FaultDecision
}
