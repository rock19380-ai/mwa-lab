package dev.mwalab.wallet

import dev.mwalab.identity.IdentityRepository
import dev.mwalab.identity.TestEndpointIdentity
import dev.mwalab.rpc.WalletUtilityTestRpcGateway
import dev.mwalab.rpc.DevnetRpcResult
import dev.mwalab.rpc.TransportFailureReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestWalletServiceTest {
    private val publicKey = ByteArray(32) { (it + 1).toByte() }
    private val identity = object : IdentityRepository {
        override suspend fun getOrCreate() = TestEndpointIdentity(publicKey, "PUBLIC_DEVNET_ADDRESS")
        override suspend fun reset(): TestEndpointIdentity = error("reset must not be used")
    }

    @Test fun balanceUsesProtectedIdentityPublicKeyAndReturnsOnlyPublicState() = runTest {
        var requested: ByteArray? = null
        val gateway = object : WalletUtilityTestRpcGateway() {
            override suspend fun getBalance(publicKey: ByteArray): DevnetRpcResult<Long> {
                requested = publicKey.copyOf()
                return DevnetRpcResult.Success(1_234_567_890L)
            }
        }
        val service = TestWalletService(identity, gateway)

        val result = service.loadBalance()

        assertTrue(result is WalletBalanceLoadResult.Available)
        result as WalletBalanceLoadResult.Available
        assertEquals("PUBLIC_DEVNET_ADDRESS", result.address)
        assertEquals(1_234_567_890L, result.lamports)
        assertTrue(requested!!.contentEquals(publicKey))
        assertTrue(result.toString().contains("PUBLIC_DEVNET_ADDRESS"))
        assertTrue(!result.toString().contains("seed", ignoreCase = true))
    }

    @Test fun airdropRateLimitIsDistinctFromGenericRpcUnavailability() = runTest {
        val gateway = object : WalletUtilityTestRpcGateway() {
            override suspend fun requestAirdrop(publicKey: ByteArray, lamports: Long) =
                DevnetRpcResult.TransportFailure(TransportFailureReason.RATE_LIMITED)
        }
        val service = TestWalletService(identity, gateway)
        assertEquals(WalletAirdropRequestResult.RateLimited, service.requestAirdrop())
    }

    @Test fun submittedAirdropIsNotConfirmedUntilCommitmentEvidenceArrives() = runTest {
        val signature = ByteArray(64) { 7 }
        val gateway = object : WalletUtilityTestRpcGateway() {
            override suspend fun requestAirdrop(publicKey: ByteArray, lamports: Long) =
                DevnetRpcResult.Success(signature)
            override suspend fun awaitCommitment(signature: ByteArray, commitment: String, timeoutMillis: Long) =
                DevnetRpcResult.Success(false)
        }
        val service = TestWalletService(identity, gateway)
        val requested = service.requestAirdrop()
        assertTrue(requested is WalletAirdropRequestResult.Submitted)
        requested as WalletAirdropRequestResult.Submitted
        assertEquals(WalletAirdropConfirmationResult.NotConfirmed, service.confirmAirdrop(requested.signature))
    }
}
