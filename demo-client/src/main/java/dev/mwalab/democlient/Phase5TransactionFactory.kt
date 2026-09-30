package dev.mwalab.democlient

/** Public dApp-side templates. Callers supply a fresh Devnet blockhash for live use. */
enum class Phase5TransactionKind { GOOD_MEMO, BAD_SYSTEM_OPCODE }

object Phase5TransactionFactory {
    private val memo = "MWA Lab Phase 5 simulation acceptance".encodeToByteArray()

    fun build(kind: Phase5TransactionKind, feePayer: ByteArray, recentBlockhash: ByteArray):
        DemoLegacyTransactionFactory.UnsignedTransaction {
        require(feePayer.size == 32 && recentBlockhash.size == 32)
        if (kind == Phase5TransactionKind.GOOD_MEMO) {
            return DemoLegacyTransactionFactory.memoTransaction(feePayer, recentBlockhash, memo)
        }
        // Unknown System Program opcode is structurally valid but fails at runtime
        // after Devnet fee/blockhash preconditions. No product-side failure is injected.
        val message = byteArrayOf(1, 0, 1, 2) + feePayer + ByteArray(32) +
            recentBlockhash + byteArrayOf(1, 1, 0, 4, -1, -1, -1, -1)
        val transaction = byteArrayOf(1) + ByteArray(64) + message
        require(transaction.size <= 1232)
        return DemoLegacyTransactionFactory.UnsignedTransaction(
            transaction, message, recentBlockhash.copyOf())
    }
}
