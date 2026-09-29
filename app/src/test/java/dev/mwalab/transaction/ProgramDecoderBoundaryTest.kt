package dev.mwalab.transaction

import dev.mwalab.security.DiagnosticSanitizer
import java.lang.reflect.Modifier
import java.math.BigInteger
import java.util.IdentityHashMap
import org.junit.Assert.*
import org.junit.Test

class ProgramDecoderBoundaryTest {
    private val fixtures = DecoderTestFixtures

    @Test
    fun decoderExceptionIsSanitizedAndCannotChangeSigningAcceptanceOrBytes() {
        val bytes = fixtures.transaction(KnownProgram.SYSTEM.programId, fixtures.systemTransfer)
        val original = bytes.copyOf()
        val parsedBefore = LegacyTransactionCodec.parseForSigner(bytes, ByteArray(32) { 0x11 })
        val throwing = object : ProgramDecoder {
            override val program = KnownProgram.SYSTEM
            override fun decode(accounts: List<InstructionAccountReference>, data: ByteArray): InstructionDecoding =
                throw IllegalStateException("RAW_EXCEPTION_SENTINEL")
        }
        val faulty = TransactionInspector(decoders = ProgramDecoderRegistry(listOf(throwing))).inspect(bytes)
        assertEquals(TransactionInspectionStatus.PARSED, faulty.inspectionStatus)
        assertEquals(DecodedInstruction.Unavailable(KnownProgram.SYSTEM, InstructionDecodingFailure.DECODER_FAILURE),
            faulty.instructions!!.single().decodedInstruction)
        assertFalse(domainStrings(faulty).any { it.contains("RAW_EXCEPTION_SENTINEL") })
        val parsedAfter = LegacyTransactionCodec.parseForSigner(bytes, ByteArray(32) { 0x11 })
        assertEquals(parsedBefore.signerIndex, parsedAfter.signerIndex)
        assertArrayEquals(parsedBefore.message, parsedAfter.message)
        assertArrayEquals(parsedBefore.withSignature(ByteArray(64) { 0x5a }),
            parsedAfter.withSignature(ByteArray(64) { 0x5a }))
        assertArrayEquals(original, bytes)
        // Decoder success cannot grant signer authority either.
        val successful = TransactionInspector().inspect(bytes)
        assertTrue(successful.instructions!!.single().decodedInstruction is DecodedInstruction.SystemTransfer)
        try {
            LegacyTransactionCodec.parseForSigner(bytes, ByteArray(32) { 0x66 })
            fail("Diagnostic success must not grant signing")
        } catch (error: LegacyTransactionCodec.Rejected) {
            assertEquals(LegacyTransactionCodec.RejectionReason.SIGNER_NOT_REQUIRED, error.reason)
        }
    }

    @Test
    fun malformedSemanticInstructionDoesNotTightenFrozenCodecAcceptance() {
        val bytes = fixtures.transaction(KnownProgram.SYSTEM.programId, fixtures.systemTransfer.copyOf(5))
        val diagnostic = TransactionInspector().inspect(bytes)
        assertEquals(TransactionInspectionStatus.PARSED, diagnostic.inspectionStatus)
        assertTrue(diagnostic.instructions!!.single().decodedInstruction is DecodedInstruction.Malformed)
        assertNotNull(LegacyTransactionCodec.parseForSigner(bytes, ByteArray(32) { 0x11 }))
    }

    @Test
    fun decodeOnlyRunsAfterWholeStructureValidationAndCannotMutateOwnedWireInput() {
        var calls = 0
        val mutating = object : ProgramDecoder {
            override val program = KnownProgram.SYSTEM
            override fun decode(accounts: List<InstructionAccountReference>, data: ByteArray): InstructionDecoding {
                calls++
                data.fill(0x7f)
                return InstructionDecoding(DecodedInstruction.Unsupported(program))
            }
        }
        val inspector = TransactionInspector(decoders = ProgramDecoderRegistry(listOf(mutating)))
        val bytes = fixtures.transaction(KnownProgram.SYSTEM.programId, fixtures.systemTransfer)
        val original = bytes.copyOf()
        val malformed = inspector.inspect(bytes + byteArrayOf(0))
        assertEquals(TransactionInspectionStatus.MALFORMED, malformed.inspectionStatus)
        assertEquals(0, calls)
        val diagnostic = inspector.inspect(bytes)
        assertEquals(1, calls)
        assertArrayEquals(original, bytes)
        assertEquals(DiagnosticSanitizer.sha256(original), diagnostic.fingerprintSha256)
        assertEquals("9d4669043442ae1dd96001d9abe61442bde1116add5910c2611d461b5592da16",
            diagnostic.instructions!!.single().dataSha256)
    }

    @Test
    fun wrongProgramOrAccountRoleOutputAndMismatchedDataCannotEscapeRegistryValidation() {
        val data = fixtures.systemTransfer
        val bytes = fixtures.transaction(KnownProgram.SYSTEM.programId, data)
        val instruction = SolanaWireTransactionParser().parse(bytes).instructions!!.single()
        val invalidResults = listOf(
            DecodedInstruction.Unsupported(KnownProgram.SPL_TOKEN),
            DecodedInstruction.SystemTransfer(TransactionWireFixtures.legacyKeys[1],
                TransactionWireFixtures.legacyKeys[0], BigInteger.TEN),
            DecodedInstruction.Unknown,
        )
        for (badResult in invalidResults) {
            val faulty = object : ProgramDecoder {
                override val program = KnownProgram.SYSTEM
                override fun decode(accounts: List<InstructionAccountReference>, data: ByteArray) =
                    InstructionDecoding(badResult)
            }
            val decoded = ProgramDecoderRegistry(listOf(faulty)).decode(instruction, data)
            assertEquals(DecodedInstruction.Unavailable(KnownProgram.SYSTEM, InstructionDecodingFailure.DECODER_FAILURE),
                decoded.decodedInstruction)
        }
        for (badData in listOf(data.copyOf(5), data.copyOf().also { it[11] = 1 })) {
            val decoded = ProgramDecoderRegistry().decode(instruction, badData)
            assertEquals(DecodedInstruction.Unavailable(KnownProgram.SYSTEM, InstructionDecodingFailure.DECODER_FAILURE),
                decoded.decodedInstruction)
        }
    }

    @Test
    fun rawUnknownInstructionAndMemoTextNeverEnterSummaryGraph() {
        val sentinel = "MOCK_CALLER_SECRET_SENTINEL_DO_NOT_RETAIN"
        val payload = sentinel.encodeToByteArray()
        for (programId in listOf(TransactionWireFixtures.blockhash, KnownProgram.MEMO.programId,
            KnownProgram.SYSTEM.programId, KnownProgram.SPL_TOKEN.programId)) {
            val summary = TransactionInspector().inspect(fixtures.transaction(programId, payload))
            val strings = domainStrings(summary)
            assertFalse(strings.any { it.contains(sentinel) })
            assertFalse(strings.any { it.contains(java.util.Base64.getEncoder().encodeToString(payload)) })
            assertFalse(strings.any { it.contains(payload.joinToString("") { "%02x".format(it) }) })
            assertEquals(DiagnosticSanitizer.sha256(payload), summary.instructions!!.single().dataSha256)
            assertEquals(payload.size, summary.instructions.single().dataLength)
        }
        val fields = listOf(DecodedInstruction.SplTokenTransfer::class.java,
            DecodedInstruction.SplTokenTransferChecked::class.java).flatMap { it.declaredFields.toList() }
        assertFalse(fields.any { it.type == Float::class.javaPrimitiveType || it.type == Double::class.javaPrimitiveType })
        assertFalse(fields.any { it.name in listOf("symbol", "name", "usdValue", "balance", "mintMetadata") })
    }

    @Test
    fun decoderDomainAmountsAccountsAndMultisigListsRemainCheckedAndImmutable() {
        val source = TransactionWireFixtures.legacyKeys[0]
        val destination = TransactionWireFixtures.legacyKeys[1]
        for (amount in listOf(BigInteger.valueOf(-1), fixtures.maximumU64 + BigInteger.ONE)) {
            assertInvalid { DecodedInstruction.SystemTransfer(source, destination, amount) }
            assertInvalid { DecodedInstruction.SplTokenTransfer(source, destination, TokenAuthority(source, emptyList()), amount) }
        }
        val signer = InstructionAccountReference(0, AccountSummary(0, source, true, true, true))
        val references = mutableListOf(signer)
        val authority = TokenAuthority(destination, references)
        references.clear()
        assertEquals(listOf(signer), authority.multisigSignerReferences)
        try {
            (authority.multisigSignerReferences as MutableList).clear()
            fail("Multisig references must be immutable")
        } catch (_: UnsupportedOperationException) { }
        assertInvalid { TokenAuthority(destination, listOf(InstructionAccountReference(2, null))) }
        assertInvalid { DecodedInstruction.SplTokenTransferChecked(source, destination, source, authority, BigInteger.ONE, 256) }
        assertInvalid { InstructionSummary(0, 1, KnownProgram.MEMO.programId, emptyList(), 12,
            DiagnosticSanitizer.sha256(fixtures.systemTransfer), DecodedInstruction.SystemTransfer(source, destination, BigInteger.ONE)) }
        assertInvalid { ProgramDecoderRegistry(listOf(SystemProgramDecoder(), SystemProgramDecoder())) }
    }

    @Test
    fun tableDrivenMalformedInstructionBytesAreDeterministicAndNeverThrow() {
        val random = java.util.Random(47)
        for (program in KnownProgram.entries) {
            repeat(256) {
                val data = ByteArray(it % 32).also { bytes -> random.nextBytes(bytes) }
                val bytes = fixtures.transaction(program.programId, data, listOf(0, 1, 2))
                val inspector = TransactionInspector()
                val a = inspector.inspect(bytes)
                assertEquals(a, inspector.inspect(bytes.copyOf()))
                assertEquals(TransactionInspectionStatus.PARSED, a.inspectionStatus)
                assertEquals(DiagnosticSanitizer.sha256(data), a.instructions!!.single().dataSha256)
                domainStrings(a)
            }
        }
    }

    private fun assertInvalid(block: () -> Any) {
        try { block(); fail("Expected checked diagnostic domain rejection") }
        catch (_: IllegalArgumentException) { }
    }

    /** Visits only the durable diagnostic graph; Java immutable numeric internals are not wire payloads. */
    private fun domainStrings(root: Any): List<String> {
        val seen = IdentityHashMap<Any, Boolean>()
        val strings = mutableListOf<String>()
        fun visit(value: Any?) {
            if (value == null || seen.put(value, true) != null) return
            assertFalse("No raw byte array in durable domain", value is ByteArray)
            assertFalse("No transient presentation in durable domain", value is MemoPreview || value is InstructionDecoding)
            when (value) {
                is String -> strings += value
                is Iterable<*> -> value.forEach(::visit)
                is Enum<*> -> Unit
                else -> if (value.javaClass.name.startsWith("dev.mwalab.transaction.")) {
                    value.javaClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.forEach {
                        it.isAccessible = true
                        visit(it.get(value))
                    }
                }
            }
        }
        visit(root)
        return strings
    }
}
