package dev.mwalab.mwa.evidence

import java.util.concurrent.CopyOnWriteArrayList

enum class MwaSessionEvent {
    ASSOCIATION_ACCEPTED,
    ASSOCIATION_REJECTED,
    SCENARIO_START_REQUESTED,
    SCENARIO_READY,
    SERVING_CLIENTS,
    SERVING_COMPLETE,
    SCENARIO_COMPLETE,
    SCENARIO_ERROR,
    TEARDOWN_COMPLETE,
    LOW_POWER_NO_CONNECTION,
    AUTHORIZE_REQUEST,
    AUTHORIZE_SUCCEEDED,
    AUTHORIZE_CHAIN_REJECTED,
    AUTHORIZE_OPTIONAL_FEATURES_REJECTED,
    AUTHORIZE_SIGN_IN_REJECTED,
    AUTHORIZE_REQUESTED_ADDRESS_REJECTED,
    AUTHORIZE_IDENTITY_UNAVAILABLE,
    REAUTHORIZE_REQUEST,
    REAUTHORIZE_SUCCEEDED,
    REAUTHORIZE_REJECTED,
    DEAUTHORIZED_COMPLETED,
    SIGN_TRANSACTIONS_DECLINED_PHASE_1,
    SIGN_TRANSACTIONS_REQUEST,
    SIGN_TRANSACTIONS_APPROVED,
    SIGN_TRANSACTIONS_REJECTED,
    SIGN_TRANSACTIONS_INVALID,
    SIGN_TRANSACTIONS_SUCCEEDED,
    SIGN_MESSAGES_REQUEST,
    SIGN_MESSAGES_APPROVED,
    SIGN_MESSAGES_REJECTED,
    SIGN_MESSAGES_INVALID,
    SIGN_MESSAGES_SUCCEEDED,
    SIGN_AND_SEND_DECLINED_PHASE_1,
    SIGN_AND_SEND_REQUEST,
    SIGN_AND_SEND_APPROVED,
    SIGN_AND_SEND_REJECTED,
    SIGN_AND_SEND_INVALID,
    SIGN_AND_SEND_SUBMITTED,
    SIGN_AND_SEND_NOT_SUBMITTED,
    CLOSE_REQUESTED,
}

data class MwaSessionEvidence(
    val event: MwaSessionEvent,
    val timestampEpochMillis: Long = System.currentTimeMillis(),
    val detail: String? = null,
)

fun interface MwaSessionEvidenceSink {
    fun record(event: MwaSessionEvidence)
}

/**
 * Process-local Phase 1 evidence seam.
 *
 * Only protocol-safe summaries may be recorded here. Never record the raw
 * association URI, association token/public key, auth token, private key,
 * seed, mnemonic, payload, signature, or encrypted identity material.
 */
object MwaSessionEvidenceStore : MwaSessionEvidenceSink {
    private val events = CopyOnWriteArrayList<MwaSessionEvidence>()

    override fun record(event: MwaSessionEvidence) {
        events += event
    }

    fun snapshot(): List<MwaSessionEvidence> = events.toList()

    fun resetForTest() {
        events.clear()
    }
}
