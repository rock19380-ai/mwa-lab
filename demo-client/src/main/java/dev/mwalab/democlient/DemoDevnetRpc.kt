package dev.mwalab.democlient

import android.util.Base64
import com.funkatronics.encoders.Base58
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong

/** Fixed-endpoint Devnet RPC helper used only by the deterministic demo dApp. */
class DemoDevnetRpc {
    private val requestId = AtomicLong(0)

    data class LatestBlockhash(
        val bytes: ByteArray,
        val lastValidBlockHeight: Long,
    )

    fun getLatestBlockhash(): LatestBlockhash {
        val result = rpc(
            "getLatestBlockhash",
            JSONArray().put(JSONObject().put("commitment", "processed")),
        ) as? JSONObject ?: error("Devnet getLatestBlockhash returned malformed result")
        val value = result.optJSONObject("value")
            ?: error("Devnet getLatestBlockhash result is missing value")
        val encoded = value.optString("blockhash", "")
        val blockhash = DemoLegacyTransactionFactory.decodeBase58(encoded)
        check(blockhash.size == DemoLegacyTransactionFactory.BLOCKHASH_BYTES) {
            "Devnet blockhash has unexpected length"
        }
        val lastValidBlockHeight = value.optLong("lastValidBlockHeight", -1)
        check(lastValidBlockHeight >= 0) { "Devnet blockhash height is malformed" }
        return LatestBlockhash(blockhash, lastValidBlockHeight)
    }

    fun getBalance(publicKey: ByteArray): Long {
        require(publicKey.size == DemoLegacyTransactionFactory.PUBLIC_KEY_BYTES)
        val result = rpc(
            "getBalance",
            JSONArray()
                .put(Base58.encodeToString(publicKey))
                .put(JSONObject().put("commitment", "processed")),
        ) as? JSONObject ?: error("Devnet getBalance returned malformed result")
        check(result.has("value")) { "Devnet getBalance result is missing value" }
        return result.getLong("value")
    }

    fun getFeeForMessage(message: ByteArray): Long {
        val result = rpc(
            "getFeeForMessage",
            JSONArray()
                .put(Base64.encodeToString(message, Base64.NO_WRAP))
                .put(JSONObject().put("commitment", "processed")),
        ) as? JSONObject ?: error("Devnet getFeeForMessage returned malformed result")
        check(result.has("value") && !result.isNull("value")) {
            "Devnet could not calculate a fee for the acceptance transaction"
        }
        return result.getLong("value")
    }

    private fun rpc(method: String, params: JSONArray): Any? {
        val connection = try {
            (URL(DEVNET_RPC_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
        } catch (_: IOException) {
            error("Devnet RPC connection setup failed")
        }

        try {
            val body = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", requestId.incrementAndGet())
                .put("method", method)
                .put("params", params)
                .toString()
                .toByteArray(StandardCharsets.UTF_8)

            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            check(status in 200..299) { "Devnet RPC HTTP failure" }
            val bytes = connection.inputStream.use { readBounded(it) }
                ?: error("Devnet RPC response exceeded safety bound")
            val response = JSONObject(String(bytes, StandardCharsets.UTF_8))
            if (response.has("error")) {
                val rpcError = response.optJSONObject("error")
                val code = rpcError?.optInt("code", Int.MIN_VALUE) ?: Int.MIN_VALUE
                error("Devnet RPC returned error code $code")
            }
            check(response.has("result")) { "Devnet RPC response is missing result" }
            return response.opt("result")
        } catch (_: SocketTimeoutException) {
            error("Devnet RPC timed out")
        } catch (io: IOException) {
            throw IllegalStateException("Devnet RPC I/O failure", io)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_RESPONSE_BYTES) return null
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        private const val DEVNET_RPC_URL = "https://api.devnet.solana.com"
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 12_000
        private const val MAX_RESPONSE_BYTES = 64 * 1024
    }
}
