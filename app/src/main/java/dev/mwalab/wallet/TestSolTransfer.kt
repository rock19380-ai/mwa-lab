package dev.mwalab.wallet

import com.funkatronics.encoders.Base58
import dev.mwalab.identity.IdentityRepository
import dev.mwalab.rpc.DevnetRpcGateway
import dev.mwalab.rpc.DevnetRpcResult
import dev.mwalab.rpc.DevnetSendOptions
import dev.mwalab.signing.LabSigningService
import dev.mwalab.transaction.LegacyTransactionCodec
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.math.BigInteger

const val MIN_SEND_FEE_RESERVE_LAMPORTS: Long = 10_000L

data class WalletSendReview(
    val fromAddress: String,
    val toAddress: String,
    val lamports: Long,
) {
    val amountSol: String get() = formatLamportsAsSol(lamports)
}

sealed interface WalletSendState {
    data object Idle : WalletSendState
    data object Validating : WalletSendState
    data class Review(val transfer: WalletSendReview) : WalletSendState
    data class Sending(val transfer: WalletSendReview) : WalletSendState
    data class Confirmed(val transfer: WalletSendReview) : WalletSendState
    data class SubmittedUnknown(val transfer: WalletSendReview) : WalletSendState
    data class Failed(
        val reason: WalletSendFailureReason,
        val rpcCode: Int? = null,
    ) : WalletSendState
}

enum class WalletSendFailureReason {
    INVALID_RECIPIENT,
    INVALID_AMOUNT,
    INSUFFICIENT_FUNDS,
    IDENTITY,
    BLOCKHASH,
    SIGNING,
    RPC_REJECTED,
    RPC_UNAVAILABLE,
    MALFORMED_RPC,
    SIGNATURE_MISMATCH,
}

class PreparedTestSolTransfer internal constructor(
    fromPublicKey: ByteArray,
    val fromAddress: String,
    toPublicKey: ByteArray,
    val toAddress: String,
    val lamports: Long,
) {
    private val fromBytes = fromPublicKey.copyOf()
    private val toBytes = toPublicKey.copyOf()

    init {
        require(fromBytes.size == 32)
        require(toBytes.size == 32)
        require(lamports > 0)
    }

    fun fromPublicKeyBytes(): ByteArray = fromBytes.copyOf()
    fun toPublicKeyBytes(): ByteArray = toBytes.copyOf()
    fun review(): WalletSendReview = WalletSendReview(fromAddress, toAddress, lamports)
}

sealed interface WalletSendPreparationResult {
    data class Ready(val transfer: PreparedTestSolTransfer) : WalletSendPreparationResult
    data class Failed(
        val reason: WalletSendFailureReason,
        val rpcCode: Int? = null,
    ) : WalletSendPreparationResult
}

sealed interface WalletSendSubmissionResult {
    data object Confirmed : WalletSendSubmissionResult
    data object SubmittedUnknown : WalletSendSubmissionResult
    data class Failed(
        val reason: WalletSendFailureReason,
        val rpcCode: Int? = null,
    ) : WalletSendSubmissionResult
}

class TestWalletSendService(
    private val identityRepository: IdentityRepository,
    private val signingService: LabSigningService,
    private val rpcGateway: DevnetRpcGateway,
) {
    suspend fun prepare(
        recipientText: String,
        amountText: String,
    ): WalletSendPreparationResult {
        val recipient = SolanaPublicKeyParser.parse(recipientText)
            ?: return WalletSendPreparationResult.Failed(WalletSendFailureReason.INVALID_RECIPIENT)
        val lamports = parseSolAmountToLamports(amountText)
            ?: return WalletSendPreparationResult.Failed(WalletSendFailureReason.INVALID_AMOUNT)

        val identity = try {
            identityRepository.getOrCreate()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return WalletSendPreparationResult.Failed(WalletSendFailureReason.IDENTITY)
        }

        val balance = when (val result = rpcGateway.getBalance(identity.publicKeyBytes())) {
            is DevnetRpcResult.Success -> result.value
            is DevnetRpcResult.RpcError -> return WalletSendPreparationResult.Failed(
                WalletSendFailureReason.RPC_REJECTED,
                result.code,
            )
            is DevnetRpcResult.TransportFailure -> return WalletSendPreparationResult.Failed(
                WalletSendFailureReason.RPC_UNAVAILABLE,
            )
            DevnetRpcResult.MalformedResponse -> return WalletSendPreparationResult.Failed(
                WalletSendFailureReason.MALFORMED_RPC,
            )
        }

        val required = try {
            Math.addExact(lamports, MIN_SEND_FEE_RESERVE_LAMPORTS)
        } catch (_: ArithmeticException) {
            return WalletSendPreparationResult.Failed(WalletSendFailureReason.INVALID_AMOUNT)
        }
        if (balance < required) {
            return WalletSendPreparationResult.Failed(WalletSendFailureReason.INSUFFICIENT_FUNDS)
        }

        return WalletSendPreparationResult.Ready(
            PreparedTestSolTransfer(
                fromPublicKey = identity.publicKeyBytes(),
                fromAddress = identity.displayAddress,
                toPublicKey = recipient,
                toAddress = Base58.encodeToString(recipient),
                lamports = lamports,
            ),
        )
    }

    suspend fun submit(transfer: PreparedTestSolTransfer): WalletSendSubmissionResult {
        val identity = try {
            signingService.publicIdentity()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return WalletSendSubmissionResult.Failed(WalletSendFailureReason.IDENTITY)
        }
        if (!identity.publicKeyBytes().contentEquals(transfer.fromPublicKeyBytes())) {
            return WalletSendSubmissionResult.Failed(WalletSendFailureReason.IDENTITY)
        }

        val latest = when (val result = rpcGateway.getLatestBlockhash()) {
            is DevnetRpcResult.Success -> result.value
            is DevnetRpcResult.RpcError -> return WalletSendSubmissionResult.Failed(
                WalletSendFailureReason.BLOCKHASH,
                result.code,
            )
            is DevnetRpcResult.TransportFailure,
            DevnetRpcResult.MalformedResponse -> return WalletSendSubmissionResult.Failed(
                WalletSendFailureReason.BLOCKHASH,
            )
        }

        val unsigned = try {
            TestSolTransferBuilder.build(
                fromPublicKey = transfer.fromPublicKeyBytes(),
                toPublicKey = transfer.toPublicKeyBytes(),
                recentBlockhash = latest.blockhash,
                lamports = transfer.lamports,
            )
        } catch (_: Throwable) {
            return WalletSendSubmissionResult.Failed(WalletSendFailureReason.INVALID_AMOUNT)
        }

        val parsed = try {
            LegacyTransactionCodec.parseForSigner(unsigned, transfer.fromPublicKeyBytes())
        } catch (_: Throwable) {
            return WalletSendSubmissionResult.Failed(WalletSendFailureReason.SIGNING)
        }
        if (!parsed.recentBlockhash.contentEquals(latest.blockhash)) {
            return WalletSendSubmissionResult.Failed(WalletSendFailureReason.BLOCKHASH)
        }

        val signature = try {
            signingService.sign(parsed.message)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return WalletSendSubmissionResult.Failed(WalletSendFailureReason.SIGNING)
        }
        if (signature.size != LegacyTransactionCodec.SIGNATURE_BYTES) {
            return WalletSendSubmissionResult.Failed(WalletSendFailureReason.SIGNING)
        }
        val signed = parsed.withSignature(signature)

        val minContextSlot = latest.contextSlot
            .takeIf { it in 0..Int.MAX_VALUE.toLong() }
            ?.toInt()
        val options = DevnetSendOptions(
            minContextSlot = minContextSlot,
            commitment = "confirmed",
            skipPreflight = false,
            maxRetries = 3,
            waitForCommitmentToSendNextTransaction = true,
        )
        val submittedSignature = when (val result = rpcGateway.sendTransaction(signed, options)) {
            is DevnetRpcResult.Success -> result.value
            is DevnetRpcResult.RpcError -> return WalletSendSubmissionResult.Failed(
                WalletSendFailureReason.RPC_REJECTED,
                result.code,
            )
            // Once the signed transaction has crossed the RPC transport boundary, a
            // timeout/IO/malformed response is ambiguous: the node may have accepted
            // it before the response was lost. Never report that state as a definite
            // failure and never encourage an automatic retry.
            is DevnetRpcResult.TransportFailure,
            DevnetRpcResult.MalformedResponse -> return WalletSendSubmissionResult.SubmittedUnknown
        }
        if (!submittedSignature.contentEquals(signature)) {
            // The canonical transaction id is the first Ed25519 signature. A
            // mismatching response cannot prove non-submission of our transaction.
            return WalletSendSubmissionResult.SubmittedUnknown
        }

        return when (val confirmation = rpcGateway.awaitCommitment(submittedSignature, "confirmed")) {
            is DevnetRpcResult.Success -> if (confirmation.value) {
                WalletSendSubmissionResult.Confirmed
            } else {
                WalletSendSubmissionResult.SubmittedUnknown
            }
            is DevnetRpcResult.RpcError,
            is DevnetRpcResult.TransportFailure,
            DevnetRpcResult.MalformedResponse -> WalletSendSubmissionResult.SubmittedUnknown
        }
    }
}

object SolanaPublicKeyParser {
    fun parse(raw: String): ByteArray? {
        val value = raw.trim()
        if (value.length !in 32..44) return null
        if (value.any { BASE58_ALPHABET.indexOf(it) < 0 }) return null

        var number = BigInteger.ZERO
        val radix = BigInteger.valueOf(58)
        for (character in value) {
            val digit = BASE58_ALPHABET.indexOf(character)
            number = number.multiply(radix).add(BigInteger.valueOf(digit.toLong()))
        }

        var body = if (number == BigInteger.ZERO) ByteArray(0) else number.toByteArray()
        if (body.size > 1 && body[0] == 0.toByte()) body = body.copyOfRange(1, body.size)
        val leadingZeros = value.takeWhile { it == '1' }.length
        val decoded = ByteArray(leadingZeros) + body
        if (decoded.size != 32) return null
        if (Base58.encodeToString(decoded) != value) return null
        return decoded
    }

    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
}

fun parseSolAmountToLamports(raw: String): Long? {
    val value = raw.trim()
    if (!SOL_AMOUNT_PATTERN.matches(value)) return null
    val parts = value.split('.', limit = 2)
    val whole = parts[0].toLongOrNull() ?: return null
    val fractional = parts.getOrNull(1).orEmpty().padEnd(9, '0').toLongOrNull() ?: 0L
    val lamports = try {
        Math.addExact(Math.multiplyExact(whole, LAMPORTS_PER_SOL), fractional)
    } catch (_: ArithmeticException) {
        return null
    }
    return lamports.takeIf { it > 0L }
}

object TestSolTransferBuilder {
    fun build(
        fromPublicKey: ByteArray,
        toPublicKey: ByteArray,
        recentBlockhash: ByteArray,
        lamports: Long,
    ): ByteArray {
        require(fromPublicKey.size == LegacyTransactionCodec.PUBLIC_KEY_BYTES)
        require(toPublicKey.size == LegacyTransactionCodec.PUBLIC_KEY_BYTES)
        require(recentBlockhash.size == LegacyTransactionCodec.BLOCKHASH_BYTES)
        require(lamports > 0L)

        val message = ByteArrayOutputStream().apply {
            write(byteArrayOf(1, 0, 1))
            write(3)
            write(fromPublicKey)
            write(toPublicKey)
            write(ByteArray(32)) // 11111111111111111111111111111111
            write(recentBlockhash)
            write(1) // instruction count
            write(2) // System Program account index
            write(2) // two instruction account indexes
            write(0) // from
            write(1) // to
            write(12) // u32 instruction discriminator + u64 lamports
            write(byteArrayOf(2, 0, 0, 0))
            repeat(8) { shift -> write(((lamports ushr (shift * 8)) and 0xff).toInt()) }
        }.toByteArray()

        return ByteArrayOutputStream().apply {
            write(1) // one transaction signature
            write(ByteArray(LegacyTransactionCodec.SIGNATURE_BYTES))
            write(message)
        }.toByteArray().also {
            require(it.size <= LegacyTransactionCodec.MAX_TRANSACTION_BYTES)
        }
    }
}

fun walletSendFailureText(reason: WalletSendFailureReason): String = when (reason) {
    WalletSendFailureReason.INVALID_RECIPIENT -> "Invalid Solana recipient address."
    WalletSendFailureReason.INVALID_AMOUNT -> "Enter a positive SOL amount with at most 9 decimal places."
    WalletSendFailureReason.INSUFFICIENT_FUNDS -> "Insufficient Devnet SOL after the test transfer and fee reserve."
    WalletSendFailureReason.IDENTITY -> "Test Wallet identity changed or is unavailable. Review again before sending."
    WalletSendFailureReason.BLOCKHASH -> "Unable to obtain a fresh Devnet blockhash. Nothing was submitted."
    WalletSendFailureReason.SIGNING -> "Protected Test Wallet signing failed. Nothing was submitted."
    WalletSendFailureReason.RPC_REJECTED -> "Devnet RPC rejected the transfer."
    WalletSendFailureReason.RPC_UNAVAILABLE -> "Devnet RPC is unavailable. Confirmation was not fabricated."
    WalletSendFailureReason.MALFORMED_RPC -> "Devnet RPC returned an unexpected response."
    WalletSendFailureReason.SIGNATURE_MISMATCH -> "Devnet RPC returned an unexpected transaction signature."
}

private val SOL_AMOUNT_PATTERN = Regex("(?:0|[1-9][0-9]*)(?:\\.[0-9]{1,9})?")
