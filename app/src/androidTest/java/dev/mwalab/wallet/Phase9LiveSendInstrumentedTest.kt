package dev.mwalab.wallet

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.app.MwaLabComposition
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

        val funded = requireManualFunding(walletService)
        val identity = identityRepository.getOrCreate()
        assertEquals(identity.displayAddress, funded.address)

        val beforeSessions = sessionRepository.observeSessions().first().size

        val recipient = InstrumentationRegistry.getArguments()
            .getString("mwa_phase9_recipient")
            ?: error("Live Devnet recipient must be explicitly selected")
        assertTrue(
            "Live Devnet recipient must be a canonical Solana address distinct from the wallet",
            SolanaPublicKeyParser.parse(recipient) != null && recipient != identity.displayAddress,
        )

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

    private suspend fun requireManualFunding(
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

        error(
            "LIVE_DEVNET_FUNDING_REQUIRED address=$address " +
                "reason=manual_balance_below_threshold_or_unavailable " +
                "minimum_lamports=$MIN_LIVE_BALANCE_LAMPORTS",
        )
    }

    private companion object {
        const val MIN_LIVE_BALANCE_LAMPORTS = 100_000L
    }
}
