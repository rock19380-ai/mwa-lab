package dev.mwalab.report

import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.mwalab.capabilities.CapabilitySnapshot
import dev.mwalab.capabilities.CapabilitySnapshotSource
import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.session.MwaSession
import dev.mwalab.session.SessionCloseReason
import dev.mwalab.storage.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticReportExportInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun persistedRestartExportMatchesRoomEvidence() = runBlocking {
        val name = "phase7-report-reopen-${UUID.randomUUID()}.db"
        val id = UUID.randomUUID().toString()
        val db = Room.databaseBuilder(context, MwaLabDatabase::class.java, name).build()
        try {
            val sessions = RoomSessionRepository(db.sessionDao(), db.protocolEventDao())
            sessions.createSession(MwaSession(id, 100, dappIdentityName = "Restart demo"))
            sessions.recordProtocolEvent(ProtocolEvent(id, "$id:1", 1,
                ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS, 110, 150,
                ProtocolOutcome.FAILURE, -3, ProtocolFailureSource.INJECTED,
                mapOf("payload_count" to "1", "auth_token" to "raw-auth-secret-SENTINEL"),
                mapOf("result" to "rejected"), "FAULT_SIGN_REJECT"))
            sessions.finishSession(id, 200, SessionCloseReason.SCENARIO_COMPLETE)
            RoomCapabilitySnapshotRepository(db.capabilitySnapshotDao()).recordSnapshot(
                CapabilitySnapshot(id, 120, CapabilitySnapshotSource.CONFIGURED_WALLETLIB_PROFILE,
                    10, 5, listOf("legacy"), listOf("solana:sign_transactions")))
        } finally { db.close() }

        val reopened = Room.databaseBuilder(context, MwaLabDatabase::class.java, name).build()
        try {
            val sessions = RoomSessionRepository(reopened.sessionDao(), reopened.protocolEventDao())
            val reports = DiagnosticReportExportUseCase(
                DiagnosticReportSnapshotAssembler(sessions,
                    RoomCapabilitySnapshotRepository(reopened.capabilitySnapshotDao()),
                    RoomTransactionDiagnosticRepository(reopened.transactionDiagnosticDao()),
                    RoomSimulationRepository(reopened.simulationResultDao())),
                DiagnosticReportCacheWriter(context.cacheDir))
            val md = reports.export(id, DiagnosticReportFormat.MARKDOWN)
            val json = reports.export(id, DiagnosticReportFormat.JSON)
            val copy = reports.copySummary(id)
            val parsed = JSONObject(json.file.readText())
            val event = parsed.getJSONArray("events").getJSONObject(0)
            assertEquals(id, parsed.getJSONObject("session").getString("session_id"))
            assertEquals("COMPLETE", parsed.getJSONObject("session").getString("completeness"))
            assertEquals("FAULT_SIGN_REJECT", event.getString("injected_fault_id"))
            assertEquals("ERROR_NOT_SIGNED", event.getString("protocol_error_name"))
            assertEquals("INJECTED", event.getString("failure_source"))
            assertEquals("Restart demo", parsed.getJSONObject("session").getString("dapp_display_name"))
            listOf(md.file.readText(), json.file.readText(), copy.text).forEach { output ->
                assertTrue(output.contains("FAULT_SIGN_REJECT"))
                assertTrue(output.contains("ERROR_NOT_SIGNED"))
                assertFalse(output.contains("raw-auth-secret-SENTINEL"))
            }
            assertEquals(ReportCompleteness.COMPLETE, md.completeness)
            assertEquals(ReportCompleteness.COMPLETE, json.completeness)
            listOf(md, json).forEach { artifact ->
                val chooser = DiagnosticReportShareIntentFactory(context).create(artifact.file, artifact.format)
                val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
                assertEquals(artifact.format.mimeType, send.type)
                assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                assertFalse(chooser.toUri(0).contains("raw-auth-secret-SENTINEL"))
                assertFalse(send.toUri(0).contains("raw-auth-secret-SENTINEL"))
                assertFalse(send.clipData.toString().contains("raw-auth-secret-SENTINEL"))
            }
        } finally { reopened.close(); context.deleteDatabase(name) }
    }

    @Test fun providerGrantsOnlyReportCacheContentUri() {
        val authority = context.packageName + DiagnosticReportShareIntentFactory.AUTHORITY_SUFFIX
        val info = context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA)
        assertNotNull(info)
        assertFalse(info!!.exported)
        assertTrue(info.grantUriPermissions)
        val reportDir = DiagnosticReportCacheWriter(context.cacheDir).reportDirectory()
        val file = File.createTempFile("mwa-lab-test-", ".md", reportDir)
        try {
            file.writeText("Sanitized diagnostic report")
            val chooser = DiagnosticReportShareIntentFactory(context).create(file, DiagnosticReportFormat.MARKDOWN)
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("text/plain", send.type)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val uri = send.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("content", uri.scheme)
            assertEquals(authority, uri.authority)
            assertEquals("Sanitized diagnostic report", context.contentResolver.openInputStream(uri)!!
                .bufferedReader().use { it.readText() })
            assertFalse(chooser.toUri(0).contains("raw-auth-secret-SENTINEL"))
            val otherFile = File(context.cacheDir, "outside-report.md")
            otherFile.writeText("outside")
            try {
                try {
                    FileProvider.getUriForFile(context, authority, otherFile)
                    fail("Provider exposed outside report directory")
                } catch (_: IllegalArgumentException) { }
            } finally { otherFile.delete() }
            try {
                DiagnosticReportShareIntentFactory(context).create(File(reportDir, "../outside.md"),
                    DiagnosticReportFormat.MARKDOWN)
                fail("Path traversal accepted")
            } catch (_: IllegalArgumentException) { }
        } finally { file.delete() }
    }
}
