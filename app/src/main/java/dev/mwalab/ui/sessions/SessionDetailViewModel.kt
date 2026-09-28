package dev.mwalab.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mwalab.session.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDetailViewModel(repository: SessionRepository, scope: CoroutineScope? = null) : ViewModel() {
    private data class Selection(val id: String? = null, val attempt: Int = 0)
    private val selection = MutableStateFlow(Selection())
    val state: StateFlow<SessionDetailUiState> = selection.flatMapLatest { selected ->
        val id = selected.id
        if (id == null) flowOf(SessionDetailUiState.Missing)
        else repository.observeSession(id).map<dev.mwalab.session.SessionSummary?, SessionDetailUiState> {
            if (it == null) SessionDetailUiState.Missing else SessionDetailUiState.Ready(it)
        }.onStart { emit(SessionDetailUiState.Loading) }.catch { emit(SessionDetailUiState.Error) }
    }.stateIn(scope ?: viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionDetailUiState.Loading)

    fun selectSession(id: String) { selection.value = Selection(id) }
    fun retry() { selection.update { it.copy(attempt = it.attempt + 1) } }
}
