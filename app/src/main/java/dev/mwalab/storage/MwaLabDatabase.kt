package dev.mwalab.storage

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.mwalab.storage.capabilities.CapabilitySnapshotDao
import dev.mwalab.storage.capabilities.CapabilitySnapshotEntity
import dev.mwalab.storage.protocol.ProtocolEventDao
import dev.mwalab.storage.protocol.ProtocolEventEntity
import dev.mwalab.storage.session.SessionDao
import dev.mwalab.storage.session.SessionEntity
import dev.mwalab.storage.simulation.SimulationResultDao
import dev.mwalab.storage.simulation.SimulationResultEntity
import dev.mwalab.storage.transaction.TransactionDiagnosticDao
import dev.mwalab.storage.transaction.TransactionDiagnosticEntity

@Database(
    entities = [
        SessionEntity::class,
        ProtocolEventEntity::class,
        CapabilitySnapshotEntity::class,
        TransactionDiagnosticEntity::class,
        SimulationResultEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class MwaLabDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun protocolEventDao(): ProtocolEventDao
    abstract fun capabilitySnapshotDao(): CapabilitySnapshotDao
    abstract fun transactionDiagnosticDao(): TransactionDiagnosticDao
    abstract fun simulationResultDao(): SimulationResultDao

    companion object {
        const val DATABASE_NAME = "mwa_lab.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Empty diagnostic tables only: historical sessions are never backfilled.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS capability_snapshots (
                        session_id TEXT NOT NULL,
                        captured_at_ms INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        max_transactions INTEGER NOT NULL,
                        max_messages INTEGER NOT NULL,
                        supported_versions_json TEXT NOT NULL,
                        optional_features_json TEXT NOT NULL,
                        PRIMARY KEY(session_id),
                        FOREIGN KEY(session_id) REFERENCES sessions(session_id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS transaction_diagnostics (
                        transaction_id TEXT NOT NULL,
                        session_id TEXT NOT NULL,
                        event_id TEXT NOT NULL,
                        payload_index INTEGER NOT NULL,
                        fingerprint_sha256 TEXT NOT NULL,
                        wire_length INTEGER NOT NULL,
                        version TEXT NOT NULL,
                        inspection_status TEXT NOT NULL,
                        summary_json TEXT NOT NULL,
                        PRIMARY KEY(transaction_id),
                        FOREIGN KEY(session_id) REFERENCES sessions(session_id)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(event_id) REFERENCES protocol_events(event_id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transaction_diagnostics_session_id " +
                        "ON transaction_diagnostics(session_id)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transaction_diagnostics_event_id " +
                        "ON transaction_diagnostics(event_id)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_transaction_diagnostics_event_id_payload_index " +
                        "ON transaction_diagnostics(event_id, payload_index)",
                )
            }
        }


        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Forward-only empty child table. Historical rows remain unchanged.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS simulation_results (
                        simulation_id TEXT NOT NULL,
                        session_id TEXT NOT NULL,
                        event_id TEXT NOT NULL,
                        request_id TEXT NOT NULL,
                        payload_index INTEGER NOT NULL,
                        attempt_number INTEGER NOT NULL,
                        fingerprint_sha256 TEXT NOT NULL,
                        started_at_ms INTEGER NOT NULL,
                        completed_at_ms INTEGER NOT NULL,
                        duration_ms INTEGER NOT NULL,
                        outcome TEXT NOT NULL,
                        failure_source TEXT NOT NULL,
                        commitment TEXT NOT NULL,
                        context_slot INTEGER,
                        error_kind TEXT,
                        instruction_index INTEGER,
                        instruction_error_kind TEXT,
                        custom_program_error_code INTEGER,
                        rpc_error_code INTEGER,
                        availability_reason TEXT,
                        units_consumed INTEGER,
                        logs_json TEXT NOT NULL,
                        logs_truncated INTEGER NOT NULL,
                        PRIMARY KEY(simulation_id),
                        FOREIGN KEY(session_id) REFERENCES sessions(session_id)
                            ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(event_id) REFERENCES protocol_events(event_id)
                            ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_simulation_results_session_id ON simulation_results(session_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_simulation_results_event_id ON simulation_results(event_id)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_simulation_results_event_id_payload_index_attempt_number " +
                        "ON simulation_results(event_id, payload_index, attempt_number)",
                )
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE sessions ADD COLUMN association_mode " +
                        "TEXT NOT NULL DEFAULT 'LOCAL'",
                )
                db.execSQL(
                    "ALTER TABLE sessions ADD COLUMN identity_verification_state " +
                        "TEXT NOT NULL DEFAULT 'NOT_AVAILABLE'",
                )
            }
        }

        fun create(context: Context): MwaLabDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                MwaLabDatabase::class.java,
                DATABASE_NAME,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }
}
