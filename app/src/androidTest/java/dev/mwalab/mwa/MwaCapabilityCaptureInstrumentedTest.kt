package dev.mwalab.mwa

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import dev.mwalab.approval.ApprovalCoordinator
import dev.mwalab.approval.ApprovalState
import dev.mwalab.capabilities.CapabilitySnapshot
import dev.mwalab.capabilities.CapabilitySnapshotRepository
import dev.mwalab.capabilities.snapshotForSession
import dev.mwalab.mwa.association.AssociationOpenResult
import dev.mwalab.mwa.capabilities.MwaCapabilityProfile
import dev.mwalab.mwa.evidence.MwaSessionEvidence
import dev.mwalab.mwa.evidence.MwaSessionEvent
import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.protocol.recorder.PersistentProtocolRecorder
import dev.mwalab.session.*
import dev.mwalab.storage.MwaLabDatabase
import dev.mwalab.storage.RoomCapabilitySnapshotRepository
import dev.mwalab.storage.RoomSessionRepository
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.bouncycastle.math.ec.rfc8032.Ed25519
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MwaCapabilityCaptureInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun realSessionsCaptureBeforeStartWithExactProfileAndIndependentBindingAcrossReopen() {
        Fixture().use { f ->
            val historical = MwaSession("historical", 100, 200, "Phase 3 history",
                SessionCloseReason.SERVING_COMPLETE)
            val historicalEvent = ProtocolEvent("historical", "historical:1", 1,
                ProtocolMethod.AUTHORIZE, 110, 120, ProtocolOutcome.SUCCESS)
            runBlocking {
                f.sessions.createSession(historical)
                f.sessions.recordProtocolEvent(historicalEvent)
            }
            val captures = CopyOnWriteArrayList<CapabilitySnapshot>()
            val checking = object : CapabilitySnapshotRepository by f.capabilities {
                override suspend fun recordSnapshot(snapshot: CapabilitySnapshot) {
                    val parent = f.sessions.getSession(snapshot.sessionId)!!
                    assertTrue(parent.events.isEmpty())
                    assertTrue(snapshot.capturedAtEpochMillis >= parent.session.startedAtEpochMillis)
                    // Exactly one configured capture per new real session, before start.
                    assertEquals(captures.size, f.evidence.count {
                        it.event == MwaSessionEvent.SCENARIO_START_REQUESTED })
                    f.capabilities.recordSnapshot(snapshot)
                    captures += snapshot
                }
            }
            val host = f.host(checking)
            val associations = List(2) { LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS) }
            try {
                associations.forEachIndexed { index, association ->
                    val before = System.currentTimeMillis()
                    val client = f.connect(host, association)
                    val captured = captures[index]
                    assertTrue(captured.capturedAtEpochMillis in before..System.currentTimeMillis())
                    assertEquals(MwaCapabilityProfile.snapshotForSession(captured.sessionId,
                        captured.capturedAtEpochMillis, MwaCapabilityProfile.createWalletConfig()), captured)
                    assertEquals(captured, runBlocking { f.capabilities.getSnapshot(captured.sessionId) })

                    val advertised = client.getCapabilities().get(10, TimeUnit.SECONDS)
                    assertEquals(captured.maxTransactionsPerSigningRequest, advertised.maxTransactionsPerSigningRequest)
                    assertEquals(captured.maxMessagesPerSigningRequest, advertised.maxMessagesPerSigningRequest)
                    assertEquals(captured.supportedTransactionVersions, advertised.supportedTransactionVersions.toList())
                    assertEquals(captured.optionalFeatures, advertised.supportedOptionalFeatures.toList())
                    assertTrue(runBlocking { f.sessions.getSession(captured.sessionId)!! }.events.isEmpty())
                    authorize(client, "Session $index")
                    val row = f.awaitSession(captured.sessionId, 1)
                    assertEquals(listOf(ProtocolMethod.AUTHORIZE), row.events.map { it.method })
                    assertEquals(listOf(1L), row.events.map { it.sequence })
                    assertEquals(listOf(captured.sessionId + ":1"), row.events.map { it.eventId })
                }
                assertEquals(2, captures.size)
                assertNotEquals(captures[0].sessionId, captures[1].sessionId)
                assertNotSame(captures[0], captures[1])
                host.close()
                f.db.close()
                val reopened = Room.databaseBuilder(context, MwaLabDatabase::class.java, f.name).build()
                try {
                    val caps = RoomCapabilitySnapshotRepository(reopened.capabilitySnapshotDao())
                    val sessions = RoomSessionRepository(reopened.sessionDao(), reopened.protocolEventDao())
                    runBlocking {
                        captures.forEach { assertEquals(it, caps.getSnapshot(it.sessionId)) }
                        assertNull(caps.getSnapshot("historical"))
                        val old = sessions.getSession("historical")!!
                        assertEquals(historical, old.session)
                        assertEquals(listOf(historicalEvent), old.events)
                        assertFalse(sessions.observeSessions().first().flatMap { it.events }
                            .any { it.method == ProtocolMethod.GET_CAPABILITIES })
                    }
                } finally { reopened.close() }
                assertFalse(f.evidence.any { it.event == MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED })
            } finally {
                host.close()
                associations.forEach { runCatching { it.close().get(10, TimeUnit.SECONDS) } }
            }
        }
    }

    @Test
    fun capabilityWriteFailureNeverChangesAssociationAuthorizationSigningOrSequencing() {
        // Include cancellation and a failing evidence sink at this synchronous
        // diagnostic boundary: neither may prevent the real scenario from starting.
        listOf("storage", "cancelled", "logging").forEach { failure ->
            Fixture().use { f ->
                val attempted = CopyOnWriteArrayList<CapabilitySnapshot>()
                val throwing = object : CapabilitySnapshotRepository by f.capabilities {
                    override suspend fun recordSnapshot(snapshot: CapabilitySnapshot) {
                        assertNotNull(f.sessions.getSession(snapshot.sessionId))
                        attempted += snapshot
                        if (failure == "cancelled") throw CancellationException("SECRET_CAPABILITY_FAILURE")
                        error("SECRET_CAPABILITY_FAILURE")
                    }
                }
                val finished = CountDownLatch(1)
                val host = f.host(throwing, failCapabilityLog = failure == "logging", onFinished = { finished.countDown() })
                val association = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
                try {
                    val client = f.connect(host, association)
                    assertEquals(1, attempted.size)
                    val id = attempted.single().sessionId
                    assertNull(runBlocking { f.capabilities.getSnapshot(id) })
                    client.getCapabilities().get(10, TimeUnit.SECONDS)
                    assertTrue(runBlocking { f.sessions.getSession(id)!! }.events.isEmpty())
                    val authorization = authorize(client, "Capability write failure")
                    assertTrue(authorization.authToken.isNotEmpty())
                    val message = "CAPABILITY_FAILURE_SIGNING_RESULT".toByteArray()
                    @Suppress("DEPRECATION")
                    val pending = client.signMessages(arrayOf(message),
                        arrayOf(authorization.accounts.single().publicKey))
                    val approval = runBlocking {
                        kotlinx.coroutines.withTimeout(10_000) {
                            f.approvals.state.first { it is ApprovalState.Pending }
                        }
                    } as ApprovalState.Pending
                    assertTrue(f.approvals.approve(approval.request.requestId))
                    val signed = pending.get(10, TimeUnit.SECONDS).signedPayloads.single()
                    assertArrayEquals(message, signed.copyOfRange(0, message.size))
                    assertTrue(Ed25519.verify(signed.takeLast(64).toByteArray(), 0,
                        authorization.accounts.single().publicKey, 0, message, 0, message.size))
                    client.deauthorize(authorization.authToken).get(10, TimeUnit.SECONDS)
                    val row = f.awaitSession(id, 3)
                    assertEquals(listOf(ProtocolMethod.AUTHORIZE, ProtocolMethod.SIGN_MESSAGES,
                        ProtocolMethod.DEAUTHORIZE), row.events.map { it.method })
                    assertEquals(listOf(1L, 2L, 3L), row.events.map { it.sequence })
                    assertTrue(row.events.all { it.outcome == ProtocolOutcome.SUCCESS && it.sessionId == id })
                    assertFalse(row.events.any { it.method == ProtocolMethod.GET_CAPABILITIES })
                    assertTrue(f.evidence.any { it.event == MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED &&
                        it.detail == "capability_snapshot" })
                    assertFalse(f.evidence.toString().contains("SECRET_CAPABILITY_FAILURE"))
                } finally {
                    // Initiate client close before server close to avoid pinned clientlib's cleanup lock inversion.
                    runCatching { association.close().get(10, TimeUnit.SECONDS) }
                    // Walletlib completion callbacks must finish using this disposable database before it closes.
                    assertTrue("Natural walletlib teardown did not finish", finished.await(10, TimeUnit.SECONDS))
                    host.close()
                }
            }
        }
    }

    @Test
    fun failedParentSessionWriteSkipsCapabilityCaptureAndStillAllowsAuthorization() {
        Fixture().use { f ->
            var attempts = 0
            val checking = object : CapabilitySnapshotRepository by f.capabilities {
                override suspend fun recordSnapshot(snapshot: CapabilitySnapshot) { attempts++ }
            }
            val failingSessions = object : SessionRepository by f.sessions {
                override suspend fun createSession(session: MwaSession) { error("SECRET_PARENT_FAILURE") }
            }
            val host = f.host(checking, failingSessions)
            val association = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
            try {
                val client = f.connect(host, association)
                assertTrue(authorize(client, "Parent failure").authToken.isNotEmpty())
                assertEquals(0, attempts)
                assertTrue(runBlocking { f.sessions.observeSessions().first() }.isEmpty())
                assertTrue(f.evidence.any { it.detail == "session_create" })
                assertFalse(f.evidence.any { it.detail == "capability_snapshot" })
            } finally { host.close(); runCatching { association.close().get(10, TimeUnit.SECONDS) } }
        }
    }

    @Test
    fun rejectedAssociationCreatesNoSessionOrCapabilitySnapshot() {
        Fixture().use { f ->
            var attempts = 0
            val checking = object : CapabilitySnapshotRepository by f.capabilities {
                override suspend fun recordSnapshot(snapshot: CapabilitySnapshot) { attempts++ }
            }
            val host = f.host(checking)
            try {
                assertTrue(host.openAssociation(Uri.parse("https://invalid.invalid")) is AssociationOpenResult.Rejected)
                assertEquals(0, attempts)
                assertTrue(runBlocking { f.sessions.observeSessions().first() }.isEmpty())
            } finally { host.close() }
        }
    }

    private fun authorize(client: MobileWalletAdapterClient, label: String) = client.authorize(
        Uri.parse("https://phase4-capabilities.invalid"), Uri.parse("icon.png"), label,
        ProtocolContract.CHAIN_SOLANA_DEVNET, null, null, null, null).get(10, TimeUnit.SECONDS)

    private inner class Fixture : java.io.Closeable {
        val name = "phase4-capabilities-" + UUID.randomUUID() + ".db"
        val db = Room.databaseBuilder(context, MwaLabDatabase::class.java, name).build()
        val sessions = RoomSessionRepository(db.sessionDao(), db.protocolEventDao())
        val capabilities = RoomCapabilitySnapshotRepository(db.capabilitySnapshotDao())
        val approvals = ApprovalCoordinator()
        val evidence = CopyOnWriteArrayList<MwaSessionEvidence>()
        fun host(capabilityRepository: CapabilitySnapshotRepository,
            sessionRepository: SessionRepository = sessions, failCapabilityLog: Boolean = false,
            onFinished: () -> Unit = {}): MwaSessionHost {
            val recorder = PersistentProtocolRecorder(sessionRepository)
            return MwaSessionHost(context, approvalCoordinator = approvals,
                evidenceSink = {
                    evidence += it
                    if (failCapabilityLog && it.detail == "capability_snapshot") error("SECRET_LOG_FAILURE")
                }, protocolEvidenceSink = {}, protocolRecorder = recorder,
                sessionLifecycleCoordinator = SessionLifecycleCoordinator(sessionRepository, recorder),
                capabilitySnapshotRepository = capabilityRepository, onSessionFinished = onFinished)
        }
        fun connect(host: MwaSessionHost, association: LocalAssociationScenario): MobileWalletAdapterClient {
            val uri = LocalAssociationIntentCreator.createAssociationIntent(null, association.port, association.session).data
            assertTrue(host.openAssociation(uri) is AssociationOpenResult.Accepted)
            return association.start().get(30, TimeUnit.SECONDS)
        }
        fun awaitSession(id: String, count: Int): SessionSummary = runBlocking {
            kotlinx.coroutines.withTimeout(10_000) {
                sessions.observeSession(id).first { it?.eventCount == count }!!
            }
        }
        override fun close() { db.close(); context.deleteDatabase(name) }
    }
}
