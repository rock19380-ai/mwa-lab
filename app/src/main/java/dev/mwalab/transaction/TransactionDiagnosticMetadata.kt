package dev.mwalab.transaction

import java.math.BigInteger
import java.util.Collections

/** Wire/representation bounds, independent of the signing codec and capability authority. */
object TransactionInspectionLimits {
    const val MAX_TRANSACTION_BYTES = 1232
    const val MAX_SIGNATURES = 64
    const val MAX_ACCOUNTS = 256
    const val MAX_INSTRUCTIONS = 256
    const val MAX_ACCOUNTS_PER_INSTRUCTION = 256
    const val MAX_INSTRUCTION_DATA_BYTES = MAX_TRANSACTION_BYTES
    const val MAX_ADDRESS_TABLE_LOOKUPS = MAX_ACCOUNTS
    const val MAX_SHORTVEC_BYTES = 3
    const val MAX_SHORTVEC_VALUE = 65535
}

internal fun <T> immutableDiagnosticList(values: List<T>, maximum: Int): List<T> {
    require(values.size <= maximum) { "Diagnostic collection exceeds its representation bound" }
    return Collections.unmodifiableList(ArrayList(values))
}

internal fun requireDiagnosticHash(value: String) {
    require(value.matches(Regex("[0-9a-f]{64}"))) { "Expected a canonical SHA-256 fingerprint" }
}

internal fun requireDiagnosticKey(value: String) {
    require(value.length in 32..44) { "Expected a bounded public key or blockhash" }
    val alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    var number = BigInteger.ZERO
    value.forEach { character ->
        val digit = alphabet.indexOf(character)
        require(digit >= 0) { "Expected canonical base58 metadata" }
        number = number.multiply(BigInteger.valueOf(58)).add(BigInteger.valueOf(digit.toLong()))
    }
    val length = value.takeWhile { it == '1' }.length + (number.bitLength() + 7) / 8
    require(length == 32) { "Expected 32-byte public metadata" }
}
