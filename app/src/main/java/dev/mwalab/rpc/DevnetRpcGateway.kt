package dev.mwalab.rpc

import android.util.Base64
import com.funkatronics.encoders.Base58
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong

sealed interface DevnetRpcResult<out T> {
    data class Success<T>(val value: T) : DevnetRpcResult<T>
    data class RpcError(val code: Int) : DevnetRpcResult<Nothing>
    data class TransportFailure(val reason: TransportFailureReason) : DevnetRpcResult<Nothing>
    data object MalformedResponse : DevnetRpcResult<Nothing>
}

enum class TransportFailureReason {
    TIMEOUT,
    IO,
    HTTP,
}

data class DevnetSendOptions(
    val minContextSlot: Int?,
    val commitment: String?,
    val skipPreflight: Boolean?,
    val maxRetries: Int?,
    val waitForCommitmentToSendNextTransaction: Boolean?,
) {
    fun validatedOrNull(): DevnetSendOptions? {
        if (minContextSlot != null && minContextSlot < 0) return null
        if (maxRetries != null && maxRetries < 0) return null
        if (commitment != null && commitment !in SUPPORTED_COMMITMENTS) return null
        return this
    }

    companion object {
        val SUPPORTED_COMMITMENTS = setOf("processed", "confirmed", "finalized")
    }
}

interface DevnetRpcGateway {
    suspend fun isBlockhashValid(
        blockhash: ByteArray,
        minContextSlot: Int? = null,
    ): DevnetRpcResult<Boolean>

    suspend fun sendTransaction(
        signedTransaction: ByteArray,
        options: DevnetSendOptions,
    ): DevnetRpcResult<ByteArray>

    suspend fun awaitCommitment(
        signature: ByteArray,
        commitment: String,
        timeoutMillis: Long = DEFAULT_CONFIRMATION_TIMEOUT_MS,
    ): DevnetRpcResult<Boolean>

    companion object {
        const val DEFAULT_CONFIRMATION_TIMEOUT_MS = 20_000L
    }
}

internal sealed interface DevnetHttpTransportResult {
    data class Response(
        val statusCode: Int,
        val body: ByteArray,
    ) : DevnetHttpTransportResult

    data class Failure(
        val reason: TransportFailureReason,
    ) : DevnetHttpTransportResult

    data object Malformed : DevnetHttpTransportResult
}

internal fun interface DevnetHttpTransport {
    suspend fun post(body: ByteArray): DevnetHttpTransportResult
}

private class FixedDevnetHttpTransport : DevnetHttpTransport {
    override suspend fun post(body: ByteArray): DevnetHttpTransportResult = withContext(Dispatchers.IO) {
        val connection = try {
            (URL(SolanaDevnetRpcGateway.DEVNET_RPC_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
        } catch (_: IOException) {
            return@withContext DevnetHttpTransportResult.Failure(TransportFailureReason.IO)
        }

        try {
            connection.outputStream.use { out -> out.write(body) }
            val status = connection.responseCode
            if (status !in 200..299) {
                return@withContext DevnetHttpTransportResult.Response(status, ByteArray(0))
            }
            val responseBytes = connection.inputStream.use { readBounded(it) }
                ?: return@withContext DevnetHttpTransportResult.Malformed
            DevnetHttpTransportResult.Response(status, responseBytes)
        } catch (_: SocketTimeoutException) {
            DevnetHttpTransportResult.Failure(TransportFailureReason.TIMEOUT)
        } catch (_: IOException) {
            DevnetHttpTransportResult.Failure(TransportFailureReason.IO)
        } catch (_: Throwable) {
            DevnetHttpTransportResult.Malformed
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream): ByteArray? {
        val buffer = ByteArray(4096)
        val output = ByteArrayOutputStream()
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (output.size() + read > MAX_RPC_RESPONSE_BYTES) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val MAX_RPC_RESPONSE_BYTES = 64 * 1024
    }
}

/**
 * Fixed-endpoint Solana Devnet RPC client.
 *
 * No caller-controlled RPC URL is accepted. Raw transactions, signatures and
 * RPC bodies are never logged or returned in diagnostic error strings.
 */
class SolanaDevnetRpcGateway internal constructor(
    private val transport: DevnetHttpTransport = FixedDevnetHttpTransport(),
) : DevnetRpcGateway {
    private val requestId = AtomicLong(0)

    override suspend fun isBlockhashValid(
        blockhash: ByteArray,
        minContextSlot: Int?,
    ): DevnetRpcResult<Boolean> {
        if (blockhash.size != 32 || (minContextSlot != null && minContextSlot < 0)) {
            return DevnetRpcResult.MalformedResponse
        }
        val config = JSONObject().put("commitment", "processed")
        minContextSlot?.let { config.put("minContextSlot", it) }
        val params = JSONArray()
            .put(Base58.encodeToString(blockhash))
            .put(config)
        return when (val response = rpc("isBlockhashValid", params)) {
            is RpcEnvelope.Success -> {
                val result = response.value as? JSONObject
                    ?: return DevnetRpcResult.MalformedResponse
                if (!result.has("value")) return DevnetRpcResult.MalformedResponse
                DevnetRpcResult.Success(result.optBoolean("value", false))
            }

            is RpcEnvelope.RpcError -> DevnetRpcResult.RpcError(response.code)
            is RpcEnvelope.TransportFailure -> DevnetRpcResult.TransportFailure(response.reason)
            RpcEnvelope.Malformed -> DevnetRpcResult.MalformedResponse
        }
    }

    override suspend fun sendTransaction(
        signedTransaction: ByteArray,
        options: DevnetSendOptions,
    ): DevnetRpcResult<ByteArray> {
        if (signedTransaction.isEmpty() || signedTransaction.size > MAX_TRANSACTION_BYTES) {
            return DevnetRpcResult.MalformedResponse
        }
        val validated = options.validatedOrNull() ?: return DevnetRpcResult.MalformedResponse
        val config = JSONObject().put("encoding", "base64")
        validated.minContextSlot?.let { config.put("minContextSlot", it) }
        validated.commitment?.let { config.put("preflightCommitment", it) }
        validated.skipPreflight?.let { config.put("skipPreflight", it) }
        validated.maxRetries?.let { config.put("maxRetries", it) }

        val encoded = Base64.encodeToString(signedTransaction, Base64.NO_WRAP)
        val params = JSONArray().put(encoded).put(config)
        return when (val response = rpc("sendTransaction", params)) {
            is RpcEnvelope.Success -> {
                val signatureBase58 = response.value as? String
                    ?: return DevnetRpcResult.MalformedResponse
                val signature = decodeBase58(signatureBase58)
                    ?: return DevnetRpcResult.MalformedResponse
                if (signature.size != SIGNATURE_BYTES) {
                    DevnetRpcResult.MalformedResponse
                } else {
                    DevnetRpcResult.Success(signature)
                }
            }

            is RpcEnvelope.RpcError -> DevnetRpcResult.RpcError(response.code)
            is RpcEnvelope.TransportFailure -> DevnetRpcResult.TransportFailure(response.reason)
            RpcEnvelope.Malformed -> DevnetRpcResult.MalformedResponse
        }
    }

    override suspend fun awaitCommitment(
        signature: ByteArray,
        commitment: String,
        timeoutMillis: Long,
    ): DevnetRpcResult<Boolean> {
        if (signature.size != SIGNATURE_BYTES || commitment !in DevnetSendOptions.SUPPORTED_COMMITMENTS) {
            return DevnetRpcResult.MalformedResponse
        }
        val signatureBase58 = Base58.encodeToString(signature)
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMillis) {
            val params = JSONArray()
                .put(JSONArray().put(signatureBase58))
                .put(JSONObject().put("searchTransactionHistory", false))
            when (val response = rpc("getSignatureStatuses", params)) {
                is RpcEnvelope.Success -> {
                    val result = response.value as? JSONObject
                        ?: return DevnetRpcResult.MalformedResponse
                    val values = result.optJSONArray("value")
                        ?: return DevnetRpcResult.MalformedResponse
                    if (values.length() != 1) return DevnetRpcResult.MalformedResponse
                    val rawStatus = values.opt(0)
                    if (rawStatus != null && rawStatus !== JSONObject.NULL) {
                        val status = rawStatus as? JSONObject
                            ?: return DevnetRpcResult.MalformedResponse
                        val err = status.opt("err")
                        if (err != null && err !== JSONObject.NULL) {
                            return DevnetRpcResult.Success(false)
                        }
                        val confirmationStatus = status.optString("confirmationStatus", "")
                        val effectiveStatus = if (
                            confirmationStatus.isEmpty() && status.isNull("confirmations")
                        ) {
                            "finalized"
                        } else {
                            confirmationStatus
                        }
                        if (commitmentReached(effectiveStatus, commitment)) {
                            return DevnetRpcResult.Success(true)
                        }
                    }
                }

                is RpcEnvelope.RpcError -> return DevnetRpcResult.RpcError(response.code)
                is RpcEnvelope.TransportFailure -> return DevnetRpcResult.TransportFailure(response.reason)
                RpcEnvelope.Malformed -> return DevnetRpcResult.MalformedResponse
            }
            delay(CONFIRMATION_POLL_INTERVAL_MS)
        }
        return DevnetRpcResult.Success(false)
    }

    private suspend fun rpc(method: String, params: JSONArray): RpcEnvelope {
        val body = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", requestId.incrementAndGet())
            .put("method", method)
            .put("params", params)
            .toString()
            .toByteArray(StandardCharsets.UTF_8)

        return when (val transportResult = transport.post(body)) {
            is DevnetHttpTransportResult.Failure -> {
                RpcEnvelope.TransportFailure(transportResult.reason)
            }

            DevnetHttpTransportResult.Malformed -> RpcEnvelope.Malformed

            is DevnetHttpTransportResult.Response -> {
                if (transportResult.statusCode !in 200..299) {
                    return RpcEnvelope.TransportFailure(TransportFailureReason.HTTP)
                }
                if (transportResult.body.size > MAX_RPC_RESPONSE_BYTES) {
                    return RpcEnvelope.Malformed
                }
                val response = try {
                    JSONObject(String(transportResult.body, StandardCharsets.UTF_8))
                } catch (_: Throwable) {
                    return RpcEnvelope.Malformed
                }

                val error = response.optJSONObject("error")
                if (error != null) {
                    return RpcEnvelope.RpcError(error.optInt("code", Int.MIN_VALUE))
                }
                if (!response.has("result")) return RpcEnvelope.Malformed
                RpcEnvelope.Success(response.opt("result"))
            }
        }
    }

    private fun commitmentReached(actual: String, requested: String): Boolean {
        val actualRank = commitmentRank(actual) ?: return false
        val requestedRank = commitmentRank(requested) ?: return false
        return actualRank >= requestedRank
    }

    private fun commitmentRank(value: String): Int? = when (value) {
        "processed" -> 0
        "confirmed" -> 1
        "finalized" -> 2
        else -> null
    }

    private fun decodeBase58(value: String): ByteArray? {
        if (value.isEmpty() || value.length > MAX_BASE58_SIGNATURE_CHARS) return null
        var number = BigInteger.ZERO
        for (character in value) {
            val digit = BASE58_ALPHABET.indexOf(character)
            if (digit < 0) return null
            number = number.multiply(BASE_58).add(BigInteger.valueOf(digit.toLong()))
        }

        val leadingZeros = value.takeWhile { it == '1' }.length
        val encoded = number.toByteArray().let { bytes ->
            if (bytes.size > 1 && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
        }
        if (number == BigInteger.ZERO) {
            return ByteArray(leadingZeros.coerceAtLeast(1))
        }
        return ByteArray(leadingZeros + encoded.size).also { out ->
            encoded.copyInto(out, destinationOffset = leadingZeros)
        }
    }

    private sealed interface RpcEnvelope {
        data class Success(val value: Any?) : RpcEnvelope
        data class RpcError(val code: Int) : RpcEnvelope
        data class TransportFailure(val reason: TransportFailureReason) : RpcEnvelope
        data object Malformed : RpcEnvelope
    }

    companion object {
        const val DEVNET_RPC_URL = "https://api.devnet.solana.com"
        private const val MAX_RPC_RESPONSE_BYTES = 64 * 1024
        private const val MAX_TRANSACTION_BYTES = 1232
        private const val SIGNATURE_BYTES = 64
        private const val MAX_BASE58_SIGNATURE_CHARS = 128
        private const val CONFIRMATION_POLL_INTERVAL_MS = 500L
        private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        private val BASE_58 = BigInteger.valueOf(58L)
    }
}
