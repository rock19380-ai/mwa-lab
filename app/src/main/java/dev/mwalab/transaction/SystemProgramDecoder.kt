package dev.mwalab.transaction

/** SystemInstruction::Transfer: little-endian u32 tag 2 and little-endian u64 lamports. */
internal class SystemProgramDecoder : ProgramDecoder {
    override val program = KnownProgram.SYSTEM

    override fun decode(accounts: List<InstructionAccountReference>, data: ByteArray): InstructionDecoding {
        if (data.size < 4) return malformedInstruction(program, InstructionDecodingFailure.INVALID_DATA_LENGTH)
        if (data[0] != 2.toByte() || data[1] != 0.toByte() || data[2] != 0.toByte() || data[3] != 0.toByte()) {
            return InstructionDecoding(DecodedInstruction.Unsupported(program))
        }
        if (data.size != 12) return malformedInstruction(program, InstructionDecodingFailure.INVALID_DATA_LENGTH)
        if (accounts.size != 2) return malformedInstruction(program, InstructionDecodingFailure.INVALID_ACCOUNTS)
        if (accounts.any { it.account == null }) return unresolvedInstruction(program)
        return InstructionDecoding(DecodedInstruction.SystemTransfer(
            accounts[0].account!!.publicKey, accounts[1].account!!.publicKey, instructionU64(data, 4)))
    }
}
