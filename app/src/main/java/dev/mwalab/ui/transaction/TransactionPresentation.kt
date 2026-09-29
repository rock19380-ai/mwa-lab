package dev.mwalab.ui.transaction

import dev.mwalab.transaction.*
import java.math.BigDecimal

data class TransactionInspectorRow(val text: String, val heading: Boolean = false)
data class TransactionInspectorSection(val title: String, val rows: List<TransactionInspectorRow>)

/** Presentation of persisted, checked metadata only. No parser, RPC, or wallet authority. */
object TransactionPresentation {
    fun version(summary: TransactionSummary): String = when (val version = summary.transactionVersion) {
        TransactionVersion.LEGACY -> "legacy"
        TransactionVersion.V0 -> "v0"
        TransactionVersion.UNKNOWN -> "unknown"
        is TransactionVersion.VERSIONED_UNSUPPORTED -> "unsupported version ${version.number}"
    }

    fun status(summary: TransactionSummary): String = when (summary.inspectionStatus) {
        TransactionInspectionStatus.PARSED -> "Structural inspection complete"
        TransactionInspectionStatus.PARTIAL -> "Partial inspection"
        TransactionInspectionStatus.UNSUPPORTED_VERSION -> "Unsupported version"
        TransactionInspectionStatus.MALFORMED -> "Malformed transaction"
    }

    fun sections(summary: TransactionSummary): List<TransactionInspectorSection> = listOf(
        section("Overview") {
            line("Inspection status: ${status(summary)}")
            line("Version: ${version(summary)}")
            line("Wire length: ${summary.wireLength} bytes")
            line("MWA Lab payload fingerprint (SHA-256): ${summary.fingerprintSha256}")
            line("Diagnostic fingerprint of the received serialized payload; not proof of on-chain execution.")
            line("Fee payer: ${summary.feePayer ?: "Unavailable"}")
            line("Required signer count: ${summary.requiredSignerCount ?: "Unavailable"}")
            line("Recent blockhash: ${summary.recentBlockhash ?: "Unavailable"}")
            line("Instruction count: ${summary.instructionCount ?: "Unavailable"}")
            if (summary.transactionVersion == TransactionVersion.V0) {
                line("Partial inspection: only static accounts and wire metadata are available. Lookup-table addresses are unresolved. v0 signing is not supported.")
            }
            summary.error?.let { line("Structural error: ${label(it.reason.name)} at byte ${it.byteOffset}") }
            line("Diagnostics do not establish signing eligibility or the protocol result.")
        },
        section("Accounts") {
            line("Static account privileges are derived from the message header.")
            when {
                summary.accounts == null -> line("Static accounts unavailable for this inspection.")
                summary.accounts.isEmpty() -> line("No static accounts recorded.")
                else -> summary.accounts.forEach { account ->
                    line("Account #${account.index}", heading = true)
                    line("Public key: ${account.publicKey}")
                    line("${if (account.isSigner) "Signer" else "Not signer"} · ${if (account.isWritable) "Writable" else "Read-only"}${if (account.isFeePayer) " · Fee payer" else ""}")
                }
            }
            if (summary.transactionVersion == TransactionVersion.V0) {
                line("Loaded lookup-table accounts: unavailable; no network resolution was performed.")
            }
        },
        section("Instructions") {
            when {
                summary.instructions == null -> line("Instructions unavailable for this inspection.")
                summary.instructions.isEmpty() -> line("No compiled instructions.")
                else -> summary.instructions.forEach { instruction ->
                    line("Instruction #${instruction.index + 1}", heading = true)
                    line(instruction.programName ?: "Unknown Program")
                    line("Program ID: ${instruction.programId}")
                    line("Accounts:")
                    if (instruction.accountReferences.isEmpty()) line("No account references.")
                    instruction.accountReferences.forEach { reference ->
                        line("#${reference.index} → ${reference.account?.publicKey ?: "Unavailable (unresolved lookup account)"}")
                    }
                    when (val decoded = instruction.decodedInstruction) {
                        DecodedInstruction.Unknown -> line("Unknown semantics")
                        is DecodedInstruction.Unsupported -> line("Known program, unsupported instruction")
                        is DecodedInstruction.Malformed -> line("Malformed known instruction: ${label(decoded.reason.name)}")
                        is DecodedInstruction.Unavailable -> line("Decoded operation unavailable: ${label(decoded.reason.name)}")
                        is DecodedInstruction.SystemTransfer -> {
                            line("Operation: Transfer")
                            line("From: ${decoded.from}"); line("To: ${decoded.to}")
                            line("Lamports: ${decoded.lamports}")
                            line("DEVNET SOL: ${BigDecimal(decoded.lamports).movePointLeft(9).stripTrailingZeros().toPlainString()}")
                        }
                        is DecodedInstruction.Memo -> {
                            line("Memo display status: ${label(decoded.previewStatus.name)}")
                            line("Memo text was not retained. Only sanitized length, hash, and display status are stored.")
                        }
                        is DecodedInstruction.SplTokenTransfer -> {
                            line("Operation: Transfer")
                            line("Source: ${decoded.source}"); line("Destination: ${decoded.destination}")
                            tokenAuthority(decoded.authority); line("Raw amount: ${decoded.rawAmount}")
                        }
                        is DecodedInstruction.SplTokenTransferChecked -> {
                            line("Operation: TransferChecked")
                            line("Source: ${decoded.source}"); line("Mint: ${decoded.mint}")
                            line("Destination: ${decoded.destination}"); tokenAuthority(decoded.authority)
                            line("Raw amount: ${decoded.rawAmount}")
                            line("Declared decimals (wire metadata): ${decoded.declaredDecimals}")
                        }
                    }
                    line("Instruction data: ${instruction.dataLength} bytes")
                    line("Instruction data SHA-256: ${instruction.dataSha256}")
                }
            }
        },
        section("Raw metadata") {
            line("Sanitized metadata only; serialized transactions, signatures, and raw instruction bytes are not retained.")
            line("Payload index: ${summary.payloadIndex} (zero-based)")
            line("Declared signature count: ${summary.signatureCount ?: "Unavailable"}")
            line("Static account count: ${summary.staticAccountCount ?: "Unavailable"}")
            line("Wire-declared account slots: ${summary.totalAccountCount ?: "Unavailable"}")
            summary.header?.let {
                line("Header required signatures: ${it.numRequiredSignatures}")
                line("Header read-only signed accounts: ${it.numReadonlySignedAccounts}")
                line("Header read-only unsigned accounts: ${it.numReadonlyUnsignedAccounts}")
            }
            summary.addressTableLookups?.forEach { lookup ->
                line("Lookup #${lookup.index}: ${lookup.tableAccount}")
                line("Writable lookup indexes: ${lookup.writableIndexes.joinToString().ifEmpty { "None" }}")
                line("Read-only lookup indexes: ${lookup.readonlyIndexes.joinToString().ifEmpty { "None" }}")
            }
            summary.limitations.forEach { line("Limitation: ${label(it.name)}") }
        },
    )

    private fun MutableList<TransactionInspectorRow>.tokenAuthority(authority: TokenAuthority) {
        line("Authority: ${authority.publicKey}")
        authority.multisigSignerReferences.forEach {
            line("Multisig reference #${it.index}: ${it.account!!.publicKey}")
        }
    }
    private fun MutableList<TransactionInspectorRow>.line(text: String, heading: Boolean = false) {
        add(TransactionInspectorRow(text, heading))
    }
    private fun section(title: String, body: MutableList<TransactionInspectorRow>.() -> Unit) =
        TransactionInspectorSection(title, buildList(body))
    private fun label(value: String) = value.lowercase().replace('_', ' ')
}
