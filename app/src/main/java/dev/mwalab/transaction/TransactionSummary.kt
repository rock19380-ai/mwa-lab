package dev.mwalab.transaction

import dev.mwalab.protocol.EventId
import dev.mwalab.session.SessionId

/** Optional immutable binding supplied by a genuine recorder context, never invented by parsing. */
data class TransactionDiagnosticBinding(val sessionId: SessionId, val eventId: EventId) {
    init {
        require(sessionId.isNotBlank() && sessionId != "unknown" && sessionId.length <= 128)
        require(eventId.isNotBlank() && eventId != "unknown" && eventId.length <= 256)
        require(sessionId.none { it.isISOControl() } && eventId.none { it.isISOControl() })
    }
}

/** Safe read-only metadata; never retains transaction, signature, or instruction bytes. */
class TransactionSummary(
    val fingerprintSha256: String,
    val wireLength: Int,
    val transactionVersion: TransactionVersion,
    val inspectionStatus: TransactionInspectionStatus,
    // Canonically declared signature-vector count; signature bytes are never retained.
    val signatureCount: Int? = null,
    val header: TransactionMessageHeader? = null,
    val recentBlockhash: String? = null,
    accounts: List<AccountSummary>? = null,
    instructions: List<InstructionSummary>? = null,
    addressTableLookups: List<AddressTableLookupSummary>? = null,
    limitations: List<TransactionInspectionLimitation> = emptyList(),
    val error: TransactionInspectionError? = null,
    val payloadIndex: Int = 0,
    val binding: TransactionDiagnosticBinding? = null,
) {
    val accounts = accounts?.let { immutableDiagnosticList(it, TransactionInspectionLimits.MAX_ACCOUNTS) }
    val instructions = instructions?.let { immutableDiagnosticList(it, TransactionInspectionLimits.MAX_INSTRUCTIONS) }
    val addressTableLookups = addressTableLookups?.let {
        immutableDiagnosticList(it, TransactionInspectionLimits.MAX_ADDRESS_TABLE_LOOKUPS) }
    val limitations = immutableDiagnosticList(limitations, TransactionInspectionLimitation.entries.size)
    val sessionId: SessionId? get() = binding?.sessionId
    val eventId: EventId? get() = binding?.eventId
    // Internal diagnostic row identity, unrelated to a Solana transaction identifier/fingerprint.
    val diagnosticId: String? get() = eventId?.let { "$it:payload:$payloadIndex" }
    val staticAccountCount: Int? get() = accounts?.size
    /** Number of wire-declared account slots, not proof that lookup addresses exist on chain. */
    val totalAccountCount: Int? get() = staticAccountCount?.let { count ->
        addressTableLookups?.let { count + it.sumOf { lookup -> lookup.referencedAccountCount } } }
    val instructionCount: Int? get() = instructions?.size
    val feePayer: String? get() = accounts?.firstOrNull()?.publicKey
    val requiredSignerCount: Int? get() = header?.numRequiredSignatures
    val requiredSigners: List<AccountSummary>? get() = accounts?.let {
        immutableDiagnosticList(it.filter { account -> account.isSigner }, TransactionInspectionLimits.MAX_SIGNATURES) }

    init {
        requireDiagnosticHash(fingerprintSha256)
        require(wireLength >= 0 && payloadIndex >= 0)
        require(signatureCount == null || signatureCount in 1..TransactionInspectionLimits.MAX_SIGNATURES)
        require(this.limitations.distinct().size == this.limitations.size)
        require(error == null || error.byteOffset <= wireLength)
        when (inspectionStatus) {
            TransactionInspectionStatus.MALFORMED -> {
                require(error != null)
                require(header == null && recentBlockhash == null && accounts == null &&
                    instructions == null && addressTableLookups == null)
            }
            TransactionInspectionStatus.UNSUPPORTED_VERSION -> {
                require(wireLength in 1..TransactionInspectionLimits.MAX_TRANSACTION_BYTES)
                require(transactionVersion is TransactionVersion.VERSIONED_UNSUPPORTED && error == null)
                require(signatureCount != null && header == null && recentBlockhash == null &&
                    accounts == null && instructions == null && addressTableLookups == null)
                require(TransactionInspectionLimitation.UNSUPPORTED_MESSAGE_VERSION in this.limitations)
            }
            TransactionInspectionStatus.PARSED, TransactionInspectionStatus.PARTIAL -> {
                require(wireLength in 1..TransactionInspectionLimits.MAX_TRANSACTION_BYTES && error == null)
                require(signatureCount != null && header != null && signatureCount == header.numRequiredSignatures)
                require(recentBlockhash != null); requireDiagnosticKey(recentBlockhash)
                require(this.accounts != null && this.instructions != null && this.addressTableLookups != null)
                require(this.accounts.size >= header.numRequiredSignatures)
                require(header.numReadonlyUnsignedAccounts <= this.accounts.size - header.numRequiredSignatures)
                this.accounts.forEachIndexed { index, account ->
                    require(account.index == index && account.isFeePayer == (index == 0))
                    require(account.isSigner == header.isSigner(index))
                    require(account.isWritable == header.isWritable(index, this.accounts.size))
                }
                require(totalAccountCount!! <= TransactionInspectionLimits.MAX_ACCOUNTS)
                this.addressTableLookups.forEachIndexed { index, lookup -> require(lookup.index == index) }
                // Canonical structural metadata accounts for the exact original wire size.
                // All operands are bounded before these additions/multiplications.
                fun lengthBytes(value: Int) = if (value < 128) 1 else if (value < 16384) 2 else 3
                val instructionBytes = this.instructions.sumOf {
                    1 + lengthBytes(it.accountReferences.size) + it.accountReferences.size +
                        lengthBytes(it.dataLength) + it.dataLength
                }
                val lookupBytes = if (transactionVersion == TransactionVersion.V0) {
                    lengthBytes(this.addressTableLookups.size) + this.addressTableLookups.sumOf {
                        32 + lengthBytes(it.writableIndexes.size) + it.writableIndexes.size +
                            lengthBytes(it.readonlyIndexes.size) + it.readonlyIndexes.size
                    }
                } else 0
                val expectedLength = lengthBytes(signatureCount) + signatureCount * 64 +
                    (if (transactionVersion == TransactionVersion.V0) 1 else 0) + 3 +
                    lengthBytes(this.accounts.size) + this.accounts.size * 32 + 32 +
                    lengthBytes(this.instructions.size) + instructionBytes + lookupBytes
                require(wireLength == expectedLength) { "Diagnostic structure and wire length disagree" }
                this.instructions.forEachIndexed { index, instruction ->
                    require(instruction.index == index && instruction.programIdIndex < this.accounts.size)
                    require(instruction.programId == this.accounts[instruction.programIdIndex].publicKey)
                    instruction.accountReferences.forEach { reference ->
                        require(reference.index < totalAccountCount!!)
                        require(reference.account == this.accounts.getOrNull(reference.index))
                    }
                }
                if (inspectionStatus == TransactionInspectionStatus.PARSED) {
                    require(transactionVersion == TransactionVersion.LEGACY && this.addressTableLookups.isEmpty())
                } else {
                    require(transactionVersion == TransactionVersion.V0)
                    require(TransactionInspectionLimitation.VERSIONED_INSPECTION_PARTIAL in this.limitations)
                    require(this.addressTableLookups.isEmpty() ||
                        TransactionInspectionLimitation.LOOKUP_ADDRESSES_UNRESOLVED in this.limitations)
                }
            }
        }
    }

    internal fun bind(binding: TransactionDiagnosticBinding?, payloadIndex: Int) = TransactionSummary(
        fingerprintSha256, wireLength, transactionVersion, inspectionStatus, signatureCount, header,
        recentBlockhash, accounts, instructions, addressTableLookups, limitations, error, payloadIndex, binding)

    override fun equals(other: Any?): Boolean = other is TransactionSummary &&
        fingerprintSha256 == other.fingerprintSha256 && wireLength == other.wireLength &&
        transactionVersion == other.transactionVersion && inspectionStatus == other.inspectionStatus &&
        signatureCount == other.signatureCount && header == other.header && recentBlockhash == other.recentBlockhash &&
        accounts == other.accounts && instructions == other.instructions && addressTableLookups == other.addressTableLookups &&
        limitations == other.limitations && error == other.error && payloadIndex == other.payloadIndex && binding == other.binding
    override fun hashCode(): Int = listOf(fingerprintSha256, wireLength, transactionVersion, inspectionStatus,
        signatureCount, header, recentBlockhash, accounts, instructions, addressTableLookups,
        limitations, error, payloadIndex, binding).hashCode()
}
