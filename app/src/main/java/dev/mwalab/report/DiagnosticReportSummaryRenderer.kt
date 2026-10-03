package dev.mwalab.report

import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.protocol.ProtocolMethod
import java.nio.charset.StandardCharsets

/** Concise clipboard projection of the same canonical report used by both file renderers. */
class DiagnosticReportSummaryRenderer {
    fun render(report: DiagnosticReport): String {
        ReportOutputBounds.validate(report)
        val focus = report.events.firstOrNull { it.outcome == ProtocolOutcome.FAILURE }
            ?: report.events.lastOrNull { it.method != ProtocolMethod.DEAUTHORIZE }
            ?: report.events.lastOrNull()
        val text = buildString {
            appendLine("MWA Lab Diagnostic Summary")
            appendLine()
            appendLine(report.session.dappDisplayName ?: "Unknown dApp")
            appendLine("Solana Devnet")
            appendLine("Association: ${report.session.associationMode.name}")
            appendLine("Identity: ${report.session.identityVerificationState.name}")
            appendLine("Session: ${report.session.status.name}")
            appendLine("Completeness: ${report.session.completeness.name}")
            appendLine("Events: ${report.events.size}${if (report.truncated) " (report truncated)" else ""}")
            if (focus != null) {
                appendLine()
                appendLine("Method: ${focus.method.name}")
                appendLine("Outcome: ${focus.outcome.name}")
                appendLine("Failure source: ${focus.failureSource.name}")
                appendLine("Protocol result: ${focus.protocolErrorName ?: "none recorded"}" +
                    (focus.protocolErrorCode?.let { " ($it)" } ?: ""))
                focus.injectedFaultId?.let {
                    appendLine("INTENTIONAL TEST CONDITION: $it")
                }
            }
            if (report.session.completeness == ReportCompleteness.PARTIAL) {
                appendLine("This session may still receive additional diagnostic evidence.")
            }
            appendLine()
            appendLine("No wallet secrets, auth tokens, raw payloads, or signatures included.")
        }
        require(text.toByteArray(StandardCharsets.UTF_8).size <= MAX_SUMMARY_BYTES)
        return text
    }

    companion object { const val MAX_SUMMARY_BYTES = 4096 }
}
