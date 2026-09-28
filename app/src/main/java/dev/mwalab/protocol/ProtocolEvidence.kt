package dev.mwalab.protocol

import dev.mwalab.session.SessionId
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The single terminal event model, also named [ProtocolEvent] by Phase 3.
 * Summaries must already be sanitized; this compatibility model is not a raw
 * request sanitizer or a persistence entry point. See the recorder design.
 */
data class ProtocolEvidence(
    val sessionId: SessionId = "unknown",
    val eventId: EventId = "unknown:0",
    val sequence: Long = 0,
    val method: ProtocolMethod,
    val startedAtEpochMillis: Long,
    val completedAtEpochMillis: Long,
    val outcome: ProtocolOutcome,
    val protocolErrorCode: Int? = null,
    val failureSource: ProtocolFailureSource = ProtocolFailureSource.NONE,
    val requestSummary: Map<String, String> = emptyMap(),
    val responseSummary: Map<String, String> = emptyMap(),
    // Reserved metadata only. Phase 3 producers leave both null.
    val injectedFaultId: String? = null,
    val capabilityContext: Map<String, String>? = null,
) {
    val durationMillis: Long
        get() = (completedAtEpochMillis - startedAtEpochMillis).coerceAtLeast(0)
}

fun interface ProtocolEvidenceSink {
    fun record(event: ProtocolEvidence)
}

/**
 * Process-local structured protocol-event seam. Phase 3 will persist it.
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
