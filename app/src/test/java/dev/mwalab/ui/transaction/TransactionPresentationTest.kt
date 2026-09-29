package dev.mwalab.ui.transaction

import dev.mwalab.transaction.*
import org.junit.Assert.*
import org.junit.Test

class TransactionPresentationTest {
    private val inspector = TransactionInspector()
    private fun lines(summary: TransactionSummary) = TransactionPresentation.sections(summary).flatMap { it.rows }.map { it.text }
    private fun decoded(program: KnownProgram, data: ByteArray, references: List<Int> = listOf(0, 1), keys: Int = 4) =
        inspector.inspect(DecoderTestFixtures.transaction(program.programId, data, references, keyCount = keys))

    @Test fun legacyOverviewAccountsAndInstructionReferencesAreExact() {
        val summary = inspector.inspect(TransactionWireFixtures.legacy().bytes)
        val sections = TransactionPresentation.sections(summary)
        assertEquals(listOf("Overview", "Accounts", "Instructions", "Raw metadata"), sections.map { it.title })
        val text = lines(summary)
        assertTrue(text.contains("Inspection status: Structural inspection complete"))
        assertTrue(text.contains("Version: legacy"))
        assertTrue(text.contains("Required signer count: 2"))
        assertTrue(text.contains("Recent blockhash: ${TransactionWireFixtures.blockhash}"))
        assertTrue(text.contains("Instruction count: 2"))
        assertTrue(text.contains("Signer · Writable · Fee payer"))
        assertTrue(text.contains("Signer · Read-only"))
        assertTrue(text.contains("Not signer · Writable"))
        assertTrue(text.contains("Not signer · Read-only"))
        assertTrue(text.contains("#2 → ${TransactionWireFixtures.legacyKeys[2]}"))
        assertTrue(text.contains("MWA Lab payload fingerprint (SHA-256): ${summary.fingerprintSha256}"))
    }

    @Test fun systemTransferShowsExactUnsignedLamportsAndSolWithoutRounding() {
        val data = DecoderTestFixtures.unsignedData(byteArrayOf(2, 0, 0, 0), DecoderTestFixtures.maximumU64)
        val text = lines(decoded(KnownProgram.SYSTEM, data))
        assertTrue(text.contains("System Program"))
        assertTrue(text.contains("Operation: Transfer"))
        assertTrue(text.contains("From: ${TransactionWireFixtures.legacyKeys[0]}"))
        assertTrue(text.contains("To: ${TransactionWireFixtures.legacyKeys[1]}"))
        assertTrue(text.contains("Lamports: 18446744073709551615"))
        assertTrue(text.contains("DEVNET SOL: 18446744073.709551615"))
    }

    @Test fun memoRendersPersistedStatusLengthAndHashWithoutText() {
        val bytes = "MEMO_TEXT_SENTINEL".toByteArray()
        val summary = decoded(KnownProgram.MEMO, bytes, emptyList(), 2)
        val text = lines(summary)
        assertTrue(text.contains("Memo Program"))
        assertTrue(text.contains("Memo display status: displayable"))
        assertTrue(text.any { it.startsWith("Memo text was not retained.") })
        assertTrue(text.contains("Instruction data: ${bytes.size} bytes"))
        assertTrue(text.contains("Instruction data SHA-256: ${summary.instructions!!.single().dataSha256}"))
        assertFalse(text.joinToString().contains("MEMO_TEXT_SENTINEL"))
    }

    @Test fun splTransferAndCheckedUseOnlyWireAmountsAndDeclaredDecimals() {
        val transfer = lines(decoded(KnownProgram.SPL_TOKEN, DecoderTestFixtures.tokenTransfer, listOf(0, 1, 2)))
        assertTrue(transfer.contains("SPL Token Program"))
        assertTrue(transfer.contains("Raw amount: 1000000"))
        assertTrue(transfer.contains("Authority: ${TransactionWireFixtures.legacyKeys[2]}"))
        val checked = lines(decoded(KnownProgram.SPL_TOKEN, DecoderTestFixtures.tokenTransferChecked, listOf(0, 1, 2, 3), 5))
        assertTrue(checked.contains("Operation: TransferChecked"))
        assertTrue(checked.contains("Mint: ${TransactionWireFixtures.legacyKeys[1]}"))
        assertTrue(checked.contains("Declared decimals (wire metadata): 6"))
        assertFalse((transfer + checked).any { it.contains("USD") || it.contains("symbol") || it.contains("balance") })
    }

    @Test fun unknownProgramHasExplicitUnknownSemanticsAndSanitizedDataOnly() {
        val summary = inspector.inspect(TransactionWireFixtures.legacy().bytes)
        val text = lines(summary)
        assertTrue(text.contains("Unknown Program"))
        assertTrue(text.contains("Unknown semantics"))
        assertTrue(text.contains("Program ID: ${TransactionWireFixtures.legacyKeys[4]}"))
        assertTrue(text.contains("Instruction data: 3 bytes"))
        assertTrue(text.contains("Instruction data SHA-256: ${TransactionWireFixtures.dataFingerprint}"))
    }

    @Test fun partialV0NeverFabricatesLoadedAddressesOrEnablesSigning() {
        val summary = inspector.inspect(TransactionWireFixtures.v0().bytes)
        val text = lines(summary)
        assertTrue(text.contains("Version: v0"))
        assertTrue(text.contains("Inspection status: Partial inspection"))
        assertTrue(text.contains("#2 → Unavailable (unresolved lookup account)"))
        assertTrue(text.any { it.contains("v0 signing is not supported") })
        assertTrue(text.contains("Loaded lookup-table accounts: unavailable; no network resolution was performed."))
    }

    @Test fun malformedDiagnosticsRetainOnlyAvailableMetadata() {
        val summary = inspector.inspect(byteArrayOf())
        val text = lines(summary)
        assertTrue(text.contains("Inspection status: Malformed transaction"))
        assertTrue(text.contains("Version: unknown"))
        assertTrue(text.contains("Fee payer: Unavailable"))
        assertTrue(text.contains("Instruction count: Unavailable"))
        assertTrue(text.contains("Static accounts unavailable for this inspection."))
        assertTrue(text.contains("Instructions unavailable for this inspection."))
        assertTrue(text.contains("Structural error: empty payload at byte 0"))
    }

    @Test fun unsupportedVersionAndKnownUnsupportedInstructionAreDistinct() {
        val version = inspector.inspect(TransactionWireFixtures.build(version = 1).bytes)
        assertEquals("unsupported version 1", TransactionPresentation.version(version))
        assertEquals("Unsupported version", TransactionPresentation.status(version))
        val known = lines(decoded(KnownProgram.SYSTEM, byteArrayOf(1, 0, 0, 0)))
        assertTrue(known.contains("System Program"))
        assertTrue(known.contains("Known program, unsupported instruction"))
        assertFalse(known.contains("Unknown Program"))
        assertFalse(known.contains("Operation: Transfer"))
    }
}
