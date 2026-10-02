package dev.mwalab.report

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Grants one temporary read-only content URI for an already-rendered cache artifact. */
class DiagnosticReportShareIntentFactory(private val context: Context) {
    fun create(file: File, format: DiagnosticReportFormat): Intent {
        val directory = File(context.cacheDir, DiagnosticReportCacheWriter.DIRECTORY_NAME).canonicalFile
        require(file.canonicalFile.parentFile == directory && file.isFile)
        require(file.extension == format.extension && file.length() in 1..ReportLimits.MAX_RENDERED_BYTES.toLong())
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        require(uri.scheme == "content" && uri.authority == context.packageName + AUTHORITY_SUFFIX)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Sanitized diagnostic report", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share sanitized diagnostic report").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object { const val AUTHORITY_SUFFIX = ".diagnosticreports" }
}
