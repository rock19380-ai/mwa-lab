package dev.mwalab.approval

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mwalab.security.DiagnosticSanitizer
import dev.mwalab.transaction.*
import dev.mwalab.ui.theme.MWALabTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SigningApprovalUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun verifiedSystemTransferDetailsAreScrollableWithWarningsAndDecisionsAlwaysVisible() {
        val bytes = TransactionApprovalTestVectors.systemTransfer()
        val summary = TransactionInspector().inspect(bytes)
        val request = request(arrayOf(bytes))
        compose.setContent { MWALabTheme { SigningApprovalScreen(ApprovalState.Pending(request), {}, {}) } }
        warningsAndButtons()
        for (text in listOf("TRANSACTION 1", "Version: legacy", "Fee payer: ${summary.feePayer}",
            "Required signer count: 1", "Recent blockhash: ${summary.recentBlockhash}", "Instruction count: 1",
            "Instruction 1: System Program", "Operation: Transfer", "Lamports: 10000000", "DEVNET SOL: 0.01",
            "MWA Lab payload fingerprint: ${summary.fingerprintSha256}")) {
            compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
        }
        warningsAndButtons()
    }

    @Test
    fun separatePayloadCardsKeepOriginalOrderAndUnknownAndPartialRenderingTruthful() {
        val bytes = arrayOf(TransactionApprovalTestVectors.systemTransfer(),
            TransactionApprovalTestVectors.systemTransfer(knownProgram = false),
            TransactionApprovalTestVectors.systemTransfer(versioned = true))
        compose.setContent { MWALabTheme { SigningApprovalScreen(ApprovalState.Pending(request(bytes)), {}, {}) } }
        compose.onNodeWithText("TRANSACTION 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("TRANSACTION 2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Instruction 1: Unknown Program").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Unknown semantics").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("TRANSACTION 3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Version: v0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Inspection: partial").performScrollTo().assertIsDisplayed()
        warningsAndButtons()
    }

    @Test
    fun unavailableDiagnosticsDoNotDisableApproveRejectOrExposeExceptionText() {
        val bytes = TransactionApprovalTestVectors.systemTransfer()
        val request = request(arrayOf(bytes)).copy(transactionSummaries = TransactionApprovalDiagnostics(1, listOf(null)))
        val decisions = mutableListOf<String>()
        compose.setContent { MWALabTheme { SigningApprovalScreen(ApprovalState.Pending(request),
            { decisions += "approve:$it" }, { decisions += "reject:$it" }) } }
        compose.onNodeWithText("Transaction diagnostics unavailable.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("APPROVE").performClick()
        compose.onNodeWithText("REJECT").performClick()
        compose.runOnIdle { assertEquals(listOf("approve:original-request-id", "reject:original-request-id"), decisions) }
        compose.onAllNodesWithText("Exception", substring = true).assertCountEquals(0)
        warningsAndButtons()
    }

    @Test
    fun messageApprovalRetainsOriginalContentAndUsesTheOriginalRequestId() {
        val message = "unchanged message".encodeToByteArray()
        val hash = DiagnosticSanitizer.sha256(message)
        val request = ApprovalRequest(requestId = "original-message-id", sessionId = "session",
            method = "sign_messages", dappIdentityName = "Message dApp", chain = "solana:devnet",
            payloadFingerprints = listOf(hash), payloadLengths = listOf(message.size))
        var decided: String? = null
        compose.setContent { MWALabTheme { SigningApprovalScreen(ApprovalState.Pending(request), { decided = it }, {}) } }
        compose.onNodeWithText("#1 ${hash.take(12)}… (${message.size} bytes)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Method: sign_messages").assertExists()
        compose.onAllNodesWithText("TRANSACTION", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Transaction diagnostics", substring = true).assertCountEquals(0)
        compose.onNodeWithText("APPROVE").performClick()
        compose.runOnIdle { assertEquals("original-message-id", decided) }
        warningsAndButtons()
    }

    private fun request(payloads: Array<ByteArray>) = ApprovalRequest(requestId = "original-request-id",
        sessionId = "session", method = "sign_transactions", dappIdentityName = "Transaction dApp",
        chain = "solana:devnet", payloadFingerprints = payloads.map { DiagnosticSanitizer.sha256(it) },
        payloadLengths = payloads.map { it.size },
        transactionSummaries = PreApprovalTransactionInspection(TransactionInspector(), payloads.size)
            .inspect(payloads, "session", null))

    private fun warningsAndButtons() {
        for (text in listOf("MWA LAB TEST ENDPOINT", "SOLANA DEVNET", "NO REAL FUNDS", "APPROVE", "REJECT")) {
            compose.onNodeWithText(text).assertIsDisplayed()
        }
    }
}
