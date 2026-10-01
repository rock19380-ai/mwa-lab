package dev.mwalab.faults

import com.solana.mobilewalletadapter.common.ProtocolContract
import dev.mwalab.protocol.ProtocolMethod

/** Frozen Phase 6 catalog. This does not evaluate request-specific preconditions. */
object FaultCatalog {
    private val signing = setOf(
        ProtocolMethod.SIGN_MESSAGES,
        ProtocolMethod.SIGN_TRANSACTIONS,
        ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
    )
    private val transactions = setOf(
        ProtocolMethod.SIGN_TRANSACTIONS,
        ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS,
    )

    val profiles: List<FaultProfile> = listOf(
        FaultProfile(FaultId.NORMAL, "Normal mode", "Original protocol behavior",
            emptySet(), null, null),
        FaultProfile(FaultId.AUTH_REJECT, "Reject authorization", "Deliberate authorization decline",
            setOf(ProtocolMethod.AUTHORIZE, ProtocolMethod.REAUTHORIZE),
            FaultHook.AUTHORIZATION_DECISION, ProtocolContract.ERROR_AUTHORIZATION_FAILED),
        FaultProfile(FaultId.SIGN_REJECT, "Reject signing", "Deliberate not-signed result before approval",
            signing, FaultHook.SIGNING_PRE_APPROVAL, ProtocolContract.ERROR_NOT_SIGNED),
        FaultProfile(FaultId.DELAY_5S, "Delay 5 seconds", "Cancellable fixed delay before approval",
            signing, FaultHook.SIGNING_PRE_APPROVAL, null),
        FaultProfile(FaultId.UNSUPPORTED_CHAIN, "Unsupported chain", "Deliberate unsupported cluster result",
            setOf(ProtocolMethod.AUTHORIZE), FaultHook.AUTHORIZATION_DECISION,
            ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED),
        FaultProfile(FaultId.INVALID_PAYLOAD, "Invalid payload", "Deliberate invalid-payload result",
            signing, FaultHook.SIGNING_VALIDATION, ProtocolContract.ERROR_INVALID_PAYLOADS),
        FaultProfile(FaultId.TOO_MANY_PAYLOADS, "Too many payloads", "Deliberate request-limit result",
            signing, FaultHook.SIGNING_VALIDATION, ProtocolContract.ERROR_TOO_MANY_PAYLOADS),
        FaultProfile(FaultId.RPC_UNAVAILABLE, "RPC unavailable", "Deliberate unavailable submission gateway",
            setOf(ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS), FaultHook.SUBMISSION_PRE_RPC,
            ProtocolContract.ERROR_NOT_SUBMITTED),
        FaultProfile(FaultId.SUBMISSION_FAILURE, "Submission failure", "Deliberate pre-submission failure",
            setOf(ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS), FaultHook.SUBMISSION_PRE_RPC,
            ProtocolContract.ERROR_NOT_SUBMITTED),
        FaultProfile(FaultId.STALE_BLOCKHASH, "Stale transaction", "Deliberate stale-blockhash result",
            transactions, FaultHook.TRANSACTION_BLOCKHASH_CHECK,
            ProtocolContract.ERROR_INVALID_PAYLOADS),
    )

    private val byId = profiles.associateBy { it.id }
    private val byStableId = profiles.associateBy { it.id.stableId }

    init {
        check(profiles.size == FaultId.entries.size)
        check(byId.size == profiles.size && byStableId.size == profiles.size)
    }

    fun get(id: FaultId): FaultProfile = checkNotNull(byId[id])

    /** Unknown external or persisted IDs are not guessed. */
    fun find(stableId: String?): FaultProfile? = byStableId[stableId]
}
