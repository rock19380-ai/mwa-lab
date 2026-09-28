package dev.mwalab.ui.sessions

import dev.mwalab.session.SessionSummary

sealed interface SessionsUiState {
    data object Loading : SessionsUiState
    data object Empty : SessionsUiState
    data class Ready(val sessions: List<SessionSummary>) : SessionsUiState
    data object Error : SessionsUiState
}

sealed interface SessionDetailUiState {
    data object Loading : SessionDetailUiState
    data object Missing : SessionDetailUiState
    data class Ready(val summary: SessionSummary) : SessionDetailUiState
    data object Error : SessionDetailUiState
}

sealed interface IdentityUiState {
    data object Loading : IdentityUiState
    data class Ready(val publicAddress: String) : IdentityUiState
    data object Unavailable : IdentityUiState
}

data class HomeUiState(
    val identity: IdentityUiState = IdentityUiState.Loading,
    val history: SessionsUiState = SessionsUiState.Loading,
) {
    val lastSession: SessionSummary? get() = (history as? SessionsUiState.Ready)?.sessions?.firstOrNull()
}
