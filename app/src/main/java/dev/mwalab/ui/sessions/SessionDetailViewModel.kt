package dev.mwalab.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mwalab.capabilities.CapabilitySnapshotRepository
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.session.SessionRepository
import dev.mwalab.transaction.TransactionDiagnosticRepository
import dev.mwalab.simulation.SimulationRepository
import dev.mwalab.report.DiagnosticReportExportUseCase
import dev.mwalab.report.DiagnosticReportFormat
import dev.mwalab.report.ReportCompleteness
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModel(
    repository: SessionRepository,
    capabilityRepository: CapabilitySnapshotRepository,
    scope: CoroutineScope? = null,
    transactionRepository: TransactionDiagnosticRepository? = null,
    simulationRepository: SimulationRepository? = null,
    private val reportExports: DiagnosticReportExportUseCase? = null,
) : ViewModel() {
    private data class Selection(val id: String? = null, val attempt: Int = 0)
    private val selection = MutableStateFlow(Selection())
    private val capabilityAttempt = MutableStateFlow(0)
    private val transactionAttempts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val simulationAttempts = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val actionScope = scope ?: viewModelScope
    private var reportJob: Job? = null
    private val mutableReportState = MutableStateFlow<ReportExportUiState>(ReportExportUiState.Idle)
    val reportState: StateFlow<ReportExportUiState> = mutableReportState.asStateFlow()
    private val reportEffectChannel = Channel<ReportExportEffect>(Channel.BUFFERED)
    val reportEffects = reportEffectChannel.receiveAsFlow()
    val state: StateFlow<SessionDetailUiState> = selection.flatMapLatest { selected ->
        val id = selected.id
        if (id == null) flowOf(SessionDetailUiState.Missing)
        else {
            val timeline = repository.observeSession(id).map<dev.mwalab.session.SessionSummary?, SessionDetailUiState> {
                if (it == null) SessionDetailUiState.Missing else SessionDetailUiState.Ready(it)
            }.onStart { emit(SessionDetailUiState.Loading) }.catch { emit(SessionDetailUiState.Error) }
            val capabilities = capabilityAttempt.flatMapLatest {
                flow<SessionCapabilityUiState> {
                    emitAll(capabilityRepository.observeSnapshot(id).map { snapshot ->
                        if (snapshot == null) SessionCapabilityUiState.Missing
                        else {
                            require(snapshot.sessionId == id) { "Capability session mismatch" }
                            SessionCapabilityUiState.Recorded(snapshot)
                        }
                    })
                }.onStart { emit(SessionCapabilityUiState.Loading) }
                    .catch { emit(SessionCapabilityUiState.Unavailable) }
            }
            val historyWithTransactions = timeline.flatMapLatest { history ->
                if (history !is SessionDetailUiState.Ready) flowOf(history)
                else {
                    val events = history.summary.events.filter { it.method == ProtocolMethod.SIGN_TRANSACTIONS ||
                        it.method == ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS }
                    val reads = events.map { event ->
                        transactionAttempts.map { it[event.eventId] ?: 0 }.distinctUntilChanged().flatMapLatest {
                            flow<SessionTransactionUiState> {
                                val rows = transactionRepository?.observeForEvent(id, event.eventId) ?: flowOf(emptyList())
                                emitAll(rows.map { summaries ->
                                    require(summaries.all { it.sessionId == id && it.eventId == event.eventId })
                                    require(summaries.map { it.payloadIndex }.distinct().size == summaries.size)
                                    if (summaries.isEmpty()) SessionTransactionUiState.Missing
                                    else SessionTransactionUiState.Recorded(summaries.sortedBy { it.payloadIndex })
                                })
                            }.onStart { emit(SessionTransactionUiState.Loading) }
                                .catch { emit(SessionTransactionUiState.Unavailable) }
                        }.map { event.eventId to it }
                    }
                    if (reads.isEmpty()) flowOf(history)
                    else combine(reads) { states -> history.copy(transactions = states.toMap()) }
                }
            }
            val historyWithSimulations = historyWithTransactions.flatMapLatest { history ->
                if (history !is SessionDetailUiState.Ready || simulationRepository == null) flowOf(history)
                else {
                    val events = history.summary.events.filter { it.method == ProtocolMethod.SIGN_TRANSACTIONS ||
                        it.method == ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS }
                    val reads = events.map { event ->
                        simulationAttempts.map { it[event.eventId] ?: 0 }.distinctUntilChanged().flatMapLatest {
                            flow<SessionSimulationUiState> {
                                emitAll(simulationRepository.observeForEvent(id, event.eventId).map { attempts ->
                                    require(attempts.all { it.target.sessionId == id && it.target.eventId == event.eventId })
                                    require(attempts.map { it.target.payloadIndex to it.attemptNumber }.distinct().size == attempts.size)
                                    if (attempts.isEmpty()) SessionSimulationUiState.Missing
                                    else SessionSimulationUiState.Recorded(
                                        attempts.sortedWith(compareBy({ it.target.payloadIndex }, { it.attemptNumber })))
                                })
                            }.onStart { emit(SessionSimulationUiState.Loading) }
                                .catch { emit(SessionSimulationUiState.Unavailable) }
                        }.map { event.eventId to it }
                    }
                    if (reads.isEmpty()) flowOf(history)
                    else combine(reads) { states -> history.copy(simulations = states.toMap()) }
                }
            }
            combine(historyWithSimulations, capabilities) { history, capability ->
                if (history is SessionDetailUiState.Ready) history.copy(capabilities = capability)
                else history
            }
        }
    }.stateIn(scope ?: viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState.Loading)

    fun selectSession(id: String) {
        if (selection.value.id != id) {
            reportJob?.cancel()
            mutableReportState.value = ReportExportUiState.Idle
        }
        selection.value = Selection(id)
    }
    fun shareReport(format: DiagnosticReportFormat) {
        val id = selection.value.id ?: return
        val exporter = reportExports ?: return
        reportJob?.cancel()
        reportJob = actionScope.launch {
            mutableReportState.value = ReportExportUiState.Building
            try {
                val artifact = exporter.export(id, format)
                if (selection.value.id != id) return@launch
                mutableReportState.value = ReportExportUiState.Sharing(format, artifact.completeness, artifact.truncated)
                reportEffectChannel.send(ReportExportEffect.Share(artifact))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (selection.value.id == id) mutableReportState.value = ReportExportUiState.Error
            }
        }
    }
    fun copyReportSummary() {
        val id = selection.value.id ?: return
        val exporter = reportExports ?: return
        reportJob?.cancel()
        reportJob = actionScope.launch {
            mutableReportState.value = ReportExportUiState.Building
            try {
                val summary = exporter.copySummary(id)
                if (selection.value.id != id) return@launch
                reportEffectChannel.send(ReportExportEffect.Copy(summary.sessionId, summary.text, summary.completeness, summary.truncated))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (selection.value.id == id) mutableReportState.value = ReportExportUiState.Error
            }
        }
    }
    fun reportShareLaunched(format: DiagnosticReportFormat, completeness: ReportCompleteness, truncated: Boolean) {
        mutableReportState.value = ReportExportUiState.Ready(format, completeness, truncated)
    }
    fun reportCopied(completeness: ReportCompleteness, truncated: Boolean) {
        mutableReportState.value = ReportExportUiState.Copied(completeness, truncated)
    }
    fun reportDeliveryFailed() { mutableReportState.value = ReportExportUiState.Error }
    fun isSelectedSession(id: String): Boolean = selection.value.id == id
    fun retry() { selection.update { it.copy(attempt = it.attempt + 1) } }
    fun retryCapabilities() { capabilityAttempt.update { it + 1 } }
    fun retryTransactions(eventId: String) {
        transactionAttempts.update { it + (eventId to ((it[eventId] ?: 0) + 1)) }
    }
    fun retrySimulations(eventId: String) {
        simulationAttempts.update { it + (eventId to ((it[eventId] ?: 0) + 1)) }
    }
}
