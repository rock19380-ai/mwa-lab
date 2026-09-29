package dev.mwalab.ui.transaction

import dev.mwalab.transaction.*
import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class TransactionApprovalPresentationTest {
    @Test
    fun systemTransferUsesExactLamportsAndSolAndDisplaysVerifiedOverview() {
        val summary = TransactionInspector().inspect(DecoderTestFixtures.transaction(KnownProgram.SYSTEM.programId,
            DecoderTestFixtures.systemTransfer))
        val lines = TransactionApprovalPresentation.lines(summary)
        for (line in listOf("Version: legacy", "Required signer count: 1", "Instruction count: 1",
            "Operation: Transfer", "Lamports: 10000000", "DEVNET SOL: 0.01",
            "Fee payer: ${summary.feePayer}", "Recent blockhash: ${summary.recentBlockhash}",
            "MWA Lab payload fingerprint: ${summary.fingerprintSha256}")) assertTrue(line, line in lines)
        for ((amount, sol) in listOf(BigInteger.ONE to "0.000000001",
            DecoderTestFixtures.maximumU64 to "18446744073.709551615")) {
            val vector = DecoderTestFixtures.transaction(KnownProgram.SYSTEM.programId,
                DecoderTestFixtures.unsignedData(byteArrayOf(2, 0, 0, 0), amount))
            assertTrue("DEVNET SOL: $sol" in TransactionApprovalPresentation.lines(TransactionInspector().inspect(vector)))
        }
    }

    @Test
    fun unknownProgramShowsRealProgramReferencesAndLengthHashWithoutInventedOperation() {
        val summary = TransactionInspector().inspect(DecoderTestFixtures.transaction(TransactionWireFixtures.blockhash,
            DecoderTestFixtures.systemTransfer))
        val lines = TransactionApprovalPresentation.lines(summary)
        assertTrue("Instruction 1: Unknown Program" in lines)
        assertTrue("Program ID: ${TransactionWireFixtures.blockhash}" in lines)
        assertTrue("Instruction data: 12 bytes" in lines)
        assertTrue(lines.any { it.startsWith("Instruction data SHA-256: ") })
        assertFalse(lines.any { it.startsWith("Operation:") || it.startsWith("Lamports:") })
    }

    @Test
    fun partialV0AndMalformedMetadataNeverInventMissingStructure() {
        val partial = TransactionInspector().inspect(TransactionWireFixtures.v0().bytes)
        val lines = TransactionApprovalPresentation.lines(partial)
        assertTrue("Version: v0" in lines)
        assertTrue("Inspection: partial" in lines)
        assertTrue(lines.any { it.contains("Unresolved lookup account") })
        val malformed = TransactionApprovalPresentation.lines(TransactionInspector().inspect(byteArrayOf()))
        assertTrue("Inspection: malformed" in malformed)
        assertTrue("Fee payer: Unavailable" in malformed)
        assertTrue("Required signer count: Unavailable" in malformed)
    }

    @Test
    fun tokenPresentationRetainsRawAmountAndDeclaredDecimalsOnlyAndMemoTextIsAbsent() {
        val token = TransactionInspector().inspect(DecoderTestFixtures.transaction(KnownProgram.SPL_TOKEN.programId,
            DecoderTestFixtures.tokenTransferChecked, listOf(0, 1, 2, 0)))
        val lines = TransactionApprovalPresentation.lines(token)
        assertTrue("Operation: TransferChecked" in lines)
        assertTrue("Raw amount: 1000000" in lines)
        assertTrue("Declared decimals (wire metadata): 6" in lines)
        assertFalse(lines.any { it.contains("USD") || it.contains("symbol") || it.contains("balance") })
        val secret = "MEMO_TEXT_NOT_FOR_DURABLE_SUMMARY"
        val memo = TransactionInspector().inspect(DecoderTestFixtures.transaction(KnownProgram.MEMO.programId,
            secret.encodeToByteArray(), emptyList()))
        assertFalse(TransactionApprovalPresentation.lines(memo).any { it.contains(secret) })
    }
}
