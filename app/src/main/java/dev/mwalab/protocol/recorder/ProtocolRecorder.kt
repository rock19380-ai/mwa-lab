package dev.mwalab.protocol.recorder

import dev.mwalab.faults.FaultId
import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.session.SessionId

interface ProtocolRecorder {
    suspend fun begin(
        sessionId: SessionId,
        method: ProtocolMethod,
        requestSummary: Map<String, String> = emptyMap(),
    ): ProtocolEventHandle

    /**
     * Attach evidence only when a synthetic condition is actually applied.
     * Implementations may reject completed, unknown, or conflicting handles.
     */
    suspend fun markInjectedFault(handle: ProtocolEventHandle, faultId: FaultId) {
        throw UnsupportedOperationException("Fault annotation is not implemented")
    }

    suspend fun complete(
        handle: ProtocolEventHandle,
        outcome: ProtocolOutcome,
        protocolErrorCode: Int? = null,
        failureSource: ProtocolFailureSource = ProtocolFailureSource.NONE,
        responseSummary: Map<String, String> = emptyMap(),
    ): CompletionResult

    /**
     * Settle still-pending diagnostic handles for [sessionId]. This never sends
     * a protocol response and never grants stale requests authority.
     */
    suspend fun cancelPendingForSession(
        sessionId: SessionId,
        responseSummary: Map<String, String> = mapOf("reason" to "session_closed"),
    ): List<CompletionResult>

    sealed interface CompletionResult {
        val event: ProtocolEvent

        data class Persisted(override val event: ProtocolEvent) : CompletionResult

        data class PersistenceFailed(override val event: ProtocolEvent) : CompletionResult

        data class AlreadyCompleted(override val event: ProtocolEvent) : CompletionResult
    }
}
