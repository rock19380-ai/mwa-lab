package dev.mwalab.ui.sessions

import dev.mwalab.identity.IdentityRepository
import dev.mwalab.identity.TestEndpointIdentity
import dev.mwalab.rpc.WalletUtilityTestRpcGateway
import dev.mwalab.rpc.DevnetRpcResult
import dev.mwalab.session.MwaSession
import dev.mwalab.session.SessionRepository
import dev.mwalab.session.SessionSummary
import dev.mwalab.wallet.TestWalletService
import dev.mwalab.wallet.WalletAirdropState
import dev.mwalab.wallet.WalletBalanceState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeWalletViewModelTest {
    private val publicKey = ByteArray(32) { (it + 1).toByte() }
    private val identity = object : IdentityRepository {
        override suspend fun getOrCreate() = TestEndpointIdentity(publicKey, "PUBLIC_DEVNET_ADDRESS")
        override suspend fun reset(): TestEndpointIdentity = error("reset must not be called")
    }

    @Test fun walletBalanceRefreshIsIndependentFromProtocolHistory() = runTest {
        var balanceCalls = 0
        val gateway = object : WalletUtilityTestRpcGateway() {
            override suspend fun getBalance(publicKey: ByteArray): DevnetRpcResult<Long> {
                balanceCalls++
                return DevnetRpcResult.Success(2_000_000_001L)
            }
        }
        val model = HomeViewModel(
            identity,
            EmptySessions(),
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            TestWalletService(identity, gateway),
            clock = { 123L },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect { _ -> } }
        runCurrent()

        val wallet = model.state.value.wallet
        assertEquals("PUBLIC_DEVNET_ADDRESS", wallet.address)
        assertEquals(WalletBalanceState.Available(2_000_000_001L), wallet.balance)
        assertEquals(123L, wallet.lastRefreshAtEpochMillis)
        assertTrue(balanceCalls >= 1)
        assertEquals(SessionsUiState.Empty, model.state.value.history)
    }

    @Test fun unconfirmedAirdropNeverBecomesConfirmedSuccess() = runTest {
        val signature = ByteArray(64) { 5 }
        val gateway = object : WalletUtilityTestRpcGateway() {
            override suspend fun getBalance(publicKey: ByteArray) = DevnetRpcResult.Success(0L)
            override suspend fun requestAirdrop(publicKey: ByteArray, lamports: Long) =
                DevnetRpcResult.Success(signature)
            override suspend fun awaitCommitment(signature: ByteArray, commitment: String, timeoutMillis: Long) =
                DevnetRpcResult.Success(false)
        }
        val model = HomeViewModel(
            identity,
            EmptySessions(),
            backgroundScope,
            StandardTestDispatcher(testScheduler),
            TestWalletService(identity, gateway),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect { _ -> } }
        runCurrent()
        model.requestDevnetSol()
        runCurrent()

        assertEquals(WalletAirdropState.UnknownConfirmation, model.state.value.wallet.airdrop)
    }

    private class EmptySessions : SessionRepository {
        override fun observeSessions(): Flow<List<SessionSummary>> = flowOf(emptyList())
        override fun observeSession(sessionId: String): Flow<SessionSummary?> = flowOf(null)
        override suspend fun getSession(sessionId: String): SessionSummary? = null
        override suspend fun createSession(session: MwaSession) = Unit
        override suspend fun finishSession(
            sessionId: String,
            completedAtEpochMillis: Long,
            closeReason: dev.mwalab.session.SessionCloseReason,
        ) = Unit
        override suspend fun updateDappIdentity(sessionId: String, dappIdentityName: String?) = Unit
        override suspend fun recordProtocolEvent(event: dev.mwalab.protocol.ProtocolEvent) = Unit
    }
}
