package dev.mwalab.report

import dev.mwalab.mwa.association.AssociationMode
import dev.mwalab.mwa.association.DappVerificationState
import dev.mwalab.capabilities.*
import dev.mwalab.protocol.*
import dev.mwalab.session.*
import dev.mwalab.simulation.*
import dev.mwalab.transaction.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class DiagnosticReportTest {
    @get:Rule val temporary = TemporaryFolder()
    private val id = "11111111-1111-4111-8111-111111111111"
    private fun event(n: Long = 1, outcome: ProtocolOutcome = ProtocolOutcome.SUCCESS,
        source: ProtocolFailureSource = ProtocolFailureSource.NONE, fault: String? = null,
        error: Int? = null, request: Map<String, String> = mapOf("payload_count" to "2")) =
        ProtocolEvent(id, "$id:$n", n, ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS, 100, 120,
            outcome, error, source, request, mapOf("result" to "submitted"), fault)
    private class SessionSource(var summary: SessionSummary) : SessionRepository {
        var reads = 0
        var changing = false
        override suspend fun getSession(sessionId: String): SessionSummary? {
            reads++
            return if (changing) SessionSummary(summary.session, summary.events.map {
                it.copy(responseSummary = mapOf("result" to "read_" + reads)) }) else summary
        }
        override suspend fun createSession(session: MwaSession) = error("read only")
        override suspend fun finishSession(sessionId: String, completedAtEpochMillis: Long,
            closeReason: SessionCloseReason) = error("read only")
        override suspend fun updateDappIdentity(sessionId: String, dappIdentityName: String?) = error("read only")
        override suspend fun recordProtocolEvent(event: ProtocolEvent) = error("read only")
        override fun observeSessions(): Flow<List<SessionSummary>> = flowOf(listOf(summary))
        override fun observeSession(sessionId: String): Flow<SessionSummary?> = flowOf(summary)
    }
    private class CapabilitySource(var snapshot: CapabilitySnapshot? = null) : CapabilitySnapshotRepository {
        override suspend fun getSnapshot(sessionId: String) = snapshot
        override suspend fun recordSnapshot(snapshot: CapabilitySnapshot) = error("read only")
        override fun observeSnapshot(sessionId: String): Flow<CapabilitySnapshot?> = flowOf(snapshot)
    }
    private class TransactionSource(val rows: Map<String, List<TransactionSummary>> = emptyMap()) : TransactionDiagnosticRepository {
        override suspend fun getForEvent(sessionId: String, eventId: String) = rows[eventId].orEmpty()
        override suspend fun recordForEvent(sessionId: String, eventId: String, summaries: List<TransactionSummary>) = error("read only")
        override fun observeForEvent(sessionId: String, eventId: String): Flow<List<TransactionSummary>> = flowOf(rows[eventId].orEmpty())
    }
    private class SimulationSource(val rows: Map<String, List<SimulationResult>> = emptyMap()) : SimulationRepository {
        override suspend fun getForEvent(sessionId: String, eventId: String) = rows[eventId].orEmpty()
        override suspend fun recordForEvent(sessionId: String, eventId: String, results: List<SimulationResult>) = error("read only")
        override fun observeForEvent(sessionId: String, eventId: String): Flow<List<SimulationResult>> = flowOf(rows[eventId].orEmpty())
    }
    private fun source(events: List<ProtocolEvent>, open: Boolean = false, label: String = "Demo dApp") =
        SessionSource(SessionSummary(MwaSession(id, 10, if (open) null else 200, label,
            if (open) null else SessionCloseReason.SCENARIO_COMPLETE), events))
    private fun build(s: SessionSource, c: CapabilitySource = CapabilitySource(),
        t: TransactionSource = TransactionSource(), r: SimulationSource = SimulationSource()) = runBlocking {
        requireNotNull(DiagnosticReportSnapshotAssembler(s, c, t, r) { 500 }.build(id))
    }
    @Test fun successAndEmptyChildrenParseBack() {
        val report = build(source(listOf(event())))
        assertEquals(ReportCompleteness.COMPLETE, report.session.completeness)
        assertEquals(ProtocolFailureSource.NONE, report.events.single().failureSource)
        assertTrue(report.events.single().transactions.isEmpty())
        val parsed = JSONObject(JsonDiagnosticReportRenderer().render(report))
        assertEquals(DiagnosticReport.FORMAT, parsed.getString("format"))
        assertEquals(1, parsed.getInt("version"))
        val sessionJson = parsed.getJSONObject("session")
        assertEquals("COMPLETE", sessionJson.getString("completeness"))
        assertEquals("LOCAL", sessionJson.getString("association_mode"))
        assertEquals("NOT_AVAILABLE", sessionJson.getString("identity_verification_state"))
        assertEquals("SUCCESS", parsed.getJSONArray("events").getJSONObject(0).getString("outcome"))
        val markdown = MarkdownDiagnosticReportRenderer().render(report)
        assertTrue(markdown.contains("Outcome: SUCCESS"))
        assertTrue(markdown.contains("Association mode: LOCAL"))
        assertTrue(markdown.contains("Identity status: NOT\\_AVAILABLE"))
        assertTrue(markdown.contains("## Reproduction Context"))
        assertTrue(markdown.contains("1970-01-01T00:00:00.500Z"))
        assertTrue(markdown.contains("byte-for-byte reproduction is not available"))
    }
    @Test fun remoteTransportMetadataStaysUnverifiedAcrossReportProjections() {
        val source = source(listOf(event())).also { existing ->
            existing.summary = SessionSummary(
                existing.summary.session.copy(
                    associationMode = AssociationMode.REMOTE,
                    identityVerificationState = DappVerificationState.REMOTE_UNVERIFIED,
                ),
                existing.summary.events,
            )
        }
        val report = build(source)
        val json = JSONObject(JsonDiagnosticReportRenderer().render(report)).getJSONObject("session")
        assertEquals("REMOTE", json.getString("association_mode"))
        assertEquals("REMOTE_UNVERIFIED", json.getString("identity_verification_state"))
        val markdown = MarkdownDiagnosticReportRenderer().render(report)
        assertTrue(markdown.contains("Association mode: REMOTE"))
        assertTrue(markdown.contains("Identity status: REMOTE"))
        val summary = DiagnosticReportSummaryRenderer().render(report)
        assertTrue(summary.contains("Association: REMOTE"))
        assertTrue(summary.contains("Identity: REMOTE_UNVERIFIED"))
    }

    @Test fun failureTruthPreservesFiveIndependentCases() {
        val cases = listOf(
            event(outcome = ProtocolOutcome.FAILURE, source = ProtocolFailureSource.INJECTED,
                fault = "FAULT_SIGN_REJECT", error = -3),
            event(fault = "FAULT_DELAY_5S"),
            event(outcome = ProtocolOutcome.FAILURE, source = ProtocolFailureSource.RPC_NETWORK,
                fault = "FAULT_DELAY_5S", error = -4),
            event(outcome = ProtocolOutcome.FAILURE, source = ProtocolFailureSource.OBSERVED_PROTOCOL,
                error = -3),
            event())
        cases.forEach { original ->
            val report = build(source(listOf(original)))
            val actual = report.events.single()
            assertEquals(original.outcome, actual.outcome)
            assertEquals(original.failureSource, actual.failureSource)
            assertEquals(original.injectedFaultId, actual.injectedFaultId)
            assertEquals(original.protocolErrorCode, actual.protocolErrorCode)
            val parsed = JSONObject(JsonDiagnosticReportRenderer().render(report))
                .getJSONArray("events").getJSONObject(0)
            assertEquals(original.outcome.name, parsed.getString("outcome"))
            assertEquals(original.failureSource.name, parsed.getString("failure_source"))
            if (original.injectedFaultId == null) assertTrue(parsed.isNull("injected_fault_id"))
            else assertEquals(original.injectedFaultId, parsed.getString("injected_fault_id"))
        }
    }
    @Test fun capabilitiesTransactionsAndSimulationsStayOrdered() {
        val cap = CapabilitySnapshot(id, 20, CapabilitySnapshotSource.CONFIGURED_WALLETLIB_PROFILE,
            10, 5, listOf("legacy", "v0"), listOf("solana:feature_b", "solana:feature_a"))
        val unknown = fixture("legacy-unknown-program.hex", 0)
        val system = fixture("legacy-system-transfer.hex", 1)
        val results = listOf(simulation(1, 2, SimulationOutcome.PASS),
            simulation(0, 2, SimulationOutcome.FAIL), simulation(0, 1, SimulationOutcome.UNAVAILABLE))
        val report = build(source(listOf(event(2), event(1))), CapabilitySource(cap),
            TransactionSource(mapOf("$id:1" to listOf(system, unknown))),
            SimulationSource(mapOf("$id:1" to results)))
        assertEquals(listOf(1L, 2L), report.events.map { it.sequence })
        assertEquals(listOf(0, 1), report.events[0].transactions.map { it.payloadIndex })
        assertEquals(listOf(0 to 1, 0 to 2, 1 to 2),
            report.events[0].simulations.map { it.payloadIndex to it.attemptNumber })
        assertEquals(listOf("solana:feature_a", "solana:feature_b"), report.capabilities!!.optionalFeatures)
        assertNull(report.events[0].transactions[0].instructions[0].programName)
        assertEquals("System Program", report.events[0].transactions[1].instructions[0].programName)
        assertNotNull(report.events[0].transactions[1].instructions[0].exactAmount)
        val parsed = JSONObject(JsonDiagnosticReportRenderer().render(report))
            .getJSONArray("events").getJSONObject(0).getJSONArray("simulations")
        assertEquals("UNAVAILABLE", parsed.getJSONObject(0).getString("outcome"))
        assertEquals("RPC_NETWORK", parsed.getJSONObject(0).getString("failure_source"))
        assertEquals("FAIL", parsed.getJSONObject(1).getString("outcome"))
        assertEquals("PASS", parsed.getJSONObject(2).getString("outcome"))
        assertTrue(MarkdownDiagnosticReportRenderer().render(report).contains("not a guarantee of submission success"))
    }
    @Test fun openAndChangingSessionsArePartial() {
        val open = build(source(listOf(event()), open = true))
        assertEquals(ReportCompleteness.PARTIAL, open.session.completeness)
        assertTrue(open.warnings.any { it.code == "OPEN_SESSION" })
        val changing = source(listOf(event())).also { it.changing = true }
        val partial = build(changing)
        assertEquals(ReportCompleteness.PARTIAL, partial.session.completeness)
        assertTrue(partial.warnings.any { it.code == "CHANGING_SESSION" })
        assertEquals(4, changing.reads)
    }
    @Test fun hostileFieldsBoundsUnicodeAndMarkdownEscaping() {
        val sentinels = listOf("raw-auth-secret-abc", "private-key-secret-abc", "seed-secret-abc",
            "association-token-secret-abc", "raw-message-secret-abc", "raw-transaction-secret-abc",
            "signature-secret-abc")
        val request = (0..50).associate { "unknown_$it" to sentinels[it % sentinels.size] } +
            mapOf("result" to sentinels[0], "payload_count" to "2")
        val report = build(source((1L..70L).map { event(it, request = request) },
            label = "Demo | *<世界>"))
        assertEquals(64, report.events.size)
        assertTrue(report.truncated)
        assertTrue(report.warnings.any { it.code == "OMITTED_UNSAFE_OR_EXCESS_EVIDENCE" })
        val markdown = MarkdownDiagnosticReportRenderer().render(report)
        val json = JsonDiagnosticReportRenderer().render(report)
        assertTrue(markdown.contains("Demo \\| \\*&lt;世界&gt;"))
        assertFalse(markdown.contains('\u0001'))
        assertEquals("Demo | *<世界>", JSONObject(json).getJSONObject("session").getString("dapp_display_name"))
        assertTrue(JSONObject(json).getBoolean("truncated"))
        listOf(report.toString(), markdown, json).forEach { text ->
            sentinels.forEach { sentinel -> assertFalse(text.contains(sentinel)) }
        }
    }
    @Test fun capabilitySecretMarkerIsOmittedBeforeCanonicalOutput() {
        val cap = CapabilitySnapshot(id, 20, CapabilitySnapshotSource.CONFIGURED_WALLETLIB_PROFILE,
            10, 5, listOf("legacy"), listOf("solana:normal", "solana:private-key-secret-abc"))
        val report = build(source(listOf(event())), CapabilitySource(cap))
        assertEquals(listOf("solana:normal"), report.capabilities!!.optionalFeatures)
        assertTrue(report.truncated)
        assertFalse(JsonDiagnosticReportRenderer().render(report).contains("private-key-secret-abc"))
        assertFalse(MarkdownDiagnosticReportRenderer().render(report).contains("private-key-secret-abc"))
    }

    @Test fun simulationLogsAndSummaryFieldsUseIndependentBounds() {
        val logs = BoundedLogs(List(20) { "Program success" }, false)
        val result = SimulationResult("sim-0-1", SimulationTargetRef(id, "$id:1", "request", 0,
            "a".repeat(64)), 1, 100, 105, 5, SimulationOutcome.PASS, ProtocolFailureSource.NONE,
            "processed", logs = logs)
        val report = build(source(listOf(event())), r = SimulationSource(mapOf("$id:1" to listOf(result))))
        assertEquals(16, report.events.single().simulations.single().programLogs.size)
        assertTrue(report.events.single().simulations.single().logsTruncated)
        assertTrue(report.truncated)
        assertTrue(report.warnings.any { it.code == "OMITTED_UNSAFE_OR_EXCESS_EVIDENCE" })
    }

    @Test fun directOversizedModelIsRejectedBeforeRenderingAndJsonEscapesQuotes() {
        val report = build(source(listOf(event()), label = "A \"quoted\" \\ dApp"))
        val parsed = JSONObject(JsonDiagnosticReportRenderer().render(report))
        assertEquals("A \"quoted\" \\ dApp", parsed.getJSONObject("session").getString("dapp_display_name"))
        val markdown = MarkdownDiagnosticReportRenderer().render(report)
        assertTrue(markdown.contains("A \"quoted\" \\\\ dApp"))
        val excessive = report.copy(events = List(65) { report.events.single() })
        assertThrows(IllegalArgumentException::class.java) { JsonDiagnosticReportRenderer().render(excessive) }
        assertThrows(IllegalArgumentException::class.java) { MarkdownDiagnosticReportRenderer().render(excessive) }
        val unsafe = report.copy(session = report.session.copy(dappDisplayName = "private-key-secret-abc"))
        assertThrows(IllegalArgumentException::class.java) { JsonDiagnosticReportRenderer().render(unsafe) }
        val policy = ReportSanitizationPolicy()
        assertEquals("badlabel", policy.label("bad\u0001label"))
        assertTrue(policy.truncated)
    }

    @Test fun excessiveSafeSummaryFieldsKeepDeterministicFirstKeys() {
        val fields = (0..9).flatMap { index ->
            listOf("payload_${index}_sha256" to "a".repeat(64),
                "payload_${index}_length" to "123")
        } + listOf("payload_count" to "10", "address_count" to "1",
            "submitted_count" to "1", "requested_feature_count" to "0",
            "signed_payload_count" to "1", "requested_address_count" to "0")
        val first = build(source(listOf(event(request = fields.toMap()))))
        val reversed = build(source(listOf(event(request = fields.reversed().toMap()))))
        val firstKeys = first.events.single().requestSummary.keys.toList()
        assertEquals(ReportLimits.MAX_SUMMARY_FIELDS, firstKeys.size)
        assertEquals(firstKeys.sorted(), firstKeys)
        assertEquals(first.events.single().requestSummary, reversed.events.single().requestSummary)
        assertTrue(first.truncated)
        assertTrue(first.warnings.any { it.code == "OMITTED_UNSAFE_OR_EXCESS_EVIDENCE" })
    }

    @Test fun cacheFilesAndClipboardDeriveFromCanonicalReport() {
        val source = source(listOf(event(outcome = ProtocolOutcome.FAILURE,
            source = ProtocolFailureSource.INJECTED, fault = "FAULT_SIGN_REJECT", error = -3)))
        val report = build(source)
        val directory = temporary.newFolder("cache")
        val writer = DiagnosticReportCacheWriter(directory)
        val markdown = writer.write(report, DiagnosticReportFormat.MARKDOWN)
        val json = writer.write(report, DiagnosticReportFormat.JSON)
        assertEquals("diagnostic_reports", requireNotNull(markdown.parentFile).name)
        assertEquals(requireNotNull(markdown.parentFile).canonicalFile,
            requireNotNull(json.parentFile).canonicalFile)
        assertTrue(markdown.name.matches(Regex("mwa-lab-[a-f0-9-]+-[0-9]+\\.md")))
        assertEquals(MarkdownDiagnosticReportRenderer().render(report), markdown.readText())
        assertEquals(JsonDiagnosticReportRenderer().render(report), json.readText())
        assertEquals("ERROR_NOT_SIGNED", JSONObject(json.readText())
            .getJSONArray("events").getJSONObject(0).getString("protocol_error_name"))
        val summary = DiagnosticReportSummaryRenderer().render(report)
        listOf(markdown.readText(), json.readText(), summary).forEach {
            assertTrue(it.contains("FAULT_SIGN_REJECT"))
            assertTrue(it.contains("ERROR_NOT_SIGNED"))
            assertTrue(it.contains("INJECTED"))
        }
        assertEquals(2, source.reads)
    }

    @Test fun successfulReportFocusSkipsTrailingDeauthorize() {
        val report = build(source(listOf(event(1), event(2).copy(
            method = ProtocolMethod.DEAUTHORIZE, requestSummary = emptyMap(),
            responseSummary = emptyMap()))))
        val markdown = MarkdownDiagnosticReportRenderer().render(report)
        val summary = DiagnosticReportSummaryRenderer().render(report)
        assertTrue(markdown.contains("Method to compare: SIGN"))
        assertFalse(markdown.contains("Method to compare: DEAUTHORIZE"))
        assertTrue(summary.contains("Method: SIGN_AND_SEND_TRANSACTIONS"))
        assertFalse(summary.contains("Method: DEAUTHORIZE"))
        assertTrue(summary.contains("Failure source: NONE"))
    }

    @Test fun hostileSentinelsNeverReachModelFilesOrClipboard() {
        val sentinels = listOf("raw-auth-secret-SENTINEL", "private-key-secret-SENTINEL",
            "seed-secret-SENTINEL", "association-token-secret-SENTINEL",
            "raw-message-secret-SENTINEL", "raw-transaction-secret-SENTINEL",
            "signature-secret-SENTINEL")
        val source = source(listOf(event(request = sentinels.mapIndexed { index, value ->
            "unknown_$index" to value }.toMap() + mapOf("result" to sentinels.first()))),
            label = sentinels[1])
        val report = build(source)
        val writer = DiagnosticReportCacheWriter(temporary.newFolder("hostile-cache"))
        val outputs = listOf(report.toString(), MarkdownDiagnosticReportRenderer().render(report),
            JsonDiagnosticReportRenderer().render(report),
            writer.write(report, DiagnosticReportFormat.MARKDOWN).readText(),
            writer.write(report, DiagnosticReportFormat.JSON).readText(),
            DiagnosticReportSummaryRenderer().render(report))
        outputs.forEach { output -> sentinels.forEach { sentinel ->
            assertFalse("Leaked $sentinel", output.contains(sentinel))
        } }
    }

    @Test fun cacheFilenameCannotContainSessionPathAndPartialSummaryIsTruthful() {
        val report = build(source(listOf(event(fault = "FAULT_DELAY_5S")), open = true))
        val unsafeId = report.copy(session = report.session.copy(sessionId = "<redacted-id>"))
        val writer = DiagnosticReportCacheWriter(temporary.newFolder("path-cache"))
        val file = writer.write(unsafeId, DiagnosticReportFormat.JSON)
        assertEquals(writer.reportDirectory().canonicalFile, file.canonicalFile.parentFile)
        assertFalse(file.name.contains(".."))
        val summary = DiagnosticReportSummaryRenderer().render(report)
        assertTrue(summary.contains("PARTIAL"))
        assertTrue(summary.contains("This session may still receive additional diagnostic evidence."))
        assertTrue(summary.contains("FAULT_DELAY_5S"))
        assertTrue(summary.contains("Failure source: NONE"))
    }

    private fun fixture(name: String, index: Int): TransactionSummary {
        val hex = requireNotNull(javaClass.classLoader!!.getResourceAsStream(name))
            .bufferedReader().use { it.readText().trim() }
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return TransactionInspector().inspect(bytes, index, TransactionDiagnosticBinding(id, "$id:1"))
    }
    private fun simulation(index: Int, attempt: Int, outcome: SimulationOutcome): SimulationResult {
        val source = when (outcome) {
            SimulationOutcome.PASS -> ProtocolFailureSource.NONE
            SimulationOutcome.FAIL -> ProtocolFailureSource.SIMULATION
            SimulationOutcome.UNAVAILABLE -> ProtocolFailureSource.RPC_NETWORK
        }
        return SimulationResult("sim-$index-$attempt", SimulationTargetRef(id, "$id:1", "request", index,
            "a".repeat(64)), attempt, 100, 105, 5, outcome, source, "processed",
            error = if (outcome == SimulationOutcome.FAIL) SimulationErrorSummary(SimulationErrorKind.BLOCKHASH_NOT_FOUND) else null,
            availabilityReason = if (outcome == SimulationOutcome.UNAVAILABLE) SimulationAvailabilityReason.IO else null,
            logs = BoundedLogs(if (outcome == SimulationOutcome.UNAVAILABLE) emptyList() else listOf("Program success"), false))
    }
}
