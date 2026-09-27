package dev.mwalab.transaction

/**
 * Bounded parser/signature patcher for Solana legacy wire transactions.
 *
 * Phase 2 deliberately supports legacy transactions only. Versioned messages
 * fail closed even if they would otherwise be structurally parseable.
 */
object LegacyTransactionCodec {
    const val MAX_TRANSACTION_BYTES = 1232
    const val SIGNATURE_BYTES = 64
    const val PUBLIC_KEY_BYTES = 32
    const val BLOCKHASH_BYTES = 32

    enum class RejectionReason {
        EMPTY,
        TOO_LARGE,
        MALFORMED,
        VERSIONED_UNSUPPORTED,
        SIGNATURE_COUNT_MISMATCH,
        SIGNER_NOT_REQUIRED,
    }

    class Rejected(
        val reason: RejectionReason,
    ) : IllegalArgumentException(reason.name)

    data class Parsed(
        val original: ByteArray,
        val message: ByteArray,
        val signerIndex: Int,
        val signatureCount: Int,
        val recentBlockhash: ByteArray,
        private val signatureSectionOffset: Int,
    ) {
        fun withSignature(signature: ByteArray): ByteArray {
            require(signature.size == SIGNATURE_BYTES) {
                "Ed25519 transaction signature must be $SIGNATURE_BYTES bytes"
            }
            return original.copyOf().also { out ->
                signature.copyInto(
                    destination = out,
                    destinationOffset = signatureSectionOffset + signerIndex * SIGNATURE_BYTES,
                )
            }
        }

        fun primarySignature(transaction: ByteArray = original): ByteArray {
            require(transaction.size == original.size) { "Transaction size changed unexpectedly" }
            return transaction.copyOfRange(
                signatureSectionOffset,
                signatureSectionOffset + SIGNATURE_BYTES,
            )
        }
    }

    fun parseForSigner(
        transaction: ByteArray,
        signerPublicKey: ByteArray,
    ): Parsed {
        if (transaction.isEmpty()) throw Rejected(RejectionReason.EMPTY)
        if (transaction.size > MAX_TRANSACTION_BYTES) throw Rejected(RejectionReason.TOO_LARGE)
        if (signerPublicKey.size != PUBLIC_KEY_BYTES) throw Rejected(RejectionReason.SIGNER_NOT_REQUIRED)

        try {
            var cursor = 0
            val signatures = readShortVec(transaction, cursor)
            cursor = signatures.next
            val signatureCount = signatures.value
            if (signatureCount <= 0 || signatureCount > MAX_SIGNATURES) {
                throw Rejected(RejectionReason.MALFORMED)
            }

            val signatureSectionOffset = cursor
            requireRemaining(transaction, cursor, signatureCount * SIGNATURE_BYTES)
            cursor += signatureCount * SIGNATURE_BYTES
            val messageOffset = cursor

            requireRemaining(transaction, cursor, 1)
            if (u8(transaction, cursor) and 0x80 != 0) {
                throw Rejected(RejectionReason.VERSIONED_UNSUPPORTED)
            }

            requireRemaining(transaction, cursor, 3)
            val requiredSignatures = u8(transaction, cursor)
            val readonlySigned = u8(transaction, cursor + 1)
            val readonlyUnsigned = u8(transaction, cursor + 2)
            cursor += 3

            if (requiredSignatures <= 0 || requiredSignatures > MAX_SIGNATURES) {
                throw Rejected(RejectionReason.MALFORMED)
            }
            if (signatureCount != requiredSignatures) {
                throw Rejected(RejectionReason.SIGNATURE_COUNT_MISMATCH)
            }
            if (readonlySigned > requiredSignatures) {
                throw Rejected(RejectionReason.MALFORMED)
            }

            val accounts = readShortVec(transaction, cursor)
            cursor = accounts.next
            val accountCount = accounts.value
            if (accountCount < requiredSignatures || accountCount > MAX_ACCOUNTS) {
                throw Rejected(RejectionReason.MALFORMED)
            }
            if (readonlyUnsigned > accountCount - requiredSignatures) {
                throw Rejected(RejectionReason.MALFORMED)
            }

            requireRemaining(transaction, cursor, accountCount * PUBLIC_KEY_BYTES)
            var signerIndex = -1
            for (index in 0 until requiredSignatures) {
                val keyOffset = cursor + index * PUBLIC_KEY_BYTES
                if (rangeEquals(transaction, keyOffset, signerPublicKey)) {
                    if (signerIndex != -1) throw Rejected(RejectionReason.MALFORMED)
                    signerIndex = index
                }
            }
            if (signerIndex == -1) throw Rejected(RejectionReason.SIGNER_NOT_REQUIRED)
            cursor += accountCount * PUBLIC_KEY_BYTES

            requireRemaining(transaction, cursor, BLOCKHASH_BYTES)
            val recentBlockhash = transaction.copyOfRange(cursor, cursor + BLOCKHASH_BYTES)
            cursor += BLOCKHASH_BYTES

            val instructions = readShortVec(transaction, cursor)
            cursor = instructions.next
            if (instructions.value > MAX_INSTRUCTIONS) throw Rejected(RejectionReason.MALFORMED)

            repeat(instructions.value) {
                requireRemaining(transaction, cursor, 1)
                val programAccountIndex = u8(transaction, cursor++)
                if (programAccountIndex >= accountCount) throw Rejected(RejectionReason.MALFORMED)

                val accountIndexes = readShortVec(transaction, cursor)
                cursor = accountIndexes.next
                if (accountIndexes.value > MAX_ACCOUNT_INDEXES_PER_INSTRUCTION) {
                    throw Rejected(RejectionReason.MALFORMED)
                }
                requireRemaining(transaction, cursor, accountIndexes.value)
                repeat(accountIndexes.value) { offset ->
                    if (u8(transaction, cursor + offset) >= accountCount) {
                        throw Rejected(RejectionReason.MALFORMED)
                    }
                }
                cursor += accountIndexes.value

                val dataLength = readShortVec(transaction, cursor)
                cursor = dataLength.next
                requireRemaining(transaction, cursor, dataLength.value)
                cursor += dataLength.value
            }

            if (cursor != transaction.size) throw Rejected(RejectionReason.MALFORMED)

            return Parsed(
                original = transaction.copyOf(),
                message = transaction.copyOfRange(messageOffset, transaction.size),
                signerIndex = signerIndex,
                signatureCount = signatureCount,
                recentBlockhash = recentBlockhash,
                signatureSectionOffset = signatureSectionOffset,
            )
        } catch (rejected: Rejected) {
            throw rejected
        } catch (_: Throwable) {
            throw Rejected(RejectionReason.MALFORMED)
        }
    }

    private data class ShortVec(
        val value: Int,
        val next: Int,
    )

    private fun readShortVec(bytes: ByteArray, start: Int): ShortVec {
        var cursor = start
        var value = 0
        var shift = 0
        var consumed = 0
        repeat(3) {
            requireRemaining(bytes, cursor, 1)
            val current = u8(bytes, cursor++)
            consumed += 1
            value = value or ((current and 0x7f) shl shift)
            if (current and 0x80 == 0) {
                val canonicalLength = when {
                    value < 0x80 -> 1
                    value < 0x4000 -> 2
                    else -> 3
                }
                if (consumed != canonicalLength) throw Rejected(RejectionReason.MALFORMED)
                return ShortVec(value, cursor)
            }
            shift += 7
        }
        throw Rejected(RejectionReason.MALFORMED)
    }

    private fun rangeEquals(
        source: ByteArray,
        sourceOffset: Int,
        expected: ByteArray,
    ): Boolean {
        if (sourceOffset < 0 || sourceOffset > source.size - expected.size) return false
        for (index in expected.indices) {
            if (source[sourceOffset + index] != expected[index]) return false
        }
        return true
    }

    private fun u8(bytes: ByteArray, index: Int): Int = bytes[index].toInt() and 0xff

    private fun requireRemaining(bytes: ByteArray, cursor: Int, count: Int) {
        if (cursor < 0 || count < 0 || cursor > bytes.size - count) {
            throw Rejected(RejectionReason.MALFORMED)
        }
    }

    private const val MAX_SIGNATURES = 64
    private const val MAX_ACCOUNTS = 256
    private const val MAX_INSTRUCTIONS = 256
    private const val MAX_ACCOUNT_INDEXES_PER_INSTRUCTION = 256
}
