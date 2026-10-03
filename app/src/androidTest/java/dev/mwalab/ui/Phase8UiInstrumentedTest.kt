package dev.mwalab.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.faults.FaultCatalog
import dev.mwalab.faults.FaultId
import dev.mwalab.protocol.ProtocolEvidence
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.session.MwaSession
import dev.mwalab.session.SessionCloseReason
import dev.mwalab.session.SessionSummary
import dev.mwalab.ui.faults.FaultLabScreen
import dev.mwalab.ui.identity.LabIdentityScreen
import dev.mwalab.ui.sessions.*
import dev.mwalab.ui.settings.SettingsScreen
import dev.mwalab.ui.settings.ThemeMode
import dev.mwalab.ui.settings.UiPreferences
import dev.mwalab.ui.theme.MWALabTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase8UiInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeExplainsProductSafetyAndCurrentFaultWithoutFabricatedReadiness() {
        compose.setContent { MWALabTheme {
            HomeScreen(HomeUiState(IdentityUiState.Ready("8u3XMxFwZ7NmpSKk619MsrmZkMTskzrrcq5LwFA1TEqb"),
                SessionsUiState.Empty), {}, {}, {}, FaultCatalog.get(FaultId.NORMAL))
        } }
        compose.onNodeWithText("MWA LAB").assertExists()
        compose.onNodeWithText("Mobile Wallet Adapter protocol debugger", substring = true).assertExists()
        compose.onAllNodesWithText("DEVNET ONLY").assertCountEquals(2)
        compose.onNodeWithTag("home-safety-banner").assertIsDisplayed()
        compose.onNodeWithTag("home-connection-devnet-badge").assertExists()
        compose.onNodeWithText("NO REAL FUNDS").assertIsDisplayed()
        compose.onNodeWithText("READY FOR DAPP CONNECTIONS").assertExists()
        compose.onNodeWithText("HOW TO CONNECT").assertExists()
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("TEST WALLET"))
        compose.onNodeWithText("TEST WALLET").assertExists()
        compose.onNodeWithTag("home-list").performScrollToNode(hasText("FAULT MODE"))
        compose.onNodeWithText("FAULT MODE").assertExists()
        compose.onNodeWithText("NORMAL").assertExists()
        compose.onNodeWithText("No intentional fault selected.").assertExists()
    }

    @Test fun failedSessionShowsMethodResultSourceAndIndependentFaultBeforeDetails() {
        val event = ProtocolEvidence("s", "s:1", 1, ProtocolMethod.SIGN_MESSAGES, 100, 150,
            ProtocolOutcome.FAILURE, -3, ProtocolFailureSource.OBSERVED_PROTOCOL,
            injectedFaultId = "FAULT_DELAY_5S")
        val summary = SessionSummary(MwaSession("s", 100, 200, "Test dApp",
            SessionCloseReason.SCENARIO_COMPLETE), listOf(event))
        compose.setContent { MWALabTheme { SessionDetailScreen(SessionDetailUiState.Ready(summary), {}, {}) } }
        compose.onNodeWithText("SESSION FAILED").assertExists()
        compose.onNodeWithText("SIGN_MESSAGES").assertExists()
        compose.onAllNodesWithText("Protocol error: ERROR_NOT_SIGNED (-3)").onFirst().assertExists()
        compose.onNodeWithText("OBSERVED_PROTOCOL").assertExists()
        compose.onNodeWithText("INJECTED").assertExists()
        compose.onNodeWithText("Delay 5 seconds (FAULT_DELAY_5S)").assertExists()
        compose.onAllNodesWithText("Request summary").assertCountEquals(0)
        val failureTop = compose.onNodeWithText("SESSION FAILED").fetchSemanticsNode().boundsInRoot.top
        val timelineTop = compose.onNodeWithText("PROTOCOL TIMELINE").fetchSemanticsNode().boundsInRoot.top
        assertTrue(failureTop < timelineTop)
    }

    @Test fun activeFaultStatesItsTargetExpectedResultAndReturnToNormal() {
        compose.setContent { MWALabTheme {
            FaultLabScreen(FaultCatalog.get(FaultId.SIGN_REJECT), {})
        } }
        compose.onNodeWithTag("fault-active-card").assertExists()
        compose.onNodeWithText("FAULT ACTIVE").assertExists()
        compose.onNodeWithText("INTENTIONAL TEST CONDITION").assertExists()
        compose.onNodeWithTag("return-to-normal").assertExists()
        compose.onNodeWithTag("fault-lab").performScrollToNode(hasTestTag("fault-FAULT_SIGN_REJECT"))
        compose.onNodeWithTag("fault-FAULT_SIGN_REJECT").assert(hasText("Expected protocol result: Protocol error: ERROR_NOT_SIGNED (-3)", substring = true))
    }

    @Test fun identityOffersOnlyPublicAddress() {
        var copied: String? = null
        val address = "8u3XMxFwZ7NmpSKk619MsrmZkMTskzrrcq5LwFA1TEqb"
        compose.setContent { MWALabTheme {
            LabIdentityScreen(IdentityUiState.Ready(address), { copied = it }, {})
        } }
        compose.onNodeWithText("Copy Address").performClick()
        compose.runOnIdle { assertEquals(address, copied) }
        compose.onAllNodesWithText("RESET IDENTITY").assertCountEquals(0)
    }

    @Test fun settingsAreFixedAndDarkThemeRenders() {
        compose.setContent { MWALabTheme(darkTheme = true) { SettingsScreen(ThemeMode.DARK, {}, "1.0 · build 1") } }
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("https://api.devnet.solana.com"))
        compose.onNodeWithText("https://api.devnet.solana.com").assertExists()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Sanitized Report v1"))
        compose.onNodeWithText("Sanitized Report v1").assertExists()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Unavailable"))
        compose.onNodeWithText("Unavailable").assertExists()
        compose.onAllNodesWithText("Custom RPC").assertCountEquals(0)
        val lightChoice = compose.onNodeWithTag("theme-mode-LIGHT").assertHasClickAction()
        val targetHeightDp = lightChoice.fetchSemanticsNode().boundsInRoot.height /
            InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue("Theme choice should meet a 48dp touch target", targetHeightDp >= 48f)
    }

    @Test fun uiPreferencePersistsThemeWithoutSessionOrWalletAuthority() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = UiPreferences(context)
        val prior = prefs.themeMode
        val priorOnboarding = prefs.onboardingSeen
        try {
            prefs.onboardingSeen = false
            assertEquals(false, UiPreferences(context).onboardingSeen)
            prefs.onboardingSeen = true
            assertEquals(true, UiPreferences(context).onboardingSeen)
            prefs.themeMode = ThemeMode.LIGHT
            assertEquals(ThemeMode.LIGHT, UiPreferences(context).themeMode)
            prefs.themeMode = ThemeMode.DARK
            assertEquals(ThemeMode.DARK, UiPreferences(context).themeMode)
            val keys = context.getSharedPreferences("mwa_lab_ui", android.content.Context.MODE_PRIVATE).all.keys
            assertEquals(true, keys.all { it == "theme_mode" || it == "onboarding_seen_v1" })
        } finally {
            prefs.themeMode = prior
            prefs.onboardingSeen = priorOnboarding
        }
    }
}
