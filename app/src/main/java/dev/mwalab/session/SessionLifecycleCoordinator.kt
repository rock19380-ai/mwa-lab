package dev.mwalab.session

import dev.mwalab.protocol.recorder.EventClock
import dev.mwalab.protocol.recorder.ProtocolRecorder
import dev.mwalab.protocol.recorder.SystemEventClock
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ConcurrentHashMap

/**
 * Phase 3 session-lifecycle persistence boundary.
 *
 * This component owns diagnostic session creation/finalization and safe dApp
 * display-name persistence. It never owns wallet authorization, approval,
 * signing, RPC, or protocol responses.
 */
class SessionLifecycleCoordinator(
    private val sessionRepository: SessionRepository,
    private val protocolRecorder: ProtocolRecorder,
    private val clock: EventClock = SystemEventClock,
) {
    private val firstCloseReasonBySession = ConcurrentHashMap<SessionId, SessionCloseReason>()

    suspend fun createSession(sessionId: SessionId): PersistenceResult {
        val session = MwaSession(
            id = sessionId,
            startedAtEpochMillis = clock.nowEpochMillis().coerceAtLeast(0L),
        )
        return persist {
            sessionRepository.createSession(session)
        }
    }

    suspend fun updateDappIdentity(
        sessionId: SessionId,
        rawIdentityName: String?,
    ): PersistenceResult {
        val safeName = sanitizeDappDisplayName(rawIdentityName)
        if (safeName == null) return PersistenceResult.Skipped
        return persist {
            sessionRepository.updateDappIdentity(sessionId, safeName)
        }
    }

    suspend fun finishSession(
        sessionId: SessionId,
        closeReason: SessionCloseReason,
    ): PersistenceResult {
        val authoritativeReason = firstCloseReasonBySession.putIfAbsent(
            sessionId,
            closeReason,
        ) ?: closeReason

        // Settle recorder-owned handles first so a final SessionSummary never
        // needs to pretend a begun request completed successfully.
        runCatchingPreservingCancellation {
            protocolRecorder.cancelPendingForSession(
                sessionId = sessionId,
                responseSummary = mapOf(
                    "reason" to "session_closed_${authoritativeReason.name.lowercase()}",
                ),
            )
        }

        return persist {
            sessionRepository.finishSession(
                sessionId = sessionId,
                completedAtEpochMillis = clock.nowEpochMillis().coerceAtLeast(0L),
                closeReason = authoritativeReason,
            )
        }
    }

    internal fun sanitizeDappDisplayName(rawIdentityName: String?): String? {
        val trimmed = rawIdentityName?.trim()?.takeIf(String::isNotBlank) ?: return null
        if (trimmed.any { it.isISOControl() }) return null
        return trimmed.take(MwaSession.MAX_DAPP_DISPLAY_NAME_LENGTH)
    }

    private suspend fun persist(block: suspend () -> Unit): PersistenceResult =
        try {
            block()
            PersistenceResult.Persisted
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            PersistenceResult.PersistenceFailed
        }

    private suspend fun runCatchingPreservingCancellation(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Recorder persistence is diagnostic-only; a failed cancellation
            // settlement must never acquire wallet/protocol authority.
        }
    }

    sealed interface PersistenceResult {
        data object Persisted : PersistenceResult
        data object PersistenceFailed : PersistenceResult
        data object Skipped : PersistenceResult
    }
}
