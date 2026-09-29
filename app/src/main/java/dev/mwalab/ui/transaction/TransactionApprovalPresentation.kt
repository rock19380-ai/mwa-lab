package dev.mwalab.ui.transaction

import dev.mwalab.transaction.*
import java.math.BigDecimal

/** Verified metadata presentation only; no parsing, wallet decision, signing, or RPC. */
object TransactionApprovalPresentation {
    fun lines(summary: TransactionSummary): List<String> = buildList {
        add("Version: " + when (val version = summary.transactionVersion) {
            TransactionVersion.LEGACY -> "legacy"
            TransactionVersion.V0 -> "v0"
            TransactionVersion.UNKNOWN -> "unknown"
            is TransactionVersion.VERSIONED_UNSUPPORTED -> "unsupported version ${version.number}"
        })
        add("Inspection: ${label(summary.inspectionStatus.name)}")
        add("Fee payer: ${summary.feePayer ?: "Unavailable"}")
        add("Required signer count: ${summary.requiredSignerCount ?: "Unavailable"}")
        summary.requiredSigners?.forEach { add("Required signer: ${it.publicKey}") }
        add("Recent blockhash: ${summary.recentBlockhash ?: "Unavailable"}")
        add("Instruction count: ${summary.instructionCount ?: "Unavailable"}")
        add("Wire length: ${summary.wireLength} bytes")
        add("MWA Lab payload fingerprint: ${summary.fingerprintSha256}")
        summary.error?.let { add("Structural error: ${label(it.reason.name)}") }
        summary.limitations.forEach { add("Limitation: ${label(it.name)}") }
        summary.instructions?.forEach { instruction ->
            add("Instruction ${instruction.index + 1}: ${instruction.programName ?: "Unknown Program"}")
            add("Program ID: ${instruction.programId}")
            instruction.accountReferences.forEach { reference ->
                add("Account #${reference.index}: ${reference.account?.publicKey ?: "Unresolved lookup account"}")
            }
            add("Instruction data: ${instruction.dataLength} bytes")
            add("Instruction data SHA-256: ${instruction.dataSha256}")
            when (val decoded = instruction.decodedInstruction) {
                DecodedInstruction.Unknown -> add("Unknown semantics")
                is DecodedInstruction.Unsupported -> add("Known program, unsupported instruction")
                is DecodedInstruction.Malformed -> add("Malformed known instruction: ${label(decoded.reason.name)}")
                is DecodedInstruction.Unavailable -> add("Instruction decoding unavailable: ${label(decoded.reason.name)}")
                is DecodedInstruction.SystemTransfer -> {
                    add("Operation: Transfer"); add("From: ${decoded.from}"); add("To: ${decoded.to}")
                    add("Lamports: ${decoded.lamports}")
                    add("DEVNET SOL: ${BigDecimal(decoded.lamports).movePointLeft(9).stripTrailingZeros().toPlainString()}")
                }
                is DecodedInstruction.Memo -> {
                    add("Memo display status: ${label(decoded.previewStatus.name)}")
                    add("Memo text is not included in diagnostic summaries.")
                }
                is DecodedInstruction.SplTokenTransfer -> {
                    add("Operation: Transfer"); add("Source: ${decoded.source}"); add("Destination: ${decoded.destination}")
                    add("Authority: ${decoded.authority.publicKey}"); add("Raw amount: ${decoded.rawAmount}")
                    decoded.authority.multisigSignerReferences.forEach { add("Multisig reference #${it.index}: ${it.account!!.publicKey}") }
                }
                is DecodedInstruction.SplTokenTransferChecked -> {
                    add("Operation: TransferChecked"); add("Source: ${decoded.source}"); add("Mint: ${decoded.mint}")
                    add("Destination: ${decoded.destination}"); add("Authority: ${decoded.authority.publicKey}")
                    add("Raw amount: ${decoded.rawAmount}"); add("Declared decimals (wire metadata): ${decoded.declaredDecimals}")
                    decoded.authority.multisigSignerReferences.forEach { add("Multisig reference #${it.index}: ${it.account!!.publicKey}") }
                }
            }
        }
    }

    private fun label(value: String) = value.lowercase().replace('_', ' ')
}
