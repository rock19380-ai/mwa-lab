package dev.mwalab.transaction

/**
 * Conservative parser used only to prevent transaction-message payloads from
 * being signed through sign_messages. It does not decode instruction semantics.
 */
object SolanaTransactionMessageDetector {
    fun isTransactionMessage(payload: ByteArray): Boolean =
        runCatching { parseExact(payload) }.getOrDefault(false)

    private fun parseExact(bytes: ByteArray): Boolean {
        if (bytes.size < MIN_MESSAGE_BYTES) return false
        var cursor = 0

        val first = u8(bytes, cursor)
        val versioned = first and 0x80 != 0
        if (versioned) {
            val version = first and 0x7f
            if (version != 0) return false
            cursor += 1
        }

        requireRemaining(bytes, cursor, 3)
        val requiredSignatures = u8(bytes, cursor)
        val readonlySigned = u8(bytes, cursor + 1)
        val readonlyUnsigned = u8(bytes, cursor + 2)
        cursor += 3

        val accountCount = readShortVec(bytes, cursor)
        cursor = accountCount.next
        if (accountCount.value == 0 || accountCount.value > MAX_ACCOUNTS) return false
        if (requiredSignatures > accountCount.value) return false
        if (readonlySigned > requiredSignatures) return false
        if (readonlyUnsigned > accountCount.value - requiredSignatures) return false

        val accountBytes = Math.multiplyExact(accountCount.value, 32)
        requireRemaining(bytes, cursor, accountBytes + 32)
        cursor += accountBytes + 32

        val instructionCount = readShortVec(bytes, cursor)
        cursor = instructionCount.next
        if (instructionCount.value > MAX_INSTRUCTIONS) return false

        repeat(instructionCount.value) {
            requireRemaining(bytes, cursor, 1)
            val programIndex = u8(bytes, cursor)
            cursor += 1
            if (programIndex >= accountCount.value) return false

            val accountIndexes = readShortVec(bytes, cursor)
            cursor = accountIndexes.next
            if (accountIndexes.value > MAX_ACCOUNT_INDEXES_PER_INSTRUCTION) return false
            requireRemaining(bytes, cursor, accountIndexes.value)
            repeat(accountIndexes.value) { offset ->
                if (u8(bytes, cursor + offset) >= accountCount.value) return false
            }
            cursor += accountIndexes.value

            val dataLength = readShortVec(bytes, cursor)
            cursor = dataLength.next
            if (dataLength.value > MAX_INSTRUCTION_DATA) return false
            requireRemaining(bytes, cursor, dataLength.value)
            cursor += dataLength.value
        }

        if (versioned) {
            val lookupCount = readShortVec(bytes, cursor)
            cursor = lookupCount.next
            if (lookupCount.value > MAX_LOOKUPS) return false
            repeat(lookupCount.value) {
                requireRemaining(bytes, cursor, 32)
                cursor += 32
                val writable = readShortVec(bytes, cursor)
                cursor = writable.next
                requireRemaining(bytes, cursor, writable.value)
                cursor += writable.value
                val readonly = readShortVec(bytes, cursor)
                cursor = readonly.next
                requireRemaining(bytes, cursor, readonly.value)
                cursor += readonly.value
            }
        }

        return cursor == bytes.size
    }

    private data class ShortVec(val value: Int, val next: Int)

    private fun readShortVec(bytes: ByteArray, start: Int): ShortVec {
        var cursor = start
        var value = 0
        var shift = 0
        repeat(3) {
            requireRemaining(bytes, cursor, 1)
            val current = u8(bytes, cursor++)
            value = value or ((current and 0x7f) shl shift)
            if (current and 0x80 == 0) return ShortVec(value, cursor)
            shift += 7
        }
        throw IllegalArgumentException("shortvec too long")
    }

    private fun u8(bytes: ByteArray, index: Int): Int = bytes[index].toInt() and 0xff

    private fun requireRemaining(bytes: ByteArray, cursor: Int, count: Int) {
        require(cursor >= 0 && count >= 0 && cursor <= bytes.size - count)
    }

    private const val MIN_MESSAGE_BYTES = 3 + 1 + 32 + 32 + 1
    private const val MAX_ACCOUNTS = 256
    private const val MAX_INSTRUCTIONS = 256
    private const val MAX_ACCOUNT_INDEXES_PER_INSTRUCTION = 256
    private const val MAX_INSTRUCTION_DATA = 64 * 1024
    private const val MAX_LOOKUPS = 64
}
