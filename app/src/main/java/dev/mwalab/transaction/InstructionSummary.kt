package dev.mwalab.transaction

class InstructionSummary(
    val index: Int,
    val programIdIndex: Int,
    val programId: String,
    accountReferences: List<InstructionAccountReference>,
    val dataLength: Int,
    val dataSha256: String,
    val decodedInstruction: DecodedInstruction = DecodedInstruction.Unknown,
) {
    val accountReferences = immutableDiagnosticList(accountReferences,
        TransactionInspectionLimits.MAX_ACCOUNTS_PER_INSTRUCTION)
    val programName: String? get() = decodedInstruction.program?.displayName

    init {
        require(index in 0 until TransactionInspectionLimits.MAX_INSTRUCTIONS)
        require(programIdIndex in 1 until TransactionInspectionLimits.MAX_ACCOUNTS)
        requireDiagnosticKey(programId)
        require(dataLength in 0..TransactionInspectionLimits.MAX_INSTRUCTION_DATA_BYTES)
        requireDiagnosticHash(dataSha256)
        require(decodedInstruction.program == null || decodedInstruction.program!!.programId == programId)
        fun key(position: Int) = this.accountReferences.getOrNull(position)?.account?.publicKey
        when (decodedInstruction) {
            is DecodedInstruction.SystemTransfer -> require(dataLength == 12 && this.accountReferences.size == 2 &&
                decodedInstruction.from == key(0) && decodedInstruction.to == key(1))
            is DecodedInstruction.SplTokenTransfer -> require(dataLength == 9 && this.accountReferences.size >= 3 &&
                decodedInstruction.source == key(0) && decodedInstruction.destination == key(1) &&
                decodedInstruction.authority.publicKey == key(2) &&
                decodedInstruction.authority.multisigSignerReferences == this.accountReferences.drop(3))
            is DecodedInstruction.SplTokenTransferChecked -> require(dataLength == 10 && this.accountReferences.size >= 4 &&
                decodedInstruction.source == key(0) && decodedInstruction.mint == key(1) &&
                decodedInstruction.destination == key(2) && decodedInstruction.authority.publicKey == key(3) &&
                decodedInstruction.authority.multisigSignerReferences == this.accountReferences.drop(4))
            else -> Unit
        }
    }

    internal fun withDecoding(decoded: DecodedInstruction) = InstructionSummary(index, programIdIndex, programId,
        accountReferences, dataLength, dataSha256, decoded)

    override fun equals(other: Any?): Boolean = other is InstructionSummary &&
        index == other.index && programIdIndex == other.programIdIndex && programId == other.programId &&
        accountReferences == other.accountReferences && dataLength == other.dataLength &&
        dataSha256 == other.dataSha256 && decodedInstruction == other.decodedInstruction
    override fun hashCode(): Int = listOf(index, programIdIndex, programId, accountReferences,
        dataLength, dataSha256, decodedInstruction).hashCode()
}
