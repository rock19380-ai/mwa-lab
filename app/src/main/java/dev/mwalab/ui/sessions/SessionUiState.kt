package dev.mwalab.ui.sessions

import dev.mwalab.capabilities.CapabilitySnapshot
import dev.mwalab.session.SessionSummary
import dev.mwalab.transaction.TransactionSummary
import dev.mwalab.simulation.SimulationResult

sealed interface SessionsUiState {
    data object Loading : SessionsUiState
    data object Empty : SessionsUiState
    data class Ready(val sessions: List<SessionSummary>) : SessionsUiState
    data object Error : SessionsUiState
}

sealed interface SessionDetailUiState {
    data object Loading : SessionDetailUiState
    data object Missing : SessionDetailUiState
    data class Ready(
        val summary: SessionSummary,
        val capabilities: SessionCapabilityUiState = SessionCapabilityUiState.Missing,
        val transactions: Map<String, SessionTransactionUiState> = emptyMap(),
        val simulations: Map<String, SessionSimulationUiState> = emptyMap(),
    ) : SessionDetailUiState
    data object Error : SessionDetailUiState
}

sealed interface SessionCapabilityUiState {
    data object Loading : SessionCapabilityUiState
    data object Missing : SessionCapabilityUiState
    data class Recorded(val snapshot: CapabilitySnapshot) : SessionCapabilityUiState
    data object Unavailable : SessionCapabilityUiState
}

sealed interface SessionTransactionUiState {
    data object Loading : SessionTransactionUiState
    data object Missing : SessionTransactionUiState
    data class Recorded(val summaries: List<TransactionSummary>) : SessionTransactionUiState
    data object Unavailable : SessionTransactionUiState
}

sealed interface SessionSimulationUiState {
    data object Loading : SessionSimulationUiState
    data object Missing : SessionSimulationUiState
    data class Recorded(val attempts: List<SimulationResult>) : SessionSimulationUiState
    data object Unavailable : SessionSimulationUiState
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
