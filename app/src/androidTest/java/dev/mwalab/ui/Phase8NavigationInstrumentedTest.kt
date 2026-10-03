package dev.mwalab.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.mwalab.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase8NavigationInstrumentedTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun allFiveTypedDestinationsAreReachableAndNavigationLabelsAreAccessible() {
        if (compose.onAllNodesWithText("ENTER LAB").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("ENTER LAB").performClick()
        }
        listOf("SESSIONS", "FAULT_LAB", "LAB_IDENTITY", "SETTINGS", "HOME").forEach { route ->
            compose.onNodeWithTag("nav-$route").assertHasClickAction().performClick()
            compose.onNodeWithTag("nav-$route").assertContentDescriptionEquals(
                when (route) {
                    "FAULT_LAB" -> "Fault Lab"
                    "LAB_IDENTITY" -> "Test Wallet"
                    else -> route.lowercase().replaceFirstChar { it.uppercase() }
                })
        }
        compose.onNodeWithText("Mobile Wallet Adapter protocol debugger", substring = true).assertExists()
    }
}
