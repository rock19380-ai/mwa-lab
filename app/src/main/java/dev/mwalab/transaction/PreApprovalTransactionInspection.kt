package dev.mwalab.transaction

import dev.mwalab.security.DiagnosticSanitizer

/** Null entries mean unavailable diagnostics at the original payload index; no raw bytes retained. */
class TransactionApprovalDiagnostics internal constructor(val payloadCount: Int, summaries: List<TransactionSummary?>) {
    val summaries = immutableDiagnosticList(summaries, payloadCount)
    val omittedPayloadCount: Int get() = payloadCount - summaries.size
    init {
        require(payloadCount >= 0)
        this.summaries.forEachIndexed { index, summary -> require(summary == null || summary.payloadIndex == index) }
    }
}

/** Request-local adapter with no approval, signer, RPC, recorder-write, or storage dependency. */
class PreApprovalTransactionInspection(private val inspector: TransactionInspection, private val maximumPayloads: Int) {
    init { require(maximumPayloads > 0) }

    /** Own signable input before hashing/inspection/preparation. Invalid large inputs are never copied. */
    fun ownPayloads(payloads: Array<ByteArray>): Array<ByteArray> =
        if (payloads.size > maximumPayloads) payloads else Array(payloads.size) { index ->
            val payload = payloads[index]
            if (payload.size <= TransactionInspectionLimits.MAX_TRANSACTION_BYTES) payload.copyOf() else payload
        }

    fun inspect(payloads: Array<ByteArray>, sessionId: String, eventId: String?): TransactionApprovalDiagnostics {
        val binding = try { eventId?.let { TransactionDiagnosticBinding(sessionId, it) } }
            catch (_: Exception) { null }
        val summaries = payloads.take(maximumPayloads).mapIndexed { index, payload ->
            try {
                // An injected diagnostic component cannot mutate input later given to the signing codec.
                val view = if (payload.size <= TransactionInspectionLimits.MAX_TRANSACTION_BYTES) payload.copyOf() else payload
                val summary = inspector.inspect(view, index, binding)
                require(summary.payloadIndex == index && summary.binding == binding)
                require(summary.wireLength == payload.size && summary.fingerprintSha256 == DiagnosticSanitizer.sha256(payload))
                summary
            } catch (_: Exception) {
                // Unavailable metadata only; never exception text or a replacement protocol result.
                null
            }
        }
        return TransactionApprovalDiagnostics(payloads.size, summaries)
    }
}
