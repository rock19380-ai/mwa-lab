package dev.mwalab.ui.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mwalab.identity.IdentityRepository
import dev.mwalab.session.SessionRepository
import dev.mwalab.session.SessionSummary
import dev.mwalab.wallet.DEFAULT_AIRDROP_LAMPORTS
import dev.mwalab.wallet.TestWalletService
import dev.mwalab.wallet.TestWalletSendService
import dev.mwalab.wallet.WalletSendPreparationResult
import dev.mwalab.wallet.WalletSendState
import dev.mwalab.wallet.WalletSendSubmissionResult
import dev.mwalab.wallet.PreparedTestSolTransfer
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
    private val sendService: TestWalletSendService? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val stateScope = scope ?: viewModelScope
    private val reload = MutableStateFlow(0)
    private val walletState = MutableStateFlow(TestWalletUiState())
    private var balanceJob: Job? = null
    private var airdropJob: Job? = null
    private var sendJob: Job? = null
    private var preparedSend: PreparedTestSolTransfer? = null

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
    fun prepareSend(recipient: String, amountSol: String) {
        val service = sendService ?: return
        if (sendJob?.isActive == true) return
        preparedSend = null
        walletState.update { it.copy(send = WalletSendState.Validating) }
        sendJob = stateScope.launch(worker) {
            when (val result = service.prepare(recipient, amountSol)) {
                is WalletSendPreparationResult.Ready -> {
                    preparedSend = result.transfer
                    walletState.update {
                        it.copy(send = WalletSendState.Review(result.transfer.review()))
                    }
                }
                is WalletSendPreparationResult.Failed -> walletState.update {
                    it.copy(send = WalletSendState.Failed(result.reason, result.rpcCode))
                }
            }
        }
    }

    fun confirmSend() {
        val service = sendService ?: return
        val transfer = preparedSend ?: return
        if (sendJob?.isActive == true) return
        val review = transfer.review()
        walletState.update { it.copy(send = WalletSendState.Sending(review)) }
        sendJob = stateScope.launch(worker) {
            when (val result = service.submit(transfer)) {
                WalletSendSubmissionResult.Confirmed -> {
                    preparedSend = null
                    walletState.update { it.copy(send = WalletSendState.Confirmed(review)) }
                    refreshWallet()
                }
                WalletSendSubmissionResult.SubmittedUnknown -> {
                    preparedSend = null
                    walletState.update { it.copy(send = WalletSendState.SubmittedUnknown(review)) }
                    refreshWallet()
                }
                is WalletSendSubmissionResult.Failed -> {
                    preparedSend = null
                    walletState.update {
                        it.copy(send = WalletSendState.Failed(result.reason, result.rpcCode))
                    }
                }
            }
        }
    }

    fun cancelSend() {
        val state = walletState.value.send
        if (state is WalletSendState.Sending || state is WalletSendState.Validating) return
        preparedSend = null
        walletState.update { it.copy(send = WalletSendState.Idle) }
    }

}
