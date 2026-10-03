package dev.mwalab.mwa.association

enum class AssociationMode {
    LOCAL,
    REMOTE,
}

enum class ConnectionStatus {
    VALIDATING_ASSOCIATION,
    STARTING_SCENARIO,
    WAITING_FOR_SESSION,
    AUTHORIZATION_WAITING,
    SERVING,
    COMPLETED,
    FAILED,
    TIMED_OUT,
    CANCELED,
}

enum class DappVerificationState {
    VERIFIED,
    UNVERIFIED,
    NOT_AVAILABLE,
    REMOTE_UNVERIFIED,
}

/**
 * Sanitized, secret-free UI representation of an MWA connection.
 * Raw association URIs, association tokens, auth tokens and wallet secrets must
 * never be stored in this model.
 */
data class ConnectionPresentation(
    val mode: AssociationMode,
    val status: ConnectionStatus,
    val dappDisplayName: String?,
    val claimedUriDisplay: String?,
    val callerPackage: String?,
    val verificationState: DappVerificationState,
    val chain: String?,
)
