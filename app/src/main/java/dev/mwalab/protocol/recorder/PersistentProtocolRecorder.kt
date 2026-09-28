package dev.mwalab.protocol.recorder

import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolFailureSource
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.ProtocolOutcome
import dev.mwalab.security.DiagnosticSanitizer
import dev.mwalab.session.SessionId
import dev.mwalab.session.SessionRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.util.Collections
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Canonical Phase 3 protocol diagnostic recorder.
 *
 * The recorder owns event identity, request-start sequence allocation, timing,
 * immutable session binding and terminal-event persistence. It does not own or
 * influence wallet authorization, approval, signing, RPC, or MWA responses.
 */
class PersistentProtocolRecorder(
    private val sessionRepository: SessionRepository,
    private val clock: EventClock = SystemEventClock,
) : ProtocolRecorder {
    private val mutex = Mutex()
    private val lastSequenceBySession = mutableMapOf<SessionId, Long>()
    private val pendingByEventId = linkedMapOf<String, ProtocolEventHandle>()
    private val settlingByEventId = mutableMapOf<String, Settlement>()
    private val closedSessionSummaries = mutableMapOf<SessionId, Map<String, String>>()
    private val completedByEventId = mutableMapOf<String, ProtocolRecorder.CompletionResult>()

    override suspend fun begin(
        sessionId: SessionId,
        method: ProtocolMethod,
        requestSummary: Map<String, String>,
    ): ProtocolEventHandle {
        val (handle, closedSummary) = mutex.withLock {
            require(sessionId.isNotBlank() && sessionId != "unknown") {
                "Protocol recording requires an assigned session"
            }
            require(method != ProtocolMethod.GET_CAPABILITIES) {
                "GET_CAPABILITIES is not observable through pinned walletlib"
            }
            val sequence = Math.addExact(lastSequenceBySession[sessionId] ?: 0L, 1L)
            val eventId = "$sessionId:$sequence"
            check(eventId !in pendingByEventId && eventId !in completedByEventId) {
                "Protocol event identity collision"
            }
            val handle = ProtocolEventHandle(
                sessionId = sessionId,
                eventId = eventId,
                sequence = sequence,
                method = method,
                startedAtEpochMillis = clock.nowEpochMillis().coerceAtLeast(0L),
                requestSummary = safeSummary(requestSummary),
            )
            lastSequenceBySession[sessionId] = sequence
            pendingByEventId[eventId] = handle
            handle to closedSessionSummaries[sessionId]
        }
        // A callback racing diagnostic close still belongs to its original
        // session. It cannot leave a newly pending handle after close's snapshot.
        if (closedSummary != null) {
            complete(handle, ProtocolOutcome.CANCELLED, failureSource = ProtocolFailureSource.UNKNOWN,
                responseSummary = closedSummary)
        }
        return handle
    }

    override suspend fun complete(
        handle: ProtocolEventHandle,
        outcome: ProtocolOutcome,
        protocolErrorCode: Int?,
        failureSource: ProtocolFailureSource,
        responseSummary: Map<String, String>,
    ): ProtocolRecorder.CompletionResult {
        val claim = mutex.withLock {
            completedByEventId[handle.eventId]?.let {
                return@withLock CompletionClaim.Already(it.event)
            }
            settlingByEventId[handle.eventId]?.let {
                return@withLock CompletionClaim.Already(it.event)
            }

            val pending = pendingByEventId[handle.eventId]
                ?: throw IllegalArgumentException("Unknown or non-pending protocol event handle")
            require(pending == handle) { "Protocol event handle does not match recorder state" }

            val event = ProtocolEvent(
                    sessionId = handle.sessionId,
                    eventId = handle.eventId,
                    sequence = handle.sequence,
                    method = handle.method,
                    startedAtEpochMillis = handle.startedAtEpochMillis,
                    completedAtEpochMillis = clock.nowEpochMillis().coerceAtLeast(0L),
                    outcome = outcome,
                    protocolErrorCode = protocolErrorCode,
                    failureSource = failureSource,
                    requestSummary = handle.requestSummary.toMap(),
                    responseSummary = safeSummary(responseSummary),
                    injectedFaultId = null,
                    capabilityContext = null,
                )
            // Validate/snapshot response metadata before relinquishing pending
            // ownership. Invalid diagnostics must remain settleable on close.
            pendingByEventId.remove(handle.eventId)
            settlingByEventId[handle.eventId] = Settlement(event, CompletableDeferred())
            CompletionClaim.First(event)
        }

        if (claim is CompletionClaim.Already) {
            return ProtocolRecorder.CompletionResult.AlreadyCompleted(claim.event)
        }

        val event = (claim as CompletionClaim.First).event
        // Once a terminal result is claimed, caller cancellation must not leave
        // its persistence/close waiters orphaned. This has no protocol authority.
        return withContext(NonCancellable) {
            val result = try {
                sessionRepository.recordProtocolEvent(event)
                ProtocolRecorder.CompletionResult.Persisted(event)
            } catch (_: Exception) {
                ProtocolRecorder.CompletionResult.PersistenceFailed(event)
            }
            mutex.withLock {
                completedByEventId[handle.eventId] = result
                settlingByEventId.remove(handle.eventId)?.finished?.complete(result)
            }
            result
        }
    }

    override suspend fun cancelPendingForSession(
        sessionId: SessionId,
        responseSummary: Map<String, String>,
    ): List<ProtocolRecorder.CompletionResult> {
        val safeCloseSummary = safeSummary(responseSummary)
        val handles = mutex.withLock {
            if (!closedSessionSummaries.containsKey(sessionId)) {
                closedSessionSummaries[sessionId] = safeCloseSummary
            }
            pendingByEventId.values.filter { it.sessionId == sessionId }.sortedBy { it.sequence }
        }
        val results = handles.map { handle ->
            complete(handle, ProtocolOutcome.CANCELLED, failureSource = ProtocolFailureSource.UNKNOWN,
                responseSummary = safeCloseSummary)
        }
        // A concurrent complete may have claimed a handle before close's
        // snapshot. Wait outside the allocator lock before publishing closure.
        val writes = mutex.withLock {
            settlingByEventId.values.filter { it.event.sessionId == sessionId }.map { it.finished }
        }
        writes.forEach { it.await() }
        return results
    }

    private data class Settlement(
        val event: ProtocolEvent,
        val finished: CompletableDeferred<ProtocolRecorder.CompletionResult>,
    )

    private fun safeSummary(fields: Map<String, String>): Map<String, String> {
        require(fields.size <= MAX_SUMMARY_FIELDS) { "Protocol summary has too many fields" }
        val normalized = linkedMapOf<String, String>()
        for ((rawKey, rawValue) in fields) {
            val key = rawKey.trim()
            require(key.isNotEmpty() && key.length <= MAX_SUMMARY_KEY_LENGTH) {
                "Protocol summary key is invalid"
            }
            require(isApprovedSummaryKey(key)) {
                "Protocol summary key is not approved for persistence"
            }
            require(key !in normalized) { "Protocol summary contains duplicate normalized keys" }
            normalized[key] = when (key) {
                "chain" -> if (rawValue in SAFE_CHAINS) rawValue else "<unsupported>"
                "commitment" -> if (rawValue in SAFE_COMMITMENTS) rawValue else "<unsupported>"
                else -> rawValue
            }
        }
        return Collections.unmodifiableMap(DiagnosticSanitizer.sanitizeFields(normalized).toMap())
    }

    private fun isApprovedSummaryKey(key: String): Boolean =
        key in APPROVED_SUMMARY_KEYS || PAYLOAD_METADATA_KEY.matches(key)

    private sealed interface CompletionClaim {
        data class First(val event: ProtocolEvent) : CompletionClaim
        data class Already(val event: ProtocolEvent) : CompletionClaim
    }

    private companion object {
        const val MAX_SUMMARY_FIELDS = 64
        const val MAX_SUMMARY_KEY_LENGTH = 64

        val SAFE_CHAINS = setOf("solana:devnet", "<missing>")
        val SAFE_COMMITMENTS = setOf("processed", "confirmed", "finalized", "<default>")
        val PAYLOAD_METADATA_KEY = Regex("payload_[0-9]+_(sha256|length)")
        val APPROVED_SUMMARY_KEYS = setOf(
            "address_count",
            "authorization_reference",
            "authorization_state",
            "chain",
            "commitment",
            "commitment_verified",
            "max_retries_present",
            "method",
            "min_context_slot_present",
            "network_validation",
            "not_submitted_count",
            "payload_count",
            "public_account",
            "reason",
            "requested_address_count",
            "requested_feature_count",
            "result",
            "rpc_network",
            "sign_in_requested",
            "signed_payload_count",
            "skip_preflight",
            "submitted_count",
            "transaction_version",
            "wait_for_commitment",
        )
    }
}
