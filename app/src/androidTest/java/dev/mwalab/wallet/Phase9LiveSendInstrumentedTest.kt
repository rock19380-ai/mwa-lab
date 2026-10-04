package dev.mwalab.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.funkatronics.encoders.Base58
import dev.mwalab.app.MwaLabComposition
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase9LiveSendInstrumentedTest {
    @Test
    fun oneLamportDevnetTransferConfirmsWithoutCreatingMwaSession() = runBlocking {
        assumeTrue(
            "Live Devnet acceptance runs only when explicitly selected.",
            InstrumentationRegistry.getArguments().getString("mwa_phase9_live") == "1",
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val identityRepository = MwaLabComposition.identityRepository(context)
        val sessionRepository = MwaLabComposition.sessionRepository(context)
        val walletService = MwaLabComposition.testWalletService(context)
        val sendService = MwaLabComposition.testWalletSendService(context)

        val funded = ensureFunding(walletService)
        val identity = identityRepository.getOrCreate()
        assertEquals(identity.displayAddress, funded.address)

        val beforeSessions = sessionRepository.observeSessions().first().size

        val recipientBytes = ByteArray(32) { index ->
            ((index * 17 + 29) and 0xff).toByte()
        }.also {
            if (it.contentEquals(identity.publicKeyBytes())) {
                it[31] = (it[31].toInt() xor 1).toByte()
            }
        }
        val recipient = Base58.encodeToString(recipientBytes)

        val prepared = sendService.prepare(recipient, "0.000000001")
        assertTrue(
            "Live Send preparation failed for ${identity.displayAddress}: $prepared",
            prepared is WalletSendPreparationResult.Ready,
        )

        val result = sendService.submit(
            (prepared as WalletSendPreparationResult.Ready).transfer,
        )
        assertEquals(WalletSendSubmissionResult.Confirmed, result)

        val afterSessions = sessionRepository.observeSessions().first().size
        assertEquals(
            "A direct Test Wallet send must not create an MWA protocol session",
            beforeSessions,
            afterSessions,
        )
    }

    private suspend fun ensureFunding(
        walletService: TestWalletService,
    ): WalletBalanceLoadResult.Available {
        val initial = walletService.loadBalance()
        if (
            initial is WalletBalanceLoadResult.Available &&
            initial.lamports >= MIN_LIVE_BALANCE_LAMPORTS
        ) {
            return initial
        }

        val address = when (initial) {
            is WalletBalanceLoadResult.Available -> initial.address
            is WalletBalanceLoadResult.Unavailable -> initial.address ?: "<identity unavailable>"
        }

        when (val requested = walletService.requestAirdrop(DEFAULT_AIRDROP_LAMPORTS)) {
            is WalletAirdropRequestResult.Submitted -> {
                walletService.confirmAirdrop(requested.signature)
            }
            WalletAirdropRequestResult.RateLimited ->
                error(
                    "LIVE_DEVNET_FUNDING_REQUIRED address=$address " +
                        "reason=airdrop_rate_limited",
                )
            is WalletAirdropRequestResult.RpcUnavailable ->
                error(
                    "LIVE_DEVNET_FUNDING_REQUIRED address=$address " +
                        "reason=rpc_unavailable_${requested.reason}",
                )
            is WalletAirdropRequestResult.Failed ->
                error(
                    "LIVE_DEVNET_FUNDING_REQUIRED address=$address " +
                        "reason=airdrop_failed rpc_code=${requested.rpcCode}",
                )
        }

        repeat(6) {
            val refreshed = walletService.loadBalance()
            if (
                refreshed is WalletBalanceLoadResult.Available &&
                refreshed.lamports >= MIN_LIVE_BALANCE_LAMPORTS
            ) {
                return refreshed
            }
            delay(1_000)
        }

        val final = walletService.loadBalance()
        error(
            "LIVE_DEVNET_FUNDING_REQUIRED address=$address " +
                "reason=balance_not_funded_after_airdrop final=$final",
        )
    }

    private companion object {
        const val MIN_LIVE_BALANCE_LAMPORTS = 100_000L
    }
}
