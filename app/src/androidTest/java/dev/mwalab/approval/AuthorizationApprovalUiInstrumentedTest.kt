package dev.mwalab.approval

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mwalab.mwa.association.AssociationMode
import dev.mwalab.mwa.association.DappVerificationState
import dev.mwalab.ui.theme.MWALabTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthorizationApprovalUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun localAuthorizationShowsTruthfulUnverifiedIdentityAndOriginalDecisionId() {
        val request = request()
        val decisions = mutableListOf<String>()
        compose.setContent {
            MWALabTheme {
                AuthorizationApprovalScreen(
                    request = request,
                    onApprove = { decisions += "approve:$it" },
                    onReject = { decisions += "reject:$it" },
                )
            }
        }

        compose.onNodeWithText("CONNECT DAPP").assertIsDisplayed()
        compose.onNodeWithText("dApp: Demo dApp").assertIsDisplayed()
        compose.onNodeWithText("Claimed URI: https://example.invalid/app").assertIsDisplayed()
        compose.onNodeWithText("Connection: SAME-DEVICE MWA").assertIsDisplayed()
        compose.onNodeWithText("Identity: UNVERIFIED").assertIsDisplayed()
        compose.onNodeWithText("Network: SOLANA DEVNET").assertIsDisplayed()
        compose.onNodeWithText("NO REAL FUNDS").assertIsDisplayed()

        compose.onNodeWithTag("authorization-approve").performClick()
        compose.onNodeWithTag("authorization-reject").performClick()
        compose.runOnIdle {
            assertEquals(listOf("approve:auth-request", "reject:auth-request"), decisions)
        }
    }

    @Test
    fun remotePresentationNeverImpliesVerifiedIdentity() {
        compose.setContent {
            MWALabTheme {
                AuthorizationApprovalScreen(
                    request = request().copy(
                        associationMode = AssociationMode.REMOTE,
                        verificationState = DappVerificationState.REMOTE_UNVERIFIED,
                    ),
                    onApprove = {},
                    onReject = {},
                )
            }
        }
        compose.onNodeWithText("Connection: REMOTE MWA").assertIsDisplayed()
        compose.onNodeWithText("Identity: UNVERIFIED REMOTE DAPP").assertIsDisplayed()
        compose.onNodeWithText("Only continue if you initiated this test.").assertIsDisplayed()
    }

    private fun request() = AuthorizationApprovalRequest(
        requestId = "auth-request",
        sessionId = "session-1",
        generation = 1,
        associationMode = AssociationMode.LOCAL,
        dappDisplayName = "Demo dApp",
        claimedUriDisplay = "https://example.invalid/app",
        callerPackage = null,
        verificationState = DappVerificationState.UNVERIFIED,
        chain = "solana:devnet",
        requestedFeatures = emptyList(),
        requestedAddressCount = 0,
    )
}
