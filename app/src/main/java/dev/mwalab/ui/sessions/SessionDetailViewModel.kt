package dev.mwalab.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mwalab.capabilities.CapabilitySnapshotRepository
import dev.mwalab.protocol.ProtocolMethod
import dev.mwalab.session.SessionRepository
import dev.mwalab.transaction.TransactionDiagnosticRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModel(
    repository: SessionRepository,
    capabilityRepository: CapabilitySnapshotRepository,
    scope: CoroutineScope? = null,
    transactionRepository: TransactionDiagnosticRepository? = null,
) : ViewModel() {
    private data class Selection(val id: String? = null, val attempt: Int = 0)
    private val selection = MutableStateFlow(Selection())
    private val capabilityAttempt = MutableStateFlow(0)
    private val transactionAttempts = MutableStateFlow<Map<String, Int>>(emptyMap())
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
            combine(historyWithTransactions, capabilities) { history, capability ->
                if (history is SessionDetailUiState.Ready) history.copy(capabilities = capability)
                else history
            }
        }
    }.stateIn(scope ?: viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState.Loading)

    fun selectSession(id: String) { selection.value = Selection(id) }
    fun retry() { selection.update { it.copy(attempt = it.attempt + 1) } }
    fun retryCapabilities() { capabilityAttempt.update { it + 1 } }
    fun retryTransactions(eventId: String) {
        transactionAttempts.update { it + (eventId to ((it[eventId] ?: 0) + 1)) }
    }
}
