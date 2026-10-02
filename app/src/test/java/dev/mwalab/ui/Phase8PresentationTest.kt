package dev.mwalab.ui

import dev.mwalab.protocol.ProtocolEvidence
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.session.MwaSession
import dev.mwalab.session.SessionCloseReason
import dev.mwalab.session.SessionSummary
import dev.mwalab.ui.navigation.AppDestination
import dev.mwalab.ui.sessions.sessionHeroPresentation
import dev.mwalab.ui.settings.SettingsSafetyInfo
import dev.mwalab.ui.settings.ThemeMode
import org.junit.Assert.*
import org.junit.Test

class Phase8PresentationTest {
    @Test fun typedNavigationHasFiveTopLevelDestinationsAndDetailReturnsToSessions() {
        assertEquals(5, AppDestination.topLevel.size)
        assertEquals(AppDestination.SESSIONS, AppDestination.SESSION_DETAIL.backDestination())
        assertEquals(AppDestination.HOME, AppDestination.SETTINGS.backDestination())
        assertFalse(AppDestination.SESSION_DETAIL.isTopLevel)
        assertEquals(6, AppDestination.entries.size)
    }

    @Test fun themePreferenceFallsBackToSystemForUnknownStoredValue() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("UNSAFE"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStored("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored("DARK"))
    }

    @Test fun failedSessionHeroPreservesObservedSourceAndInjectedConditionIndependently() {
        val event = ProtocolEvidence("s", "s:1", 1, ProtocolMethod.SIGN_MESSAGES, 100, 150,
            ProtocolOutcome.FAILURE, -3, ProtocolFailureSource.OBSERVED_PROTOCOL,
            injectedFaultId = "FAULT_DELAY_5S")
        val hero = sessionHeroPresentation(SessionSummary(MwaSession("s", 100, 200,
            closeReason = SessionCloseReason.SCENARIO_COMPLETE), listOf(event)))
        assertEquals("FAIL", hero.status)
        assertEquals("SIGN_MESSAGES", hero.failedMethod)
        assertTrue(hero.protocolResult!!.contains("ERROR_NOT_SIGNED"))
        assertEquals("OBSERVED_PROTOCOL", hero.failureSource)
        assertEquals("Delay 5 seconds (FAULT_DELAY_5S)", hero.injectedFault)
        assertEquals("100 ms", hero.duration)
    }

    @Test fun successfulSessionHeroDoesNotInventFailureOrInjectedFault() {
        val event = ProtocolEvidence("s", "s:1", 1, ProtocolMethod.AUTHORIZE, 100, 150,
            ProtocolOutcome.SUCCESS, null, ProtocolFailureSource.NONE)
        val hero = sessionHeroPresentation(SessionSummary(MwaSession("s", 100, 200,
            closeReason = SessionCloseReason.SCENARIO_COMPLETE), listOf(event)))
        assertEquals("PASS", hero.status)
        assertNull(hero.failedMethod)
        assertNull(hero.failureSource)
        assertNull(hero.injectedFault)
    }


    @Test fun earlierInjectedConditionRemainsVisibleWhenLaterFailureIsObserved() {
        val injected = ProtocolEvidence("s", "s:1", 1, ProtocolMethod.AUTHORIZE, 100, 110,
            ProtocolOutcome.SUCCESS, null, ProtocolFailureSource.NONE,
            injectedFaultId = "FAULT_DELAY_5S")
        val observed = ProtocolEvidence("s", "s:2", 2, ProtocolMethod.SIGN_MESSAGES, 120, 150,
            ProtocolOutcome.FAILURE, -3, ProtocolFailureSource.OBSERVED_PROTOCOL)
        val hero = sessionHeroPresentation(SessionSummary(MwaSession("s", 100, 200,
            closeReason = SessionCloseReason.SCENARIO_COMPLETE), listOf(injected, observed)))
        assertEquals("OBSERVED_PROTOCOL", hero.failureSource)
        assertEquals("Delay 5 seconds (FAULT_DELAY_5S)", hero.injectedFault)
    }
    @Test fun settingsSafetyProjectionHasOnlyFixedDevnetAndSanitizedInformation() {
        val info = SettingsSafetyInfo()
        assertEquals("Solana Devnet", info.network)
        assertEquals("https://api.devnet.solana.com", info.rpc)
        assertEquals("Sanitized Report v1", info.report)
        assertEquals("Unavailable", info.mainnet)
        assertTrue(SettingsSafetyInfo::class.java.declaredFields.all {
            java.lang.reflect.Modifier.isFinal(it.modifiers)
        })
        assertFalse(SettingsSafetyInfo::class.java.methods.any { it.name.startsWith("set") })
    }
}
