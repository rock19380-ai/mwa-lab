package dev.mwalab.approval

import dev.mwalab.mwa.association.AssociationMode
import dev.mwalab.mwa.association.DappVerificationState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import java.util.UUID

sealed interface AuthorizationDecision {
    data object Approved : AuthorizationDecision
    data object Rejected : AuthorizationDecision
    data object Cancelled : AuthorizationDecision
    data object Expired : AuthorizationDecision
    data object Busy : AuthorizationDecision
}

data class AuthorizationApprovalRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val generation: Long,
    val associationMode: AssociationMode,
    val dappDisplayName: String?,
    val claimedUriDisplay: String?,
    val callerPackage: String?,
    val verificationState: DappVerificationState,
    val chain: String,
    val requestedFeatures: List<String>,
    val requestedAddressCount: Int,
) {
    init {
        require(sessionId.isNotBlank()) { "Authorization approval requires a session" }
        require(generation >= 0) { "Authorization approval generation must be non-negative" }
        require(chain.isNotBlank() && chain.length <= MAX_TEXT_LENGTH) { "Authorization chain is invalid" }
        require(dappDisplayName == null || isSafeDisplayText(dappDisplayName))
        require(claimedUriDisplay == null || isSafeDisplayText(claimedUriDisplay))
        require(callerPackage == null || isSafeDisplayText(callerPackage))
        require(requestedAddressCount >= 0)
        require(requestedFeatures.size <= MAX_FEATURES)
        require(requestedFeatures.all(::isSafeDisplayText))
    }

    companion object {
        private const val MAX_TEXT_LENGTH = 256
        private const val MAX_FEATURES = 32

        private fun isSafeDisplayText(value: String): Boolean =
            value.isNotBlank() &&
                value == value.trim() &&
                value.length <= MAX_TEXT_LENGTH &&
                value.none { it.isISOControl() }
    }
}

sealed interface AuthorizationApprovalState {
    data object Idle : AuthorizationApprovalState
    data class Pending(val request: AuthorizationApprovalRequest) : AuthorizationApprovalState
}

/**
 * Single-flight human-consent boundary for authorize requests.
 *
 * This coordinator never owns walletlib requests, auth tokens, signing keys or
 * protocol completion. It only binds one sanitized approval request to one
 * decision and rejects stale request IDs.
 */
class AuthorizationApprovalCoordinator(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    private data class PendingDecision(
        val request: AuthorizationApprovalRequest,
        val result: CompletableDeferred<AuthorizationDecision>,
    )

    private val lock = Any()
    private val mutableState = MutableStateFlow<AuthorizationApprovalState>(AuthorizationApprovalState.Idle)
    private var pending: PendingDecision? = null

    val state: StateFlow<AuthorizationApprovalState> = mutableState.asStateFlow()

    suspend fun requestApproval(request: AuthorizationApprovalRequest): AuthorizationDecision {
        val deferred = CompletableDeferred<AuthorizationDecision>()
        synchronized(lock) {
            if (pending != null) return AuthorizationDecision.Busy
            pending = PendingDecision(request, deferred)
            mutableState.value = AuthorizationApprovalState.Pending(request)
        }

        return try {
            withTimeout(timeoutMillis) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            AuthorizationDecision.Expired
        } finally {
            synchronized(lock) {
                if (pending?.request?.requestId == request.requestId) {
                    pending = null
                    mutableState.value = AuthorizationApprovalState.Idle
                }
            }
        }
    }

    fun approve(requestId: String): Boolean = complete(requestId, AuthorizationDecision.Approved)

    fun reject(requestId: String): Boolean = complete(requestId, AuthorizationDecision.Rejected)

    fun cancelPending() {
        val target = synchronized(lock) { pending }
        target?.result?.complete(AuthorizationDecision.Cancelled)
    }

    fun cancelSession(sessionId: String, generation: Long) {
        val target = synchronized(lock) {
            pending?.takeIf {
                it.request.sessionId == sessionId && it.request.generation == generation
            }
        }
        target?.result?.complete(AuthorizationDecision.Cancelled)
    }

    private fun complete(requestId: String, decision: AuthorizationDecision): Boolean {
        val target = synchronized(lock) {
            pending?.takeIf { it.request.requestId == requestId }
        } ?: return false
        return target.result.complete(decision)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 120_000L
    }
}
