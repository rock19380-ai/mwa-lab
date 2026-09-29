package dev.mwalab.transaction

import dev.mwalab.mwa.capabilities.MwaCapabilityProfile
import dev.mwalab.security.DiagnosticSanitizer
import org.junit.Assert.*
import org.junit.Test

class PreApprovalTransactionInspectionTest {
    private val maximum = MwaCapabilityProfile.MAX_TRANSACTIONS_PER_SIGNING_REQUEST

    @Test
    fun payloadOwnershipBindsFingerprintToTheBytesUsedForPreparation() {
        val original = TransactionWireFixtures.legacy().bytes
        val saved = original.copyOf()
        val incoming = arrayOf(original)
        val helper = PreApprovalTransactionInspection(TransactionInspector(), maximum)
        val owned = helper.ownPayloads(incoming)
        original.fill(0x7f)
        incoming[0] = byteArrayOf(0)
        val summary = helper.inspect(owned, "session-a", "session-a:2").summaries.single()!!
        assertEquals(DiagnosticSanitizer.sha256(saved), summary.fingerprintSha256)
        assertArrayEquals(saved, owned.single())
        assertNotNull(LegacyTransactionCodec.parseForSigner(owned.single(), ByteArray(32) { 0x11 }))
        assertEquals(TransactionDiagnosticBinding("session-a", "session-a:2"), summary.binding)
    }

    @Test
    fun failedPayloadInspectionPreservesSlotsAndDoesNotHideLaterPayloads() {
        val delegate = TransactionInspector()
        val helper = PreApprovalTransactionInspection(object : TransactionInspection {
            override fun inspect(transaction: ByteArray, payloadIndex: Int, binding: TransactionDiagnosticBinding?): TransactionSummary {
                if (payloadIndex == 0) error("INSPECTOR_SECRET_EXCEPTION")
                return delegate.inspect(transaction, payloadIndex, binding)
            }
        }, maximum)
        val diagnostics = helper.inspect(arrayOf(TransactionWireFixtures.legacy().bytes,
            TransactionWireFixtures.legacy().bytes), "session", "session:3")
        assertEquals(2, diagnostics.payloadCount)
        assertNull(diagnostics.summaries[0])
        assertEquals(1, diagnostics.summaries[1]!!.payloadIndex)
        assertEquals("session:3", diagnostics.summaries[1]!!.eventId)
        assertEquals(0, diagnostics.omittedPayloadCount)
    }

    @Test
    fun inspectorMutationCannotChangeAuthoritativePayloadOrCanonicalFingerprint() {
        val saved = TransactionWireFixtures.legacy().bytes
        val delegate = TransactionInspector()
        val helper = PreApprovalTransactionInspection(object : TransactionInspection {
            override fun inspect(transaction: ByteArray, payloadIndex: Int, binding: TransactionDiagnosticBinding?): TransactionSummary {
                val summary = delegate.inspect(transaction, payloadIndex, binding)
                transaction.fill(0)
                return summary
            }
        }, maximum)
        val owned = helper.ownPayloads(arrayOf(saved))
        val diagnostics = helper.inspect(owned, "session", null)
        assertArrayEquals(saved, owned.single())
        assertEquals(DiagnosticSanitizer.sha256(saved), diagnostics.summaries.single()!!.fingerprintSha256)
        assertNull(diagnostics.summaries.single()!!.binding)
    }

    @Test
    fun foreignPayloadIndexesBindingsAndFingerprintsAreUnavailableInsteadOfMisleadingApproval() {
        for (mode in listOf("index", "binding", "fingerprint")) {
            val helper = PreApprovalTransactionInspection(object : TransactionInspection {
                override fun inspect(transaction: ByteArray, payloadIndex: Int, binding: TransactionDiagnosticBinding?): TransactionSummary =
                    TransactionInspector().inspect(if (mode == "fingerprint") transaction.copyOf().also { it[1] = 0 } else transaction,
                        if (mode == "index") 3 else payloadIndex, if (mode == "binding") null else binding)
            }, maximum)
            assertNull(helper.inspect(arrayOf(TransactionWireFixtures.legacy().bytes), "session", "session:2").summaries.single())
        }
    }

    @Test
    fun configuredRequestBoundIsAppliedOnlyToDiagnosticsAndOverLimitCountRemainsExact() {
        val payloads = Array(maximum + 2) { TransactionWireFixtures.legacy().bytes }
        val helper = PreApprovalTransactionInspection(TransactionInspector(), maximum)
        assertSame(payloads, helper.ownPayloads(payloads))
        val diagnostics = helper.inspect(payloads, "session", null)
        assertEquals(maximum + 2, diagnostics.payloadCount)
        assertEquals(maximum, diagnostics.summaries.size)
        assertEquals(2, diagnostics.omittedPayloadCount)
        assertEquals((0 until maximum).toList(), diagnostics.summaries.map { it!!.payloadIndex })
        try { (diagnostics.summaries as MutableList).clear(); fail("Owned immutable diagnostics") }
        catch (_: UnsupportedOperationException) { }
    }

    @Test
    fun oversizedAndMalformedInputsRemainDiagnosticMetadataWithoutAnExtraLargeCopy() {
        val bytes = ByteArray(TransactionInspectionLimits.MAX_TRANSACTION_BYTES + 1)
        val helper = PreApprovalTransactionInspection(TransactionInspector(), maximum)
        val owned = helper.ownPayloads(arrayOf(bytes))
        assertSame(bytes, owned.single())
        val summary = helper.inspect(owned, "session", null).summaries.single()!!
        assertEquals(TransactionInspectionStatus.MALFORMED, summary.inspectionStatus)
        assertEquals(TransactionInspectionFailure.TRANSACTION_TOO_LARGE, summary.error!!.reason)
        assertEquals(bytes.size, summary.wireLength)
        assertEquals(DiagnosticSanitizer.sha256(bytes), summary.fingerprintSha256)
    }
}
