package dev.mwalab.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mwalab.faults.FaultCatalog
import dev.mwalab.faults.FaultId
import dev.mwalab.ui.identity.LabIdentityScreen
import dev.mwalab.ui.onboarding.OnboardingScreen
import dev.mwalab.ui.sessions.HomeScreen
import dev.mwalab.ui.sessions.HomeUiState
import dev.mwalab.ui.sessions.IdentityUiState
import dev.mwalab.ui.sessions.SessionsUiState
import dev.mwalab.ui.theme.MWALabTheme
import dev.mwalab.wallet.TestWalletUiState
import dev.mwalab.wallet.WalletAirdropState
import dev.mwalab.wallet.WalletBalanceState
import dev.mwalab.wallet.WalletSendReview
import dev.mwalab.wallet.WalletSendState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase9FirstRunWalletUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    private val address = "8u3XMxFwZ7NmpSKk619MsrmZkMTskzrrcq5LwFA1TEqb"
    private val wallet = TestWalletUiState(
        address = address,
        balance = WalletBalanceState.Available(1_500_000_000L),
        airdrop = WalletAirdropState.Idle,
        lastRefreshAtEpochMillis = 1,
    )

    @Test fun manualFirstRunExplainsDebuggerDevnetTestWalletAndSameDeviceFlow() {
        compose.setContent {
            MWALabTheme {
                OnboardingScreen(
                    FaultCatalog.get(FaultId.NORMAL),
                    HomeUiState(IdentityUiState.Ready(address), SessionsUiState.Empty, wallet),
                    {},
                )
            }
        }

        compose.onNodeWithText("MWA Protocol Debugger").assertExists()
        compose.onNodeWithText("YOUR TEST WALLET").assertExists()
        compose.onNodeWithText(address).assertExists()
        compose.onNodeWithText("1.5 SOL · Devnet").assertExists()
        compose.onNodeWithText("TEST ON THIS PHONE").assertExists()
        compose.onNodeWithTag("onboarding-list").performScrollToNode(hasTestTag("enter-lab"))
        compose.onNodeWithTag("enter-lab").assertHasClickAction()
    }

    @Test fun homeHasRequiredConnectionGuidanceAndHowToConnectTroubleshooting() {
        compose.setContent {
            MWALabTheme {
                HomeScreen(
                    state = HomeUiState(IdentityUiState.Ready(address), SessionsUiState.Empty, wallet),
                    onSessions = {},
                    onSession = {},
                    onRetry = {},
                    activeFault = FaultCatalog.get(FaultId.NORMAL),
                )
            }
        }

        compose.onNodeWithText("READY FOR DAPP CONNECTIONS").assertExists()
        compose.onNodeWithText("TEST A DAPP ON THIS PHONE").assertExists()
        compose.onAllNodesWithText("SCAN REMOTE MWA QR").assertCountEquals(0)
        compose.onAllNodesWithText("PASTE MWA URI").assertCountEquals(0)
        compose.onNodeWithText("HOW TO CONNECT").performClick()
        compose.onNodeWithText("SAME-DEVICE MWA").assertIsDisplayed()
        compose.onNodeWithText("Only Solana Devnet is supported.", substring = true).assertExists()
    }

    @Test fun testWalletReceiveQrIsExplicitlyAddressOnlyAndAirdropActionIsVisible() {
        var requested = 0
        compose.setContent {
            MWALabTheme {
                LabIdentityScreen(
                    identity = IdentityUiState.Ready(address),
                    onCopy = {},
                    onRetry = {},
                    wallet = wallet,
                    onRequestAirdrop = { requested++ },
                )
            }
        }

        compose.onNodeWithText("Test Wallet").assertExists()
        compose.onNodeWithTag("identity-list").performScrollToNode(hasTestTag("receive-address-qr"))
        compose.onNodeWithText("RECEIVE TEST SOL").assertExists()
        compose.onNodeWithTag("receive-address-qr").assertIsDisplayed()
        compose.onNodeWithText("It is NOT an MWA connection QR.").assertExists()
        compose.onNodeWithTag("identity-list").performScrollToNode(hasTestTag("wallet-request-devnet-sol"))
        compose.onNodeWithTag("wallet-request-devnet-sol").performClick()
        compose.runOnIdle { assertEquals(1, requested) }
    }
    @Test fun sendTestSolUsesExplicitInputSurface() {
        var capturedRecipient: String? = null
        var capturedAmount: String? = null
        compose.setContent {
            MWALabTheme {
                HomeScreen(
                    state = HomeUiState(IdentityUiState.Ready(address), SessionsUiState.Empty, wallet),
                    onSessions = {},
                    onSession = {},
                    onRetry = {},
                    activeFault = FaultCatalog.get(FaultId.NORMAL),
                    onPrepareSend = { recipient, amount ->
                        capturedRecipient = recipient
                        capturedAmount = amount
                    },
                )
            }
        }

        compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("send-test-sol"))
        compose.onNodeWithTag("send-test-sol").performClick()
        compose.onNodeWithTag("send-recipient").performTextInput(address)
        compose.onNodeWithTag("send-amount").performTextInput("0.01")
        compose.onNodeWithTag("review-send-test-sol").performClick()
        compose.runOnIdle {
            assertEquals(address, capturedRecipient)
            assertEquals("0.01", capturedAmount)
        }
    }

    @Test fun sendTestSolReviewSurfaceShowsDevnetTruth() {
        compose.setContent {
            MWALabTheme {
                HomeScreen(
                    state = HomeUiState(
                        IdentityUiState.Ready(address),
                        SessionsUiState.Empty,
                        wallet.copy(
                            send = WalletSendState.Review(
                                WalletSendReview(address, address, 10_000_000L),
                            ),
                        ),
                    ),
                    onSessions = {},
                    onSession = {},
                    onRetry = {},
                )
            }
        }

        compose.onNodeWithText("DEVNET TEST TRANSFER").assertIsDisplayed()
        compose.onNodeWithText("Amount: 0.01 SOL").assertIsDisplayed()
        compose.onNodeWithText("Network: Solana Devnet").assertIsDisplayed()
        compose.onNodeWithText("This uses test funds only.").assertIsDisplayed()
        compose.onNodeWithTag("confirm-send-test-sol").assertHasClickAction()
    }

}
