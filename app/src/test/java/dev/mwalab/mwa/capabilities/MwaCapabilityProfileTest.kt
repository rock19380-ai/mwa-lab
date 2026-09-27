package dev.mwalab.mwa.capabilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MwaCapabilityProfileTest {
    @Test
    fun snapshotMatchesPinnedPhase1Profile() {
        val snapshot = MwaCapabilityProfile.snapshot()

        assertEquals(10, snapshot.maxTransactionsPerSigningRequest)
        assertEquals(10, snapshot.maxMessagesPerSigningRequest)
        assertEquals(listOf("legacy"), snapshot.supportedTransactionVersions)
        assertTrue(snapshot.optionalFeatures.isEmpty())
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
}
