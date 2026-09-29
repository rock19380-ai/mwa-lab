package dev.mwalab.transaction

import dev.mwalab.security.DiagnosticSanitizer
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class Phase4TransactionVectorsTest(private val name: String) {
    private val bytes = resource("$name.hex").trim().also { require(it.matches(Regex("[0-9a-f]+"))) }
        .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun expected(field: String) = requireNotNull(expectations.getProperty("$name.$field")) { "$name.$field missing" }
    private fun check(field: String, actual: Any?) = assertEquals("$name.$field", expected(field), actual?.toString() ?: "unavailable")

    @Test fun structuralAndDecoderDiagnosticsMatchIndependentFixtureExpectations() {
        val summary = TransactionInspector().inspect(bytes)
        val structural = SolanaWireTransactionParser().parse(bytes)
        check("fingerprint_sha256", summary.fingerprintSha256); check("wire_length", summary.wireLength)
        check("version", if (summary.transactionVersion == TransactionVersion.V0) "V0" else "LEGACY")
        check("status", summary.inspectionStatus.name); check("signature_count", summary.signatureCount)
        check("fee_payer", summary.feePayer); check("required_signer_count", summary.requiredSignerCount)
        check("account_count", summary.staticAccountCount); check("instruction_count", summary.instructionCount)
        assertEquals(structural.accounts, summary.accounts)
        assertEquals(structural.recentBlockhash, summary.recentBlockhash)
        assertEquals(structural.inspectionStatus, summary.inspectionStatus)
        if (summary.inspectionStatus == TransactionInspectionStatus.MALFORMED) {
            check("error", summary.error!!.reason.name); check("error_offset", summary.error.byteOffset)
            assertNull(summary.accounts); assertNull(summary.instructions); assertNull(summary.header)
            return
        }
        check("recent_blockhash", summary.recentBlockhash)
        check("account_keys", summary.accounts!!.joinToString(",") { it.publicKey })
        check("account_signers", summary.accounts.joinToString(",") { it.isSigner.toString() })
        check("account_writable", summary.accounts.joinToString(",") { it.isWritable.toString() })
        assertTrue(summary.accounts[0].isFeePayer)
        assertTrue(summary.accounts.drop(1).none { it.isFeePayer })
        val instruction = summary.instructions!!.single()
        check("program_id", instruction.programId)
        check("references", instruction.accountReferences.joinToString(",") { it.index.toString() })
        check("data_length", instruction.dataLength); check("data_sha256", instruction.dataSha256)
        when (val decoded = instruction.decodedInstruction) {
            DecodedInstruction.Unknown -> {
                check("decoded", "UNKNOWN"); assertNull(instruction.programName)
            }
            is DecodedInstruction.Memo -> {
                check("decoded", "MEMO"); check("memo_status", decoded.previewStatus.name)
            }
            is DecodedInstruction.SystemTransfer -> {
                check("decoded", "SYSTEM_TRANSFER"); check("decoded_from", decoded.from)
                check("decoded_to", decoded.to); check("lamports", decoded.lamports)
            }
            is DecodedInstruction.SplTokenTransfer -> {
                check("decoded", "SPL_TRANSFER"); check("source", decoded.source)
                check("destination", decoded.destination); check("authority", decoded.authority.publicKey)
                check("raw_amount", decoded.rawAmount)
            }
            is DecodedInstruction.SplTokenTransferChecked -> {
                check("decoded", "SPL_TRANSFER_CHECKED"); check("source", decoded.source)
                check("mint", decoded.mint); check("destination", decoded.destination)
                check("authority", decoded.authority.publicKey); check("raw_amount", decoded.rawAmount)
                check("declared_decimals", decoded.declaredDecimals)
            }
            is DecodedInstruction.Unavailable -> {
                check("decoded", "UNAVAILABLE"); check("decoding_reason", decoded.reason.name)
                check("total_account_count", summary.totalAccountCount)
                val unresolved = instruction.accountReferences.single { it.account == null }
                check("unresolved_reference", unresolved.index)
                assertTrue(TransactionInspectionLimitation.LOOKUP_ADDRESSES_UNRESOLVED in summary.limitations)
            }
            else -> fail("Unexpected decoded diagnostic")
        }
    }

    @Test fun unchangedSigningCodecStillRejectsVersionedAndInvalidFixtures() {
        val signer = ByteArray(32) { 0x11 }
        if (expected("status") == "PARSED") {
            val signed = LegacyTransactionCodec.parseForSigner(bytes, signer)
            assertArrayEquals(bytes.copyOfRange(65, bytes.size), signed.message)
            assertEquals(0, signed.signerIndex)
        } else {
            val failure = runCatching { LegacyTransactionCodec.parseForSigner(bytes, signer) }.exceptionOrNull()
            assertTrue(failure is LegacyTransactionCodec.Rejected)
            val reason = (failure as LegacyTransactionCodec.Rejected).reason
            assertEquals(if (expected("version") == "V0") LegacyTransactionCodec.RejectionReason.VERSIONED_UNSUPPORTED
                else LegacyTransactionCodec.RejectionReason.MALFORMED, reason)
        }
    }

    @Test fun exactWireFingerprintsAndRepeatedInspectionAreDeterministic() {
        check("fingerprint_sha256", DiagnosticSanitizer.sha256(bytes))
        val inspector = TransactionInspector()
        assertEquals(inspector.inspect(bytes), inspector.inspect(bytes))
        assertTrue(bytes.copyOfRange(1, 65).all { it == 0.toByte() })
    }

    companion object {
        private fun resource(name: String) = requireNotNull(Phase4TransactionVectorsTest::class.java.classLoader!!
            .getResourceAsStream(name)).bufferedReader().use { it.readText() }
        private val expectations = Properties().apply { load(resource("expected.properties").reader()) }
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<String>> = expectations.getProperty("cases").split(",").map { arrayOf(it) }
    }
}
