package dev.mwalab.transaction

import java.math.BigInteger

/** Allowlisted, immutable diagnostics; never a claim of execution or authorization. */
sealed interface DecodedInstruction {
    val program: KnownProgram? get() = null
    data object Unknown : DecodedInstruction
    data class Unsupported(override val program: KnownProgram) : DecodedInstruction
    data class Malformed(override val program: KnownProgram, val reason: InstructionDecodingFailure) : DecodedInstruction
    data class Unavailable(override val program: KnownProgram, val reason: InstructionDecodingFailure) : DecodedInstruction

    data class SystemTransfer(val from: String, val to: String, val lamports: BigInteger) : DecodedInstruction {
        override val program get() = KnownProgram.SYSTEM
        init { requireDiagnosticKey(from); requireDiagnosticKey(to); requireDiagnosticU64(lamports) }
    }

    // Text is deliberately absent. MemoPreview is a separate transient decoder result.
    data class Memo(val previewStatus: MemoPreviewStatus) : DecodedInstruction {
        override val program get() = KnownProgram.MEMO
    }

    data class SplTokenTransfer(
        val source: String, val destination: String, val authority: TokenAuthority, val rawAmount: BigInteger,
    ) : DecodedInstruction {
        override val program get() = KnownProgram.SPL_TOKEN
        init { requireDiagnosticKey(source); requireDiagnosticKey(destination); requireDiagnosticU64(rawAmount) }
    }

    data class SplTokenTransferChecked(
        val source: String, val mint: String, val destination: String,
        val authority: TokenAuthority, val rawAmount: BigInteger, val declaredDecimals: Int,
    ) : DecodedInstruction {
        override val program get() = KnownProgram.SPL_TOKEN
        init {
            requireDiagnosticKey(source); requireDiagnosticKey(mint); requireDiagnosticKey(destination)
            requireDiagnosticU64(rawAmount); require(declaredDecimals in 0..255)
        }
    }
}

/** Positional authority and trailing multisig references; no single-signer inference. */
class TokenAuthority(val publicKey: String, multisigSignerReferences: List<InstructionAccountReference>) {
    val multisigSignerReferences = immutableDiagnosticList(multisigSignerReferences,
        TransactionInspectionLimits.MAX_ACCOUNTS_PER_INSTRUCTION)
    init {
        requireDiagnosticKey(publicKey)
        require(this.multisigSignerReferences.all { it.account != null })
    }
    override fun equals(other: Any?): Boolean = other is TokenAuthority && publicKey == other.publicKey &&
        multisigSignerReferences == other.multisigSignerReferences
    override fun hashCode(): Int = listOf(publicKey, multisigSignerReferences).hashCode()
}

enum class KnownProgram(val programId: String, val displayName: String) {
    SYSTEM("11111111111111111111111111111111", "System Program"),
    // Canonical Memo program used by the frozen repository Demo Client.
    MEMO("MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr", "Memo Program"),
    SPL_TOKEN("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA", "SPL Token Program"),
}

enum class InstructionDecodingFailure { INVALID_DATA_LENGTH, INVALID_ACCOUNTS, UNRESOLVED_ACCOUNTS, DECODER_FAILURE }
enum class MemoPreviewStatus { DISPLAYABLE, TRUNCATED, INVALID_UTF8, UNSAFE_TEXT }

private val diagnosticU64Maximum = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
internal fun requireDiagnosticU64(value: BigInteger) {
    require(value.signum() >= 0 && value <= diagnosticU64Maximum) { "Expected an exact unsigned 64-bit amount" }
}
