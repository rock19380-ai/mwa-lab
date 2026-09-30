package dev.mwalab.rpc

import dev.mwalab.simulation.BoundedLogs
import dev.mwalab.simulation.SimulationErrorKind
import dev.mwalab.simulation.SimulationErrorSummary
import dev.mwalab.simulation.SimulationLimits
import org.json.JSONArray
import org.json.JSONObject

/** Simulation context is derived only from validated existing transaction methods. */
class DevnetSimulationOptions private constructor(
    val commitment: String,
    val minContextSlot: Int?,
) {
    companion object {
        fun forSignTransactions() = DevnetSimulationOptions("processed", null)

        fun forSignAndSend(options: DevnetSendOptions): DevnetSimulationOptions {
            val valid = requireNotNull(options.validatedOrNull()) { "Invalid Devnet send options" }
            return DevnetSimulationOptions(valid.commitment ?: "processed", valid.minContextSlot)
        }
    }
}

/** Safe parsed RPC fields only. No raw JSON envelope or transaction bytes. */
data class SimulationRpcValue(
    val contextSlot: Long,
    val error: SimulationErrorSummary?,
    val logs: BoundedLogs,
    val unitsConsumed: Long?,
) {
    init {
        require(contextSlot >= 0)
        require(unitsConsumed == null || unitsConsumed >= 0)
    }
}

internal object SimulationRpcValueParser {
    fun parse(raw: Any?): DevnetRpcResult<SimulationRpcValue> {
        val result = raw as? JSONObject ?: return DevnetRpcResult.MalformedResponse
        val context = result.opt("context") as? JSONObject ?: return DevnetRpcResult.MalformedResponse
        val slot = exactNonnegativeLong(context.opt("slot")) ?: return DevnetRpcResult.MalformedResponse
        val value = result.opt("value") as? JSONObject ?: return DevnetRpcResult.MalformedResponse
        if (!value.has("err")) return DevnetRpcResult.MalformedResponse
        val rawError = value.opt("err")
        val error = if (rawError == null || rawError === JSONObject.NULL) null else parseError(rawError)

        val rawLogs = value.opt("logs")
        val lines = ArrayList<String>()
        when (rawLogs) {
            null, JSONObject.NULL -> Unit
            is JSONArray -> {
                for (index in 0 until rawLogs.length()) {
                    val line = rawLogs.opt(index) as? String ?: return DevnetRpcResult.MalformedResponse
                    if (index < SimulationLimits.MAX_LOG_LINES) lines += line
                }
            }
            else -> return DevnetRpcResult.MalformedResponse
        }
        val bounded = SimulationLimits.sanitizeLogs(lines)
        val logs = if (rawLogs is JSONArray && rawLogs.length() > SimulationLimits.MAX_LOG_LINES) {
            BoundedLogs(bounded.lines, true)
        } else bounded
        val units = if (!value.has("unitsConsumed") || value.isNull("unitsConsumed")) null
            else exactNonnegativeLong(value.opt("unitsConsumed")) ?: return DevnetRpcResult.MalformedResponse
        return DevnetRpcResult.Success(SimulationRpcValue(slot, error, logs, units))
    }

    private fun parseError(raw: Any): SimulationErrorSummary {
        if (raw is String) return SimulationErrorSummary(when (raw) {
            "BlockhashNotFound" -> SimulationErrorKind.BLOCKHASH_NOT_FOUND
            "AccountNotFound" -> SimulationErrorKind.ACCOUNT_NOT_FOUND
            "InsufficientFundsForFee" -> SimulationErrorKind.INSUFFICIENT_FUNDS_FOR_FEE
            else -> SimulationErrorKind.UNKNOWN_SIMULATION_ERROR
        })
        if (raw !is JSONObject || raw.length() != 1 || !raw.has("InstructionError")) {
            return SimulationErrorSummary(SimulationErrorKind.UNKNOWN_SIMULATION_ERROR)
        }
        val instruction = raw.opt("InstructionError") as? JSONArray
            ?: return SimulationErrorSummary(SimulationErrorKind.UNKNOWN_SIMULATION_ERROR)
        if (instruction.length() != 2) return SimulationErrorSummary(SimulationErrorKind.UNKNOWN_SIMULATION_ERROR)
        val index = exactNonnegativeLong(instruction.opt(0))
            ?.takeIf { it <= SimulationLimits.MAX_INSTRUCTION_INDEX }?.toInt()
            ?: return SimulationErrorSummary(SimulationErrorKind.UNKNOWN_SIMULATION_ERROR)
        val detail = instruction.opt(1)
        if (detail is String && detail != "Custom" && detail in SimulationErrorSummary.KNOWN_INSTRUCTION_ERRORS) {
            return SimulationErrorSummary(SimulationErrorKind.INSTRUCTION_ERROR, index, detail)
        }
        if (detail is JSONObject && detail.length() == 1 && detail.has("Custom")) {
            val code = exactNonnegativeLong(detail.opt("Custom"))
                ?.takeIf { it <= SimulationLimits.MAX_CUSTOM_ERROR_CODE }
                ?: return SimulationErrorSummary(SimulationErrorKind.UNKNOWN_SIMULATION_ERROR)
            return SimulationErrorSummary(SimulationErrorKind.INSTRUCTION_ERROR, index, "Custom", code)
        }
        return SimulationErrorSummary(SimulationErrorKind.UNKNOWN_SIMULATION_ERROR)
    }

    private fun exactNonnegativeLong(value: Any?): Long? = when (value) {
        is Int, is Long, is java.math.BigInteger, is java.math.BigDecimal ->
            value.toString().toLongOrNull()?.takeIf { it >= 0 }
        else -> null
    }
}
