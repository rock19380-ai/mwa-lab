package dev.mwalab.mwa

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import dev.mwalab.app.MwaLabComposition
import dev.mwalab.mwa.evidence.MwaSessionEvent
import dev.mwalab.mwa.evidence.MwaSessionEvidenceStore
import dev.mwalab.protocol.ProtocolEvidenceStore
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.session.SessionRepository
import dev.mwalab.session.SessionSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MwaAuthorizationRecorderInstrumentedTest {
    @Test
    fun authorizationFamilyPersistsCanonicalOrderedEventsWithoutCapabilitiesFabrication() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repository = MwaLabComposition.sessionRepository(context)
        val beforeIds = runBlocking {
            repository.observeSessions().first().map { it.session.id }.toSet()
        }
        MwaSessionEvidenceStore.resetForTest()
        ProtocolEvidenceStore.resetForTest()

        val expectedIdentity = runBlocking {
            MwaLabComposition.identityRepository(context).getOrCreate()
        }
        val localAssociation = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
        val implicitIntent = LocalAssociationIntentCreator.createAssociationIntent(
            null,
            localAssociation.port,
            localAssociation.session,
        )
        val explicitIntent = Intent(implicitIntent)
            .setClass(context, MobileWalletAdapterActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(explicitIntent)
        val client = localAssociation.start().get(30, TimeUnit.SECONDS)
        assertNotNull(client)
        awaitEvent(MwaSessionEvent.SCENARIO_READY)
        awaitEvent(MwaSessionEvent.SERVING_CLIENTS)

        // walletlib 2.0.7 owns get_capabilities internally. It must not appear
        // as a fabricated host-observed protocol event in the persistent timeline.
        client.getCapabilities().get(10, TimeUnit.SECONDS)

        val identityName = "MWA Lab Phase 3 auth recorder ${System.nanoTime()}"
        val identityUri = Uri.parse("https://phase3-auth-recorder.invalid")
        val iconUri = Uri.parse("icon.png")

        val mainnetFailure = runCatching {
            client.authorize(
                identityUri,
                iconUri,
                identityName,
                ProtocolContract.CHAIN_SOLANA_MAINNET,
                null,
                null,
                null,
                null,
            ).get(10, TimeUnit.SECONDS)
        }.exceptionOrNull()
        assertNotNull("Mainnet authorization must fail closed", mainnetFailure)
        awaitEvent(MwaSessionEvent.AUTHORIZE_CHAIN_REJECTED)

        val authorization = client.authorize(
            identityUri,
            iconUri,
            identityName,
            ProtocolContract.CHAIN_SOLANA_DEVNET,
            null,
            null,
            null,
            null,
        ).get(10, TimeUnit.SECONDS)
        assertTrue(authorization.authToken.isNotEmpty())
        awaitEvent(MwaSessionEvent.AUTHORIZE_SUCCEEDED)

        val reauthorization = client.authorize(
            identityUri,
            iconUri,
            identityName,
            ProtocolContract.CHAIN_SOLANA_DEVNET,
            authorization.authToken,
            null,
            arrayOf(expectedIdentity.publicKeyBytes()),
            null,
        ).get(10, TimeUnit.SECONDS)
        assertTrue(reauthorization.authToken.isNotEmpty())
        awaitEvent(MwaSessionEvent.REAUTHORIZE_SUCCEEDED)

        client.deauthorize(reauthorization.authToken).get(10, TimeUnit.SECONDS)
        awaitEvent(MwaSessionEvent.DEAUTHORIZED_COMPLETED)

        val active = awaitNewSession(repository, beforeIds)
        val recorded = awaitSession(repository, active.session.id) { summary ->
            summary.events.count { it.method == ProtocolMethod.AUTHORIZE } >= 2 &&
                summary.events.any { it.method == ProtocolMethod.REAUTHORIZE } &&
                summary.events.any { it.method == ProtocolMethod.DEAUTHORIZE }
        }

        assertEquals(identityName, recorded.session.dappIdentityName)
        assertEquals(
            listOf(
                ProtocolMethod.AUTHORIZE,
                ProtocolMethod.AUTHORIZE,
                ProtocolMethod.REAUTHORIZE,
                ProtocolMethod.DEAUTHORIZE,
            ),
            recorded.events.map { it.method },
        )
        assertEquals(listOf(1L, 2L, 3L, 4L), recorded.events.map { it.sequence })
        assertEquals(
            recorded.events.mapIndexed { index, _ -> "${recorded.session.id}:${index + 1}" },
            recorded.events.map { it.eventId },
        )

        val mainnet = recorded.events[0]
        assertEquals(ProtocolOutcome.FAILURE, mainnet.outcome)
        assertEquals(ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED, mainnet.protocolErrorCode)
        assertEquals(ProtocolFailureSource.OBSERVED_PROTOCOL, mainnet.failureSource)
        assertEquals("unsupported_chain", mainnet.responseSummary["result"])

        val devnet = recorded.events[1]
        assertEquals(ProtocolOutcome.SUCCESS, devnet.outcome)
        assertEquals(ProtocolFailureSource.NONE, devnet.failureSource)
        assertEquals("authorized", devnet.responseSummary["result"])

        val reauth = recorded.events[2]
        assertEquals(ProtocolOutcome.SUCCESS, reauth.outcome)
        assertEquals("reauthorized", reauth.responseSummary["result"])

        val deauth = recorded.events[3]
        assertEquals(ProtocolOutcome.SUCCESS, deauth.outcome)
        assertEquals("revoked", deauth.responseSummary["result"])

        assertFalse(recorded.events.any { it.method == ProtocolMethod.GET_CAPABILITIES })
        assertTrue(recorded.events.all { it.completedAtEpochMillis >= it.startedAtEpochMillis })
        assertTrue(
            "Persisted structured summaries must not contain raw identity URI/token/private material",
            recorded.events.none { event ->
                (event.requestSummary + event.responseSummary).values.any { value ->
                    val normalized = value.lowercase()
                    normalized.contains("phase3-auth-recorder.invalid") ||
                        normalized.contains("auth_token") ||
                        normalized.contains("private_key") ||
                        normalized.contains("mnemonic") ||
                        normalized.contains("seed")
                }
            },
        )
        assertFalse(
            MwaSessionEvidenceStore.snapshot().any {
                it.event == MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED
            },
        )

        // Keep the Phase 2 process-local compatibility evidence alive until
        // all supported methods finish migration to the persistent recorder.
        val compatibility = ProtocolEvidenceStore.snapshot()
        assertTrue(compatibility.any { it.method == ProtocolMethod.AUTHORIZE })
        assertTrue(compatibility.any { it.method == ProtocolMethod.REAUTHORIZE })
        assertTrue(compatibility.any { it.method == ProtocolMethod.DEAUTHORIZE })

        localAssociation.close().get(10, TimeUnit.SECONDS)
        awaitEvent(MwaSessionEvent.SERVING_COMPLETE)
    }

    private fun awaitNewSession(
        repository: SessionRepository,
        beforeIds: Set<String>,
    ): SessionSummary {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val sessions = runBlocking { repository.observeSessions().first() }
            sessions.firstOrNull { it.session.id !in beforeIds }?.let { return it }
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for Phase 3 authorization session")
    }

    private fun awaitSession(
        repository: SessionRepository,
        sessionId: String,
        predicate: (SessionSummary) -> Boolean,
    ): SessionSummary {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val session = runBlocking { repository.getSession(sessionId) }
            if (session != null && predicate(session)) return session
            Thread.sleep(50)
        }
        throw AssertionError("Timed out waiting for persistent authorization events for $sessionId")
    }

    private fun awaitEvent(expected: MwaSessionEvent) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (MwaSessionEvidenceStore.snapshot().any { it.event == expected }) return
            Thread.sleep(50)
        }
        throw AssertionError(
            "Timed out waiting for $expected; observed=" +
                MwaSessionEvidenceStore.snapshot().joinToString { it.event.name },
        )
    }
}
