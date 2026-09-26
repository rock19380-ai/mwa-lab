package dev.mwalab.protocol

import java.util.concurrent.CopyOnWriteArrayList

data class ProtocolEvidence(
    val method: ProtocolMethod,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val outcome: ProtocolOutcome,
    val protocolErrorCode: Int? = null,
    val failureSource: ProtocolFailureSource = ProtocolFailureSource.NONE,
    val requestSummary: Map<String, String> = emptyMap(),
    val responseSummary: Map<String, String> = emptyMap(),
) {
    val durationMillis: Long
        get() = (completedAtEpochMillis - startedAtEpochMillis).coerceAtLeast(0)
}

fun interface ProtocolEvidenceSink {
    fun record(event: ProtocolEvidence)
}

/**
 * Minimal process-local Phase 1 evidence seam.
 *
 * This is intentionally not the Phase 3 recorder: no Room history, no export
 * bundle, no timeline model, and no raw authorization/secret material.
 */
object ProtocolEvidenceStore : ProtocolEvidenceSink {
    private val events = CopyOnWriteArrayList<ProtocolEvidence>()

    override fun record(event: ProtocolEvidence) {
        events += event
    }

    fun snapshot(): List<ProtocolEvidence> = events.toList()

    fun resetForTest() {
        events.clear()
    }
}
