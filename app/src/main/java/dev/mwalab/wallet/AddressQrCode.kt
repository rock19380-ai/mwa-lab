package dev.mwalab.wallet

import kotlin.math.abs
import kotlin.math.max

/**
 * Dependency-free QR encoder for the narrow Phase 9 receive-address contract.
 *
 * It emits QR Model 2, Version 3, error-correction level L, byte mode, mask 0.
 * Version 3-L carries up to 53 byte-mode bytes; a Solana base58 public key is
 * at most 44 ASCII characters. The encoder accepts no arbitrary binary or
 * secret material and works fully offline.
 */
object AddressQrCode {
    const val VERSION = 3
    const val SIZE = 29
    const val MAX_PAYLOAD_BYTES = 53
    const val QUIET_ZONE_MODULES = 4

    fun encode(payload: String): QrMatrix {
        val bytes = payload.toByteArray(Charsets.ISO_8859_1)
        require(payload.isNotEmpty()) { "QR payload must not be empty" }
        require(bytes.size <= MAX_PAYLOAD_BYTES) { "QR payload exceeds Version 3-L capacity" }
        require(payload.all { it in BASE58_ALPHABET }) { "Receive QR accepts only a base58 public address" }

        val data = encodeData(bytes)
        val ecc = reedSolomonRemainder(data, ECC_CODEWORDS)
        val codewords = IntArray(data.size + ecc.size)
        data.copyInto(codewords)
        ecc.copyInto(codewords, data.size)

        val modules = Array(SIZE) { BooleanArray(SIZE) }
        val functions = Array(SIZE) { BooleanArray(SIZE) }
        fun setFunction(x: Int, y: Int, dark: Boolean) {
            modules[y][x] = dark
            functions[y][x] = true
        }

        for (i in 0 until SIZE) {
            setFunction(6, i, i % 2 == 0)
            setFunction(i, 6, i % 2 == 0)
        }

        fun drawFinder(centerX: Int, centerY: Int) {
            for (dy in -4..4) for (dx in -4..4) {
                val x = centerX + dx
                val y = centerY + dy
                if (x !in 0 until SIZE || y !in 0 until SIZE) continue
                val distance = max(abs(dx), abs(dy))
                setFunction(x, y, distance != 2 && distance != 4)
            }
        }
        drawFinder(3, 3)
        drawFinder(SIZE - 4, 3)
        drawFinder(3, SIZE - 4)

        for (dy in -2..2) for (dx in -2..2) {
            setFunction(22 + dx, 22 + dy, max(abs(dx), abs(dy)) != 1)
        }

        drawFormatBits(setFunction = ::setFunction)

        var bitIndex = 0
        var right = SIZE - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vertical in 0 until SIZE) {
                val upward = ((right + 1) and 2) == 0
                val y = if (upward) SIZE - 1 - vertical else vertical
                for (j in 0..1) {
                    val x = right - j
                    if (functions[y][x]) continue
                    var dark = false
                    if (bitIndex < codewords.size * 8) {
                        val value = codewords[bitIndex ushr 3]
                        dark = ((value ushr (7 - (bitIndex and 7))) and 1) != 0
                    }
                    if ((x + y) % 2 == 0) dark = !dark // mask 0
                    modules[y][x] = dark
                    bitIndex++
                }
            }
            right -= 2
        }
        return QrMatrix(modules)
    }

    private fun encodeData(bytes: ByteArray): IntArray {
        val bits = ArrayList<Boolean>(DATA_CODEWORDS * 8)
        fun appendBits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) bits += ((value ushr i) and 1) != 0
        }

        appendBits(0b0100, 4) // byte mode
        appendBits(bytes.size, 8) // versions 1..9 use an 8-bit byte count
        bytes.forEach { appendBits(it.toInt() and 0xFF, 8) }

        val capacity = DATA_CODEWORDS * 8
        repeat(minOf(4, capacity - bits.size)) { bits += false }
        while (bits.size % 8 != 0) bits += false

        val result = ArrayList<Int>(DATA_CODEWORDS)
        for (offset in bits.indices step 8) {
            var value = 0
            for (index in 0 until 8) value = (value shl 1) or if (bits[offset + index]) 1 else 0
            result += value
        }
        var pad = 0
        while (result.size < DATA_CODEWORDS) {
            result += if (pad % 2 == 0) 0xEC else 0x11
            pad++
        }
        return result.toIntArray()
    }

    private fun reedSolomonRemainder(data: IntArray, degree: Int): IntArray {
        val exp = IntArray(512)
        val log = IntArray(256)
        var value = 1
        for (i in 0 until 255) {
            exp[i] = value
            log[value] = i
            value = value shl 1
            if ((value and 0x100) != 0) value = value xor 0x11D
        }
        for (i in 255 until exp.size) exp[i] = exp[i - 255]

        fun multiply(a: Int, b: Int): Int = if (a == 0 || b == 0) 0 else exp[log[a] + log[b]]

        var generator = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(generator.size + 1)
            for (j in generator.indices) {
                next[j] = next[j] xor generator[j]
                next[j + 1] = next[j + 1] xor multiply(generator[j], exp[i])
            }
            generator = next
        }

        val remainder = IntArray(degree)
        for (dataByte in data) {
            val factor = dataByte xor remainder[0]
            for (i in 0 until degree - 1) remainder[i] = remainder[i + 1]
            remainder[degree - 1] = 0
            for (i in remainder.indices) remainder[i] = remainder[i] xor multiply(generator[i + 1], factor)
        }
        return remainder
    }

    private fun drawFormatBits(setFunction: (Int, Int, Boolean) -> Unit) {
        val data = 1 shl 3 // ECC L format bits=01, mask=0
        var remainder = data
        repeat(10) { remainder = (remainder shl 1) xor ((remainder ushr 9) * 0x537) }
        val bits = ((data shl 10) or remainder) xor 0x5412
        fun bit(index: Int): Boolean = ((bits ushr index) and 1) != 0

        for (i in 0..5) setFunction(8, i, bit(i))
        setFunction(8, 7, bit(6))
        setFunction(8, 8, bit(7))
        setFunction(7, 8, bit(8))
        for (i in 9..14) setFunction(14 - i, 8, bit(i))

        for (i in 0..7) setFunction(SIZE - 1 - i, 8, bit(i))
        for (i in 8..14) setFunction(8, SIZE - 15 + i, bit(i))
        setFunction(8, SIZE - 8, true)
    }

    private const val DATA_CODEWORDS = 55
    private const val ECC_CODEWORDS = 15
    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
}

class QrMatrix internal constructor(private val modules: Array<BooleanArray>) {
    val size: Int get() = modules.size

    fun isDark(x: Int, y: Int): Boolean {
        require(x in 0 until size && y in 0 until size)
        return modules[y][x]
    }
}
