package dev.mwalab.wallet

const val LAMPORTS_PER_SOL: Long = 1_000_000_000L
const val DEFAULT_AIRDROP_LAMPORTS: Long = 500_000_000L

enum class WalletUnavailableReason {
    IDENTITY,
    TIMEOUT,
    NETWORK,
    HTTP,
    RATE_LIMITED,
    RPC_ERROR,
    MALFORMED,
}

sealed interface WalletBalanceState {
    data object Loading : WalletBalanceState
    data class Available(val lamports: Long) : WalletBalanceState
    data class Unavailable(val reason: WalletUnavailableReason) : WalletBalanceState
}

sealed interface WalletAirdropState {
    data object Idle : WalletAirdropState
    data object Requesting : WalletAirdropState
    data object Submitted : WalletAirdropState
    data object Confirmed : WalletAirdropState
    data object RateLimited : WalletAirdropState
    data class RpcUnavailable(val reason: WalletUnavailableReason) : WalletAirdropState
    data class Failed(val rpcCode: Int? = null) : WalletAirdropState
    data object UnknownConfirmation : WalletAirdropState
}

data class TestWalletUiState(
    val address: String? = null,
    val balance: WalletBalanceState = WalletBalanceState.Loading,
    val airdrop: WalletAirdropState = WalletAirdropState.Idle,
    val send: WalletSendState = WalletSendState.Idle,
    val lastRefreshAtEpochMillis: Long? = null,
)

fun walletUnavailableReasonText(reason: WalletUnavailableReason): String = when (reason) {
    WalletUnavailableReason.IDENTITY -> "test identity unavailable"
    WalletUnavailableReason.TIMEOUT -> "Devnet RPC timeout"
    WalletUnavailableReason.NETWORK -> "network unavailable"
    WalletUnavailableReason.HTTP -> "Devnet RPC unavailable"
    WalletUnavailableReason.RATE_LIMITED -> "Devnet RPC rate-limited"
    WalletUnavailableReason.RPC_ERROR -> "Devnet RPC error"
    WalletUnavailableReason.MALFORMED -> "unexpected Devnet RPC response"
}

fun formatLamportsAsSol(lamports: Long): String {
    require(lamports >= 0) { "Lamports must be non-negative" }
    val whole = lamports / LAMPORTS_PER_SOL
    val fractional = lamports % LAMPORTS_PER_SOL
    if (fractional == 0L) return whole.toString()
    val fractionalText = fractional.toString().padStart(9, '0').trimEnd('0')
    return "$whole.$fractionalText"
}
