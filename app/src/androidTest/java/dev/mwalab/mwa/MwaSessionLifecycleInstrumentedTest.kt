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
import dev.mwalab.mwa.association.AssociationOpenResult
import dev.mwalab.mwa.evidence.MwaSessionEvent
import dev.mwalab.mwa.evidence.MwaSessionEvidenceStore
import dev.mwalab.session.SessionCloseReason
import dev.mwalab.session.SessionRepository
import dev.mwalab.session.SessionSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MwaSessionLifecycleInstrumentedTest {
    @Test
    fun invalidAssociationDoesNotCreatePersistentProductSession() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = MwaLabComposition.sessionRepository(context)
        val beforeIds = repository.observeSessions().first().map { it.session.id }.toSet()
        val host = MwaSessionHost(context)

        try {
            val result = host.openAssociation(Uri.parse("https://not-an-mwa-association.invalid"))
            assertTrue(result is AssociationOpenResult.Rejected)
        } finally {
            host.close()
        }

        val afterIds = repository.observeSessions().first().map { it.session.id }.toSet()
        assertEquals(beforeIds, afterIds)
    }

    @Test
    fun validAssociationPersistsIdentityAndServingCompleteAcrossActivityTeardown() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val repository = MwaLabComposition.sessionRepository(context)
        val beforeIds = runBlocking {
            repository.observeSessions().first().map { it.session.id }.toSet()
        }
        MwaSessionEvidenceStore.resetForTest()

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

        val active = awaitNewSession(repository, beforeIds)
        assertNull(active.session.completedAtEpochMillis)
        assertNull(active.session.closeReason)
        assertEquals("solana:devnet", active.session.cluster)

        val identityName = "MWA Lab Phase 3 lifecycle client"
        val authorizationFuture = client.authorize(
            Uri.parse("https://phase3-lifecycle.invalid"),
            Uri.parse("icon.png"),
            identityName,
            ProtocolContract.CHAIN_SOLANA_DEVNET,
            null,
            null,
            null,
            null,
        )
        AuthorizationApprovalTestDriver.approveNext()
        val authorization = authorizationFuture.get(10, TimeUnit.SECONDS)
        assertTrue(authorization.authToken.isNotEmpty())

        val identified = awaitSession(repository, active.session.id) {
            it.session.dappIdentityName == identityName
        }
        assertEquals(identityName, identified.session.dappIdentityName)

        localAssociation.close().get(10, TimeUnit.SECONDS)
        awaitEvent(MwaSessionEvent.SERVING_COMPLETE)

        val closed = awaitSession(repository, active.session.id) {
            it.session.completedAtEpochMillis != null
        }
        assertEquals(SessionCloseReason.SERVING_COMPLETE, closed.session.closeReason)
        assertNotNull(closed.session.completedAtEpochMillis)
        assertFalse(
            MwaSessionEvidenceStore.snapshot().any {
                it.event == MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED
            },
        )
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
        throw AssertionError("Timed out waiting for a newly persisted Phase 3 session")
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
        throw AssertionError("Timed out waiting for persisted session state for $sessionId")
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
