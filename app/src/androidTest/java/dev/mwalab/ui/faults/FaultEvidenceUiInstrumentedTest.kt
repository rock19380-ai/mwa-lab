package dev.mwalab.ui.faults

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mwalab.approval.ApprovalRequest
import dev.mwalab.approval.ApprovalState
import dev.mwalab.approval.SigningApprovalScreen
import dev.mwalab.faults.FaultId
import dev.mwalab.protocol.*
import dev.mwalab.session.MwaSession
import dev.mwalab.session.SessionCloseReason
import dev.mwalab.session.SessionSummary
import dev.mwalab.ui.sessions.*
import dev.mwalab.ui.theme.MWALabTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaultEvidenceUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun requestBannerKeepsCapturedDelayEvenAfterUnrelatedSelectionChanges() {
        val request = ApprovalRequest(sessionId = "s", method = "sign_messages", dappIdentityName = "test",
            chain = "solana:devnet", payloadFingerprints = listOf("abc"), payloadLengths = listOf(3),
            faultSnapshotId = FaultId.DELAY_5S)
        var unrelatedGlobalSelection by mutableStateOf(FaultId.DELAY_5S)
        compose.setContent { MWALabTheme {
            // Recomposition does not change the immutable request snapshot.
            unrelatedGlobalSelection.name
            SigningApprovalScreen(ApprovalState.Pending(request), {}, {})
        } }
        compose.onNodeWithTag("request-fault-banner").assertExists()
        compose.onNodeWithText("Request fault snapshot: Delay 5 seconds (FAULT_DELAY_5S)").assertExists()
        compose.runOnIdle { unrelatedGlobalSelection = FaultId.NORMAL }
        compose.onNodeWithText("Request fault snapshot: Delay 5 seconds (FAULT_DELAY_5S)").assertExists()
    }

    @Test fun timelineShowsDelayConditionAndActualObservedRejectionSeparately() {
        val event = ProtocolEvidence(sessionId = "s", eventId = "s:1", sequence = 1,
            method = ProtocolMethod.SIGN_MESSAGES, startedAtEpochMillis = 100,
            completedAtEpochMillis = 5_150, outcome = ProtocolOutcome.FAILURE,
            protocolErrorCode = -3, failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
            injectedFaultId = "FAULT_DELAY_5S")
        val summary = SessionSummary(
            MwaSession(
                id = "s",
                startedAtEpochMillis = 100,
                completedAtEpochMillis = 5_150,
                closeReason = SessionCloseReason.SCENARIO_COMPLETE,
            ),
            listOf(event),
        )
        compose.setContent { MWALabTheme {
            SessionDetailScreen(SessionDetailUiState.Ready(summary), {}, {})
        } }
        compose.onNodeWithTag("protocol-timeline").performScrollToNode(hasText("#1 SIGN_MESSAGES"))
        compose.onNodeWithText("Failure source: OBSERVED_PROTOCOL").assertExists()
        compose.onNodeWithText("Injected condition: Delay 5 seconds (FAULT_DELAY_5S)").assertExists()
        compose.onNodeWithText("INTENTIONAL TEST CONDITION").assertExists()
    }
}
