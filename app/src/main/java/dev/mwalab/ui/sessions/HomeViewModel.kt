package dev.mwalab.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mwalab.identity.IdentityRepository
import dev.mwalab.session.SessionRepository
import dev.mwalab.session.SessionSummary
import dev.mwalab.wallet.DEFAULT_AIRDROP_LAMPORTS
import dev.mwalab.wallet.TestWalletService
import dev.mwalab.wallet.TestWalletUiState
import dev.mwalab.wallet.WalletAirdropConfirmationResult
import dev.mwalab.wallet.WalletAirdropRequestResult
import dev.mwalab.wallet.WalletAirdropState
import dev.mwalab.wallet.WalletBalanceLoadResult
import dev.mwalab.wallet.WalletBalanceState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    identityRepository: IdentityRepository,
    sessionRepository: SessionRepository,
    scope: CoroutineScope? = null,
    private val worker: CoroutineDispatcher = Dispatchers.IO,
    private val walletService: TestWalletService? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val stateScope = scope ?: viewModelScope
    private val reload = MutableStateFlow(0)
    private val walletState = MutableStateFlow(TestWalletUiState())
    private var balanceJob: Job? = null
    private var airdropJob: Job? = null

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
    val state: StateFlow<HomeUiState> = combine(identity, history, walletState, ::HomeUiState)
        .stateIn(stateScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        if (walletService != null) refreshWallet()
    }

    fun retry() {
        reload.update { it + 1 }
        refreshWallet()
    }

    fun refreshWallet() {
        val service = walletService ?: return
        balanceJob?.cancel()
        walletState.update { it.copy(balance = WalletBalanceState.Loading) }
        balanceJob = stateScope.launch(worker) {
            when (val result = service.loadBalance()) {
                is WalletBalanceLoadResult.Available -> walletState.update {
                    it.copy(
                        address = result.address,
                        balance = WalletBalanceState.Available(result.lamports),
                        lastRefreshAtEpochMillis = clock(),
                    )
                }
                is WalletBalanceLoadResult.Unavailable -> walletState.update {
                    it.copy(
                        address = result.address ?: it.address,
                        balance = WalletBalanceState.Unavailable(result.reason),
                        lastRefreshAtEpochMillis = clock(),
                    )
                }
            }
        }
    }

    fun requestDevnetSol() {
        val service = walletService ?: return
        if (airdropJob?.isActive == true) return
        val current = walletState.value.airdrop
        if (current is WalletAirdropState.Requesting || current is WalletAirdropState.Submitted) return
        airdropJob = stateScope.launch(worker) {
            walletState.update { it.copy(airdrop = WalletAirdropState.Requesting) }
            when (val requested = service.requestAirdrop(DEFAULT_AIRDROP_LAMPORTS)) {
                is WalletAirdropRequestResult.Submitted -> {
                    walletState.update { it.copy(airdrop = WalletAirdropState.Submitted) }
                    when (service.confirmAirdrop(requested.signature)) {
                        WalletAirdropConfirmationResult.Confirmed -> {
                            walletState.update { it.copy(airdrop = WalletAirdropState.Confirmed) }
                            refreshWallet()
                        }
                        WalletAirdropConfirmationResult.NotConfirmed,
                        WalletAirdropConfirmationResult.Unknown -> {
                            walletState.update { it.copy(airdrop = WalletAirdropState.UnknownConfirmation) }
                            refreshWallet()
                        }
                    }
                }
                WalletAirdropRequestResult.RateLimited -> walletState.update {
                    it.copy(airdrop = WalletAirdropState.RateLimited)
                }
                is WalletAirdropRequestResult.RpcUnavailable -> walletState.update {
                    it.copy(airdrop = WalletAirdropState.RpcUnavailable(requested.reason))
                }
                is WalletAirdropRequestResult.Failed -> walletState.update {
                    it.copy(airdrop = WalletAirdropState.Failed(requested.rpcCode))
                }
            }
        }
    }
}
