package dev.mwalab.mwa

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationIntentCreator
import com.solana.mobilewalletadapter.clientlib.scenario.LocalAssociationScenario
import com.solana.mobilewalletadapter.clientlib.scenario.Scenario
import com.solana.mobilewalletadapter.common.ProtocolContract
import dev.mwalab.app.MwaLabComposition
import dev.mwalab.approval.ApprovalCoordinator
import dev.mwalab.approval.ApprovalState
import dev.mwalab.identity.TestEndpointIdentity
import dev.mwalab.mwa.association.AssociationOpenResult
import dev.mwalab.mwa.evidence.MwaSessionEvidence
import dev.mwalab.mwa.evidence.MwaSessionEvent
import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.protocol.recorder.PersistentProtocolRecorder
import dev.mwalab.session.*
import dev.mwalab.signing.LabSigningService
import dev.mwalab.storage.MwaLabDatabase
import dev.mwalab.storage.RoomSessionRepository
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MwaHostileRecorderInstrumentedTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun replacementAtPreparationApprovalAndSigningKeepsOldEventsInOldSession() {
        listOf("preparation", "approval", "signing", "host_close").forEach { point ->
            Fixture(point).use { f ->
                val gate = CompletableDeferred<Unit>()
                val entered = CompletableDeferred<Unit>()
                val realSigner = MwaLabComposition.signingService(context)
                val signer = object : LabSigningService {
                    override suspend fun publicIdentity(): TestEndpointIdentity {
                        if (point == "preparation") { entered.complete(Unit); gate.await() }
                        return realSigner.publicIdentity()
                    }
                    override suspend fun sign(message: ByteArray): ByteArray {
                        if (point == "signing") { entered.complete(Unit); gate.await() }
                        return realSigner.sign(message)
                    }
                }
                val host = f.host(signer)
                val associationA = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
                val associationB = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
                try {
                    val a = f.connect(host, associationA)
                    val auth = authorize(a, "Old session $point")
                    val oldId = f.awaitSession { it.session.dappIdentityName == "Old session $point" }.session.id
                    @Suppress("DEPRECATION")
                    val pending = a.signMessages(arrayOf("OLD_REQUEST_SECRET_$point".toByteArray()),
                        arrayOf(auth.accounts.single().publicKey))
                    if (point == "preparation") runBlocking { entered.await() }
                    else {
                        val approval = f.awaitApproval()
                        if (point == "signing") {
                            assertTrue(f.approvals.approve(approval.request.requestId))
                            runBlocking { entered.await() }
                        }
                    }
                    if (point == "host_close") host.close()
                    else {
                        val b = f.connect(host, associationB)
                        authorize(b, "Replacement session $point")
                        val replacement = f.awaitSession { it.session.dappIdentityName == "Replacement session $point" }
                        assertEquals(listOf(1L), replacement.events.map { it.sequence })
                        assertEquals(listOf(ProtocolMethod.AUTHORIZE), replacement.events.map { it.method })
                    }
                    gate.complete(Unit)
                    assertNotNull(runCatching { pending.get(10, TimeUnit.SECONDS) }.exceptionOrNull())
                    val old = f.awaitSession { it.session.id == oldId && it.session.closeReason != null && it.eventCount == 2 }
                    assertEquals(if (point == "host_close") SessionCloseReason.HOST_CLOSED
                        else SessionCloseReason.REPLACED_BY_ASSOCIATION_ATTEMPT, old.session.closeReason)
                    assertEquals(ProtocolOutcome.CANCELLED, old.events[1].outcome)
                    assertEquals(oldId, old.events[1].sessionId)
                    assertEquals(listOf(1L, 2L), old.events.map { it.sequence })
                    assertEquals("Old session $point", old.session.dappIdentityName)
                    host.close()
                    val sessions = runBlocking { f.repo.observeSessions().first() }
                    assertEquals(if (point == "host_close") 1 else 2, sessions.size)
                    assertTrue(sessions.all { s -> s.events.all { it.sessionId == s.session.id } })
                } finally {
                    gate.complete(Unit)
                    host.close()
                    runCatching { associationA.close().get(10, TimeUnit.SECONDS) }
                    runCatching { associationB.close().get(10, TimeUnit.SECONDS) }
                }
            }
        }
    }

    @Test
    fun injectedStorageFailureNeverChangesSuccessfulWalletResponses() {
        listOf("create", "event", "identity", "finish").forEach { point ->
            Fixture("failure-$point").use { f ->
                val throwing = object : SessionRepository by f.repo {
                    override suspend fun createSession(session: MwaSession) {
                        if (point == "create") error("INJECTED_STORAGE_SENTINEL")
                        f.repo.createSession(session)
                    }
                    override suspend fun recordProtocolEvent(event: ProtocolEvent) {
                        if (point == "event") error("INJECTED_STORAGE_SENTINEL")
                        f.repo.recordProtocolEvent(event)
                    }
                    override suspend fun updateDappIdentity(sessionId: SessionId, dappIdentityName: String?) {
                        if (point == "identity") error("INJECTED_STORAGE_SENTINEL")
                        f.repo.updateDappIdentity(sessionId, dappIdentityName)
                    }
                    override suspend fun finishSession(sessionId: SessionId, completedAtEpochMillis: Long,
                        closeReason: SessionCloseReason) {
                        if (point == "finish") error("INJECTED_STORAGE_SENTINEL")
                        f.repo.finishSession(sessionId, completedAtEpochMillis, closeReason)
                    }
                }
                val host = f.host(repository = throwing)
                val association = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
                try {
                    val client = f.connect(host, association)
                    val authorization = authorize(client, "Storage failure test")
                    assertTrue(authorization.authToken.isNotEmpty())
                    client.getCapabilities().get(10, TimeUnit.SECONDS)
                    @Suppress("DEPRECATION")
                    val signing = client.signMessages(arrayOf("SAFE_PROTOCOL_RESULT_TEST".toByteArray()),
                        arrayOf(authorization.accounts.single().publicKey))
                    assertTrue(f.approvals.approve(f.awaitApproval().request.requestId))
                    val signed = signing.get(10, TimeUnit.SECONDS)
                    assertTrue(signed.signedPayloads.single().size > 64)
                    client.deauthorize(authorization.authToken).get(10, TimeUnit.SECONDS)
                    host.close()
                    assertTrue("Missing diagnostic storage failure at $point", f.evidence.any {
                        it.event == MwaSessionEvent.DIAGNOSTIC_PERSISTENCE_FAILED })
                    assertFalse(f.evidence.any { it.event == MwaSessionEvent.SIGN_MESSAGES_REJECTED })
                    assertFalse(runBlocking { f.repo.observeSessions().first() }.flatMap { it.events }
                        .any { it.method == ProtocolMethod.GET_CAPABILITIES })
                } finally { host.close(); runCatching { association.close().get(10, TimeUnit.SECONDS) } }
            }
        }
    }

    @Test
    fun realAuthorizationAndSigningSecretsAreAbsentFromActualSQLiteAndWalBytes() {
        Fixture("wire-secrets").use { f ->
            val host = f.host()
            val association = LocalAssociationScenario(Scenario.DEFAULT_CLIENT_TIMEOUT_MS)
            try {
                val client = f.connect(host, association)
                val auth = authorize(client, "SQLite positive control dApp")
                val message = "SECRET_RAW_MESSAGE_PHASE3_${UUID.randomUUID()}".toByteArray()
                @Suppress("DEPRECATION")
                val pending = client.signMessages(arrayOf(message), arrayOf(auth.accounts.single().publicKey))
                assertTrue(f.approvals.approve(f.awaitApproval().request.requestId))
                val signed = pending.get(10, TimeUnit.SECONDS).signedPayloads.single()
                client.deauthorize(auth.authToken).get(10, TimeUnit.SECONDS)
                f.awaitSession { it.eventCount == 3 }
                val secrets = listOf(auth.authToken, String(message), signed.toString(Charsets.ISO_8859_1),
                    Base64.encodeToString(signed, Base64.NO_WRAP),
                    signed.takeLast(64).toByteArray().toString(Charsets.ISO_8859_1))
                f.assertAbsent(secrets)
                host.close()
                f.db.close()
                f.assertAbsent(secrets)
            } finally { host.close(); runCatching { association.close().get(10, TimeUnit.SECONDS) } }
        }
    }

    private fun authorize(client: MobileWalletAdapterClient, label: String) = client.authorize(
        android.net.Uri.parse("https://secret-identity-query.invalid/?secret=NEVER_PERSIST_URI"),
        android.net.Uri.parse("SECRET_ICON_URI.png"), label, ProtocolContract.CHAIN_SOLANA_DEVNET,
        null, null, null, null).get(10, TimeUnit.SECONDS)

    private inner class Fixture(label: String) : java.io.Closeable {
        val name = "phase3-$label-${UUID.randomUUID()}.db"
        val db = Room.databaseBuilder(context, MwaLabDatabase::class.java, name).build()
        val repo = RoomSessionRepository(db.sessionDao(), db.protocolEventDao())
        val approvals = ApprovalCoordinator()
        val evidence = CopyOnWriteArrayList<MwaSessionEvidence>()
        fun host(signer: LabSigningService = MwaLabComposition.signingService(context),
            repository: SessionRepository = repo): MwaSessionHost {
            val recorder = PersistentProtocolRecorder(repository)
            return MwaSessionHost(context, signingService = signer, approvalCoordinator = approvals,
                evidenceSink = { evidence += it }, protocolEvidenceSink = {}, protocolRecorder = recorder,
                sessionLifecycleCoordinator = SessionLifecycleCoordinator(repository, recorder))
        }
        fun connect(host: MwaSessionHost, association: LocalAssociationScenario): MobileWalletAdapterClient {
            val uri = LocalAssociationIntentCreator.createAssociationIntent(null, association.port, association.session).data
            assertTrue(host.openAssociation(uri) is AssociationOpenResult.Accepted)
            return association.start().get(30, TimeUnit.SECONDS)
        }
        fun awaitApproval(): ApprovalState.Pending {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < end) {
                (approvals.state.value as? ApprovalState.Pending)?.let { return it }
                Thread.sleep(20)
            }
            error("Approval did not become pending")
        }
        fun awaitSession(predicate: (SessionSummary) -> Boolean): SessionSummary {
            val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < end) {
                runBlocking { repo.observeSessions().first() }.firstOrNull(predicate)?.let { return it }
                Thread.sleep(20)
            }
            error("Persistent session condition not met")
        }
        fun assertAbsent(secrets: List<String>) {
            val main = context.getDatabasePath(name)
            val files = listOf(main, File(main.path + "-wal"), File(main.path + "-shm")).filter { it.exists() }
            assertTrue(files.isNotEmpty())
            val bytes = files.joinToString { it.readBytes().toString(Charsets.ISO_8859_1) }
            assertTrue(bytes.contains("SQLite positive control dApp"))
            (secrets + listOf("NEVER_PERSIST_URI", "SECRET_ICON_URI")).forEach {
                assertFalse("Raw wire material persisted", bytes.contains(it))
            }
        }
        override fun close() { db.close(); context.deleteDatabase(name) }
    }
}
