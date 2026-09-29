package dev.mwalab.storage.capabilities

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.capabilities.CapabilitySnapshot
import dev.mwalab.capabilities.snapshotForSession
import dev.mwalab.mwa.capabilities.MwaCapabilityProfile
import dev.mwalab.session.MwaSession
import dev.mwalab.storage.MwaLabDatabase
import dev.mwalab.storage.RoomCapabilitySnapshotRepository
import dev.mwalab.storage.RoomSessionRepository
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapabilitySnapshotPersistenceInstrumentedTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseNames = mutableListOf<String>()

    @After
    fun tearDown() {
        databaseNames.forEach(context::deleteDatabase)
    }

    @Test
    fun absentSnapshotStaysAbsentWithoutCreatingProtocolEvidence() = runBlocking {
        withDatabase { db ->
            val sessions = sessions(db)
            val capabilities = capabilities(db)
            sessions.createSession(MwaSession("historical", 100))
            assertNull(capabilities.getSnapshot("historical"))
            assertNull(capabilities.observeSnapshot("historical").first())
            assertNull(capabilities.getSnapshot("missing"))
            assertTrue(db.protocolEventDao().getForSession("historical").isEmpty())
            assertTrue(db.transactionDiagnosticDao().getForEvent("historical:1").isEmpty())
        }
    }

    @Test
    fun insertReadObserveAndReopenRetainExactSessionSnapshot() = runBlocking {
        val name = newName()
        val first = snapshot("a", 110)
        val second = snapshot("b", 220)
        withDatabase(name) { db ->
            sessions(db).createSession(MwaSession("a", 100))
            sessions(db).createSession(MwaSession("b", 200))
            val repo = capabilities(db)
            repo.recordSnapshot(first)
            repo.recordSnapshot(second)
            assertEquals(first, repo.getSnapshot("a"))
            assertEquals(second, repo.observeSnapshot("b").first())
            assertTrue(db.protocolEventDao().getForSession("a").isEmpty())
            assertTrue(db.protocolEventDao().getForSession("b").isEmpty())
        }
        withDatabase(name) { db ->
            val repo = capabilities(db)
            assertEquals(first, repo.getSnapshot("a"))
            assertEquals(second, repo.getSnapshot("b"))
        }
    }

    @Test
    fun foreignKeyRejectsSnapshotForMissingSession() = runBlocking {
        withDatabase { db ->
            val failure = runCatching {
                capabilities(db).recordSnapshot(snapshot("orphan", 1))
            }.exceptionOrNull()
            assertTrue("Missing-session insert must fail its FK", failure is SQLiteConstraintException)
            assertNull(db.capabilitySnapshotDao().get("orphan"))
            assertNull(db.sessionDao().get("orphan"))
        }
    }

    @Test
    fun deletingParentCascadesOnlyItsOwnCapabilitySnapshot() = runBlocking {
        withDatabase { db ->
            sessions(db).createSession(MwaSession("a", 1))
            sessions(db).createSession(MwaSession("b", 2))
            val repo = capabilities(db)
            repo.recordSnapshot(snapshot("a", 1))
            repo.recordSnapshot(snapshot("b", 2))
            db.openHelper.writableDatabase.execSQL("DELETE FROM sessions WHERE session_id = ?", arrayOf("a"))
            assertNull(repo.getSnapshot("a"))
            assertNotNull(repo.getSnapshot("b"))
        }
    }

    @Test
    fun identicalReplayIsIdempotentAndConflictingCaptureCannotReplaceHistory() = runBlocking {
        withDatabase { db ->
            sessions(db).createSession(MwaSession("a", 1))
            val repo = capabilities(db)
            val original = snapshot("a", 2)
            repo.recordSnapshot(original)
            repo.recordSnapshot(original)
            assertTrue(runCatching { repo.recordSnapshot(snapshot("a", 3)) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(original, repo.getSnapshot("a"))
            assertEquals(1L, count(db, "capability_snapshots"))
            assertTrue(db.protocolEventDao().getForSession("a").isEmpty())
        }
    }

    @Test
    fun directDaoRejectsInvalidMetadataWithoutWritingSnapshot() = runBlocking {
        withDatabase { db ->
            sessions(db).createSession(MwaSession("a", 1))
            val valid = snapshot("a", 2).toEntity()
            listOf(
                valid.copy(source = "UNKNOWN_PROVENANCE"),
                valid.copy(supportedVersionsJson = "[1]"),
                valid.copy(optionalFeaturesJson = "[null]"),
                valid.copy(capturedAtEpochMillis = -1),
            ).forEach { invalid ->
                assertTrue(runCatching { db.capabilitySnapshotDao().insertOnce(invalid) }
                    .exceptionOrNull() is IllegalArgumentException)
            }
            assertNull(capabilities(db).getSnapshot("a"))
            assertEquals(0L, count(db, "capability_snapshots"))
            assertTrue(db.protocolEventDao().getForSession("a").isEmpty())
        }
    }

    @Test
    fun orderedSerializationIsCanonicalAndRejectsCoercionOrTrailingContent() {
        val labels = listOf("second", "first")
        val encoded = CapabilityListJson.encode(labels)
        assertEquals("[\"second\",\"first\"]", encoded)
        repeat(4) { assertEquals(encoded, CapabilityListJson.encode(labels)) }
        assertEquals(labels, CapabilityListJson.decode(encoded))
        assertEquals("[]", CapabilityListJson.encode(emptyList()))
        listOf("", "{}", "[1]", "[null]", "[true]", "[[]]", "[\"a\"] garbage",
            "[\"a\"]{}", "['a']", "[ \"a\" ]", "[\"a\",]", "[\"a\"]\u0000{}",
            " ".repeat(16 * 1024 + 1),
            CapabilityListJson.encode(listOf("a")).replace("a", "x".repeat(129)),
            "[" + List(CapabilitySnapshot.MAX_COLLECTION_SIZE + 1) { "\"a\"" }.joinToString(",") + "]",
        ).forEach { invalid ->
            assertThrows(IllegalArgumentException::class.java) { CapabilityListJson.decode(invalid) }
        }
    }

    @Test
    fun corruptStoredProvenanceCollectionsAndScalarMetadataAreRejected() = runBlocking {
        val corruptions = listOf(
            "source" to "UNKNOWN_PROVENANCE",
            "supported_versions_json" to "[1]",
            "supported_versions_json" to "[]",
            "optional_features_json" to "[null]",
            "captured_at_ms" to "-1",
            "max_transactions" to "-1",
        )
        withDatabase { db ->
            val repo = capabilities(db)
            corruptions.forEachIndexed { index, (column, value) ->
                val id = "corrupt-$index"
                sessions(db).createSession(MwaSession(id, 1))
                repo.recordSnapshot(snapshot(id, 2))
                // Simulate external database corruption, bypassing the production DAO.
                db.openHelper.writableDatabase.execSQL(
                    "UPDATE capability_snapshots SET $column = ? WHERE session_id = ?", arrayOf(value, id),
                )
                assertTrue(runCatching { repo.getSnapshot(id) }.exceptionOrNull() is IllegalArgumentException)
                assertNotNull(db.sessionDao().get(id))
                assertTrue(db.protocolEventDao().getForSession(id).isEmpty())
            }
        }
    }

    @Test
    fun newTablesExposeOnlyTheFrozenMetadataColumns() = runBlocking {
        withDatabase { db ->
            val expected = mapOf(
                "capability_snapshots" to setOf("session_id", "captured_at_ms", "source", "max_transactions",
                    "max_messages", "supported_versions_json", "optional_features_json"),
                "transaction_diagnostics" to setOf("transaction_id", "session_id", "event_id", "payload_index",
                    "fingerprint_sha256", "wire_length", "version", "inspection_status", "summary_json"),
            )
            expected.forEach { (table, allowed) ->
                val columns = mutableSetOf<String>()
                db.openHelper.readableDatabase.query("PRAGMA table_info($table)").use { cursor ->
                    while (cursor.moveToNext()) {
                        columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                        assertTrue(cursor.getString(cursor.getColumnIndexOrThrow("type")) != "BLOB")
                    }
                }
                assertEquals(allowed, columns)
                assertTrue(columns.none { Regex("auth|private|seed|mnemonic|encryption|secret|raw").containsMatchIn(it) })
                assertEquals(0L, count(db, table))
            }
        }
    }

    private fun snapshot(id: String, timestamp: Long) =
        MwaCapabilityProfile.snapshotForSession(id, timestamp, MwaCapabilityProfile.createWalletConfig())

    private fun sessions(db: MwaLabDatabase) = RoomSessionRepository(db.sessionDao(), db.protocolEventDao())
    private fun capabilities(db: MwaLabDatabase) = RoomCapabilitySnapshotRepository(db.capabilitySnapshotDao())
    private fun newName() = ("phase4-capabilities-" + UUID.randomUUID() + ".db").also(databaseNames::add)
    private fun open(name: String) = Room.databaseBuilder(context, MwaLabDatabase::class.java, name)
        .addMigrations(MwaLabDatabase.MIGRATION_1_2).build()

    private suspend fun withDatabase(
        name: String = newName(),
        block: suspend (MwaLabDatabase) -> Unit,
    ) {
        val db = open(name)
        try { block(db) } finally { db.close() }
    }

    private fun count(db: MwaLabDatabase, table: String): Long =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
}
