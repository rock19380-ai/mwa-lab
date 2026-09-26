package dev.mwalab.security

import com.solana.mobilewalletadapter.common.ProtocolContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkPolicyTest {
    @Test
    fun modernDevnetChainIsAllowed() {
        val decision = NetworkPolicy.evaluate(ProtocolContract.CHAIN_SOLANA_DEVNET)
        assertEquals(
            NetworkDecision.Allowed(ProtocolContract.CHAIN_SOLANA_DEVNET),
            decision,
        )
    }

    @Test
    fun legacyDevnetClusterIsNormalizedAndAllowed() {
        val decision = NetworkPolicy.evaluate(ProtocolContract.CLUSTER_DEVNET)
        assertEquals(
            NetworkDecision.Allowed(ProtocolContract.CHAIN_SOLANA_DEVNET),
            decision,
        )
    }

    @Test
    fun productionChainIsRejected() {
        val decision = NetworkPolicy.evaluate(ProtocolContract.CHAIN_SOLANA_MAINNET)
        assertTrue(decision is NetworkDecision.Rejected)
        decision as NetworkDecision.Rejected
        assertEquals(NetworkRejectionReason.PRODUCTION_NOT_ALLOWED, decision.reason)
        assertEquals(ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED, decision.protocolErrorCode)
    }

    @Test
    fun legacyMainnetClusterIsRejected() {
        val decision = NetworkPolicy.evaluate(ProtocolContract.CLUSTER_MAINNET_BETA)
        assertTrue(decision is NetworkDecision.Rejected)
        assertEquals(
            NetworkRejectionReason.PRODUCTION_NOT_ALLOWED,
            (decision as NetworkDecision.Rejected).reason,
        )
    }

    @Test
    fun testnetIsRejectedAsUnsupported() {
        val decision = NetworkPolicy.evaluate(ProtocolContract.CHAIN_SOLANA_TESTNET)
        assertTrue(decision is NetworkDecision.Rejected)
        assertEquals(
            NetworkRejectionReason.UNSUPPORTED_NETWORK,
            (decision as NetworkDecision.Rejected).reason,
        )
    }

    @Test
    fun missingAndMalformedValuesFailClosed() {
        val values = listOf<String?>(null, "", "   ", "solana:\u0000devnet")
        values.forEach { value ->
            val decision = NetworkPolicy.evaluate(value)
            assertTrue(decision is NetworkDecision.Rejected)
            assertEquals(
                NetworkRejectionReason.MISSING_OR_MALFORMED,
                (decision as NetworkDecision.Rejected).reason,
            )
        }
    }

    @Test
    fun unknownChainFailsClosed() {
        val decision = NetworkPolicy.evaluate("solana:unknown")
        assertTrue(decision is NetworkDecision.Rejected)
        assertEquals(
            NetworkRejectionReason.UNSUPPORTED_NETWORK,
            (decision as NetworkDecision.Rejected).reason,
        )
    }
}
