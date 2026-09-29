package dev.mwalab.transaction

import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.recorder.ProtocolEventHandle
import dev.mwalab.protocol.recorder.ProtocolRecorder
import dev.mwalab.session.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/** Adds child persistence after canonical settlement; never completes a recorder or wallet request. */
class TransactionDiagnosticSettlement(
    private val sessions: SessionRepository,
    private val diagnostics: TransactionDiagnosticRepository,
    private val workerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    class Context internal constructor(
        internal val handle: ProtocolEventHandle,
        internal val summaries: List<TransactionSummary>,
        internal val onFailure: () -> Unit,
    ) {
        internal val result = AtomicReference<ProtocolRecorder.CompletionResult?>()
    }
    private val active = ConcurrentHashMap<String, Context>()

    fun begin(handle: ProtocolEventHandle?, summaries: TransactionApprovalDiagnostics, onFailure: () -> Unit): Context? {
        if (handle == null) return null
        val safe = summaries.summaries.filterNotNull()
        if (safe.isEmpty()) return null
        if (handle.method !in setOf(ProtocolMethod.SIGN_TRANSACTIONS, ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS) ||
            safe.any { it.sessionId != handle.sessionId || it.eventId != handle.eventId }) {
            runCatching(onFailure)
            return null
        }
        val context = Context(handle, immutableDiagnosticList(safe, safe.size), onFailure)
        if (active.putIfAbsent(handle.eventId, context) != null) {
            runCatching(onFailure)
            return null
        }
        return context
    }

    /** Observe the existing result without changing first-terminal ownership or persistence. */
    fun completed(result: ProtocolRecorder.CompletionResult) {
        active[result.event.eventId]?.result?.compareAndSet(null, result)
    }

    /** Called from the transaction handler's finally, including cancellation/close races. */
    fun finish(context: Context?) {
        if (context == null || !active.remove(context.handle.eventId, context)) return
        if (context.result.get() is ProtocolRecorder.CompletionResult.PersistenceFailed) return
        workerScope.launch {
            try {
                val saved = withTimeoutOrNull(PARENT_WAIT_MILLIS) {
                    val handle = context.handle
                    fun matching(event: ProtocolEvent) = event.eventId == handle.eventId &&
                        event.sessionId == handle.sessionId && event.method == handle.method &&
                        event.sequence == handle.sequence && event.startedAtEpochMillis == handle.startedAtEpochMillis &&
                        event.requestSummary == handle.requestSummary
                    val found = sessions.getSession(handle.sessionId)?.events?.singleOrNull { matching(it) }
                        ?: sessions.observeSession(handle.sessionId).first { session ->
                            session?.events?.any { matching(it) } == true
                        }!!.events.single { matching(it) }
                    context.result.get()?.let { require(found == it.event) }
                    diagnostics.recordForEvent(handle.sessionId, handle.eventId, context.summaries)
                    true
                }
                if (saved != true) runCatching(context.onFailure)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Fixed failure evidence only: never exception text, payloads, tokens, or new protocol outcomes.
                runCatching(context.onFailure)
            }
        }
    }

    companion object { const val PARENT_WAIT_MILLIS = 5_000L }
}
