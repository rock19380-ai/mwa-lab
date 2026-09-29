package dev.mwalab.transaction

/** Classic TokenInstruction tags 3/12 only. Token-2022 and mint metadata are outside this registry. */
internal class SplTokenProgramDecoder : ProgramDecoder {
    override val program = KnownProgram.SPL_TOKEN

    override fun decode(accounts: List<InstructionAccountReference>, data: ByteArray): InstructionDecoding {
        if (data.isEmpty()) return malformedInstruction(program, InstructionDecodingFailure.INVALID_DATA_LENGTH)
        val tag = data[0].toInt() and 0xff
        if (tag != 3 && tag != 12) return InstructionDecoding(DecodedInstruction.Unsupported(program))
        val checked = tag == 12
        if (data.size != (if (checked) 10 else 9)) {
            return malformedInstruction(program, InstructionDecodingFailure.INVALID_DATA_LENGTH)
        }
        val requiredAccounts = if (checked) 4 else 3
        if (accounts.size < requiredAccounts) return malformedInstruction(program, InstructionDecodingFailure.INVALID_ACCOUNTS)
        if (accounts.any { it.account == null }) return unresolvedInstruction(program)
        val authority = TokenAuthority(accounts[requiredAccounts - 1].account!!.publicKey, accounts.drop(requiredAccounts))
        val amount = instructionU64(data, 1)
        return InstructionDecoding(if (checked) {
            DecodedInstruction.SplTokenTransferChecked(accounts[0].account!!.publicKey,
                accounts[1].account!!.publicKey, accounts[2].account!!.publicKey, authority, amount,
                data[9].toInt() and 0xff)
        } else {
            DecodedInstruction.SplTokenTransfer(accounts[0].account!!.publicKey,
                accounts[1].account!!.publicKey, authority, amount)
        })
    }
}
