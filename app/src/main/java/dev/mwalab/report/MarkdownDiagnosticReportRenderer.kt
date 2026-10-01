package dev.mwalab.report

import java.nio.charset.StandardCharsets

/** Stable UTF-8/LF issue-attachment rendering of one canonical report. */
class MarkdownDiagnosticReportRenderer {
    fun render(report: DiagnosticReport): String {
        ReportOutputBounds.validate(report)
        val out = buildString {
            appendLine("# MWA Lab Diagnostic Report")
            appendLine()
            appendLine("## Report Metadata")
            line("Format", report.format)
            line("Version", report.version)
            line("Generated at (epoch ms)", report.generatedAtEpochMillis)
            line("Truncated", report.truncated)
            appendLine()
            appendLine("## Environment")
            line("Application", report.application.name)
            line("walletlib", report.application.walletlibVersion)
            line("Cluster", report.environment.cluster)
            appendLine()
            appendLine("## Session")
            line("Session ID", report.session.sessionId)
            line("dApp label", report.session.dappDisplayName)
            line("Started at (epoch ms)", report.session.startedAtEpochMillis)
            line("Completed at (epoch ms)", report.session.completedAtEpochMillis)
            line("Duration (ms)", report.session.durationMillis)
            line("Status", report.session.status.name)
            line("Close reason", report.session.closeReason)
            line("Completeness", report.session.completeness.name)
            appendLine()
            appendLine("## Capability Snapshot")
            val capability = report.capabilities
            if (capability == null) appendLine("No persisted capability snapshot.") else {
                line("Source", capability.source)
                line("Captured at (epoch ms)", capability.capturedAtEpochMillis)
                line("Max transactions/request", capability.maxTransactionsPerSigningRequest)
                line("Max messages/request", capability.maxMessagesPerSigningRequest)
                line("Supported transaction versions", capability.supportedTransactionVersions.joinToString())
                line("Optional features", capability.optionalFeatures.joinToString())
            }
            appendLine()
            appendLine("## Protocol Timeline")
            if (report.events.isEmpty()) appendLine("No observed protocol events.")
            for (e in report.events) {
                appendLine("### Event ${e.sequence}: ${e.method.name}")
                line("Event ID", e.eventId)
                line("Started at (epoch ms)", e.startedAtEpochMillis)
                line("Completed at (epoch ms)", e.completedAtEpochMillis)
                line("Duration (ms)", e.durationMillis)
                line("Outcome", e.outcome.name)
                line("Protocol error code", e.protocolErrorCode)
                line("Failure source", e.failureSource.name)
                line("Injected fault ID", e.injectedFaultId)
                if (e.injectedFaultId != null) appendLine("Intentional test condition applied; terminal source is shown separately.")
                fields("Request summary", e.requestSummary)
                fields("Response summary", e.responseSummary)
                fields("Capability context", e.capabilityContext)
                appendLine()
            }
            appendLine("## Failure Analysis")
            val failures = report.events.filter { it.outcome.name == "FAILURE" }
            if (failures.isEmpty()) appendLine("No terminal protocol failure was recorded.")
            failures.forEach { e ->
                appendLine("- Event ${e.sequence}: ${e.method.name}; source ${e.failureSource.name}; " +
                    "protocol error ${e.protocolErrorCode?.toString() ?: "unknown"}; " +
                    "fault ${e.injectedFaultId ?: "none"}.")
            }
            appendLine()
            appendLine("## Transaction Diagnostics")
            val txEvents = report.events.filter { it.transactions.isNotEmpty() }
            if (txEvents.isEmpty()) appendLine("No transaction diagnostics were recorded.")
            txEvents.forEach { e -> e.transactions.forEach { t ->
                appendLine("### Event ${e.sequence}, payload ${t.payloadIndex}")
                line("SHA-256", t.fingerprintSha256)
                line("Wire length", t.wireLength)
                line("Version", t.transactionVersion)
                line("Inspection", t.inspectionStatus.name)
                line("Signature count", t.signatureCount)
                line("Fee payer", t.feePayer)
                line("Instruction count", t.instructionCount)
                line("Limitations", t.limitations.joinToString())
                line("Parse error", t.error)
                t.instructions.forEach { i ->
                    appendLine("- Instruction ${i.index}: ${escape(i.programName ?: "Unknown program")} " +
                        "(${escape(i.programId)}); ${escape(i.decodedKind)}; " +
                        "exact amount ${escape(i.exactAmount ?: "unknown")}; " +
                        "source ${escape(i.source ?: "unknown")}; destination ${escape(i.destination ?: "unknown")}")
                }
            } }
            appendLine()
            appendLine("## Simulation Diagnostics")
            appendLine("Simulation is diagnostic evidence only; it is not a guarantee of submission success.")
            val simEvents = report.events.filter { it.simulations.isNotEmpty() }
            if (simEvents.isEmpty()) appendLine("No simulation attempts were recorded.")
            simEvents.forEach { e -> e.simulations.forEach { s ->
                appendLine("### Event ${e.sequence}, payload ${s.payloadIndex}, attempt ${s.attemptNumber}")
                line("Transaction SHA-256", s.transactionFingerprintSha256)
                line("Outcome", s.outcome.name)
                line("Failure source", s.failureSource.name)
                line("Duration (ms)", s.durationMillis)
                line("Commitment", s.commitment)
                line("Context slot", s.contextSlot)
                line("Error kind", s.errorKind?.name)
                line("Instruction index", s.instructionIndex)
                line("Instruction error kind", s.instructionErrorKind)
                line("Custom program error code", s.customProgramErrorCode)
                line("RPC error code", s.rpcErrorCode)
                line("Availability reason", s.availabilityReason?.name)
                line("Compute units", s.unitsConsumed)
                line("Logs truncated", s.logsTruncated)
                s.programLogs.forEach { appendLine("- Log: ${escape(it)}") }
            } }
            appendLine()
            appendLine("## Warnings")
            if (report.warnings.isEmpty()) appendLine("None.")
            report.warnings.forEach { appendLine("- ${escape(it.code)}: ${escape(it.message)}") }
            appendLine()
            appendLine("## Security Notice")
            appendLine("This report intentionally excludes private keys, seeds, authorization tokens, " +
                "association secrets, raw transaction/message payloads, and raw signatures.")
        }.replace("\r\n", "\n").replace('\r', '\n')
        require(out.toByteArray(StandardCharsets.UTF_8).size <= ReportLimits.MAX_RENDERED_BYTES) {
            "Diagnostic Markdown exceeds the report byte limit"
        }
        return out
    }

    private fun StringBuilder.line(label: String, value: Any?) {
        appendLine("- $label: ${escape(value?.toString() ?: "unknown")}")
    }

    private fun StringBuilder.fields(label: String, fields: Map<String, String>?) {
        appendLine("- $label:")
        if (fields.isNullOrEmpty()) appendLine("  - None recorded")
        else fields.toSortedMap().forEach { (key, value) -> appendLine("  - ${escape(key)}: ${escape(value)}") }
    }

    private fun escape(text: String): String = buildString {
        text.forEach { c -> when (c) {
            '\\', '`', '*', '_', '[', ']', '(', ')', '#', '|', '!', '~' -> { append('\\'); append(c) }
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '\n', '\r', '\t' -> append(' ')
            else -> if (c.isISOControl()) append(' ') else append(c)
        } }
    }
}
