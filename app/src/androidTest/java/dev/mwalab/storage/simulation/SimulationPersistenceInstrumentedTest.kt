package dev.mwalab.storage.simulation

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.simulation.*
import dev.mwalab.storage.MwaLabDatabase
import dev.mwalab.storage.RoomSimulationRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SimulationPersistenceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val names = mutableListOf<String>()
    @get:Rule val migration = MigrationTestHelper(instrumentation, MwaLabDatabase::class.java)

    @After fun tearDown() { names.forEach(context::deleteDatabase) }

    @Test fun migration2To3PreservesPhase4RowsWithoutBackfill() {
        val name = name()
        migration.createDatabase(name, 2).use { old ->
            seed(old, "s", "s:1", HASH)
            old.execSQL("INSERT INTO capability_snapshots VALUES ('s', 10, 'CONFIGURED_WALLETLIB_PROFILE', 10, 10, '[\"legacy\"]', '[]')")
            old.execSQL("INSERT INTO transaction_diagnostics VALUES ('s:1:payload:0', 's', 's:1', 0, '$HASH', 100, 'LEGACY', 'PARSED', '{}')")
        }
        migration.runMigrationsAndValidate(name, 3, true, MwaLabDatabase.MIGRATION_2_3).use { db ->
            assertEquals(3, db.version)
            assertEquals(1L, count(db, "sessions"))
            assertEquals(1L, count(db, "protocol_events"))
            assertEquals(1L, count(db, "capability_snapshots"))
            assertEquals(1L, count(db, "transaction_diagnostics"))
            assertEquals(0L, count(db, "simulation_results"))
            db.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun migration1To2To3KeepsHistoryAndCreatesEmptyChildTables() {
        val name = name()
        migration.createDatabase(name, 1).use { old -> seed(old, "old", "old:1", HASH) }
        migration.runMigrationsAndValidate(name, 3, true,
            MwaLabDatabase.MIGRATION_1_2, MwaLabDatabase.MIGRATION_2_3).use { db ->
            assertEquals(3, db.version)
            assertEquals(1L, count(db, "sessions"))
            assertEquals(1L, count(db, "protocol_events"))
            assertEquals(0L, count(db, "transaction_diagnostics"))
            assertEquals(0L, count(db, "simulation_results"))
            assertEquals(0L, count(db, "capability_snapshots"))
        }
    }

    @Test fun repositoryOrdersAttemptsGuardsParentAndCascades() = runBlocking {
        val name = name()
        var db = open(name)
        try {
            seed(db.openHelper.writableDatabase, "a", "a:1", HASH)
            seed(db.openHelper.writableDatabase, "b", "b:1", HASH)
            var repo = RoomSimulationRepository(db.simulationResultDao())
            val first = result("a", "a:1", 0, 1)
            val second = result("a", "a:1", 0, 2)
            repo.recordForEvent("a", "a:1", listOf(second, first))
            assertEquals(listOf(1, 2), repo.getForEvent("a", "a:1").map { it.attemptNumber })
            assertEquals(listOf(1, 2), repo.observeForEvent("a", "a:1").first().map { it.attemptNumber })
            repo.recordForEvent("a", "a:1", listOf(first))
            assertBad { repo.recordForEvent("b", "a:1", listOf(first)) }
            assertBad { repo.recordForEvent("a", "a:1", listOf(result("a", "a:1", 0, 2, fingerprint = "f".repeat(64)))) }
            assertBad { repo.recordForEvent("a", "a:1", listOf(result("a", "a:1", 1, 3))) }
            assertBad { repo.recordForEvent("a", "missing", listOf(result("a", "missing", 0, 1))) }
            assertTrue(repo.getForEvent("b", "b:1").isEmpty())
            db.close()
            db = open(name)
            repo = RoomSimulationRepository(db.simulationResultDao())
            assertEquals(listOf(1, 2), repo.getForEvent("a", "a:1").map { it.attemptNumber })
            db.openHelper.writableDatabase.execSQL("DELETE FROM protocol_events WHERE event_id = 'a:1'")
            assertTrue(repo.getForEvent("a", "a:1").isEmpty())
            val b = result("b", "b:1", 0, 1)
            repo.recordForEvent("b", "b:1", listOf(b))
            db.openHelper.writableDatabase.execSQL("DELETE FROM sessions WHERE session_id = 'b'")
            assertTrue(repo.getForEvent("b", "b:1").isEmpty())
        } finally { db.close() }
    }

    @Test fun schemaHasOnlyStructuredSafeColumnsAndJsonRejectsUnsafeLogs() = runBlocking {
        val name = name()
        val db = open(name)
        try {
            val columns = db.openHelper.readableDatabase.query("PRAGMA table_info(simulation_results)").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
            }
            assertEquals(listOf("simulation_id", "session_id", "event_id", "request_id", "payload_index",
                "attempt_number", "fingerprint_sha256", "started_at_ms", "completed_at_ms", "duration_ms",
                "outcome", "failure_source", "commitment", "context_slot", "error_kind", "instruction_index",
                "instruction_error_kind", "custom_program_error_code", "rpc_error_code", "availability_reason",
                "units_consumed", "logs_json", "logs_truncated"), columns)
            assertFalse(columns.any { it.contains("transaction") || it.contains("signature") ||
                it.contains("token") || it.contains("secret") || it.contains("rpc_body") })
            assertBad { SimulationResultJson.encode(listOf("unsafe\u0000line")) }
            assertBad { SimulationResultJson.decode("""["unsafe\nline"]""", false) }
        } finally { db.close() }
    }

    private fun result(session: String, event: String, index: Int, attempt: Int,
        fingerprint: String = HASH): SimulationResult = SimulationResult(
        "$event:simulation:$index:$attempt", SimulationTargetRef(session, event, "request", index, fingerprint),
        attempt, 10, 20, 10, SimulationOutcome.PASS, ProtocolFailureSource.NONE, "processed",
        contextSlot = 55, unitsConsumed = 100, logs = BoundedLogs(listOf("Program success"), false))

    private fun seed(db: androidx.sqlite.db.SupportSQLiteDatabase, session: String, event: String, fingerprint: String) {
        db.execSQL("INSERT INTO sessions VALUES (?, 1, NULL, NULL, 'solana:devnet', NULL)", arrayOf(session))
        val summary = """{"payload_count":"1","payload_0_sha256":"$fingerprint","payload_0_length":"100"}"""
        db.execSQL("INSERT INTO protocol_events VALUES (?, ?, 1, 'SIGN_TRANSACTIONS', 2, 3, 'SUCCESS', NULL, 'NONE', NULL, ?, '{}', NULL)",
            arrayOf(event, session, summary))
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Long =
        db.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getLong(0) }

    private suspend fun assertBad(block: suspend () -> Unit) { assertTrue(runCatching { block() }.isFailure) }
    private fun name() = ("phase5-simulation-" + UUID.randomUUID() + ".db").also(names::add)
    private fun open(name: String) = Room.databaseBuilder(context, MwaLabDatabase::class.java, name)
        .addMigrations(MwaLabDatabase.MIGRATION_1_2, MwaLabDatabase.MIGRATION_2_3).build()

    private companion object { const val HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" }
}
