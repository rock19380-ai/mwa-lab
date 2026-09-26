package dev.mwalab.protocol

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
