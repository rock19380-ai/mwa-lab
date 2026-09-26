package dev.mwalab.mwa.authorization

import org.bouncycastle.util.encoders.Base64
import com.solana.mobilewalletadapter.common.ProtocolContract
import dev.mwalab.identity.IdentityRepository
import dev.mwalab.identity.TestEndpointIdentity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabAuthorizationPolicyTest {
    private val publicKey = ByteArray(32) { index -> (index + 1).toByte() }
    private val identity = TestEndpointIdentity(
        publicKey = publicKey,
        displayAddress = "LabAddress111111111111111111111111111111",
    )

    private val repository = object : IdentityRepository {
        override suspend fun getOrCreate(): TestEndpointIdentity = identity
        override suspend fun reset(): TestEndpointIdentity = identity
    }

    private val policy = LabAuthorizationPolicy(repository)

    @Test
    fun devnetWithoutOptionalRequestsIsGranted() = runBlocking {
        val decision = policy.evaluate(
            chain = ProtocolContract.CHAIN_SOLANA_DEVNET,
            requestedFeatures = null,
            requestedAddresses = null,
            hasSignInPayload = false,
        )

        assertTrue(decision is LabAuthorizationDecision.Granted)
        decision as LabAuthorizationDecision.Granted
        assertArrayEquals(publicKey, decision.account.publicKey)
        assertEquals(identity.displayAddress, decision.account.displayAddress)
        assertArrayEquals(
            arrayOf(ProtocolContract.CHAIN_SOLANA_DEVNET),
            decision.account.chains,
        )
    }

    @Test
    fun mainnetFailsClosed() = runBlocking {
        assertEquals(
            LabAuthorizationDecision.UnsupportedChain,
            policy.evaluate(
                ProtocolContract.CHAIN_SOLANA_MAINNET,
                null,
                null,
                false,
            ),
        )
    }

    @Test
    fun optionalFeatureRequestFailsClosed() = runBlocking {
        assertEquals(
            LabAuthorizationDecision.UnsupportedOptionalFeatures,
            policy.evaluate(
                ProtocolContract.CHAIN_SOLANA_DEVNET,
                arrayOf("solana:signInWithSolana"),
                null,
                false,
            ),
        )
    }

    @Test
    fun signInPayloadFailsClosed() = runBlocking {
        assertEquals(
            LabAuthorizationDecision.UnsupportedSignIn,
            policy.evaluate(
                ProtocolContract.CHAIN_SOLANA_DEVNET,
                null,
                null,
                true,
            ),
        )
    }

    @Test
    fun unavailableRequestedAddressFailsClosed() = runBlocking {
        assertEquals(
            LabAuthorizationDecision.RequestedAddressUnavailable,
            policy.evaluate(
                ProtocolContract.CHAIN_SOLANA_DEVNET,
                null,
                arrayOf(Base64.toBase64String(ByteArray(32) { 7 })),
                false,
            ),
        )
    }

    @Test
    fun matchingRequestedProtocolAddressIsGranted() = runBlocking {
        val decision = policy.evaluate(
            ProtocolContract.CHAIN_SOLANA_DEVNET,
            null,
            arrayOf(Base64.toBase64String(publicKey)),
            false,
        )
        assertTrue(decision is LabAuthorizationDecision.Granted)
    }
}
