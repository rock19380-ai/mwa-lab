package dev.mwalab.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mwalab.session.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionsViewModel(repository: SessionRepository, scope: CoroutineScope? = null) : ViewModel() {
    private val reload = MutableStateFlow(0)
    val state: StateFlow<SessionsUiState> = reload.flatMapLatest {
        repository.observeSessions().map<List<dev.mwalab.session.SessionSummary>, SessionsUiState> {
            if (it.isEmpty()) SessionsUiState.Empty else SessionsUiState.Ready(it.toList())
        }.onStart { emit(SessionsUiState.Loading) }.catch { emit(SessionsUiState.Error) }
    }.stateIn(scope ?: viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionsUiState.Loading)

    fun retry() { reload.update { it + 1 } }
}
