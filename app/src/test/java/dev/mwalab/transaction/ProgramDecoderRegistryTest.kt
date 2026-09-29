package dev.mwalab.transaction

import dev.mwalab.security.DiagnosticSanitizer
import java.math.BigInteger
import org.junit.Assert.*
import org.junit.Test

class ProgramDecoderRegistryTest {
    private val fixtures = DecoderTestFixtures

    @Test
    fun systemTransferHasVerifiedRolesAndExactLamports() {
        val summary = fixtures.inspect(KnownProgram.SYSTEM, fixtures.systemTransfer)
        assertEquals("System Program", summary.programName)
        assertEquals("11111111111111111111111111111111", summary.programId)
        val transfer = summary.decodedInstruction as DecodedInstruction.SystemTransfer
        assertEquals(TransactionWireFixtures.legacyKeys[0], transfer.from)
        assertEquals(TransactionWireFixtures.legacyKeys[1], transfer.to)
        assertEquals(BigInteger("10000000"), transfer.lamports)
        assertEquals("9d4669043442ae1dd96001d9abe61442bde1116add5910c2611d461b5592da16", summary.dataSha256)
        assertEquals(12, summary.dataLength)
        assertTrue(summary.accountReferences[0].account!!.isSigner)
        assertTrue(summary.accountReferences[1].account!!.isWritable)
    }

    @Test
    fun unsignedAmountsRetainZeroHighBitAndMaximumWithoutRoundingOrOverflow() {
        for (amount in listOf(BigInteger.ZERO, BigInteger.ONE, BigInteger("9007199254740993"),
            BigInteger.ONE.shiftLeft(63), fixtures.maximumU64)) {
            val system = fixtures.inspect(KnownProgram.SYSTEM, fixtures.unsignedData(byteArrayOf(2, 0, 0, 0), amount))
                .decodedInstruction as DecodedInstruction.SystemTransfer
            assertEquals(amount.toString(), system.lamports.toString())
            val token = fixtures.inspect(KnownProgram.SPL_TOKEN,
                fixtures.unsignedData(byteArrayOf(3), amount), listOf(0, 1, 2))
                .decodedInstruction as DecodedInstruction.SplTokenTransfer
            assertEquals(amount, token.rawAmount)
            val checked = fixtures.inspect(KnownProgram.SPL_TOKEN,
                fixtures.unsignedData(byteArrayOf(12), amount) + byteArrayOf(255.toByte()), listOf(0, 1, 2, 0))
                .decodedInstruction as DecodedInstruction.SplTokenTransferChecked
            assertEquals(amount, checked.rawAmount)
            assertEquals(255, checked.declaredDecimals)
        }
    }

    @Test
    fun malformedSystemTransfersNeverAcquireTransferSemantics() {
        for (size in 0..16) {
            if (size == 12) continue
            val data = fixtures.systemTransfer.copyOf(size)
            val instruction = fixtures.inspect(KnownProgram.SYSTEM, data)
            assertEquals(DecodedInstruction.Malformed(KnownProgram.SYSTEM,
                InstructionDecodingFailure.INVALID_DATA_LENGTH), instruction.decodedInstruction)
        }
        for (references in listOf(emptyList(), listOf(0), listOf(0, 1, 2))) {
            assertEquals(DecodedInstruction.Malformed(KnownProgram.SYSTEM, InstructionDecodingFailure.INVALID_ACCOUNTS),
                fixtures.inspect(KnownProgram.SYSTEM, fixtures.systemTransfer, references).decodedInstruction)
        }
        val invalidIndex = TransactionInspector().inspect(fixtures.transaction(KnownProgram.SYSTEM.programId,
            fixtures.systemTransfer, listOf(0, 99)))
        assertEquals(TransactionInspectionStatus.MALFORMED, invalidIndex.inspectionStatus)
        assertNull(invalidIndex.instructions)
    }

    @Test
    fun systemUnsupportedDiscriminatorsRemainKnownButUnsupported() {
        for (tag in listOf(byteArrayOf(0, 0, 0, 0), byteArrayOf(2, 1, 0, 0),
            byteArrayOf(2, 0, 0, 1), byteArrayOf(-1, -1, -1, -1))) {
            val instruction = fixtures.inspect(KnownProgram.SYSTEM, tag + ByteArray(8))
            assertEquals("System Program", instruction.programName)
            assertEquals(DecodedInstruction.Unsupported(KnownProgram.SYSTEM), instruction.decodedInstruction)
        }
    }

    @Test
    fun splTransferExposesOnlyWireVerifiedRolesAndRawAmount() {
        val instruction = fixtures.inspect(KnownProgram.SPL_TOKEN, fixtures.tokenTransfer, listOf(0, 1, 2))
        assertEquals("SPL Token Program", instruction.programName)
        assertEquals("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA", instruction.programId)
        val transfer = instruction.decodedInstruction as DecodedInstruction.SplTokenTransfer
        assertEquals(TransactionWireFixtures.legacyKeys[0], transfer.source)
        assertEquals(TransactionWireFixtures.legacyKeys[1], transfer.destination)
        assertEquals(TransactionWireFixtures.legacyKeys[2], transfer.authority.publicKey)
        assertTrue(transfer.authority.multisigSignerReferences.isEmpty())
        assertEquals(BigInteger("1000000"), transfer.rawAmount)
        assertEquals("0716dd48799cbd95e895663ac738ff18e96173e5c1d6c8a132be0fa8ac777f90", instruction.dataSha256)
    }

    @Test
    fun splTransferCheckedPreservesMintAndDeclaredDecimalsWithoutMetadataInference() {
        val instruction = fixtures.inspect(KnownProgram.SPL_TOKEN, fixtures.tokenTransferChecked, listOf(0, 1, 2, 0))
        val transfer = instruction.decodedInstruction as DecodedInstruction.SplTokenTransferChecked
        assertEquals(TransactionWireFixtures.legacyKeys[0], transfer.source)
        assertEquals(TransactionWireFixtures.legacyKeys[1], transfer.mint)
        assertEquals(TransactionWireFixtures.legacyKeys[2], transfer.destination)
        assertEquals(TransactionWireFixtures.legacyKeys[0], transfer.authority.publicKey)
        assertEquals(BigInteger("1000000"), transfer.rawAmount)
        assertEquals(6, transfer.declaredDecimals)
    }

    @Test
    fun multisigAccountsKeepTheirExactOrderIndexesAndHeaderPrivileges() {
        for (checked in listOf(false, true)) {
            val refs = if (checked) listOf(2, 3, 4, 5, 0, 1) else listOf(2, 3, 4, 0, 1)
            val bytes = fixtures.transaction(KnownProgram.SPL_TOKEN.programId,
                if (checked) fixtures.tokenTransferChecked else fixtures.tokenTransfer,
                refs, keyCount = 7, signatures = 2, readonlySigned = 1)
            val instruction = TransactionInspector().inspect(bytes).instructions!!.single()
            val authority = when (val decoded = instruction.decodedInstruction) {
                is DecodedInstruction.SplTokenTransfer -> decoded.authority
                is DecodedInstruction.SplTokenTransferChecked -> decoded.authority
                else -> error("Expected verified transfer")
            }
            assertEquals(instruction.accountReferences[if (checked) 3 else 2].account!!.publicKey, authority.publicKey)
            assertFalse(instruction.accountReferences[if (checked) 3 else 2].account!!.isSigner)
            assertEquals(listOf(0, 1), authority.multisigSignerReferences.map { it.index })
            assertEquals(listOf(true, true), authority.multisigSignerReferences.map { it.account!!.isSigner })
            assertEquals(listOf(true, false), authority.multisigSignerReferences.map { it.account!!.isWritable })
        }
    }

    @Test
    fun malformedSplTransfersNeverAcquireSemantics() {
        assertTrue(fixtures.inspect(KnownProgram.SPL_TOKEN, byteArrayOf()).decodedInstruction is DecodedInstruction.Malformed)
        for (tag in listOf(3, 12)) {
            val expectedSize = if (tag == 3) 9 else 10
            for (size in 1..12) {
                if (size == expectedSize) continue
                val data = ByteArray(size).also { it[0] = tag.toByte() }
                assertEquals(DecodedInstruction.Malformed(KnownProgram.SPL_TOKEN,
                    InstructionDecodingFailure.INVALID_DATA_LENGTH),
                    fixtures.inspect(KnownProgram.SPL_TOKEN, data, listOf(0, 1, 2, 0)).decodedInstruction)
            }
            val data = if (tag == 3) fixtures.tokenTransfer else fixtures.tokenTransferChecked
            for (count in 0 until if (tag == 3) 3 else 4) {
                assertEquals(DecodedInstruction.Malformed(KnownProgram.SPL_TOKEN, InstructionDecodingFailure.INVALID_ACCOUNTS),
                    fixtures.inspect(KnownProgram.SPL_TOKEN, data, List(count) { it }).decodedInstruction)
            }
        }
    }

    @Test
    fun everyOtherSplTagIsKnownButUnsupported() {
        for (tag in 0..255) {
            if (tag == 3 || tag == 12) continue
            val instruction = fixtures.inspect(KnownProgram.SPL_TOKEN, byteArrayOf(tag.toByte(), 1, 2))
            assertEquals("SPL Token Program", instruction.programName)
            assertEquals(DecodedInstruction.Unsupported(KnownProgram.SPL_TOKEN), instruction.decodedInstruction)
        }
    }

    @Test
    fun unknownProgramCannotAcquireSemanticsFromKnownLookingInstructionData() {
        val bytes = fixtures.transaction(TransactionWireFixtures.blockhash, fixtures.systemTransfer)
        val instruction = TransactionInspector().inspect(bytes).instructions!!.single()
        assertEquals(TransactionWireFixtures.blockhash, instruction.programId)
        assertNull(instruction.programName)
        assertEquals(DecodedInstruction.Unknown, instruction.decodedInstruction)
        assertEquals(listOf(0, 1), instruction.accountReferences.map { it.index })
        assertEquals(TransactionWireFixtures.legacyKeys.take(2), instruction.accountReferences.map { it.account!!.publicKey })
        assertEquals(12, instruction.dataLength)
        assertEquals(DiagnosticSanitizer.sha256(fixtures.systemTransfer), instruction.dataSha256)
    }

    @Test
    fun unknownDataHashIsStableAndMatchesIndependentGoldenValue() {
        val bytes = fixtures.transaction(TransactionWireFixtures.blockhash, byteArrayOf(1, 2, 3))
        val inspector = TransactionInspector()
        val a = inspector.inspect(bytes)
        assertEquals(a, inspector.inspect(bytes.copyOf()))
        assertEquals("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81",
            a.instructions!!.single().dataSha256)
        val changed = bytes.copyOf().also { it[it.lastIndex] = 4 }
        assertNotEquals(a.instructions.single().dataSha256, inspector.inspect(changed).instructions!!.single().dataSha256)
    }

    @Test
    fun unresolvedV0TransferAccountsNeverBecomeInventedPublicKeysOrOperations() {
        for ((program, data, refs) in listOf(
            Triple(KnownProgram.SYSTEM, fixtures.systemTransfer, listOf(0, 2)),
            Triple(KnownProgram.SPL_TOKEN, fixtures.tokenTransfer, listOf(0, 2, 0)),
            Triple(KnownProgram.SPL_TOKEN, fixtures.tokenTransferChecked, listOf(0, 2, 0, 0)))) {
            val bytes = fixtures.transaction(program.programId, data, refs, keyCount = 2, version = 0,
                lookups = listOf(TransactionWireFixtures.Lookup(0x77, listOf(3), emptyList())))
            val summary = TransactionInspector().inspect(bytes)
            assertEquals(TransactionInspectionStatus.PARTIAL, summary.inspectionStatus)
            val instruction = summary.instructions!!.single()
            assertEquals(DecodedInstruction.Unavailable(program, InstructionDecodingFailure.UNRESOLVED_ACCOUNTS),
                instruction.decodedInstruction)
            assertNull(instruction.accountReferences[1].account)
            assertEquals(2, instruction.accountReferences[1].index)
        }
    }

    @Test
    fun supportedStaticV0MetadataCanDecodeWithoutEnablingV0Signing() {
        val bytes = fixtures.transaction(KnownProgram.SYSTEM.programId, fixtures.systemTransfer, version = 0)
        val summary = TransactionInspector().inspect(bytes)
        assertEquals(TransactionInspectionStatus.PARTIAL, summary.inspectionStatus)
        assertTrue(summary.instructions!!.single().decodedInstruction is DecodedInstruction.SystemTransfer)
        try {
            LegacyTransactionCodec.parseForSigner(bytes, ByteArray(32) { 0x11 })
            fail("v0 signing must remain unsupported")
        } catch (error: LegacyTransactionCodec.Rejected) {
            assertEquals(LegacyTransactionCodec.RejectionReason.VERSIONED_UNSUPPORTED, error.reason)
        }
    }
}
