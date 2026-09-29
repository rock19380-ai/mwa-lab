package dev.mwalab.capabilities

import com.solana.mobilewalletadapter.walletlib.protocol.MobileWalletAdapterConfig
import dev.mwalab.mwa.capabilities.MwaCapabilityProfile
import dev.mwalab.session.SessionId

/**
 * Canonical session mapping from the sole profile authority through the actual
 * scenario configuration. Callers isolate diagnostic failures from MWA behavior.
 */
fun MwaCapabilityProfile.snapshotForSession(
    sessionId: SessionId,
    capturedAtEpochMillis: Long,
    walletConfig: MobileWalletAdapterConfig,
): CapabilitySnapshot {
    val profile = snapshot()
    val configuredVersions = walletConfig.supportedTransactionVersions.toList()
    val configuredFeatures = walletConfig.optionalFeatures.toList()
    require(
        walletConfig.maxTransactionsPerSigningRequest == profile.maxTransactionsPerSigningRequest &&
            walletConfig.maxMessagesPerSigningRequest == profile.maxMessagesPerSigningRequest &&
            configuredVersions == profile.supportedTransactionVersions &&
            configuredFeatures == profile.optionalFeatures,
    ) { "Capability configuration does not match the Lab profile" }

    val versions = configuredVersions.map { version ->
        require(version is String && version == MobileWalletAdapterConfig.LEGACY_TRANSACTION_VERSION) {
            "Unsupported configured transaction version representation"
        }
        version
    }
    return CapabilitySnapshot(
        sessionId = sessionId,
        capturedAtEpochMillis = capturedAtEpochMillis,
        source = CapabilitySnapshotSource.CONFIGURED_WALLETLIB_PROFILE,
        maxTransactionsPerSigningRequest = walletConfig.maxTransactionsPerSigningRequest,
        maxMessagesPerSigningRequest = walletConfig.maxMessagesPerSigningRequest,
        supportedTransactionVersions = versions,
        optionalFeatures = configuredFeatures,
    )
}
