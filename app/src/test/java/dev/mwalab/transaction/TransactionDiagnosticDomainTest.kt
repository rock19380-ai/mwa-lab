package dev.mwalab.transaction

import dev.mwalab.security.DiagnosticSanitizer
import java.lang.reflect.Modifier
import java.util.IdentityHashMap
import org.junit.Assert.*
import org.junit.Test

class TransactionDiagnosticDomainTest {
    private val parser = SolanaWireTransactionParser()

    @Test
    fun inspectorUsesCanonicalPayloadFingerprintAndOwnedInputWithoutInventingProtocolBinding() {
        val bytes = TransactionWireFixtures.legacy().bytes
        val saved = bytes.copyOf()
        val inspector = TransactionInspector()
        val summary = inspector.inspect(bytes)
        assertEquals(TransactionWireFixtures.legacyFingerprint, summary.fingerprintSha256)
        assertEquals(DiagnosticSanitizer.sha256(saved), summary.fingerprintSha256)
        assertEquals(summary, inspector.inspect(saved))
        assertEquals(parser.parse(saved), summary)
        assertNull(summary.binding)
        assertNull(summary.sessionId)
        assertNull(summary.eventId)
        assertNull(summary.diagnosticId)
        bytes.fill(0x7f)
        assertEquals(summary, inspector.inspect(saved))
        assertEquals(TransactionWireFixtures.legacyKeys, summary.accounts!!.map { it.publicKey })
        assertEquals(TransactionWireFixtures.dataFingerprint, summary.instructions!![0].dataSha256)
    }

    @Test
    fun oneByteChangesInSignaturesKeysBlockhashAndInstructionDataChangePayloadFingerprint() {
        val v = TransactionWireFixtures.legacy()
        val original = TransactionInspector().inspect(v.bytes)
        for (offset in listOf(v.offset("signatures"), v.offset("accounts"),
            v.offset("blockhash"), v.offset("data0"))) {
            val changed = v.bytes.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }
            val summary = TransactionInspector().inspect(changed)
            assertEquals(TransactionInspectionStatus.PARSED, summary.inspectionStatus)
            assertNotEquals(original.fingerprintSha256, summary.fingerprintSha256)
            assertEquals(DiagnosticSanitizer.sha256(changed), summary.fingerprintSha256)
        }
    }

    @Test
    fun immutableBindingAndOriginalPayloadIndexRemainIndependentOfFingerprint() {
        val bytes = TransactionWireFixtures.legacy().bytes
        val inspector = TransactionInspector()
        val a = inspector.inspect(bytes, 2, TransactionDiagnosticBinding("session-a", "session-a:7"))
        val b = inspector.inspect(bytes, 3, TransactionDiagnosticBinding("session-b", "session-b:1"))
        assertEquals(a.fingerprintSha256, b.fingerprintSha256)
        assertEquals("session-a", a.sessionId)
        assertEquals("session-a:7", a.eventId)
        assertEquals(2, a.payloadIndex)
        assertEquals("session-a:7:payload:2", a.diagnosticId)
        assertEquals("session-b:1:payload:3", b.diagnosticId)
        assertNotEquals(a, b)
    }

    @Test
    fun allPublishedCollectionsAreOwnedAndCannotBeMutated() {
        val parsed = parser.parse(TransactionWireFixtures.v0().bytes)
        val accounts = parsed.accounts!!.toMutableList()
        val instructions = parsed.instructions!!.toMutableList()
        val lookups = parsed.addressTableLookups!!.toMutableList()
        val limitations = parsed.limitations.toMutableList()
        val owned = TransactionSummary(parsed.fingerprintSha256, parsed.wireLength, parsed.transactionVersion,
            parsed.inspectionStatus, parsed.signatureCount, parsed.header, parsed.recentBlockhash,
            accounts, instructions, lookups, limitations)
        accounts.clear(); instructions.clear(); lookups.clear(); limitations.clear()
        assertEquals(parsed, owned)
        val references = owned.instructions!!.single().accountReferences.toMutableList()
        val instruction = InstructionSummary(0, 1, owned.accounts!![1].publicKey, references, 2,
            owned.instructions[0].dataSha256)
        references.clear()
        assertEquals(4, instruction.accountReferences.size)
        val writable = mutableListOf(7)
        val readonly = mutableListOf(9)
        val lookup = AddressTableLookupSummary(0, TransactionWireFixtures.blockhash, writable, readonly)
        writable.clear(); readonly.clear()
        assertEquals(listOf(7), lookup.writableIndexes)
        assertEquals(listOf(9), lookup.readonlyIndexes)
        listOf<List<*>>(owned.accounts!!, owned.instructions!!, owned.addressTableLookups!!, owned.limitations,
            owned.requiredSigners!!, instruction.accountReferences, lookup.writableIndexes,
            lookup.readonlyIndexes).forEach { assertUnmodifiable(it) }
    }

    @Test
    fun invalidHashesKeysIdentitiesVersionsIndexesAndPrivilegesAreRejectedAsDomainMisuse() {
        val key = TransactionWireFixtures.legacyKeys[0]
        val hash = TransactionWireFixtures.dataFingerprint
        val badFactories: List<() -> Any> = listOf(
            { TransactionSummary("not-a-hash", 0, TransactionVersion.UNKNOWN, TransactionInspectionStatus.MALFORMED) },
            { TransactionSummary(hash.uppercase(), 0, TransactionVersion.UNKNOWN, TransactionInspectionStatus.MALFORMED) },
            { TransactionDiagnosticBinding("unknown", "event") },
            { TransactionDiagnosticBinding("session", "") },
            { TransactionDiagnosticBinding("session", "event\nsecret") },
            { TransactionDiagnosticBinding("session", "x".repeat(257)) },
            { TransactionVersion.VERSIONED_UNSUPPORTED(0) },
            { TransactionVersion.VERSIONED_UNSUPPORTED(128) },
            { TransactionMessageHeader(0, 0, 0) },
            { TransactionMessageHeader(2, 2, 0) },
            { TransactionMessageHeader(65, 0, 0) },
            { AccountSummary(256, key, false, false, false) },
            { AccountSummary(0, key, false, true, true) },
            { AccountSummary(0, key, true, false, true) },
            { AccountSummary(1, key, true, true, true) },
            { AccountSummary(1, "1".repeat(31), false, false, false) },
            { AccountSummary(1, "1".repeat(33), false, false, false) },
            { AccountSummary(1, "0".repeat(32), false, false, false) },
            { AccountSummary(1, "z".repeat(44), false, false, false) },
            { InstructionAccountReference(1, AccountSummary(0, key, true, true, true)) },
            { InstructionSummary(0, 0, key, emptyList(), 0, hash) },
            { InstructionSummary(0, 1, key, emptyList(), 1233, hash) },
            { InstructionSummary(0, 1, key, List(257) { InstructionAccountReference(0, null) }, 0, hash) },
            { AddressTableLookupSummary(0, key, emptyList(), emptyList()) },
            { AddressTableLookupSummary(0, key, listOf(256), emptyList()) },
            { TransactionInspectionError(TransactionInspectionFailure.EMPTY_PAYLOAD, -1) },
            { TransactionInspector().inspect(TransactionWireFixtures.legacy().bytes, -1) },
        )
        badFactories.forEachIndexed { index, factory ->
            try { factory(); fail("Invalid domain construction succeeded: $index") }
            catch (_: IllegalArgumentException) { /* Caller/domain misuse, not an input-parser failure. */ }
        }
    }

    @Test
    fun validAllZero32BytePublicMetadataAndNullableUnverifiedFieldsStayTruthful() {
        assertEquals("1".repeat(32), AccountSummary(0, "1".repeat(32), true, true, true).publicKey)
        val malformed = parser.parse(byteArrayOf())
        assertEquals(0, malformed.wireLength)
        assertNull(malformed.signatureCount)
        assertNull(malformed.staticAccountCount)
        assertNull(malformed.totalAccountCount)
        assertNull(malformed.instructionCount)
        assertNull(malformed.requiredSigners)
        assertNull(malformed.feePayer)
    }

    @Test
    fun statusesCountsReferencesAndHeaderPrivilegeInvariantsCannotBeContradicted() {
        val p = parser.parse(TransactionWireFixtures.legacy().bytes)
        fun summary(
            status: TransactionInspectionStatus = p.inspectionStatus,
            version: TransactionVersion = p.transactionVersion,
            signatures: Int? = p.signatureCount,
            header: TransactionMessageHeader? = p.header,
            accounts: List<AccountSummary>? = p.accounts,
            instructions: List<InstructionSummary>? = p.instructions,
            error: TransactionInspectionError? = null,
        ) = TransactionSummary(p.fingerprintSha256, p.wireLength, version, status, signatures, header,
            p.recentBlockhash, accounts, instructions, p.addressTableLookups, p.limitations, error)
        val invalid: List<() -> Any> = listOf(
            { summary(status = TransactionInspectionStatus.PARTIAL) },
            { summary(version = TransactionVersion.V0) },
            { summary(status = TransactionInspectionStatus.MALFORMED) },
            { summary(status = TransactionInspectionStatus.UNSUPPORTED_VERSION) },
            { summary(signatures = 1) },
            { summary(header = null) },
            { summary(accounts = p.accounts!!.reversed()) },
            { summary(accounts = p.accounts!!.map { if (it.index == 1) it.copy(isWritable = true) else it }) },
            { summary(instructions = p.instructions!!.reversed()) },
            { summary(instructions = listOf(InstructionSummary(0, 4, p.accounts!![3].publicKey,
                emptyList(), 0, TransactionWireFixtures.dataFingerprint))) },
            { summary(instructions = listOf(InstructionSummary(0, 4, p.accounts!![4].publicKey,
                listOf(InstructionAccountReference(2, null)), 0, TransactionWireFixtures.dataFingerprint))) },
            { summary(error = TransactionInspectionError(TransactionInspectionFailure.TRAILING_DATA, p.wireLength)) },
            { TransactionSummary(p.fingerprintSha256, p.wireLength + 1, p.transactionVersion,
                p.inspectionStatus, p.signatureCount, p.header, p.recentBlockhash, p.accounts,
                p.instructions, p.addressTableLookups, p.limitations) },
        )
        invalid.forEachIndexed { index, factory ->
            try { factory(); fail("Contradictory summary succeeded: $index") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test
    fun domainGraphNeverRetainsRawPayloadSignaturesOrUnknownInstructionData() {
        val sentinel = "RAW_INSTRUCTION_SECRET_PHASE4_MUST_NOT_BE_RETAINED".toByteArray()
        val bytes = TransactionWireFixtures.build(instructions =
            listOf(TransactionWireFixtures.Instruction(1, listOf(0), sentinel))).bytes
        val summary = TransactionInspector().inspect(bytes)
        val strings = mutableListOf<String>()
        val seen = IdentityHashMap<Any, Boolean>()
        fun inspect(value: Any?) {
            if (value == null || seen.put(value, true) != null) return
            when (value) {
                is ByteArray -> fail("Raw byte array in diagnostic domain graph")
                is String -> strings += value
                is Number, is Boolean, is Enum<*> -> Unit
                is Iterable<*> -> value.forEach { inspect(it) }
                else -> {
                    if (!value.javaClass.name.startsWith("dev.mwalab.transaction.")) return
                    value.javaClass.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.forEach {
                        assertNotEquals(ByteArray::class.java, it.type)
                        it.isAccessible = true
                        inspect(it.get(value))
                    }
                }
            }
        }
        inspect(summary)
        assertEquals(DiagnosticSanitizer.sha256(sentinel), summary.instructions!!.single().dataSha256)
        assertFalse(strings.any { it.contains(String(sentinel)) })
        assertFalse(strings.any { it == java.util.Base64.getEncoder().encodeToString(bytes) })
        bytes.fill(0)
        assertEquals(TransactionWireFixtures.legacyKeys[0], summary.feePayer)
    }

    private fun assertUnmodifiable(values: List<*>) {
        try {
            @Suppress("UNCHECKED_CAST")
            (values as MutableList<Any?>).clear()
            fail("Diagnostic collection was mutable")
        } catch (_: UnsupportedOperationException) { }
    }
}
