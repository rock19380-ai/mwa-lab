package dev.mwalab.ui.transaction

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.ViewModelProvider
import dev.mwalab.ui.sessions.SessionDetailViewModel
import dev.mwalab.ui.sessions.SessionDetailUiState
import dev.mwalab.ui.sessions.SessionTransactionUiState
import dev.mwalab.MainActivity
import dev.mwalab.app.MwaLabComposition
import dev.mwalab.protocol.*
import dev.mwalab.session.*
import dev.mwalab.transaction.*
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistentTransactionInspectorActivityInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realNavigationAndActivityRecreationPreserveEventInspectorAndPinnedLabIdentity() {
        val context = compose.activity.applicationContext
        val id = "phase4-10-activity-${UUID.randomUUID()}"
        val eventId = "$id:1"
        val dappName = "Phase 4.10 UI test dApp"
        val started = System.currentTimeMillis()
        val summary = TransactionInspector().inspect(TransactionApprovalTestVectors.systemTransfer(), 0,
            TransactionDiagnosticBinding(id, eventId))
        runBlocking {
            val history = MwaLabComposition.sessionRepository(context)
            history.createSession(MwaSession(id, started, dappIdentityName = dappName))
            history.recordProtocolEvent(ProtocolEvent(id, eventId, 1, ProtocolMethod.SIGN_TRANSACTIONS,
                started + 1, started + 10, ProtocolOutcome.SUCCESS, requestSummary = mapOf(
                    "payload_count" to "1", "payload_0_sha256" to summary.fingerprintSha256,
                    "payload_0_length" to summary.wireLength.toString())))
            history.finishSession(id, started + 11, SessionCloseReason.SERVING_COMPLETE)
            MwaLabComposition.transactionDiagnosticRepository(context).recordForEvent(id, eventId, listOf(summary))
        }

        assertLabIdentity()
        compose.onNodeWithText("Sessions").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText(dappName).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sessions-list").performScrollToNode(hasText(dappName))
        compose.onNodeWithText(dappName).performClick()
        waitForDiagnostics(eventId)
        scroll("Inspect transaction 1")
        compose.onNodeWithText("Inspect transaction 1").performClick()
        scroll("Overview")
        capture("activity-overview")
        scroll("Lamports: 10000000")
        scroll("DEVNET SOL: 0.01")
        capture("activity-transfer")

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        waitForDiagnostics(eventId)
        assertLabIdentity()
        scroll("Collapse transaction 1")
        scroll("Lamports: 10000000")
        scroll("Raw metadata")
        compose.onAllNodesWithText("Simulation").assertCountEquals(0)
        compose.onAllNodesWithText("#2 GET_CAPABILITIES").assertCountEquals(0)
    }

    private fun waitForDiagnostics(eventId: String) {
        compose.waitUntil(10_000) {
            val state = ViewModelProvider(compose.activity)[SessionDetailViewModel::class.java].state.value
            (state as? SessionDetailUiState.Ready)?.transactions?.get(eventId) is SessionTransactionUiState.Recorded
        }
    }
    private fun assertLabIdentity() {
        compose.onNodeWithText("MWA LAB TEST ENDPOINT").assertIsDisplayed()
        compose.onNodeWithText("SOLANA DEVNET · NO REAL FUNDS").assertIsDisplayed()
    }
    private fun scroll(text: String) {
        compose.onNodeWithTag("protocol-timeline").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }
    private fun capture(label: String) {
        if (Build.VERSION.SDK_INT < 29) return
        val context = compose.activity
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "phase4-10-$label-${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MWA-Lab-Phase4-10")
        })!!
        context.contentResolver.openOutputStream(uri)!!.use {
            val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
