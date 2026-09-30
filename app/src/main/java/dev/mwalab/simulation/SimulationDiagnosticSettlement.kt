package dev.mwalab.simulation

import dev.mwalab.protocol.ProtocolEvent
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.protocol.recorder.ProtocolEventHandle
import dev.mwalab.protocol.recorder.ProtocolRecorder
import dev.mwalab.session.SessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/** Child persistence after the canonical parent; never completes or changes a protocol event. */
class SimulationDiagnosticSettlement(
    private val sessions: SessionRepository,
    private val simulations: SimulationRepository,
    private val workerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    class Context internal constructor(
        internal val handle: ProtocolEventHandle,
        internal val requestId: String,
        internal val onFailure: () -> Unit,
    ) {
        internal val attempts = linkedMapOf<Pair<Int, Int>, SimulationResult>()
        internal val workers = mutableListOf<Job>()
        internal var completion: ProtocolRecorder.CompletionResult? = null
        internal var finished = false
        internal var invalidated = false
    }

    private val active = ConcurrentHashMap<String, Context>()

    fun begin(handle: ProtocolEventHandle?, requestId: String, onFailure: () -> Unit = {}): Context? {
        if (handle == null || !SimulationLimits.safeIdentity(requestId) ||
            handle.method !in setOf(ProtocolMethod.SIGN_TRANSACTIONS, ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS)) return null
        val context = Context(handle, requestId, onFailure)
        return if (active.putIfAbsent(handle.eventId, context) == null) context else null
    }

    /** Called by the coordinator with safe results only. Never blocks a wallet callback. */
    fun record(result: SimulationResult): Boolean {
        val context = active[result.target.eventId] ?: return false
        synchronized(context) {
            if (context.invalidated || context.completion is ProtocolRecorder.CompletionResult.PersistenceFailed ||
                result.target.sessionId != context.handle.sessionId ||
                result.target.eventId != context.handle.eventId ||
                result.target.requestId != context.requestId) return false
            val key = result.target.payloadIndex to result.attemptNumber
            if (key in context.attempts) return false
            context.attempts[key] = result
            if (context.finished) schedule(context, result)
            return true
        }
    }

    /** Observes the existing recorder result; never requests or changes completion. */
    fun completed(result: ProtocolRecorder.CompletionResult) {
        val context = active[result.event.eventId] ?: return
        synchronized(context) {
            if (context.invalidated || !matches(context.handle, result.event)) return
            context.completion = result
        }
    }

    /** Called in the request handler's finally. Late in-flight attempts have a bounded window. */
    fun finish(context: Context?) {
        if (context == null || active[context.handle.eventId] !== context) return
        synchronized(context) {
            if (context.invalidated || context.finished) return
            context.finished = true
            if (context.completion !is ProtocolRecorder.CompletionResult.PersistenceFailed) {
                context.attempts.values.forEach { schedule(context, it) }
            }
            context.workers += workerScope.launch {
                delay(LATE_RESULT_WINDOW_MILLIS)
                expire(context)
            }
        }
    }

    fun invalidateSession(sessionId: String) {
        active.values.filter { it.handle.sessionId == sessionId }.forEach(::expire)
    }

    private fun expire(context: Context) {
        synchronized(context) {
            context.invalidated = true
            active.remove(context.handle.eventId, context)
            context.workers.forEach { it.cancel() }
            context.workers.clear()
            context.attempts.clear()
        }
    }

    private fun schedule(context: Context, result: SimulationResult) {
        context.workers += workerScope.launch {
            try {
                val saved = withTimeoutOrNull(PARENT_WAIT_MILLIS) {
                    val handle = context.handle
                    val event = sessions.getSession(handle.sessionId)?.events?.singleOrNull { matches(handle, it) }
                        ?: sessions.observeSession(handle.sessionId).first { summary ->
                            summary?.events?.any { matches(handle, it) } == true
                        }!!.events.single { matches(handle, it) }
                    synchronized(context) {
                        if (context.invalidated || context.completion is ProtocolRecorder.CompletionResult.PersistenceFailed ||
                            (context.completion != null && context.completion!!.event != event)) return@withTimeoutOrNull false
                    }
                    simulations.recordForEvent(handle.sessionId, handle.eventId, listOf(result))
                    true
                }
                if (saved != true) runCatching(context.onFailure)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                runCatching(context.onFailure)
            }
        }
    }

    private fun matches(handle: ProtocolEventHandle, event: ProtocolEvent): Boolean =
        event.eventId == handle.eventId && event.sessionId == handle.sessionId &&
            event.method == handle.method && event.sequence == handle.sequence &&
            event.startedAtEpochMillis == handle.startedAtEpochMillis &&
            event.requestSummary == handle.requestSummary

    companion object {
        const val PARENT_WAIT_MILLIS = 5_000L
        const val LATE_RESULT_WINDOW_MILLIS = 20_000L
    }
}
