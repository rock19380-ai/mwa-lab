package dev.mwalab.democlient

import java.math.BigInteger

/**
 * Minimal deterministic legacy transaction factory for Phase 2 acceptance.
 *
 * The demo client owns dApp-side transaction construction. MWA Lab receives
 * only the serialized transaction and remains responsible for validation,
 * approval, signing, and (for sign-and-send) Devnet submission.
 */
object DemoLegacyTransactionFactory {
    const val SIGNATURE_BYTES = 64
    const val PUBLIC_KEY_BYTES = 32
    const val BLOCKHASH_BYTES = 32

    data class UnsignedTransaction(
        val transaction: ByteArray,
        val message: ByteArray,
        val recentBlockhash: ByteArray,
    )

    fun memoTransaction(
        feePayer: ByteArray,
        recentBlockhash: ByteArray,
        memo: ByteArray = DEFAULT_MEMO,
    ): UnsignedTransaction {
        require(feePayer.size == PUBLIC_KEY_BYTES) { "Fee payer must be 32 bytes" }
        require(recentBlockhash.size == BLOCKHASH_BYTES) { "Recent blockhash must be 32 bytes" }
        require(memo.isNotEmpty() && memo.size <= MAX_MEMO_BYTES) { "Memo length is out of bounds" }

        val memoProgram = decodeBase58(MEMO_PROGRAM_ID)
        require(memoProgram.size == PUBLIC_KEY_BYTES) { "Memo program ID is not a Solana public key" }

        val message = buildList<Byte> {
            // Message header: one required signature, writable signer, one
            // readonly unsigned account (the Memo program).
            add(1)
            add(0)
            add(1)

            addAll(shortVec(2).toList())
            addAll(feePayer.toList())
            addAll(memoProgram.toList())
            addAll(recentBlockhash.toList())

            addAll(shortVec(1).toList()) // one instruction
            add(1) // program-id account index
            addAll(shortVec(0).toList()) // no instruction accounts
            addAll(shortVec(memo.size).toList())
            addAll(memo.toList())
        }.toByteArray()

        val transaction = buildList<Byte> {
            addAll(shortVec(1).toList())
            repeat(SIGNATURE_BYTES) { add(0) }
            addAll(message.toList())
        }.toByteArray()

        require(transaction.size <= MAX_TRANSACTION_BYTES) { "Acceptance transaction exceeds Solana packet bound" }

        return UnsignedTransaction(
            transaction = transaction,
            message = message,
            recentBlockhash = recentBlockhash.copyOf(),
        )
    }

    fun primarySignature(
        signedTransaction: ByteArray,
        expectedMessage: ByteArray,
    ): ByteArray {
        require(signedTransaction.size >= 1 + SIGNATURE_BYTES + expectedMessage.size) {
            "Signed transaction is truncated"
        }
        require(signedTransaction[0].toInt() and 0xff == 1) {
            "Expected exactly one transaction signature"
        }
        val messageOffset = 1 + SIGNATURE_BYTES
        require(
            signedTransaction.copyOfRange(messageOffset, signedTransaction.size)
                .contentEquals(expectedMessage),
        ) {
            "Wallet changed transaction message bytes"
        }
        return signedTransaction.copyOfRange(1, 1 + SIGNATURE_BYTES)
    }

    fun decodeBase58(value: String): ByteArray {
        require(value.isNotEmpty()) { "Base58 value must not be empty" }
        var number = BigInteger.ZERO
        val radix = BigInteger.valueOf(58)
        value.forEach { character ->
            val digit = BASE58_ALPHABET.indexOf(character)
            require(digit >= 0) { "Invalid Base58 character" }
            number = number.multiply(radix).add(BigInteger.valueOf(digit.toLong()))
        }

        var body = if (number == BigInteger.ZERO) ByteArray(0) else number.toByteArray()
        if (body.size > 1 && body[0] == 0.toByte()) {
            body = body.copyOfRange(1, body.size)
        }
        val leadingZeros = value.takeWhile { it == '1' }.length
        return ByteArray(leadingZeros) + body
    }

    private fun shortVec(value: Int): ByteArray {
        require(value >= 0)
        var remaining = value
        val bytes = ArrayList<Byte>(3)
        do {
            var current = remaining and 0x7f
            remaining = remaining ushr 7
            if (remaining != 0) current = current or 0x80
            bytes += current.toByte()
        } while (remaining != 0)
        return bytes.toByteArray()
    }

    private const val MAX_TRANSACTION_BYTES = 1232
    private const val MAX_MEMO_BYTES = 256
    private const val BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private const val MEMO_PROGRAM_ID = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr"
    private val DEFAULT_MEMO = "MWA Lab Phase 2 live Devnet acceptance".encodeToByteArray()
}
