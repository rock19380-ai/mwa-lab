package dev.mwalab.approval

import androidx.compose.ui.test.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mwalab.security.DiagnosticSanitizer
import dev.mwalab.simulation.*
import dev.mwalab.protocol.ProtocolFailureSource
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

    @Test
    fun simulationStatesAreDiagnosticAndNeverChangeDecisionControls() {
        val bytes = TransactionApprovalTestVectors.systemTransfer()
        val ref = SimulationTargetRef("session", "session:2", "original-request-id", 0,
            DiagnosticSanitizer.sha256(bytes))
        val request = request(arrayOf(bytes)).copy(simulationTargets = listOf(ref))
        var states by androidx.compose.runtime.mutableStateOf<Map<SimulationTargetRef, SimulationUiState>>(emptyMap())
        val decisions = mutableListOf<String>()
        var taps = 0
        compose.setContent { MWALabTheme {
            SigningApprovalScreen(ApprovalState.Pending(request), { decisions += "approve:$it" },
                { decisions += "reject:$it" }, states, { taps++ })
        } }
        compose.onNodeWithTag("simulate-button-0").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, taps); states = mapOf(ref to SimulationUiState.Running) }
        compose.onNodeWithTag("simulation-0-running").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("APPROVE").assertIsEnabled()
        compose.onNodeWithText("REJECT").assertIsEnabled()
        val pass = result(ref, SimulationOutcome.PASS, ProtocolFailureSource.NONE)
        compose.runOnIdle { states = mapOf(ref to SimulationUiState.Completed(pass)) }
        compose.onNodeWithTag("simulation-0-pass").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("simulation-0-compute-units").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("simulation-0-logs-expander").performScrollTo().performClick()
        compose.onNodeWithTag("simulation-0-log-0").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("simulation-0-pass-disclaimer").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("APPROVE").performClick()
        val fail = result(ref, SimulationOutcome.FAIL, ProtocolFailureSource.SIMULATION)
        compose.runOnIdle { states = mapOf(ref to SimulationUiState.Completed(fail)) }
        compose.onNodeWithTag("simulation-0-fail").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("simulation-0-error-kind").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("simulation-0-custom-error").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("REJECT").performClick()
        val unavailable = result(ref, SimulationOutcome.UNAVAILABLE, ProtocolFailureSource.RPC_NETWORK)
        compose.runOnIdle { states = mapOf(ref to SimulationUiState.Completed(unavailable)) }
        compose.onNodeWithTag("simulation-0-unavailable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("APPROVE").assertIsEnabled()
        compose.onNodeWithText("REJECT").assertIsEnabled()
        compose.runOnIdle { assertEquals(listOf("approve:original-request-id",
            "reject:original-request-id"), decisions) }
    }

    @Test
    fun unsupportedV0HasNoSimulateAction() {
        val v0 = TransactionApprovalTestVectors.systemTransfer(versioned = true)
        compose.setContent { MWALabTheme {
            SigningApprovalScreen(ApprovalState.Pending(request(arrayOf(v0))), {}, {})
        } }
        compose.onAllNodesWithTag("simulate-button-0").assertCountEquals(0)
        warningsAndButtons()
    }

    private fun result(ref: SimulationTargetRef, outcome: SimulationOutcome,
        source: ProtocolFailureSource) = SimulationResult(
        "simulation-${outcome.name}", ref, 1, 1, 2, 1, outcome, source, "processed",
        contextSlot = if (outcome == SimulationOutcome.UNAVAILABLE) null else 9,
        error = if (outcome == SimulationOutcome.FAIL) SimulationErrorSummary(
            SimulationErrorKind.INSTRUCTION_ERROR, 0, "Custom", 42) else null,
        availabilityReason = if (outcome == SimulationOutcome.UNAVAILABLE)
            SimulationAvailabilityReason.HTTP else null,
        unitsConsumed = if (outcome == SimulationOutcome.UNAVAILABLE) null else 15,
        logs = if (outcome == SimulationOutcome.UNAVAILABLE) BoundedLogs(emptyList(), false)
            else BoundedLogs(listOf("Program log: safe bounded text"), false))

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
