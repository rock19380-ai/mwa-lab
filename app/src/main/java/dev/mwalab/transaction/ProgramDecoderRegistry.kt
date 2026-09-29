package dev.mwalab.transaction

import dev.mwalab.security.DiagnosticSanitizer

/** Explicit ID dispatch; unknown IDs never acquire inferred names or operations. */
class ProgramDecoderRegistry internal constructor(decoders: List<ProgramDecoder>) {
    constructor() : this(listOf(SystemProgramDecoder(), MemoProgramDecoder(), SplTokenProgramDecoder()))

    private val decodersById: Map<String, ProgramDecoder> = decoders.associateBy { it.program.programId }.also {
        require(it.size == decoders.size) { "Duplicate diagnostic program decoder" }
    }

    internal fun decode(instruction: InstructionSummary, data: ByteArray): InstructionDecoding {
        val decoder = decodersById[instruction.programId] ?: return InstructionDecoding(DecodedInstruction.Unknown)
        return try {
            require(data.size == instruction.dataLength && data.size <= TransactionInspectionLimits.MAX_INSTRUCTION_DATA_BYTES)
            require(DiagnosticSanitizer.sha256(data) == instruction.dataSha256)
            val result = decoder.decode(instruction.accountReferences, data)
            require(result.decodedInstruction.program == decoder.program)
            // Validate domain/account-role relationships before publishing a result.
            instruction.withDecoding(result.decodedInstruction)
            result
        } catch (_: Exception) {
            // No exception/input text; a fault changes diagnostic availability only.
            InstructionDecoding(DecodedInstruction.Unavailable(decoder.program, InstructionDecodingFailure.DECODER_FAILURE))
        }
    }
}
