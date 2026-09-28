package dev.mwalab.ui.sessions

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.protocol.*
import dev.mwalab.session.*
import dev.mwalab.storage.*
import dev.mwalab.ui.theme.MWALabTheme
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionsUiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyStateExplainsHowToStartRecording() {
        compose.setContent { MWALabTheme { SessionsScreen(SessionsUiState.Empty, {}, {}) } }
        compose.onNodeWithText("No protocol sessions yet").assertIsDisplayed()
        compose.onNodeWithText("Connect an MWA-compatible test dApp", substring = true).assertExists()
    }

    @Test
    fun activeSuccessfulFailedAndInterruptedSessionsRenderStructuredState() {
        val summaries = listOf(summary("active"), summary("pass", SessionCloseReason.SERVING_COMPLETE),
            summary("fail", SessionCloseReason.SCENARIO_COMPLETE, ProtocolOutcome.FAILURE),
            summary("cancel", SessionCloseReason.HOST_CLOSED, ProtocolOutcome.CANCELLED))
        var opened: String? = null
        compose.setContent { MWALabTheme { SessionsScreen(SessionsUiState.Ready(summaries), { opened = it }, {}) } }
        compose.onNodeWithText("ACTIVE · recorded open").assertExists()
        compose.onNodeWithTag("sessions-list").performScrollToNode(hasText("Test dApp fail"))
        compose.onNodeWithText("Test dApp fail").performClick()
        compose.runOnIdle { assertEquals("fail", opened) }
        compose.onNodeWithTag("sessions-list").performScrollToNode(hasText("CANCELLED / INTERRUPTED"))
        compose.onNodeWithText("CANCELLED / INTERRUPTED").assertExists()
    }

    @Test
    fun timelineRendersErrorOriginTimingAndOnlySanitizedSummaries() {
        val event = event("fail", 1, ProtocolOutcome.FAILURE).copy(protocolErrorCode = -3,
            failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
            requestSummary = mapOf("payload_count" to "1", "auth_token" to "SECRET_UI_SENTINEL"),
            responseSummary = mapOf("result" to "rejected"))
        val summary = SessionSummary(MwaSession("fail", 100, 200, "Rejected dApp",
            SessionCloseReason.SCENARIO_COMPLETE), listOf(event))
        compose.setContent { MWALabTheme { SessionDetailScreen(SessionDetailUiState.Ready(summary), {}, {}) } }
        compose.onNodeWithText("SESSION FAIL").assertExists()
        compose.onNodeWithTag("protocol-timeline").performScrollToNode(hasText("#1 SIGN_MESSAGES"))
        compose.onNodeWithText("#1 SIGN_MESSAGES").assertExists()
        compose.onNodeWithText("Protocol error: ERROR_NOT_SIGNED (-3)").assertExists()
        compose.onNodeWithText("Failure source: OBSERVED_PROTOCOL").assertExists()
        compose.onNodeWithText("FAILURE · 40 ms").assertExists()
        compose.onNodeWithText("auth_token: <redacted>").assertExists()
        compose.onAllNodesWithText("SECRET_UI_SENTINEL", substring = true).assertCountEquals(0)
        compose.onNodeWithText("result: rejected").assertExists()
    }

    @Test
    fun unknownErrorIsRenderedNumericallyAndCapabilitiesAreNotTimelineEvents() {
        val event = event("unknown-error", 1, ProtocolOutcome.FAILURE).copy(protocolErrorCode = -12345)
        val summary = SessionSummary(MwaSession("unknown-error", 100, 200,
            closeReason = SessionCloseReason.SCENARIO_COMPLETE), listOf(event))
        compose.setContent { MWALabTheme { SessionDetailScreen(SessionDetailUiState.Ready(summary), {}, {}) } }
        compose.onNodeWithText("GET_CAPABILITIES: NOT OBSERVABLE THROUGH PINNED WALLETLIB").assertExists()
        compose.onAllNodesWithText("#2 GET_CAPABILITIES").assertCountEquals(0)
        compose.onNodeWithTag("protocol-timeline").performScrollToNode(hasText("#1 SIGN_MESSAGES"))
        compose.onNodeWithText("Protocol error: -12345 (Unknown protocol error)").assertExists()
    }

    @Test
    fun historyReadErrorSupportsRetryWithoutExceptionText() {
        var retries = 0
        compose.setContent { MWALabTheme { SessionsScreen(SessionsUiState.Error, {}, { retries++ }) } }
        compose.onNodeWithText("Session history unavailable.", substring = true).assertExists()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun databaseReopenLoadsHistoryThroughViewModelAndNavigationUsesPersistentTimeline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "phase3-ui-reopen-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, MwaLabDatabase::class.java, name).build()
        runBlocking {
            val repo = RoomSessionRepository(db.sessionDao(), db.protocolEventDao())
            repo.createSession(MwaSession("persisted-ui", 100, dappIdentityName = "Restart-loaded dApp"))
            repo.recordProtocolEvent(event("persisted-ui", 2, ProtocolOutcome.FAILURE).copy(protocolErrorCode = -3,
                failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL))
            repo.recordProtocolEvent(event("persisted-ui", 1).copy(method = ProtocolMethod.AUTHORIZE))
            repo.finishSession("persisted-ui", 200, SessionCloseReason.SERVING_COMPLETE)
        }
        db.close()
        val reopened = Room.databaseBuilder(context, MwaLabDatabase::class.java, name).build()
        try {
            val repo = RoomSessionRepository(reopened.sessionDao(), reopened.protocolEventDao())
            val sessions = SessionsViewModel(repo)
            val detail = SessionDetailViewModel(repo)
            compose.setContent {
                MWALabTheme {
                    var selected by remember { mutableStateOf(false) }
                    val listState by sessions.state.collectAsState()
                    val detailState by detail.state.collectAsState()
                    if (selected) SessionDetailScreen(detailState, { selected = false }, detail::retry)
                    else SessionsScreen(listState, { detail.selectSession(it); selected = true }, sessions::retry)
                }
            }
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Restart-loaded dApp").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Restart-loaded dApp").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("SESSION FAIL").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("protocol-timeline").performScrollToNode(hasText("#1 AUTHORIZE"))
            compose.onNodeWithText("#1 AUTHORIZE").assertExists()
            compose.onNodeWithTag("protocol-timeline").performScrollToNode(hasText("#2 SIGN_MESSAGES"))
            compose.onNodeWithText("Protocol error: ERROR_NOT_SIGNED (-3)").assertExists()
            compose.onNodeWithText("Failure source: OBSERVED_PROTOCOL").assertExists()
        } finally { reopened.close(); context.deleteDatabase(name) }
    }

    private fun summary(id: String, reason: SessionCloseReason? = null, outcome: ProtocolOutcome = ProtocolOutcome.SUCCESS) =
        SessionSummary(MwaSession(id, 100, if (reason == null) null else 200, "Test dApp $id", reason), listOf(event(id, 1, outcome)))
    private fun event(id: String, sequence: Long, outcome: ProtocolOutcome = ProtocolOutcome.SUCCESS) =
        ProtocolEvent(id, "$id:$sequence", sequence, ProtocolMethod.SIGN_MESSAGES, 110, 150, outcome)
}
