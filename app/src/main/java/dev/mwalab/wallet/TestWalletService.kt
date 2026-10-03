package dev.mwalab.wallet

import dev.mwalab.identity.IdentityRepository
import dev.mwalab.rpc.DevnetRpcGateway
import dev.mwalab.rpc.DevnetRpcResult
import dev.mwalab.rpc.TransportFailureReason
import kotlinx.coroutines.CancellationException

sealed interface WalletBalanceLoadResult {
    data class Available(val address: String, val lamports: Long) : WalletBalanceLoadResult
    data class Unavailable(val address: String?, val reason: WalletUnavailableReason) : WalletBalanceLoadResult
}

sealed interface WalletAirdropRequestResult {
    data class Submitted(val signature: ByteArray) : WalletAirdropRequestResult
    data object RateLimited : WalletAirdropRequestResult
    data class RpcUnavailable(val reason: WalletUnavailableReason) : WalletAirdropRequestResult
    data class Failed(val rpcCode: Int? = null) : WalletAirdropRequestResult
}

sealed interface WalletAirdropConfirmationResult {
    data object Confirmed : WalletAirdropConfirmationResult
    data object NotConfirmed : WalletAirdropConfirmationResult
    data object Unknown : WalletAirdropConfirmationResult
}

class TestWalletService(
    private val identityRepository: IdentityRepository,
    private val rpcGateway: DevnetRpcGateway,
) {
    suspend fun loadBalance(): WalletBalanceLoadResult {
        val identity = try {
            identityRepository.getOrCreate()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return WalletBalanceLoadResult.Unavailable(null, WalletUnavailableReason.IDENTITY)
        }

        return when (val result = rpcGateway.getBalance(identity.publicKeyBytes())) {
            is DevnetRpcResult.Success -> WalletBalanceLoadResult.Available(identity.displayAddress, result.value)
            is DevnetRpcResult.RpcError -> WalletBalanceLoadResult.Unavailable(
                identity.displayAddress,
                WalletUnavailableReason.RPC_ERROR,
            )
            is DevnetRpcResult.TransportFailure -> WalletBalanceLoadResult.Unavailable(
                identity.displayAddress,
                result.reason.toWalletUnavailableReason(),
            )
            DevnetRpcResult.MalformedResponse -> WalletBalanceLoadResult.Unavailable(
                identity.displayAddress,
                WalletUnavailableReason.MALFORMED,
            )
        }
    }

    suspend fun requestAirdrop(lamports: Long = DEFAULT_AIRDROP_LAMPORTS): WalletAirdropRequestResult {
        if (lamports <= 0L) return WalletAirdropRequestResult.Failed()
        val identity = try {
            identityRepository.getOrCreate()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return WalletAirdropRequestResult.RpcUnavailable(WalletUnavailableReason.IDENTITY)
        }

        return when (val result = rpcGateway.requestAirdrop(identity.publicKeyBytes(), lamports)) {
            is DevnetRpcResult.Success -> WalletAirdropRequestResult.Submitted(result.value.copyOf())
            is DevnetRpcResult.RpcError -> WalletAirdropRequestResult.Failed(result.code)
            is DevnetRpcResult.TransportFailure -> when (result.reason) {
                TransportFailureReason.RATE_LIMITED -> WalletAirdropRequestResult.RateLimited
                else -> WalletAirdropRequestResult.RpcUnavailable(result.reason.toWalletUnavailableReason())
            }
            DevnetRpcResult.MalformedResponse -> WalletAirdropRequestResult.Failed()
        }
    }

    suspend fun confirmAirdrop(signature: ByteArray): WalletAirdropConfirmationResult =
        when (val result = rpcGateway.awaitCommitment(signature, "confirmed")) {
            is DevnetRpcResult.Success -> if (result.value) {
                WalletAirdropConfirmationResult.Confirmed
            } else {
                WalletAirdropConfirmationResult.NotConfirmed
            }
            is DevnetRpcResult.RpcError,
            is DevnetRpcResult.TransportFailure,
            DevnetRpcResult.MalformedResponse -> WalletAirdropConfirmationResult.Unknown
        }
}

private fun TransportFailureReason.toWalletUnavailableReason(): WalletUnavailableReason = when (this) {
    TransportFailureReason.TIMEOUT -> WalletUnavailableReason.TIMEOUT
    TransportFailureReason.IO -> WalletUnavailableReason.NETWORK
    TransportFailureReason.HTTP -> WalletUnavailableReason.HTTP
    TransportFailureReason.RATE_LIMITED -> WalletUnavailableReason.RATE_LIMITED
}
