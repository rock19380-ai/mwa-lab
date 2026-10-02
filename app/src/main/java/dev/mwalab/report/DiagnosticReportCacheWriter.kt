package dev.mwalab.report

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class DiagnosticReportFormat(val extension: String, val mimeType: String) {
    MARKDOWN("md", "text/plain"),
    JSON("json", "application/json");

    fun render(report: DiagnosticReport): String = when (this) {
        MARKDOWN -> MarkdownDiagnosticReportRenderer().render(report)
        JSON -> JsonDiagnosticReportRenderer().render(report)
    }
}

/** Disposable app-private report artifacts. Never writes protocol or Room state. */
class DiagnosticReportCacheWriter(
    private val cacheRoot: File,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private var lastNameMillis = 0L

    @Synchronized
    fun write(report: DiagnosticReport, format: DiagnosticReportFormat): File {
        val bytes = format.render(report).toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= ReportLimits.MAX_RENDERED_BYTES)
        val directory = reportDirectory()
        cleanup(directory)
        val token = fileToken(report.session.sessionId)
        var stamp = maxOf(nowEpochMillis().coerceAtLeast(0), lastNameMillis + 1)
        var target = target(directory, token, stamp, format)
        var collisions = 0
        while (target.exists() && collisions++ < MAX_NAME_COLLISIONS) {
            stamp++
            target = target(directory, token, stamp, format)
        }
        if (target.exists()) throw IOException("Report cache filename unavailable")
        lastNameMillis = stamp
        val temporary = File.createTempFile("mwa-lab-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            if (!temporary.renameTo(target)) throw IOException("Report cache finalization failed")
            cleanup(directory)
            return target
        } finally {
            temporary.delete()
        }
    }

    fun reportDirectory(): File {
        val root = cacheRoot.canonicalFile
        val directory = File(root, DIRECTORY_NAME)
        if (!directory.isDirectory && !directory.mkdir()) throw IOException("Report cache unavailable")
        if (directory.canonicalFile.parentFile != root) throw IOException("Unsafe report cache path")
        return directory
    }

    private fun target(directory: File, token: String, stamp: Long, format: DiagnosticReportFormat): File =
        File(directory, "mwa-lab-$token-${stamp.toString().padStart(13, '0')}.${format.extension}").also {
            if (it.canonicalFile.parentFile != directory.canonicalFile) throw IOException("Unsafe report filename")
        }

    private fun fileToken(sessionId: String): String {
        val canonicalUuid = Regex("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}")
        if (canonicalUuid.matches(sessionId)) return sessionId
        val hash = MessageDigest.getInstance("SHA-256").digest(sessionId.toByteArray(StandardCharsets.UTF_8))
        return hash.take(8).joinToString("") { "%02x".format(it) }
    }

    /** Only our own named cache artifacts are eligible, with bounded deletion work per call. */
    private fun cleanup(directory: File) {
        val files = directory.listFiles().orEmpty()
        val cutoff = nowEpochMillis().coerceAtLeast(0) - MAX_AGE_MILLIS
        val finalFiles = files.filter { it.isFile && FINAL_NAME.matches(it.name) }
            .sortedWith(compareByDescending<File> { it.lastModified() }.thenByDescending { it.name })
        val expired = finalFiles.filterIndexed { index, file -> index >= MAX_FINAL_FILES || file.lastModified() < cutoff }
        val temporary = files.filter { it.isFile && it.name.startsWith("mwa-lab-") &&
            it.name.endsWith(".tmp") && it.lastModified() < cutoff }
        (expired + temporary).take(MAX_DELETIONS_PER_WRITE).forEach(File::delete)
    }

    companion object {
        const val DIRECTORY_NAME = "diagnostic_reports"
        const val MAX_FINAL_FILES = 64
        private const val MAX_NAME_COLLISIONS = 16
        private const val MAX_DELETIONS_PER_WRITE = 16
        private const val MAX_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000
        private val FINAL_NAME = Regex("mwa-lab-(?:[0-9a-f-]{36}|[0-9a-f]{16})-[0-9]{13,16}\\.(?:md|json)")
    }
}
