package dev.mwalab.storage.simulation

import dev.mwalab.simulation.BoundedLogs
import dev.mwalab.simulation.SimulationLimits
import org.json.JSONArray
import java.nio.charset.StandardCharsets

internal object SimulationResultJson {
    fun encode(logs: List<String>): String {
        val safe = BoundedLogs(logs, false).lines
        val encoded = JSONArray().also { array -> safe.forEach(array::put) }.toString()
        require(encoded.toByteArray(StandardCharsets.UTF_8).size <= SimulationLimits.MAX_RPC_RESPONSE_BYTES)
        return encoded
    }

    fun decode(encoded: String, truncated: Boolean): BoundedLogs {
        require(encoded.toByteArray(StandardCharsets.UTF_8).size <= SimulationLimits.MAX_RPC_RESPONSE_BYTES)
        val array = JSONArray(encoded)
        require(array.length() <= SimulationLimits.MAX_LOG_LINES)
        val lines = (0 until array.length()).map { index ->
            array.opt(index) as? String ?: throw IllegalArgumentException("Invalid simulation log")
        }
        return BoundedLogs(lines, truncated)
    }
}
