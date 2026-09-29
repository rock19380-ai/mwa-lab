package dev.mwalab.democlient

/** Small dApp-side public transaction construction; never wallet validation or signing. */
object Phase4TransactionFactory {
    const val TRANSFER_LAMPORTS = 10_000_000L
    val destinationBase58: String get() = com.funkatronics.encoders.Base58.encodeToString(ByteArray(32) { 0x22 })

    fun build(scenario: Phase4AcceptanceScenario, feePayer: ByteArray, recentBlockhash: ByteArray):
        DemoLegacyTransactionFactory.UnsignedTransaction {
        require(feePayer.size == 32 && recentBlockhash.size == 32)
        val versioned = scenario == Phase4AcceptanceScenario.V0_REJECT
        val unknown = scenario == Phase4AcceptanceScenario.UNKNOWN_PROGRAM
        val data = if (unknown) byteArrayOf(1, 2, 3) else byteArrayOf(2, 0, 0, 0) +
            ByteArray(8) { (TRANSFER_LAMPORTS ushr (it * 8)).toByte() }
        val message = buildList<Byte> {
            if (versioned) add(0x80.toByte())
            addAll(byteArrayOf(1, 0, 1, 3).toList())
            addAll(feePayer.toList()); addAll(ByteArray(32) { 0x22 }.toList())
            addAll(ByteArray(32) { if (unknown) 0x55 else 0 }.toList())
            addAll(recentBlockhash.toList())
            addAll(byteArrayOf(1, 2, 2, 0, if (versioned) 3 else 1, data.size.toByte()).toList())
            addAll(data.toList())
            if (versioned) {
                add(1) // One descriptor; loaded account #3 deliberately remains unresolved.
                addAll(ByteArray(32) { 0x66 }.toList())
                addAll(byteArrayOf(1, 7, 0).toList())
            }
        }.toByteArray()
        val transaction = byteArrayOf(1) + ByteArray(64) + message
        require(transaction.size <= 1232)
        return DemoLegacyTransactionFactory.UnsignedTransaction(transaction, message, recentBlockhash.copyOf())
    }
}
