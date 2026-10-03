package dev.mwalab.storage

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.mwa.association.AssociationMode
import dev.mwalab.mwa.association.DappVerificationState
import dev.mwalab.protocol.ProtocolEvidence
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.session.MwaSession
import dev.mwalab.session.SessionCloseReason
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomSessionRepositoryInstrumentedTest {
    private lateinit var context: Context
    private val databaseNames = mutableListOf<String>()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
    }

    @After
    fun tearDown() {
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun sessionAndOrderedEventsRoundTrip() = runBlocking {
        val database = openDatabase()
        val repository = RoomSessionRepository(database.sessionDao(), database.protocolEventDao())
        repository.createSession(MwaSession("session-a", 100))
        repository.recordProtocolEvent(event("session-a", 2, ProtocolMethod.SIGN_MESSAGES))
        repository.recordProtocolEvent(event("session-a", 1, ProtocolMethod.AUTHORIZE))
        repository.updateIdentityVerificationState("session-a", DappVerificationState.UNVERIFIED)
        repository.finishSession("session-a", 500, SessionCloseReason.SCENARIO_COMPLETE)

        val summary = repository.getSession("session-a")
        assertNotNull(summary)
        checkNotNull(summary)
        assertEquals(SessionCloseReason.SCENARIO_COMPLETE, summary.session.closeReason)
        assertEquals(AssociationMode.LOCAL, summary.session.associationMode)
        assertEquals(DappVerificationState.UNVERIFIED, summary.session.identityVerificationState)
        assertEquals(listOf(1L, 2L), summary.events.map { it.sequence })
        assertEquals(400L, summary.durationMillis)
        database.close()
    }

    @Test
    fun sessionsAreObservedNewestFirst() = runBlocking {
        val database = openDatabase()
        val repository = RoomSessionRepository(database.sessionDao(), database.protocolEventDao())
        repository.createSession(MwaSession("older", 100))
        repository.createSession(MwaSession("newer", 200))

        val sessions = repository.observeSessions().first()
        assertEquals(listOf("newer", "older"), sessions.map { it.session.id })
        database.close()
    }

    @Test
    fun successfulAndFailedSessionsSurviveDatabaseReopen() = runBlocking {
        val name = newDatabaseName()
        val database = openDatabase(name)
        try {
            val repository = RoomSessionRepository(database.sessionDao(), database.protocolEventDao())
            repository.createSession(MwaSession("success", 100))
            repository.recordProtocolEvent(
                event("success", 1, ProtocolMethod.AUTHORIZE, ProtocolOutcome.SUCCESS),
            )
            repository.finishSession("success", 200, SessionCloseReason.SCENARIO_COMPLETE)

            repository.createSession(MwaSession("failure", 300))
            repository.recordProtocolEvent(
                event(
                    "failure",
                    1,
                    ProtocolMethod.SIGN_MESSAGES,
                    ProtocolOutcome.FAILURE,
                    protocolError = -32003,
                    failureSource = ProtocolFailureSource.OBSERVED_PROTOCOL,
                ),
            )
            repository.finishSession("failure", 450, SessionCloseReason.SCENARIO_COMPLETE)
        } finally {
            database.close()
        }

        val reopened = openDatabase(name)
        try {
            val repository = RoomSessionRepository(reopened.sessionDao(), reopened.protocolEventDao())
            val success = repository.getSession("success")
            val failure = repository.getSession("failure")
            assertNotNull(success)
            assertNotNull(failure)
            assertEquals(ProtocolOutcome.SUCCESS, success!!.events.single().outcome)
            assertEquals(ProtocolOutcome.FAILURE, failure!!.events.single().outcome)
            assertEquals(-32003, failure.events.single().protocolErrorCode)
            assertEquals(
                ProtocolFailureSource.OBSERVED_PROTOCOL,
                failure.events.single().failureSource,
            )
        } finally {
            reopened.close()
        }
    }

    @Test
    fun sensitiveSummaryValuesDoNotReachDatabaseFiles() = runBlocking {
        val name = newDatabaseName()
        val sentinels = listOf(
            "SECRET_AUTH_TOKEN_PHASE3_SENTINEL",
            "SECRET_ASSOCIATION_TOKEN_PHASE3_SENTINEL",
            "SECRET_PRIVATE_KEY_PHASE3_SENTINEL",
            "SECRET_MESSAGE_PAYLOAD_PHASE3_SENTINEL",
            "SECRET_TRANSACTION_PAYLOAD_PHASE3_SENTINEL",
        )
        val database = openDatabase(name)
        try {
            val repository = RoomSessionRepository(database.sessionDao(), database.protocolEventDao())
            repository.createSession(MwaSession("secrets", 100))
            repository.recordProtocolEvent(
                event(
                    sessionId = "secrets",
                    sequence = 1,
                    method = ProtocolMethod.SIGN_MESSAGES,
                    request = mapOf(
                        "auth_token" to sentinels[0],
                        "association_token" to sentinels[1],
                        "private_key" to sentinels[2],
                        "raw_message_payload" to sentinels[3],
                        "raw_transaction_payload" to sentinels[4],
                        "payload_count" to "1",
                    ),
                ),
            )
            repository.finishSession("secrets", 200, SessionCloseReason.SCENARIO_COMPLETE)
        } finally {
            database.close()
        }

        val persisted = databaseFiles(name)
            .filter(File::exists)
            .joinToString(separator = "\n") { file ->
                file.readBytes().toString(Charsets.ISO_8859_1)
            }
        sentinels.forEach { sentinel ->
            assertFalse("secret persisted: $sentinel", persisted.contains(sentinel))
        }
    }

    @Test
    fun unknownSessionReturnsNull() = runBlocking {
        val database = openDatabase()
        val repository = RoomSessionRepository(database.sessionDao(), database.protocolEventDao())
        assertNull(repository.getSession("missing"))
        database.close()
    }

    private fun event(
        sessionId: String,
        sequence: Long,
        method: ProtocolMethod,
        outcome: ProtocolOutcome = ProtocolOutcome.SUCCESS,
        protocolError: Int? = null,
        failureSource: ProtocolFailureSource = ProtocolFailureSource.NONE,
        request: Map<String, String> = mapOf("payload_count" to "1"),
    ): ProtocolEvidence = ProtocolEvidence(
        sessionId = sessionId,
        eventId = "$sessionId:$sequence",
        sequence = sequence,
        method = method,
        startedAtEpochMillis = 100 + sequence,
        completedAtEpochMillis = 110 + sequence,
        outcome = outcome,
        protocolErrorCode = protocolError,
        failureSource = failureSource,
        requestSummary = request,
        responseSummary = mapOf("result" to outcome.name.lowercase()),
    )

    private fun openDatabase(name: String = newDatabaseName()): MwaLabDatabase =
        Room.databaseBuilder(context, MwaLabDatabase::class.java, name).build()

    private fun newDatabaseName(): String =
        "mwa-lab-phase3-test-${UUID.randomUUID()}.db".also(databaseNames::add)

    private fun databaseFiles(name: String): List<File> {
        val main = context.getDatabasePath(name)
        return listOf(main, File(main.path + "-wal"), File(main.path + "-shm"))
    }
}
