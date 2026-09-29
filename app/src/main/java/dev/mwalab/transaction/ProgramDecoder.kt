package dev.mwalab.transaction

/** Bounded diagnostic interpretation only; no wallet decision, storage, or RPC. */
internal interface ProgramDecoder {
    val program: KnownProgram
    fun decode(accounts: List<InstructionAccountReference>, data: ByteArray): InstructionDecoding
}

/** Ephemeral presentation only. No preview enters InstructionSummary or TransactionSummary. */
internal data class MemoPreview(val text: String, val truncated: Boolean) {
    override fun toString() = "MemoPreview(truncated=$truncated, text omitted)"
}

internal data class InstructionDecoding(
    val decodedInstruction: DecodedInstruction,
    val memoPreview: MemoPreview? = null,
)

internal fun malformedInstruction(program: KnownProgram, reason: InstructionDecodingFailure) =
    InstructionDecoding(DecodedInstruction.Malformed(program, reason))

internal fun unresolvedInstruction(program: KnownProgram) =
    InstructionDecoding(DecodedInstruction.Unavailable(program, InstructionDecodingFailure.UNRESOLVED_ACCOUNTS))

/** Called after exact instruction-size checks; unsigned and allocation-bounded. */
internal fun instructionU64(data: ByteArray, offset: Int) =
    java.math.BigInteger(1, data.copyOfRange(offset, offset + 8).reversedArray())
