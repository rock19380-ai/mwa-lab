package dev.mwalab.simulation

import dev.mwalab.rpc.DevnetSimulationOptions
import dev.mwalab.security.DiagnosticSanitizer
import dev.mwalab.transaction.TransactionInspectionStatus
import dev.mwalab.transaction.TransactionInspector
import dev.mwalab.transaction.TransactionVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SimulationUiState {
    data object NotRun : SimulationUiState
    data object Running : SimulationUiState
    data class Completed(val result: SimulationResult) : SimulationUiState
}

/** Owns transient bytes and request identity; UI receives only safe state. */
class TransactionSimulationCoordinator(
    private val service: TransactionSimulationService,
    private val onResult: (SimulationResult) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private class Registered(
        val ref: SimulationTargetRef,
        val generation: Long,
        val options: DevnetSimulationOptions,
        val bytes: ByteArray,
    ) {
        var attempt = 0
        var running = false
        var acceptsNewAttempts = true
        var job: Job? = null
    }

    private val lock = Any()
    private val registered = mutableMapOf<SimulationTargetRef, Registered>()
    private var activeSession: Pair<String, Long>? = null
    private val mutableState = MutableStateFlow<Map<SimulationTargetRef, SimulationUiState>>(emptyMap())
    val state: StateFlow<Map<SimulationTargetRef, SimulationUiState>> = mutableState.asStateFlow()

    fun activateSession(sessionId: String, generation: Long) {
        require(SimulationLimits.safeIdentity(sessionId) && generation >= 0)
        synchronized(lock) {
            if (activeSession == (sessionId to generation)) return
            registered.values.forEach { it.bytes.fill(0); it.job?.cancel() }
            registered.clear()
            mutableState.value = emptyMap()
            activeSession = sessionId to generation
        }
    }

    /** The caller has already passed the authoritative legacy preparation. */
    fun register(
        ref: SimulationTargetRef,
        generation: Long,
        transaction: ByteArray,
        options: DevnetSimulationOptions,
    ): Boolean {
        if (transaction.isEmpty() || transaction.size > SimulationLimits.MAX_TRANSACTION_BYTES ||
            DiagnosticSanitizer.sha256(transaction) != ref.transactionFingerprintSha256) return false
        val inspection = try { TransactionInspector().inspect(transaction) } catch (_: Exception) { return false }
        if (inspection.transactionVersion != TransactionVersion.LEGACY ||
            inspection.inspectionStatus != TransactionInspectionStatus.PARSED) return false
        synchronized(lock) {
            if (activeSession != (ref.sessionId to generation) || registered.containsKey(ref)) return false
            registered[ref] = Registered(ref, generation, options, transaction.copyOf())
            mutableState.value = mutableState.value + (ref to SimulationUiState.NotRun)
            return true
        }
    }

    fun simulate(ref: SimulationTargetRef): Boolean {
        val entry: Registered
        val attempt: Int
        val owned: ByteArray
        synchronized(lock) {
            entry = registered[ref] ?: return false
            if (activeSession != (ref.sessionId to entry.generation) || !entry.acceptsNewAttempts || entry.running ||
                DiagnosticSanitizer.sha256(entry.bytes) != ref.transactionFingerprintSha256) return false
            entry.running = true
            entry.attempt++
            attempt = entry.attempt
            owned = entry.bytes.copyOf()
            mutableState.value = mutableState.value + (ref to SimulationUiState.Running)
            entry.job = scope.launch {
                try {
                    val result = service.simulate(ref, attempt, owned, entry.options)
                    synchronized(lock) {
                        if (registered[ref] === entry && activeSession == (ref.sessionId to entry.generation)) {
                            entry.running = false
                            if (entry.acceptsNewAttempts) {
                                mutableState.value = mutableState.value + (ref to SimulationUiState.Completed(result))
                            } else {
                                registered.remove(ref)
                                mutableState.value = mutableState.value - ref
                            }
                            runCatching { onResult(result) }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } finally {
                    owned.fill(0)
                }
            }
            return true
        }
    }

    /** Stops new taps; an already running attempt may finish for its original request. */
    fun releaseRequest(sessionId: String, eventId: String, requestId: String) {
        synchronized(lock) {
            registered.values.filter { it.ref.sessionId == sessionId && it.ref.eventId == eventId && it.ref.requestId == requestId }.toList().forEach { entry ->
                entry.acceptsNewAttempts = false
                entry.bytes.fill(0)
                if (!entry.running) registered.remove(entry.ref)
                mutableState.value = mutableState.value - entry.ref
            }
        }
    }

    /** Session replacement/close invalidates results, including late callbacks. */
    fun invalidateSession(sessionId: String, generation: Long) {
        synchronized(lock) {
            if (activeSession != (sessionId to generation)) return
            registered.values.forEach { it.bytes.fill(0); it.job?.cancel() }
            registered.clear()
            mutableState.value = emptyMap()
            activeSession = null
        }
    }
}
