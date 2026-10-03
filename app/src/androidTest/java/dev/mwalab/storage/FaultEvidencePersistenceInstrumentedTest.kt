package dev.mwalab.storage

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.faults.FaultId
import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.session.MwaSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaultEvidencePersistenceInstrumentedTest {
    @Test
    fun injectedTerminalFailureRequiresIdBeforeRoomInsertAndValidIdSurvivesReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "phase6-fault-evidence-test.db"
        context.deleteDatabase(name)
        val event = ProtocolEvent(
            sessionId = "phase6-session",
            eventId = "phase6-session:1",
            sequence = 1,
            method = ProtocolMethod.SIGN_MESSAGES,
            startedAtEpochMillis = 100,
            completedAtEpochMillis = 200,
            outcome = ProtocolOutcome.FAILURE,
            protocolErrorCode = -3,
            failureSource = ProtocolFailureSource.INJECTED,
        )
        val database = Room.databaseBuilder(context, MwaLabDatabase::class.java, name).build()
        try {
            val repository = RoomSessionRepository(database.sessionDao(), database.protocolEventDao())
            repository.createSession(MwaSession("phase6-session", 100))
            assertTrue(runCatching { repository.recordProtocolEvent(event) }
                .exceptionOrNull() is IllegalArgumentException)
            assertTrue(repository.getSession("phase6-session")!!.events.isEmpty())
            repository.recordProtocolEvent(event.copy(injectedFaultId = FaultId.SIGN_REJECT.stableId))
        } finally {
            database.close()
        }
        val reopened = Room.databaseBuilder(context, MwaLabDatabase::class.java, name).build()
        try {
            val persisted = RoomSessionRepository(reopened.sessionDao(), reopened.protocolEventDao())
                .getSession("phase6-session")!!.events.single()
            assertEquals(ProtocolFailureSource.INJECTED, persisted.failureSource)
            assertEquals(FaultId.SIGN_REJECT.stableId, persisted.injectedFaultId)
            assertEquals(4, reopened.openHelper.readableDatabase.version)
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }
}
