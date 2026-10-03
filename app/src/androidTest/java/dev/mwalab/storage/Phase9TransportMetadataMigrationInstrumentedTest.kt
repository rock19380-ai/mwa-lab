package dev.mwalab.storage

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.mwa.association.AssociationMode
import dev.mwalab.mwa.association.DappVerificationState
import dev.mwalab.session.MwaSession
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Phase9TransportMetadataMigrationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val names = mutableListOf<String>()

    @get:Rule
    val migration = MigrationTestHelper(instrumentation, MwaLabDatabase::class.java)

    @After
    fun tearDown() {
        names.forEach(context::deleteDatabase)
    }

    @Test
    fun migration3To4PreservesPhase8EvidenceAndUsesTruthfulDefaults() {
        val name = ("phase9-transport-" + UUID.randomUUID() + ".db").also(names::add)
        migration.createDatabase(name, 3).use(::seedPhase8Database)

        migration.runMigrationsAndValidate(
            name,
            4,
            true,
            MwaLabDatabase.MIGRATION_3_4,
        ).use { migrated ->
            assertEquals(4, migrated.version)
            migrated.query(
                "SELECT session_id, dapp_identity_name, cluster, close_reason, " +
                    "association_mode, identity_verification_state FROM sessions",
            ).use { row ->
                assertEquals(true, row.moveToFirst())
                assertEquals("historical", row.getString(0))
                assertEquals("Phase 8 dApp", row.getString(1))
                assertEquals("solana:devnet", row.getString(2))
                assertEquals("SCENARIO_COMPLETE", row.getString(3))
                assertEquals("LOCAL", row.getString(4))
                assertEquals("NOT_AVAILABLE", row.getString(5))
            }
            migrated.query(
                "SELECT method, outcome, request_summary_json, response_summary_json " +
                    "FROM protocol_events WHERE event_id = 'historical:1'",
            ).use { event ->
                assertEquals(true, event.moveToFirst())
                assertEquals("AUTHORIZE", event.getString(0))
                assertEquals("SUCCESS", event.getString(1))
                assertEquals("{\"chain\":\"solana:devnet\"}", event.getString(2))
                assertEquals("{\"result\":\"authorized\"}", event.getString(3))
            }
            assertEquals(1L, count(migrated, "capability_snapshots"))
            assertEquals(1L, count(migrated, "transaction_diagnostics"))
            assertEquals(1L, count(migrated, "simulation_results"))
            val columns = migrated.query("PRAGMA table_info(sessions)").use { cursor ->
                buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
            }
            assertEquals(
                listOf(
                    "session_id", "started_at_ms", "completed_at_ms", "dapp_identity_name",
                    "cluster", "close_reason", "association_mode", "identity_verification_state",
                ),
                columns,
            )
            assertFalse(columns.any { column ->
                Regex("token|secret|uri|reflector|encryption|private|seed", RegexOption.IGNORE_CASE)
                    .containsMatchIn(column)
            })
            migrated.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }

        val database = Room.databaseBuilder(context, MwaLabDatabase::class.java, name)
            .addMigrations(
                MwaLabDatabase.MIGRATION_1_2,
                MwaLabDatabase.MIGRATION_2_3,
                MwaLabDatabase.MIGRATION_3_4,
            )
            .build()
        try {
            runBlocking {
                val repository = RoomSessionRepository(
                    database.sessionDao(),
                    database.protocolEventDao(),
                )
                val historical = repository.getSession("historical")
                assertNotNull(historical)
                checkNotNull(historical)
                assertEquals(AssociationMode.LOCAL, historical.session.associationMode)
                assertEquals(
                    DappVerificationState.NOT_AVAILABLE,
                    historical.session.identityVerificationState,
                )
                assertEquals("AUTHORIZE", historical.events.single().method.name)
                assertEquals("authorized", historical.events.single().responseSummary["result"])

                repository.createSession(
                    MwaSession(
                        id = "remote",
                        startedAtEpochMillis = 300,
                        associationMode = AssociationMode.REMOTE,
                        identityVerificationState = DappVerificationState.REMOTE_UNVERIFIED,
                    ),
                )
                val remote = requireNotNull(repository.getSession("remote"))
                assertEquals(AssociationMode.REMOTE, remote.session.associationMode)
                assertEquals(
                    DappVerificationState.REMOTE_UNVERIFIED,
                    remote.session.identityVerificationState,
                )
            }
        } finally {
            database.close()
        }
    }

    private fun seedPhase8Database(database: SupportSQLiteDatabase) {
        val hash = "a".repeat(64)
        database.execSQL(
            "INSERT INTO sessions VALUES " +
                "('historical', 100, 200, 'Phase 8 dApp', 'solana:devnet', 'SCENARIO_COMPLETE')",
        )
        database.execSQL(
            "INSERT INTO protocol_events VALUES " +
                "('historical:1', 'historical', 1, 'AUTHORIZE', 110, 120, 'SUCCESS', " +
                "NULL, 'NONE', NULL, '{\"chain\":\"solana:devnet\"}', " +
                "'{\"result\":\"authorized\"}', NULL)",
        )
        database.execSQL(
            "INSERT INTO capability_snapshots VALUES " +
                "('historical', 105, 'CONFIGURED_WALLETLIB_PROFILE', 10, 10, " +
                "'[\"legacy\"]', '[]')",
        )
        database.execSQL(
            "INSERT INTO transaction_diagnostics VALUES " +
                "('historical:1:payload:0', 'historical', 'historical:1', 0, '$hash', " +
                "100, 'LEGACY', 'PARSED', '{}')",
        )
        database.execSQL(
            """
            INSERT INTO simulation_results(
                simulation_id, session_id, event_id, request_id, payload_index,
                attempt_number, fingerprint_sha256, started_at_ms, completed_at_ms,
                duration_ms, outcome, failure_source, commitment, context_slot,
                error_kind, instruction_index, instruction_error_kind,
                custom_program_error_code, rpc_error_code, availability_reason,
                units_consumed, logs_json, logs_truncated
            ) VALUES (
                'historical:1:simulation:0:1', 'historical', 'historical:1', 'request',
                0, 1, '$hash', 121, 122, 1, 'PASS', 'NONE', 'processed', 7,
                NULL, NULL, NULL, NULL, NULL, NULL, 5, '["Program success"]', 0
            )
            """.trimIndent(),
        )
    }

    private fun count(database: SupportSQLiteDatabase, table: String): Long =
        database.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }
}
