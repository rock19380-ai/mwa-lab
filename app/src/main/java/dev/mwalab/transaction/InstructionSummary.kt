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
    // Program identification/semantic decoding belongs to the next checkpoint.
    val programName: String? get() = null

    init {
        require(index in 0 until TransactionInspectionLimits.MAX_INSTRUCTIONS)
        require(programIdIndex in 1 until TransactionInspectionLimits.MAX_ACCOUNTS)
        requireDiagnosticKey(programId)
        require(dataLength in 0..TransactionInspectionLimits.MAX_INSTRUCTION_DATA_BYTES)
        requireDiagnosticHash(dataSha256)
    }

    override fun equals(other: Any?): Boolean = other is InstructionSummary &&
        index == other.index && programIdIndex == other.programIdIndex && programId == other.programId &&
        accountReferences == other.accountReferences && dataLength == other.dataLength &&
        dataSha256 == other.dataSha256 && decodedInstruction == other.decodedInstruction
    override fun hashCode(): Int = listOf(index, programIdIndex, programId, accountReferences,
        dataLength, dataSha256, decodedInstruction).hashCode()
}
