package dev.mwalab.storage.transaction

import com.funkatronics.encoders.Base58
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.protocol.*
import dev.mwalab.protocol.recorder.PersistentProtocolRecorder
import dev.mwalab.security.DiagnosticSanitizer
import dev.mwalab.session.*
import dev.mwalab.storage.*
import dev.mwalab.transaction.*
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDiagnosticsPersistenceInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @get:Rule val migration = MigrationTestHelper(instrumentation, MwaLabDatabase::class.java)

    @Test fun oneAndMultiplePayloadsRetainExactOrderIdentityAndMetadataAcrossReopen() = runBlocking {
        Fixture().use { f ->
            val first = f.parent(listOf(TransactionApprovalTestVectors.systemTransfer()), "a")
            val second = f.parent(listOf(TransactionApprovalTestVectors.systemTransfer(knownProgram = false),
                byteArrayOf(1, 2, 3)), "b", ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS)
            val a = f.summaries(first)
            val b = f.summaries(second)
            f.repo.recordForEvent("a", first.event.eventId, a)
            f.repo.recordForEvent("b", second.event.eventId, b.reversed())
            assertEquals(a, f.repo.getForEvent("a", first.event.eventId))
            assertEquals(b, f.repo.observeForEvent("b", second.event.eventId).first())
            assertEquals(listOf(0, 1), f.db.transactionDiagnosticDao().getForEvent(second.event.eventId).map { it.payloadIndex })
            assertEquals(3L, f.count())
            f.reopen()
            assertEquals(a, f.repo.getForEvent("a", first.event.eventId))
            assertEquals(b, f.repo.getForEvent("b", second.event.eventId))
            assertEquals(first.event, f.sessions.getSession("a")!!.events.single())
            assertEquals(second.event, f.sessions.getSession("b")!!.events.single())
            assertTrue(f.repo.getForEvent("historical", "historical:1").isEmpty())
        }
    }

    @Test fun missingParentWrongSessionMethodAndPayloadMetadataAreRejectedByDirectDao() = runBlocking {
        Fixture().use { f ->
            val data = TransactionApprovalTestVectors.systemTransfer()
            val missing = TransactionInspector().inspect(data, 0, TransactionDiagnosticBinding("a", "absent")).toEntity()
            assertBad { f.db.transactionDiagnosticDao().insertForEvent("a", "absent", listOf(missing)) }
            val parent = f.parent(listOf(data), "a")
            val row = f.summaries(parent).single().toEntity()
            f.sessions.createSession(MwaSession("other", 0))
            assertBad { f.db.transactionDiagnosticDao().insertForEvent("other", parent.event.eventId, listOf(row)) }
            val wrongMethod = f.parent(listOf(data), "a", ProtocolMethod.SIGN_MESSAGES)
            assertBad { f.db.transactionDiagnosticDao().insertForEvent("a", wrongMethod.event.eventId,
                f.summaries(wrongMethod).map { it.toEntity() }) }
            val changed = TransactionInspector().inspect(data + byteArrayOf(0), 0,
                TransactionDiagnosticBinding("a", parent.event.eventId)).toEntity()
            assertBad { f.db.transactionDiagnosticDao().insertForEvent("a", parent.event.eventId, listOf(changed)) }
            assertEquals(0L, f.count())
            assertEquals(2, f.sessions.getSession("a")!!.events.size)
        }
    }

    @Test fun identicalReplayIsIdempotentAndConflictingOrDuplicatePayloadBatchCannotReplaceRows() = runBlocking {
        Fixture().use { f ->
            val parent = f.parent(listOf(TransactionApprovalTestVectors.systemTransfer()))
            val summaries = f.summaries(parent)
            val id = parent.event.eventId
            f.repo.recordForEvent("a", id, summaries)
            f.repo.recordForEvent("a", id, summaries)
            val original = f.db.transactionDiagnosticDao().getForEvent(id).single()
            val json = JSONObject(original.summaryJson)
            json.getJSONArray("instructions").getJSONObject(0).getJSONObject("decoded").put("lamports", "1")
            val conflicting = original.copy(summaryJson = json.toString())
            assertNotNull(conflicting.toDomain())
            assertBad { f.db.transactionDiagnosticDao().insertForEvent("a", id, listOf(conflicting)) }
            assertBad { f.db.transactionDiagnosticDao().insertForEvent("a", id, listOf(original, original)) }
            assertEquals(listOf(original), f.db.transactionDiagnosticDao().getForEvent(id))
            assertEquals(1L, f.count())
        }
    }

    @Test fun failingSecondInsertRollsBackEntireChildBatchWithoutChangingTerminalEvent() = runBlocking {
        Fixture().use { f ->
            val parent = f.parent(listOf(TransactionApprovalTestVectors.systemTransfer(), byteArrayOf(1)))
            f.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_diagnostic BEFORE INSERT ON transaction_diagnostics " +
                "WHEN NEW.payload_index = 1 BEGIN SELECT RAISE(ABORT, 'controlled diagnostic write failure'); END")
            assertBad { f.repo.recordForEvent("a", parent.event.eventId, f.summaries(parent)) }
            assertEquals(0L, f.count())
            assertEquals(parent.event, f.sessions.getSession("a")!!.events.single())
        }
    }

    @Test fun deletingEventAndSessionCascadesOnlyTheirOwnDiagnostics() = runBlocking {
        Fixture().use { f ->
            val a = f.parent(listOf(TransactionApprovalTestVectors.systemTransfer()), "a")
            val b = f.parent(listOf(byteArrayOf(1)), "b")
            f.repo.recordForEvent("a", a.event.eventId, f.summaries(a))
            f.repo.recordForEvent("b", b.event.eventId, f.summaries(b))
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM protocol_events WHERE event_id = ?", arrayOf(a.event.eventId))
            assertTrue(f.repo.getForEvent("a", a.event.eventId).isEmpty())
            assertEquals(1L, f.count())
            assertNotNull(f.sessions.getSession("a"))
            f.db.openHelper.writableDatabase.execSQL("DELETE FROM sessions WHERE session_id = ?", arrayOf("b"))
            assertEquals(0L, f.count())
            assertNull(f.sessions.getSession("b"))
            f.db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
        }
    }

    @Test fun codecRoundTripsAllVersionsAndSupportedDecodedVariantsWithoutRawFields() = runBlocking {
        Fixture().use { f ->
            val system = TransactionApprovalTestVectors.systemTransfer()
            val v0 = TransactionApprovalTestVectors.systemTransfer(versioned = true)
            val unsupported = v0.copyOf().also { it[65] = 0x85.toByte() }
            val token = byteArrayOf(3) + ByteArray(8) { 0xff.toByte() }
            val checked = byteArrayOf(12) + ByteArray(8) { 0xff.toByte() } + byteArrayOf(255.toByte())
            val payloads = listOf(system, v0, unsupported, byteArrayOf(1),
                wire(KnownProgram.MEMO.programId, "safe memo".encodeToByteArray(), emptyList()),
                wire(KnownProgram.SPL_TOKEN.programId, token, listOf(0, 1, 0)),
                wire(KnownProgram.SPL_TOKEN.programId, checked, listOf(0, 1, 0, 0)),
                wire(KnownProgram.SYSTEM.programId, byteArrayOf(99, 0, 0, 0)),
                wire(KnownProgram.SYSTEM.programId, byteArrayOf(2, 0, 0, 0)))
            val parent = f.parent(payloads)
            val summaries = f.summaries(parent)
            for (summary in summaries) assertEquals(summary, summary.toEntity().toDomain())
            val typed = summaries[5].instructions!!.single().decodedInstruction as DecodedInstruction.SplTokenTransfer
            assertEquals(BigInteger("18446744073709551615"), typed.rawAmount)
            assertTrue(summaries[6].toEntity().summaryJson.contains("\"declared_decimals\":255"))
            val unavailable = JSONObject(summaries[0].toEntity().summaryJson)
            unavailable.getJSONArray("instructions").getJSONObject(0).put("decoded",
                JSONObject().put("kind", "UNAVAILABLE").put("program", "SYSTEM").put("reason", "DECODER_FAILURE"))
            val changed = summaries[0].toEntity().copy(summaryJson = unavailable.toString())
            assertEquals(changed, changed.toDomain().toEntity())
            f.repo.recordForEvent("a", parent.event.eventId, summaries)
            f.reopen()
            assertEquals(summaries, f.repo.getForEvent("a", parent.event.eventId))
        }
    }

    @Test fun strictCodecRejectsUnknownKeysCoercionContradictionsDeepOrOversizedJsonAndUnboundRows() = runBlocking {
        Fixture().use { f ->
            val parent = f.parent(listOf(TransactionApprovalTestVectors.systemTransfer()))
            val row = f.summaries(parent).single().toEntity()
            val mutations = listOf(
                JSONObject(row.summaryJson).put("raw_auth_token", "CANARY"),
                JSONObject(row.summaryJson).put("wire_length", row.wireLength.toString()),
                JSONObject(row.summaryJson).put("payload_index", -1),
                JSONObject(row.summaryJson).put("instruction_count", 99),
                JSONObject(row.summaryJson).put("fee_payer", "invalid"),
                JSONObject(row.summaryJson).put("format", 2),
                JSONObject(row.summaryJson).put("version", "VERSIONED_UNSUPPORTED:01"),
                JSONObject(row.summaryJson).put("session_id", "different"),
            ).map { it.toString() } + listOf(row.summaryJson + " trailing", "[".repeat(1000) + "]".repeat(1000),
                " ".repeat(TransactionSummaryJson.MAX_ENCODED_BYTES + 1),
                row.summaryJson.replace("\"format\":1", "\"format\":1,\"format\":1"))
            mutations.forEach { encoded -> assertBad { row.copy(summaryJson = encoded).toDomain() } }
            assertBad { row.copy(fingerprintSha256 = "0".repeat(64)).toDomain() }
            assertBad { TransactionInspector().inspect(byteArrayOf(1)).toEntity() }
            f.db.openHelper.writableDatabase.execSQL("INSERT INTO transaction_diagnostics VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any>(row.diagnosticId, row.sessionId, row.eventId, row.payloadIndex, row.fingerprintSha256,
                    row.wireLength, row.version, row.inspectionStatus, mutations.first()))
            assertBad { f.repo.getForEvent("a", parent.event.eventId) }
            assertEquals(parent.event, f.sessions.getSession("a")!!.events.single())
        }
    }

    @Test fun actualSqliteAndWalContainSafeMetadataButNoPayloadInstructionMemoOrSecretCanaries() = runBlocking {
        Fixture().use { f ->
            val canaries = listOf("P49_AUTH_TOKEN_CANARY_9", "P49_PRIVATE_KEY_CANARY_9", "P49_SEED_CANARY_9")
            val data = canaries.joinToString("|").encodeToByteArray()
            val unknown = wire(Base58.encodeToString(ByteArray(32) { 0x55 }), data)
            val memo = wire(KnownProgram.MEMO.programId, data, emptyList())
            assertTrue(data.toString(Charsets.UTF_8).contains(canaries.first())) // Positive controls.
            val parent = f.parent(listOf(unknown, memo))
            val summaries = f.summaries(parent)
            f.repo.recordForEvent("a", parent.event.eventId, summaries)
            val rows = f.db.transactionDiagnosticDao().getForEvent(parent.event.eventId)
            assertEquals(DecodedInstruction.Unknown, summaries[0].instructions!!.single().decodedInstruction)
            assertTrue(summaries[1].instructions!!.single().decodedInstruction is DecodedInstruction.Memo)
            rows.forEach { row -> canaries.forEach { assertFalse(row.summaryJson.contains(it)) } }
            f.db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { while (it.moveToNext()) {} }
            val bytes = listOf("", "-wal", "-shm").mapNotNull { suffix ->
                context.getDatabasePath(f.name + suffix).takeIf { it.exists() }?.readBytes()
            }
            assertTrue(bytes.any { contains(it, rows.first().fingerprintSha256.encodeToByteArray()) })
            val forbidden = canaries.map { it.encodeToByteArray() } + listOf(data, unknown, memo,
                data.joinToString("") { "%02x".format(it) }.encodeToByteArray(),
                Base64.getEncoder().encode(data), Base64.getEncoder().encode(unknown))
            forbidden.forEach { sentinel -> bytes.forEach { assertFalse(contains(it, sentinel)) } }
            f.db.openHelper.readableDatabase.query("PRAGMA table_info(transaction_diagnostics)").use { cursor ->
                val columns = buildList { while (cursor.moveToNext()) add(cursor.getString(1)) }
                assertEquals(listOf("transaction_id", "session_id", "event_id", "payload_index", "fingerprint_sha256",
                    "wire_length", "version", "inspection_status", "summary_json"), columns)
            }
            f.db.openHelper.readableDatabase.query("PRAGMA index_list(transaction_diagnostics)").use { cursor ->
                assertTrue(generateSequence { if (cursor.moveToNext()) cursor.getString(1) to cursor.getInt(2) else null }
                    .any { it == "index_transaction_diagnostics_event_id_payload_index" to 1 })
            }
        }
    }

    @Test fun migrationRetainsHistoricalRowsEmptyDiagnosticsAndAllowsNewSummaryAfterReopen() = runBlocking {
        val name = "phase49-migrate-" + UUID.randomUUID() + ".db"
        try {
            migration.createDatabase(name, 1).use { old ->
                old.execSQL("INSERT INTO sessions VALUES ('historical', 1, NULL, NULL, 'solana:devnet', NULL)")
                old.execSQL("INSERT INTO protocol_events VALUES ('historical:1','historical',1,'AUTHORIZE',1,2,'SUCCESS',NULL," +
                    "'NONE',NULL,'{}','{}',NULL)")
            }
            migration.runMigrationsAndValidate(name, 2, true, MwaLabDatabase.MIGRATION_1_2).use { migrated ->
                migrated.query("SELECT COUNT(*) FROM transaction_diagnostics").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            }
            Fixture(name).use { f ->
                val historical = f.sessions.getSession("historical")
                assertEquals("historical:1", historical!!.events.single().eventId)
                val parent = f.parent(listOf(TransactionApprovalTestVectors.systemTransfer()), "new")
                val summary = f.summaries(parent)
                f.repo.recordForEvent("new", parent.event.eventId, summary)
                f.reopen()
                assertEquals(summary, f.repo.getForEvent("new", parent.event.eventId))
                assertEquals(historical.session, f.sessions.getSession("historical")!!.session)
                assertEquals(historical.events, f.sessions.getSession("historical")!!.events)
                assertTrue(f.repo.getForEvent("historical", "historical:1").isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }

    private inner class Fixture(val name: String = "phase49-storage-" + UUID.randomUUID() + ".db") : java.io.Closeable {
        var db = open()
        val sessions get() = RoomSessionRepository(db.sessionDao(), db.protocolEventDao())
        val repo get() = RoomTransactionDiagnosticRepository(db.transactionDiagnosticDao())
        private val recorder by lazy { PersistentProtocolRecorder(sessions) }
        private fun open() = Room.databaseBuilder(context, MwaLabDatabase::class.java, name)
            .addMigrations(MwaLabDatabase.MIGRATION_1_2, MwaLabDatabase.MIGRATION_2_3).build()
        suspend fun parent(payloads: List<ByteArray>, session: String = "a",
            method: ProtocolMethod = ProtocolMethod.SIGN_TRANSACTIONS): Parent {
            if (sessions.getSession(session) == null) sessions.createSession(MwaSession(session, 0))
            val request = buildMap {
                put("payload_count", payloads.size.toString())
                payloads.forEachIndexed { i, bytes ->
                    put("payload_${i}_sha256", DiagnosticSanitizer.sha256(bytes)); put("payload_${i}_length", bytes.size.toString())
                }
            }
            val handle = recorder.begin(session, method, request)
            val event = recorder.complete(handle, ProtocolOutcome.SUCCESS).event
            return Parent(event, payloads)
        }
        fun summaries(parent: Parent) = parent.payloads.mapIndexed { i, bytes ->
            TransactionInspector().inspect(bytes, i, TransactionDiagnosticBinding(parent.event.sessionId, parent.event.eventId))
        }
        fun count(): Long = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM transaction_diagnostics").use {
            assertTrue(it.moveToFirst()); it.getLong(0)
        }
        fun reopen() { db.close(); db = open() }
        override fun close() { db.close(); context.deleteDatabase(name) }
    }
    private data class Parent(val event: ProtocolEvent, val payloads: List<ByteArray>)
    private suspend fun assertBad(block: suspend () -> Any?) { assertTrue("Unsafe/inconsistent diagnostics must fail", runCatching { block() }.isFailure) }
    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        for (start in 0..(haystack.size - needle.size)) {
            var matches = true
            for (index in needle.indices) if (haystack[start + index] != needle[index]) { matches = false; break }
            if (matches) return true
        }
        return false
    }
    private fun wire(program: String, data: ByteArray, references: List<Int> = listOf(0, 1)): ByteArray {
        val out = ByteArrayOutputStream()
        fun shortvec(value: Int) { var n = value; do { val low = n and 127; n = n ushr 7; out.write(low or if (n > 0) 128 else 0) } while (n > 0) }
        out.write(1); out.write(ByteArray(64)); out.write(byteArrayOf(1, 0, 1, 3))
        out.write(ByteArray(32) { 0x11 }); out.write(ByteArray(32) { 0x22 })
        val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        var number = BigInteger.ZERO
        program.forEach { number = number * BigInteger.valueOf(58) + BigInteger.valueOf(alphabet.indexOf(it).toLong()) }
        var body = if (number.signum() == 0) byteArrayOf() else number.toByteArray()
        if (body.size > 1 && body[0] == 0.toByte()) body = body.copyOfRange(1, body.size)
        out.write(ByteArray(program.takeWhile { it == '1' }.length) + body)
        out.write(ByteArray(32) { 0x77 }); out.write(1); out.write(2); shortvec(references.size)
        references.forEach(out::write); shortvec(data.size); out.write(data)
        return out.toByteArray()
    }
}
