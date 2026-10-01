package dev.mwalab.faults

/** Explicit MwaSessionHost boundaries; no hook grants signing or response authority. */
enum class FaultHook {
    AUTHORIZATION_DECISION,
    SIGNING_VALIDATION,
    SIGNING_PRE_APPROVAL,
    TRANSACTION_BLOCKHASH_CHECK,
    SUBMISSION_PRE_RPC,
}
