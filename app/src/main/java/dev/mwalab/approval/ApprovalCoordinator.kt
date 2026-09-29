package dev.mwalab.approval

import dev.mwalab.transaction.TransactionApprovalDiagnostics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import java.util.UUID

sealed interface ApprovalDecision {
    data object Approved : ApprovalDecision
    data object Rejected : ApprovalDecision
    data object Cancelled : ApprovalDecision
    data object Expired : ApprovalDecision
    data object Busy : ApprovalDecision
}

data class ApprovalRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val method: String,
    val dappIdentityName: String?,
    val chain: String,
    val payloadFingerprints: List<String>,
    val payloadLengths: List<Int>,
    val transactionSummaries: TransactionApprovalDiagnostics? = null,
)

sealed interface ApprovalState {
    data object Idle : ApprovalState
    data class Pending(val request: ApprovalRequest) : ApprovalState
}

class ApprovalCoordinator(
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    private data class PendingDecision(
        val request: ApprovalRequest,
        val result: CompletableDeferred<ApprovalDecision>,
    )

    private val lock = Any()
    private val mutableState = MutableStateFlow<ApprovalState>(ApprovalState.Idle)
    private var pending: PendingDecision? = null

    val state: StateFlow<ApprovalState> = mutableState.asStateFlow()

    suspend fun requestApproval(request: ApprovalRequest): ApprovalDecision {
        val deferred = CompletableDeferred<ApprovalDecision>()
        synchronized(lock) {
            if (pending != null) return ApprovalDecision.Busy
            pending = PendingDecision(request, deferred)
            mutableState.value = ApprovalState.Pending(request)
        }

        return try {
            withTimeout(timeoutMillis) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            ApprovalDecision.Expired
        } finally {
            synchronized(lock) {
                if (pending?.request?.requestId == request.requestId) {
                    pending = null
                    mutableState.value = ApprovalState.Idle
                }
            }
        }
    }

    fun approve(requestId: String) = complete(requestId, ApprovalDecision.Approved)

    fun reject(requestId: String) = complete(requestId, ApprovalDecision.Rejected)

    fun cancelPending() {
        val target = synchronized(lock) { pending }
        target?.result?.complete(ApprovalDecision.Cancelled)
    }

    private fun complete(requestId: String, decision: ApprovalDecision): Boolean {
        val target = synchronized(lock) {
            pending?.takeIf { it.request.requestId == requestId }
        } ?: return false
        return target.result.complete(decision)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 120_000L
    }
}
