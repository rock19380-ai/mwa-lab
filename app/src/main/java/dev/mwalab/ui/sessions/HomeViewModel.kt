package dev.mwalab.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mwalab.identity.IdentityRepository
import dev.mwalab.session.SessionRepository
import dev.mwalab.session.SessionSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    identityRepository: IdentityRepository,
    sessionRepository: SessionRepository,
    scope: CoroutineScope? = null,
    worker: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val identity = reload.flatMapLatest {
        flow<IdentityUiState> {
            emit(IdentityUiState.Loading)
            emit(IdentityUiState.Ready(identityRepository.getOrCreate().displayAddress))
        }.flowOn(worker).catch { emit(IdentityUiState.Unavailable) }
    }
    private val history = reload.flatMapLatest {
        sessionRepository.observeSessions().map<List<SessionSummary>, SessionsUiState> {
            if (it.isEmpty()) SessionsUiState.Empty else SessionsUiState.Ready(it.toList())
        }.onStart { emit(SessionsUiState.Loading) }.catch { emit(SessionsUiState.Error) }
    }
    val state: StateFlow<HomeUiState> = combine(identity, history, ::HomeUiState)
        .stateIn(scope ?: viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun retry() { reload.update { it + 1 } }
}
