package dev.mwalab.report

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DiagnosticReportArtifact(
    val sessionId: String,
    val file: File,
    val format: DiagnosticReportFormat,
    val completeness: ReportCompleteness,
    val truncated: Boolean,
)
data class DiagnosticReportClipboardSummary(
    val sessionId: String,
    val text: String,
    val completeness: ReportCompleteness,
    val truncated: Boolean,
)

/** Reads persisted evidence afresh for each action; only cache artifacts are written. */
class DiagnosticReportExportUseCase(
    private val snapshots: DiagnosticReportSnapshotAssembler,
    private val cacheWriter: DiagnosticReportCacheWriter,
    private val summaryRenderer: DiagnosticReportSummaryRenderer = DiagnosticReportSummaryRenderer(),
) {
    suspend fun export(sessionId: String, format: DiagnosticReportFormat): DiagnosticReportArtifact =
        withContext(Dispatchers.IO) {
            val report = snapshots.build(sessionId) ?: throw ReportUnavailableException()
            DiagnosticReportArtifact(sessionId, cacheWriter.write(report, format), format,
                report.session.completeness, report.truncated)
        }

    suspend fun copySummary(sessionId: String): DiagnosticReportClipboardSummary = withContext(Dispatchers.IO) {
        val report = snapshots.build(sessionId) ?: throw ReportUnavailableException()
        DiagnosticReportClipboardSummary(sessionId, summaryRenderer.render(report), report.session.completeness, report.truncated)
    }
}

class ReportUnavailableException : IllegalStateException("Persisted diagnostic session unavailable")
