package dev.mwalab.transaction

import dev.mwalab.mwa.capabilities.MwaCapabilityProfile
import org.junit.Assert.*
import org.junit.Test

/** Diagnostic disagreements cannot become signing acceptance/rejection rules. */
class TransactionInspectorAuthorityTest {
    @Test
    fun v0AndEveryUnknownVersionRemainRejectedByTheFrozenSigningCodec() {
        val inspector = TransactionInspector()
        for (version in listOf(0, 1, 2, 127)) {
            val bytes = TransactionWireFixtures.build(version = version).bytes
            val summary = inspector.inspect(bytes)
            assertEquals(if (version == 0) TransactionInspectionStatus.PARTIAL
                else TransactionInspectionStatus.UNSUPPORTED_VERSION, summary.inspectionStatus)
            assertRejected(LegacyTransactionCodec.RejectionReason.VERSIONED_UNSUPPORTED) {
                LegacyTransactionCodec.parseForSigner(bytes, ByteArray(32) { 0x11 })
            }
        }
        assertEquals(MwaCapabilityProfile.snapshot().supportedTransactionVersions,
            MwaCapabilityProfile.createWalletConfig().supportedTransactionVersions.toList())
        assertFalse(MwaCapabilityProfile.createWalletConfig().supportedTransactionVersions.contains(0))
    }

    @Test
    fun diagnosticSuccessDoesNotSatisfyRequiredSignerAndDoesNotPatchTransactionBytes() {
        val bytes = TransactionWireFixtures.legacy().bytes
        val original = bytes.copyOf()
        val summary = TransactionInspector().inspect(bytes)
        assertEquals(TransactionInspectionStatus.PARSED, summary.inspectionStatus)
        assertRejected(LegacyTransactionCodec.RejectionReason.SIGNER_NOT_REQUIRED) {
            LegacyTransactionCodec.parseForSigner(bytes, ByteArray(32) { 0x66 })
        }
        val parsed = LegacyTransactionCodec.parseForSigner(bytes, ByteArray(32) { 0x22 })
        assertEquals(1, parsed.signerIndex)
        val replacement = ByteArray(64) { 0x5a }
        val signed = parsed.withSignature(replacement)
        assertArrayEquals(original, bytes)
        assertArrayEquals(original.copyOfRange(1, 65), signed.copyOfRange(1, 65))
        assertArrayEquals(replacement, signed.copyOfRange(65, 129))
        assertArrayEquals(original.copyOfRange(129, original.size), signed.copyOfRange(129, signed.size))
    }

    @Test
    fun stricterDiagnosticHeaderSanitizationDoesNotTightenExistingSigningAcceptance() {
        // Solana's structural sanitizer requires a writable fee payer. The
        // existing frozen codec permits this header: inspecting it changes no authority.
        val bytes = TransactionWireFixtures.build(readonlySigned = 1).bytes
        val summary = TransactionInspector().inspect(bytes)
        assertEquals(TransactionInspectionStatus.MALFORMED, summary.inspectionStatus)
        assertEquals(TransactionInspectionFailure.INVALID_HEADER, summary.error!!.reason)
        assertNotNull(LegacyTransactionCodec.parseForSigner(bytes, ByteArray(32) { 0x11 }))
    }

    private fun assertRejected(reason: LegacyTransactionCodec.RejectionReason, block: () -> Any) {
        try { block(); fail("Expected frozen codec rejection") }
        catch (error: LegacyTransactionCodec.Rejected) { assertEquals(reason, error.reason) }
    }
}
