package dev.mwalab.mwa.capabilities

import com.solana.mobilewalletadapter.common.ProtocolContract
import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig

data class MwaCapabilitySnapshot(
    val maxTransactionsPerSigningRequest: Int,
    val maxMessagesPerSigningRequest: Int,
    val supportedTransactionVersions: List<Any>,
    val optionalFeatures: List<String>,
)

/**
 * Single authority for the capability values fed to pinned walletlib 2.0.7.
 *
 * Phase 2 publishes explicit bounded request limits and enforces the same
 * constants in signing handlers.
 */
object MwaCapabilityProfile {
    const val MAX_TRANSACTIONS_PER_SIGNING_REQUEST = 10
    const val MAX_MESSAGES_PER_SIGNING_REQUEST = 10
    const val LOW_POWER_NO_CONNECTION_TIMEOUT_MS = 10_000L

    fun createWalletConfig(): MobileWalletAdapterConfig =
        MobileWalletAdapterConfig(
            MAX_TRANSACTIONS_PER_SIGNING_REQUEST,
            MAX_MESSAGES_PER_SIGNING_REQUEST,
            arrayOf(MobileWalletAdapterConfig.LEGACY_TRANSACTION_VERSION),
            LOW_POWER_NO_CONNECTION_TIMEOUT_MS,
            arrayOf(ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS),
        )

    fun snapshot(): MwaCapabilitySnapshot =
        MwaCapabilitySnapshot(
            maxTransactionsPerSigningRequest = MAX_TRANSACTIONS_PER_SIGNING_REQUEST,
            maxMessagesPerSigningRequest = MAX_MESSAGES_PER_SIGNING_REQUEST,
            supportedTransactionVersions =
                listOf(MobileWalletAdapterConfig.LEGACY_TRANSACTION_VERSION),
            optionalFeatures = listOf(ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS),
        )
}
