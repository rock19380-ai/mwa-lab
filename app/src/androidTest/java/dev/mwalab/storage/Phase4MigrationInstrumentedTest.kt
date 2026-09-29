package dev.mwalab.storage

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.capabilities.snapshotForSession
import dev.mwalab.mwa.capabilities.MwaCapabilityProfile
import dev.mwalab.session.MwaSession
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase4MigrationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val databaseNames = mutableListOf<String>()

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, MwaLabDatabase::class.java)

    @After
    fun tearDown() {
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun migrationPreservesEveryHistoricalColumnAndLeavesBothDiagnosticTablesEmpty() = runBlocking {
        val name = newName()
        val old = helper.createDatabase(name, 1)
        val before: Map<String, List<List<String?>>>
        try {
            seedPhase3History(old)
            before = history(old)
        } finally { old.close() }

        helper.runMigrationsAndValidate(name, 2, true, MwaLabDatabase.MIGRATION_1_2).use { migrated ->
            assertEquals(before, history(migrated))
            assertEquals(2, migrated.version)
            assertEquals(0L, count(migrated, "capability_snapshots"))
            assertEquals(0L, count(migrated, "transaction_diagnostics"))
            migrated.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }

        val configured = MwaCapabilityProfile.snapshotForSession(
            "new-phase4", 800, MwaCapabilityProfile.createWalletConfig(),
        )
        open(name).withClosed { db ->
            val capabilities = RoomCapabilitySnapshotRepository(db.capabilitySnapshotDao())
            assertNull(capabilities.getSnapshot("closed-phase3"))
            assertNull(capabilities.getSnapshot("open-phase3"))
            assertEquals(before, history(db.openHelper.readableDatabase))
            val historical = RoomSessionRepository(db.sessionDao(), db.protocolEventDao())
            assertEquals(listOf("closed-phase3:1", "closed-phase3:2"),
                checkNotNull(historical.getSession("closed-phase3")).events.map { it.eventId })
            assertEquals("open-phase3:1",
                checkNotNull(historical.getSession("open-phase3")).events.single().eventId)
            assertEquals(0L, count(db.openHelper.readableDatabase, "transaction_diagnostics"))
            RoomSessionRepository(db.sessionDao(), db.protocolEventDao())
                .createSession(MwaSession("new-phase4", 790))
            capabilities.recordSnapshot(configured)
            assertEquals(configured, capabilities.getSnapshot("new-phase4"))
        }
        open(name).withClosed { reopened ->
            val capabilities = RoomCapabilitySnapshotRepository(reopened.capabilitySnapshotDao())
            assertEquals(configured, capabilities.getSnapshot("new-phase4"))
            assertNull(capabilities.getSnapshot("closed-phase3"))
            assertNull(capabilities.getSnapshot("open-phase3"))
            assertEquals(before.getValue("protocol_events"),
                history(reopened.openHelper.readableDatabase).getValue("protocol_events"))
            assertEquals(3L, count(reopened.openHelper.readableDatabase, "sessions"))
        }
    }

    @Test
    fun productionBuilderMigrationPreservesHistoryAndValidatesGeneratedSchema() {
        val name = newName()
        val old = helper.createDatabase(name, 1)
        val before: Map<String, List<List<String?>>>
        try {
            seedPhase3History(old)
            before = history(old)
        } finally { old.close() }
        open(name).withClosed { db ->
            val migrated = db.openHelper.writableDatabase
            assertEquals(2, migrated.version)
            assertEquals(before, history(migrated))
            assertEquals(0L, count(migrated, "capability_snapshots"))
            assertEquals(0L, count(migrated, "transaction_diagnostics"))
        }
    }

    @Test
    fun failedMigrationRollsBackNewTablesAndHistoryThenAllowsSafeRetry() {
        val name = newName()
        val old = helper.createDatabase(name, 1)
        val before: Map<String, List<List<String?>>>
        try {
            seedPhase3History(old)
            before = history(old)
        } finally { old.close() }
        val failing = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MwaLabDatabase.MIGRATION_1_2.migrate(db)
                throw IllegalStateException("Injected migration failure")
            }
        }
        Room.databaseBuilder(context, MwaLabDatabase::class.java, name).addMigrations(failing).build().withClosed { db ->
            assertTrue(runCatching { db.openHelper.writableDatabase }.exceptionOrNull() is IllegalStateException)
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            assertEquals(1, db.version)
            assertEquals(before.getValue("sessions"), rows(db.rawQuery("SELECT * FROM sessions ORDER BY session_id", null)))
            assertEquals(before.getValue("protocol_events"), rows(db.rawQuery("SELECT * FROM protocol_events ORDER BY event_id", null)))
            db.rawQuery("SELECT name FROM sqlite_master WHERE name IN ('capability_snapshots', 'transaction_diagnostics')", null)
                .use { assertFalse(it.moveToFirst()) }
        }
        open(name).withClosed { db ->
            val migrated = db.openHelper.writableDatabase
            assertEquals(before, history(migrated))
            assertEquals(2, migrated.version)
            assertEquals(0L, count(migrated, "capability_snapshots"))
            assertEquals(0L, count(migrated, "transaction_diagnostics"))
        }
    }

    private fun seedPhase3History(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO sessions VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("closed-phase3", 100L, 500L, "Phase 3 dApp", "solana:devnet", "SCENARIO_COMPLETE"),
        )
        db.execSQL(
            "INSERT INTO sessions VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("open-phase3", 600L, null, null, "solana:devnet", null),
        )
        db.execSQL(
            "INSERT INTO protocol_events VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("closed-phase3:1", "closed-phase3", 1L, "AUTHORIZE", 110L, 150L,
                "SUCCESS", null, "NONE", null, "{\"chain\":\"solana:devnet\"}", "{\"result\":\"authorized\"}", null),
        )
        db.execSQL(
            "INSERT INTO protocol_events VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("closed-phase3:2", "closed-phase3", 2L, "SIGN_TRANSACTIONS", 210L, 240L,
                "FAILURE", -32003, "OBSERVED_PROTOCOL", null, "{\"payload_count\":\"2\"}",
                "{\"result\":\"rejected\"}", "{\"origin\":\"historical-safe-metadata\"}"),
        )
        db.execSQL(
            "INSERT INTO protocol_events VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf<Any?>("open-phase3:1", "open-phase3", 1L, "SIGN_MESSAGES", 610L, 630L,
                "CANCELLED", null, "UNKNOWN", null, "{}", "{}", null),
        )
    }

    private fun history(db: SupportSQLiteDatabase): Map<String, List<List<String?>>> =
        mapOf(
            "sessions" to rows(db.query("SELECT * FROM sessions ORDER BY session_id")),
            "protocol_events" to rows(db.query("SELECT * FROM protocol_events ORDER BY event_id")),
        )

    private fun rows(cursor: Cursor): List<List<String?>> = cursor.use {
        buildList {
            while (it.moveToNext()) {
                add((0 until it.columnCount).map { index -> if (it.isNull(index)) null else it.getString(index) })
            }
        }
    }

    private fun count(db: SupportSQLiteDatabase, table: String): Long =
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }

    private fun newName() = ("phase4-migration-" + UUID.randomUUID() + ".db").also(databaseNames::add)
    private inline fun <R> MwaLabDatabase.withClosed(block: (MwaLabDatabase) -> R): R =
        try { block(this) } finally { close() }

    private fun open(name: String) = Room.databaseBuilder(context, MwaLabDatabase::class.java, name)
        .addMigrations(MwaLabDatabase.MIGRATION_1_2).build()
}
