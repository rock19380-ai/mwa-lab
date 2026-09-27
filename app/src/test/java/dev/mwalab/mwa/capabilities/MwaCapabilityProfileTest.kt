package dev.mwalab.mwa.capabilities

import com.solana.mobilewalletadapter.common.ProtocolContract
import org.junit.Assert.assertEquals
import org.junit.Test

class MwaCapabilityProfileTest {
    @Test
    fun snapshotMatchesPinnedPhase1Profile() {
        val snapshot = MwaCapabilityProfile.snapshot()

        assertEquals(10, snapshot.maxTransactionsPerSigningRequest)
        assertEquals(10, snapshot.maxMessagesPerSigningRequest)
        assertEquals(listOf("legacy"), snapshot.supportedTransactionVersions)
        assertEquals(
            listOf(ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS),
            snapshot.optionalFeatures,
        )
    }

    @Test
    fun walletConfigMatchesSnapshot() {
        val config = MwaCapabilityProfile.createWalletConfig()
        val snapshot = MwaCapabilityProfile.snapshot()

        assertEquals(
            snapshot.maxTransactionsPerSigningRequest,
            config.maxTransactionsPerSigningRequest,
        )
        assertEquals(
            snapshot.maxMessagesPerSigningRequest,
            config.maxMessagesPerSigningRequest,
        )
        assertEquals(
            snapshot.supportedTransactionVersions,
            config.supportedTransactionVersions.toList(),
        )
        assertEquals(
            snapshot.optionalFeatures,
            config.optionalFeatures.toList(),
        )
    }
    @Test
    fun onlyExplicitSignTransactionsOptionalFeatureIsAdvertised() {
        val features = MwaCapabilityProfile.snapshot().optionalFeatures

        assertEquals(listOf(ProtocolContract.FEATURE_ID_SIGN_TRANSACTIONS), features)
        assertEquals(false, features.contains(ProtocolContract.FEATURE_ID_SIGN_IN_WITH_SOLANA))
    }

}
