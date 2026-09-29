package dev.mwalab.transaction

/** Read-only seam; no wallet validation, approval, signing, submission, or persistence authority. */
interface TransactionInspection {
    fun inspect(transaction: ByteArray, payloadIndex: Int = 0, binding: TransactionDiagnosticBinding? = null): TransactionSummary
}
