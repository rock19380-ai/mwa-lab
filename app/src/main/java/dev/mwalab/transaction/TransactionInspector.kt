package dev.mwalab.transaction

/** Diagnostic facade only. It cannot approve, sign, reject an MWA request, persist, or call RPC. */
class TransactionInspector(
    private val parser: SolanaWireTransactionParser = SolanaWireTransactionParser(),
    private val decoders: ProgramDecoderRegistry = ProgramDecoderRegistry(),
) : TransactionInspection {
    override fun inspect(
        transaction: ByteArray,
        payloadIndex: Int,
        binding: TransactionDiagnosticBinding?,
    ): TransactionSummary = parser.parse(transaction, decoders).bind(binding, payloadIndex)
}
