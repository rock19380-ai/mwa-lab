package dev.mwalab.report

import java.nio.charset.StandardCharsets

/** Fixed JSON v1 projection of the canonical safe report; no repository reads. */
class JsonDiagnosticReportRenderer {
    fun render(report: DiagnosticReport): String {
        ReportOutputBounds.validate(report)
        require(report.format == DiagnosticReport.FORMAT && report.version == DiagnosticReport.VERSION)
        val json = encode(linkedMapOf(
            "format" to report.format,
            "version" to report.version,
            "generated_at" to report.generatedAtEpochMillis,
            "application" to linkedMapOf("name" to report.application.name,
                "walletlib_version" to report.application.walletlibVersion),
            "environment" to linkedMapOf("cluster" to report.environment.cluster),
            "session" to linkedMapOf(
                "session_id" to report.session.sessionId,
                "dapp_display_name" to report.session.dappDisplayName,
                "started_at" to report.session.startedAtEpochMillis,
                "completed_at" to report.session.completedAtEpochMillis,
                "duration_ms" to report.session.durationMillis,
                "status" to report.session.status.name,
                "close_reason" to report.session.closeReason,
                "completeness" to report.session.completeness.name),
            "capabilities" to report.capabilities?.let { c -> linkedMapOf(
                "captured_at" to c.capturedAtEpochMillis, "source" to c.source,
                "max_transactions_per_signing_request" to c.maxTransactionsPerSigningRequest,
                "max_messages_per_signing_request" to c.maxMessagesPerSigningRequest,
                "supported_transaction_versions" to c.supportedTransactionVersions,
                "optional_features" to c.optionalFeatures) },
            "events" to report.events.map(::event),
            "warnings" to report.warnings.map { linkedMapOf("code" to it.code, "message" to it.message) },
            "truncated" to report.truncated))
        require(json.toByteArray(StandardCharsets.UTF_8).size <= ReportLimits.MAX_RENDERED_BYTES) {
            "Diagnostic JSON exceeds the report byte limit"
        }
        return json
    }

    private fun event(e: ReportProtocolEvent): Map<String, Any?> = linkedMapOf(
        "event_id" to e.eventId, "sequence" to e.sequence, "method" to e.method.name,
        "started_at" to e.startedAtEpochMillis, "completed_at" to e.completedAtEpochMillis,
        "duration_ms" to e.durationMillis, "outcome" to e.outcome.name,
        "protocol_error_code" to e.protocolErrorCode, "protocol_error_name" to e.protocolErrorName,
        "failure_source" to e.failureSource.name,
        "injected_fault_id" to e.injectedFaultId, "request_summary" to e.requestSummary.toSortedMap(),
        "response_summary" to e.responseSummary.toSortedMap(), "capability_context" to e.capabilityContext?.toSortedMap(),
        "transactions" to e.transactions.map { t -> linkedMapOf(
            "payload_index" to t.payloadIndex, "fingerprint_sha256" to t.fingerprintSha256,
            "wire_length" to t.wireLength, "transaction_version" to t.transactionVersion,
            "inspection_status" to t.inspectionStatus.name, "signature_count" to t.signatureCount,
            "fee_payer" to t.feePayer, "instruction_count" to t.instructionCount,
            "instructions" to t.instructions.map { i -> linkedMapOf(
                "index" to i.index, "program_id" to i.programId, "program_name" to i.programName,
                "decoded_kind" to i.decodedKind, "exact_amount" to i.exactAmount,
                "source" to i.source, "destination" to i.destination) },
            "limitations" to t.limitations, "error" to t.error) },
        "simulations" to e.simulations.map { s -> linkedMapOf(
            "payload_index" to s.payloadIndex, "attempt_number" to s.attemptNumber,
            "transaction_fingerprint_sha256" to s.transactionFingerprintSha256,
            "started_at" to s.startedAtEpochMillis, "duration_ms" to s.durationMillis,
            "outcome" to s.outcome.name, "failure_source" to s.failureSource.name,
            "commitment" to s.commitment, "context_slot" to s.contextSlot,
            "error_kind" to s.errorKind?.name, "instruction_index" to s.instructionIndex,
            "instruction_error_kind" to s.instructionErrorKind,
            "custom_program_error_code" to s.customProgramErrorCode,
            "rpc_error_code" to s.rpcErrorCode, "availability_reason" to s.availabilityReason?.name,
            "units_consumed" to s.unitsConsumed, "program_logs" to s.programLogs,
            "logs_truncated" to s.logsTruncated) })

    private fun encode(value: Any?): String = when (value) {
        null -> "null"
        is String -> quote(value)
        is Number, is Boolean -> value.toString()
        is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (k, v) ->
            quote(k as String) + ":" + encode(v) }
        is List<*> -> value.joinToString(",", "[", "]") { encode(it) }
        else -> error("Unsupported report JSON value")
    }

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c -> when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 0x20 || c == '\u2028' || c == '\u2029' ||
                Character.isSurrogate(c)) append("\\u%04x".format(c.code)) else append(c)
        } }
        append('"')
    }
}
