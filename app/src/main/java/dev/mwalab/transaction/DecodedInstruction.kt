package dev.mwalab.transaction

/** Structural inspection assigns no operation, program name, amount, or token semantics. */
sealed interface DecodedInstruction {
    data object Unknown : DecodedInstruction
}
