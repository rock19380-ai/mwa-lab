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
    val lines: List<String> = java.util.Collections.unmodifiableList(ArrayList(lines))

    init {
        require(lines.size <= SimulationLimits.MAX_LOG_LINES)
        require(lines.all(SimulationLimits::safeText))
        require(lines.sumOf { it.codePointCount(0, it.length) } <= SimulationLimits.MAX_LOG_TOTAL_CODE_POINTS)
    }
}
