package dev.mwalab.storage

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.protocol.ProtocolEvidence
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.protocol.recorder.EventClock
import dev.mwalab.protocol.recorder.PersistentProtocolRecorder
import dev.mwalab.session.MwaSession
import dev.mwalab.session.SessionCloseReason
import dev.mwalab.session.SessionLifecycleCoordinator
import dev.mwalab.session.SessionStatus
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomTimelineHostileInstrumentedTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun reverseCompletionReplacementAndSummariesSurviveReopen() = runBlocking {
        val name = "phase3-hostile-${UUID.randomUUID()}.db"
        try {
            open(name).withClosed { db ->
                val repo = repository(db)
                val recorder = PersistentProtocolRecorder(repo, EventClock { 110 })
                val lifecycle = SessionLifecycleCoordinator(repo, recorder, EventClock { 100 })
                lifecycle.createSession("a")
                lifecycle.updateDappIdentity("a", "  Successful test dApp  ")
                val first = recorder.begin("a", ProtocolMethod.AUTHORIZE)
                val second = recorder.begin("a", ProtocolMethod.SIGN_MESSAGES, mapOf("payload_count" to "1"))
                recorder.complete(second, ProtocolOutcome.SUCCESS, responseSummary = mapOf("result" to "signed"))
                recorder.complete(first, ProtocolOutcome.SUCCESS)
                lifecycle.finishSession("a", SessionCloseReason.SCENARIO_COMPLETE)

                lifecycle.createSession("old")
                lifecycle.updateDappIdentity("old", "Interrupted test dApp")
                val pending = recorder.begin("old", ProtocolMethod.SIGN_TRANSACTIONS)
                lifecycle.finishSession("old", SessionCloseReason.REPLACED_BY_ASSOCIATION_ATTEMPT)
                lifecycle.createSession("b")
                lifecycle.updateDappIdentity("b", "Rejected test dApp")
                val rejected = recorder.begin("b", ProtocolMethod.SIGN_MESSAGES)
                recorder.complete(rejected, ProtocolOutcome.FAILURE, -3, ProtocolFailureSource.OBSERVED_PROTOCOL,
                    mapOf("result" to "rejected"))
                recorder.complete(pending, ProtocolOutcome.SUCCESS)
                lifecycle.updateDappIdentity("old", "Old callback label")
                lifecycle.finishSession("old", SessionCloseReason.TEARDOWN_COMPLETE)
                lifecycle.finishSession("b", SessionCloseReason.SERVING_COMPLETE)
            }
            open(name).withClosed { db ->
                val repo = repository(db)
                val success = checkNotNull(repo.getSession("a"))
                assertEquals(SessionStatus.PASS, success.status)
                assertEquals("Successful test dApp", success.session.dappIdentityName)
                assertEquals(listOf(1L, 2L), success.events.map { it.sequence })
                assertEquals(listOf("a:1", "a:2"), success.events.map { it.eventId })
                assertEquals(SessionCloseReason.SCENARIO_COMPLETE, success.session.closeReason)
                assertEquals(0L, success.durationMillis)
                val failure = checkNotNull(repo.getSession("b"))
                assertEquals(SessionStatus.FAIL, failure.status)
                assertEquals("Rejected test dApp", failure.session.dappIdentityName)
                assertEquals(-3, failure.events.single().protocolErrorCode)
                assertEquals(ProtocolFailureSource.OBSERVED_PROTOCOL, failure.events.single().failureSource)
                assertEquals(1L, failure.events.single().sequence)
                val old = checkNotNull(repo.getSession("old"))
                assertEquals(SessionStatus.CANCELLED, old.status)
                assertEquals(SessionCloseReason.REPLACED_BY_ASSOCIATION_ATTEMPT, old.session.closeReason)
                assertEquals(ProtocolOutcome.CANCELLED, old.events.single().outcome)
                assertEquals("old", old.events.single().sessionId)
                assertEquals(3, repo.observeSessions().first().size)
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test
    fun concurrentCloseAndDuplicateCompletionNeverDuplicateOrMigrateEvents() = runBlocking {
        val name = "phase3-races-${UUID.randomUUID()}.db"
        try {
            open(name).withClosed { db ->
                val repo = repository(db)
                val recorder = PersistentProtocolRecorder(repo)
                val lifecycle = SessionLifecycleCoordinator(repo, recorder)
                repeat(32) { round ->
                    val id = "race-$round"
                    lifecycle.createSession(id)
                    val handles = (1..4).map { recorder.begin(id, ProtocolMethod.SIGN_MESSAGES) }
                    val tasks = handles.flatMap { handle ->
                        listOf(async(Dispatchers.Default) { recorder.complete(handle, ProtocolOutcome.SUCCESS) },
                            async(Dispatchers.Default) { recorder.complete(handle, ProtocolOutcome.FAILURE, -3,
                                ProtocolFailureSource.OBSERVED_PROTOCOL) })
                    }
                    val closes = listOf(SessionCloseReason.HOST_CLOSED, SessionCloseReason.SCENARIO_COMPLETE,
                        SessionCloseReason.REPLACED_BY_ASSOCIATION_ATTEMPT).map { reason ->
                        async(Dispatchers.Default) { lifecycle.finishSession(id, reason) }
                    }
                    tasks.awaitAll()
                    closes.awaitAll()
                    val closed = checkNotNull(repo.getSession(id))
                    assertNotNull(closed.session.completedAtEpochMillis)
                    assertEquals(listOf(1L, 2L, 3L, 4L), closed.events.map { it.sequence })
                    assertEquals(4, closed.events.map { it.eventId }.toSet().size)
                    assertTrue(closed.events.all { it.sessionId == id })
                }
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test
    fun databaseEnforcesForeignKeyAndSessionSequenceUniqueness() = runBlocking {
        val name = "phase3-constraints-${UUID.randomUUID()}.db"
        try {
            open(name).withClosed { db ->
                val repo = repository(db)
                repo.createSession(MwaSession("a", 1))
                val event = ProtocolEvidence("a", "a:1", 1, ProtocolMethod.AUTHORIZE, 1, 2, ProtocolOutcome.SUCCESS)
                repo.recordProtocolEvent(event)
                assertNotNull(runCatching { repo.recordProtocolEvent(event.copy(eventId = "other-id")) }.exceptionOrNull())
                assertNotNull(runCatching { repo.recordProtocolEvent(event.copy(sessionId = "missing", eventId = "missing:1")) }.exceptionOrNull())
                assertEquals(1, checkNotNull(repo.getSession("a")).eventCount)
                assertTrue(db.protocolEventDao().getForSession("missing").isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }

    @Test
    fun databaseAndWalBytesContainOnlySanitizedMetadata() = runBlocking {
        val name = "phase3-secrets-${UUID.randomUUID()}.db"
        val keys = listOf("auth_token", "association_token", "association_secret", "private_key", "seed",
            "mnemonic", "raw_message_payload", "raw_transaction_payload", "raw_signature", "encryption", "ciphertext")
        val sentinels = keys.associateWith { "SECRET_PHASE3_${it.uppercase()}_${UUID.randomUUID()}" }
        try {
            open(name).withClosed { db ->
                val repo = repository(db)
                repo.createSession(MwaSession("scan", 1, dappIdentityName = "Safe display label"))
                repo.recordProtocolEvent(ProtocolEvidence("scan", "scan:1", 1, ProtocolMethod.SIGN_MESSAGES, 1, 2,
                    ProtocolOutcome.SUCCESS, requestSummary = sentinels + mapOf("payload_count" to "1"),
                    responseSummary = sentinels + mapOf("result" to "signed")))
                val stored = checkNotNull(repo.getSession("scan")).events.single()
                keys.forEach { assertEquals("<redacted>", stored.requestSummary[it]) }
                assertNoSentinels(name, sentinels.values)
            }
            assertNoSentinels(name, sentinels.values)
            open(name).withClosed { db ->
                assertEquals("signed", checkNotNull(repository(db).getSession("scan")).events.single().responseSummary["result"])
            }
        } finally { context.deleteDatabase(name) }
    }

    private fun assertNoSentinels(name: String, sentinels: Collection<String>) {
        val main = context.getDatabasePath(name)
        val files = listOf(main, File(main.path + "-wal"), File(main.path + "-shm")).filter { it.exists() }
        assertTrue("No actual SQLite files inspected", files.isNotEmpty())
        val bytes = files.joinToString { it.readBytes().toString(Charsets.ISO_8859_1) }
        assertTrue("Positive control absent from SQLite bytes", bytes.contains("Safe display label"))
        sentinels.forEach { assertFalse("Secret persisted for $name", bytes.contains(it)) }
    }

    private inline fun <R> MwaLabDatabase.withClosed(block: (MwaLabDatabase) -> R): R =
        try { block(this) } finally { close() }

    private fun open(name: String) = Room.databaseBuilder(context, MwaLabDatabase::class.java, name).build()
    private fun repository(db: MwaLabDatabase) = RoomSessionRepository(db.sessionDao(), db.protocolEventDao())
}
