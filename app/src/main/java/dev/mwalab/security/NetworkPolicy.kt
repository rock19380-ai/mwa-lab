package dev.mwalab.security

import com.solana.mobilewalletadapter.common.ProtocolContract

enum class NetworkRejectionReason {
    MISSING_OR_MALFORMED,
    PRODUCTION_NOT_ALLOWED,
    UNSUPPORTED_NETWORK,
}

sealed interface NetworkDecision {
    data class Allowed(
        val canonicalChain: String = ProtocolContract.CHAIN_SOLANA_DEVNET,
    ) : NetworkDecision

    data class Rejected(
        val reason: NetworkRejectionReason,
        val protocolErrorCode: Int = ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED,
    ) : NetworkDecision
}

/**
 * The single Phase 1 network authority.
 *
 * MWA Lab is deliberately Devnet-only. Legacy "devnet" and modern
 * "solana:devnet" inputs are normalized to the modern chain identifier.
 * Production, testnet, unknown, missing and malformed values fail closed.
 */
object NetworkPolicy {
    fun evaluate(chainOrCluster: String?): NetworkDecision {
        val value = chainOrCluster?.trim()
            ?: return NetworkDecision.Rejected(NetworkRejectionReason.MISSING_OR_MALFORMED)

        if (value.isEmpty() ||
            value.length > MAX_IDENTIFIER_LENGTH ||
            value.any { it.isISOControl() || it.isWhitespace() }
        ) {
            return NetworkDecision.Rejected(NetworkRejectionReason.MISSING_OR_MALFORMED)
        }

        return when (value) {
            ProtocolContract.CHAIN_SOLANA_DEVNET,
            ProtocolContract.CLUSTER_DEVNET -> NetworkDecision.Allowed()

            ProtocolContract.CHAIN_SOLANA_MAINNET,
            ProtocolContract.CLUSTER_MAINNET_BETA ->
                NetworkDecision.Rejected(NetworkRejectionReason.PRODUCTION_NOT_ALLOWED)

            else -> NetworkDecision.Rejected(NetworkRejectionReason.UNSUPPORTED_NETWORK)
        }
    }

    private const val MAX_IDENTIFIER_LENGTH = 128
}
