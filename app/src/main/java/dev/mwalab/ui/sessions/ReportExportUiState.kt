package dev.mwalab.ui.sessions

import dev.mwalab.report.DiagnosticReportArtifact
import dev.mwalab.report.DiagnosticReportFormat
import dev.mwalab.report.ReportCompleteness

sealed interface ReportExportUiState {
    data object Idle : ReportExportUiState
    data object Building : ReportExportUiState
    data class Sharing(val format: DiagnosticReportFormat, val completeness: ReportCompleteness,
        val truncated: Boolean) : ReportExportUiState
    data class Ready(val format: DiagnosticReportFormat, val completeness: ReportCompleteness,
        val truncated: Boolean) : ReportExportUiState
    data class Copied(val completeness: ReportCompleteness, val truncated: Boolean) : ReportExportUiState
    data object Error : ReportExportUiState
}

sealed interface ReportExportEffect {
    data class Share(val artifact: DiagnosticReportArtifact) : ReportExportEffect
    data class Copy(val sessionId: String, val text: String, val completeness: ReportCompleteness,
        val truncated: Boolean) : ReportExportEffect
}
