package dev.mwalab.session

/**
 * Diagnostic session metadata, separate from walletlib authorization state.
 * [dappIdentityName] is an optional, already sanitized display label, not proof
 * of dApp authenticity. Raw identity/icon URIs do not belong in this model.
 */
data class MwaSession(
    val id: SessionId,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long? = null,
    val dappIdentityName: String? = null,
    val closeReason: SessionCloseReason? = null,
) {
    init {
        require(id.isNotBlank() && id != "unknown") { "A session needs an assigned identity" }
        require(startedAtEpochMillis >= 0) { "Session start must be an epoch timestamp" }
        require(completedAtEpochMillis == null || completedAtEpochMillis >= 0) {
            "Session completion must be an epoch timestamp"
        }
        require((completedAtEpochMillis == null) == (closeReason == null)) {
            "Completion and close reason must be supplied together"
        }
        require(dappIdentityName == null || (
            dappIdentityName.isNotBlank() &&
                dappIdentityName == dappIdentityName.trim() &&
                dappIdentityName.length <= MAX_DAPP_DISPLAY_NAME_LENGTH &&
                dappIdentityName.none { it.isISOControl() }
            )) { "dApp display label must be bounded and normalized" }
    }

    // Fixed domain invariant, not a configurable network setting.
    val cluster: String get() = "solana:devnet"

    // An open session has no final duration; UI may separately compute elapsed time.
    val durationMillis: Long?
        get() = completedAtEpochMillis?.let { (it - startedAtEpochMillis).coerceAtLeast(0) }

    companion object {
        const val MAX_DAPP_DISPLAY_NAME_LENGTH = 128
    }
}
