package dev.mwalab.ui.faults

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.MainActivity
import dev.mwalab.app.MwaLabComposition
import dev.mwalab.faults.FaultId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaultLabUiInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun normalSelectionFaultSwitchResetAndActivityRecreationUseOneAuthority() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val selection = MwaLabComposition.faultSelectionRepository(context)
        try {
            selection.select(FaultId.NORMAL)
            compose.waitForIdle()
            if (compose.onAllNodesWithText("ENTER LAB").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithText("ENTER LAB").performClick()
            }
            compose.onNodeWithTag("home-list").performScrollToNode(hasText("FAULT MODE"))
            compose.onNodeWithText("FAULT MODE").assertExists()
            compose.onNodeWithText("NORMAL").assertExists()
            compose.onNodeWithText("No intentional fault selected.").assertExists()
            compose.onNodeWithTag("nav-FAULT_LAB").performClick()
            compose.onNodeWithTag("fault-lab").performScrollToNode(hasTestTag("fault-FAULT_SIGN_REJECT"))
            compose.onNodeWithTag("fault-FAULT_SIGN_REJECT").performClick()
            compose.onNodeWithTag("fault-active-card").assertExists()
            compose.onNodeWithText("FAULT ACTIVE").assertExists()
            assertEquals(FaultId.SIGN_REJECT, selection.selected.value.id)
            compose.activityRule.scenario.recreate()
            compose.waitForIdle()
            compose.onNodeWithTag("fault-active-card").assertExists()
            compose.onAllNodesWithText("Reject signing").onFirst().assertExists()
            assertEquals(FaultId.SIGN_REJECT, selection.selected.value.id)
            compose.onNodeWithTag("nav-HOME").performClick()
            compose.onNodeWithTag("home-list").performScrollToNode(
                hasText("FAULT ACTIVE · INTENTIONAL TEST CONDITION"),
            )
            compose.onNodeWithText("FAULT ACTIVE · INTENTIONAL TEST CONDITION").assertExists()
            compose.onNodeWithTag("nav-FAULT_LAB").performClick()
            compose.onNodeWithTag("return-to-normal").performClick()
            compose.onAllNodesWithTag("fault-active-card").assertCountEquals(0)
            assertEquals(FaultId.NORMAL, selection.selected.value.id)
        } finally { selection.select(FaultId.NORMAL) }
    }
}
