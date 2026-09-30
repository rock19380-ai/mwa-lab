package dev.mwalab.simulation

object SimulationLimits {
    const val MAX_TRANSACTION_BYTES = 1232
    const val MAX_RPC_RESPONSE_BYTES = 64 * 1024
    const val MAX_LOG_LINES = 64
    const val MAX_LOG_LINE_CODE_POINTS = 512
    const val MAX_LOG_TOTAL_CODE_POINTS = 32_768
    const val MAX_INSTRUCTION_INDEX = 255
    const val MAX_CUSTOM_ERROR_CODE = 0xffff_ffffL
    const val TRUNCATION_MARKER = "Program logs truncated by MWA Lab diagnostic limit."
    const val REDACTED_LOG = "Program log content redacted by MWA Lab."

    private val invocation = Regex("^Program [1-9A-HJ-NP-Za-km-z]{32,44} invoke \\[([0-9]{1,3})\\]$")
    private val success = Regex("^Program [1-9A-HJ-NP-Za-km-z]{32,44} success$")
    private val consumed = Regex("^Program [1-9A-HJ-NP-Za-km-z]{32,44} consumed ([0-9]{1,12}) of ([0-9]{1,12}) compute units$")

    /** RPC logs can echo arbitrary transaction data. Retain only bounded runtime structure. */
    fun publicLogLine(line: String): String {
        if (line == REDACTED_LOG || line == "Program success" ||
            line.matches(Regex("^Program invocation, depth [0-9]{1,3}$")) ||
            line.matches(Regex("^Program consumed [0-9]{1,12} of [0-9]{1,12} compute units$"))
        ) return line
        invocation.matchEntire(line)?.let { return "Program invocation, depth ${it.groupValues[1]}" }
        if (success.matches(line)) return "Program success"
        consumed.matchEntire(line)?.let {
            return "Program consumed ${it.groupValues[1]} of ${it.groupValues[2]} compute units"
        }
        return REDACTED_LOG
    }

    fun safeIdentity(value: String): Boolean = value.isNotBlank() && value.length <= 128 &&
        value.all { it.code in 0x21..0x7e }

    fun safeText(value: String): Boolean {
        if (value.codePointCount(0, value.length) > MAX_LOG_LINE_CODE_POINTS) return false
        var offset = 0
        while (offset < value.length) {
            val code = value.codePointAt(offset)
            if (code < 0x20 || code == 0x7f || code in 0xd800..0xdfff ||
                Character.getType(code) == Character.FORMAT.toInt() ||
                Character.getType(code) == Character.CONTROL.toInt()) return false
            offset += Character.charCount(code)
        }
        return true
    }

    fun sanitizeLogs(raw: List<String>): BoundedLogs {
        val safe = ArrayList<String>(minOf(raw.size, MAX_LOG_LINES))
        var total = 0
        var truncated = raw.size > MAX_LOG_LINES
        for (line in raw.take(MAX_LOG_LINES)) {
            val out = StringBuilder()
            var offset = 0
            var count = 0
            while (offset < line.length && count < MAX_LOG_LINE_CODE_POINTS && total < MAX_LOG_TOTAL_CODE_POINTS) {
                val code = line.codePointAt(offset)
                val unsafe = code < 0x20 || code == 0x7f || code in 0xd800..0xdfff ||
                    Character.getType(code) == Character.FORMAT.toInt() ||
                    Character.getType(code) == Character.CONTROL.toInt()
                out.appendCodePoint(if (unsafe) 0xfffd else code)
                offset += Character.charCount(code)
                count++
                total++
            }
            if (offset < line.length) truncated = true
            safe += out.toString()
            if (total == MAX_LOG_TOTAL_CODE_POINTS) {
                if (safe.size < raw.size) truncated = true
                break
            }
        }
        return BoundedLogs(safe, truncated)
    }
}

class BoundedLogs(lines: List<String>, val truncated: Boolean) {
    val lines: List<String> = java.util.Collections.unmodifiableList(
        ArrayList(lines.map(SimulationLimits::publicLogLine)),
    )

    init {
        require(lines.size <= SimulationLimits.MAX_LOG_LINES)
        require(lines.all(SimulationLimits::safeText))
        require(lines.sumOf { it.codePointCount(0, it.length) } <= SimulationLimits.MAX_LOG_TOTAL_CODE_POINTS)
    }
}
