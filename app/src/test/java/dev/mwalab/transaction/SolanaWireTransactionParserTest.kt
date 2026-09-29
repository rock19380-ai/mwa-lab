package dev.mwalab.transaction

import dev.mwalab.transaction.TransactionInspectionFailure.*
import dev.mwalab.transaction.TransactionWireFixtures.Instruction
import dev.mwalab.transaction.TransactionWireFixtures.Lookup
import java.util.Random
import org.junit.Assert.*
import org.junit.Test

class SolanaWireTransactionParserTest {
    private val parser = SolanaWireTransactionParser()

    @Test
    fun fixedLegacyVectorHasExactHeaderFeePayerSignersPrivilegesAndBlockhash() {
        val summary = parser.parse(TransactionWireFixtures.legacy().bytes)
        assertEquals(TransactionInspectionStatus.PARSED, summary.inspectionStatus)
        assertEquals(TransactionVersion.LEGACY, summary.transactionVersion)
        assertEquals(341, summary.wireLength)
        assertEquals(TransactionWireFixtures.legacyFingerprint, summary.fingerprintSha256)
        assertEquals(2, summary.signatureCount)
        assertEquals(TransactionMessageHeader(2, 1, 1), summary.header)
        assertEquals(TransactionWireFixtures.legacyKeys[0], summary.feePayer)
        assertEquals(2, summary.requiredSignerCount)
        assertEquals(TransactionWireFixtures.legacyKeys.take(2), summary.requiredSigners!!.map { it.publicKey })
        assertEquals(TransactionWireFixtures.legacyKeys, summary.accounts!!.map { it.publicKey })
        assertEquals(listOf(true, true, false, false, false), summary.accounts.map { it.isSigner })
        assertEquals(listOf(true, false, true, true, false), summary.accounts.map { it.isWritable })
        assertEquals(listOf(true, false, false, false, false), summary.accounts.map { it.isFeePayer })
        assertEquals(TransactionWireFixtures.blockhash, summary.recentBlockhash)
        assertEquals(5, summary.staticAccountCount)
        assertEquals(5, summary.totalAccountCount)
        assertTrue(summary.addressTableLookups!!.isEmpty())
        assertNull(summary.error)
    }

    @Test
    fun instructionsResolveOrderedAndRepeatedAccountReferencesWithoutInventingSemantics() {
        val summary = parser.parse(TransactionWireFixtures.legacy().bytes)
        val instructions = summary.instructions!!
        assertEquals(2, summary.instructionCount)
        assertEquals(listOf(0, 1), instructions.map { it.index })
        assertEquals(listOf(4, 3), instructions.map { it.programIdIndex })
        assertEquals(listOf(TransactionWireFixtures.legacyKeys[4], TransactionWireFixtures.legacyKeys[3]),
            instructions.map { it.programId })
        assertEquals(listOf(0, 2, 1, 3), instructions[0].accountReferences.map { it.index })
        assertEquals(listOf(0, 2, 1, 3).map { summary.accounts!![it] }, instructions[0].accountReferences.map { it.account })
        assertEquals(listOf(2, 2), instructions[1].accountReferences.map { it.index })
        assertEquals(3, instructions[0].dataLength)
        assertEquals(TransactionWireFixtures.dataFingerprint, instructions[0].dataSha256)
        assertEquals(0, instructions[1].dataLength)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", instructions[1].dataSha256)
        assertTrue(instructions.all { it.programName == null && it.decodedInstruction == DecodedInstruction.Unknown })
        // Program-as-writable is the header request, not a guessed runtime lock.
        assertTrue(summary.accounts!![3].isWritable)
        assertTrue(TransactionInspectionLimitation.HEADER_PRIVILEGES_ONLY in summary.limitations)
        val zeroKeyVector = TransactionWireFixtures.build(keys = listOf(0x11, 0), readonlyUnsigned = 1,
            instructions = listOf(Instruction(1, listOf(0))))
        val zeroKeySummary = parser.parse(zeroKeyVector.bytes)
        assertEquals(TransactionInspectionStatus.PARSED, zeroKeySummary.inspectionStatus)
        assertEquals("1".repeat(32), zeroKeySummary.instructions!!.single().programId)
        assertNull(zeroKeySummary.instructions.single().programName)
        val zeroBlockhash = zeroKeyVector.bytes.copyOf().also {
            it.fill(0, zeroKeyVector.offset("blockhash"), zeroKeyVector.offset("blockhash") + 32)
        }
        assertEquals("1".repeat(32), parser.parse(zeroBlockhash).recentBlockhash)
    }

    @Test
    fun everySmallValidHeaderCombinationDerivesPrivilegesExactly() {
        for (required in 1..4) for (readonlySigned in 0 until required)
            for (unsigned in 0..4) for (readonlyUnsigned in 0..unsigned) {
                val count = required + unsigned
                val bytes = TransactionWireFixtures.build(signatures = required, required = required,
                    readonlySigned = readonlySigned, readonlyUnsigned = readonlyUnsigned,
                    keys = List(count) { 0x11 + it }).bytes
                val summary = parser.parse(bytes)
                assertEquals(TransactionInspectionStatus.PARSED, summary.inspectionStatus)
                summary.accounts!!.forEachIndexed { index, account ->
                    assertEquals(index < required, account.isSigner)
                    assertEquals(if (index < required) index < required - readonlySigned
                        else index < count - readonlyUnsigned, account.isWritable)
                    assertEquals(index == 0, account.isFeePayer)
                }
            }
    }

    @Test
    fun v0RetainsVerifiedStaticKeysLookupDescriptorsAndUnresolvedAccountIndexes() {
        val summary = parser.parse(TransactionWireFixtures.v0().bytes)
        assertEquals(TransactionVersion.V0, summary.transactionVersion)
        assertEquals(TransactionInspectionStatus.PARTIAL, summary.inspectionStatus)
        assertEquals(2, summary.staticAccountCount)
        assertEquals(5, summary.totalAccountCount)
        assertEquals(TransactionWireFixtures.legacyKeys[0], summary.feePayer)
        assertEquals(TransactionWireFixtures.blockhash, summary.recentBlockhash)
        assertEquals(listOf(true, false), summary.accounts!!.map { it.isSigner })
        assertEquals(listOf(true, false), summary.accounts.map { it.isWritable })
        val lookups = summary.addressTableLookups!!
        assertEquals(2, lookups.size)
        assertEquals(TransactionWireFixtures.blockhash, lookups[0].tableAccount)
        assertEquals(listOf(7), lookups[0].writableIndexes)
        assertEquals(listOf(9), lookups[0].readonlyIndexes)
        assertEquals(listOf(2), lookups[1].writableIndexes)
        assertTrue(lookups[1].readonlyIndexes.isEmpty())
        val instruction = summary.instructions!!.single()
        assertEquals(TransactionWireFixtures.legacyKeys[4], instruction.programId)
        assertEquals(listOf(0, 2, 3, 4), instruction.accountReferences.map { it.index })
        assertEquals(summary.accounts[0], instruction.accountReferences[0].account)
        assertTrue(instruction.accountReferences.drop(1).all { it.account == null })
        assertTrue(TransactionInspectionLimitation.LOOKUP_ADDRESSES_UNRESOLVED in summary.limitations)
        assertNull(summary.error)
    }

    @Test
    fun v0WithoutLookupsStillHasExplicitPartialScopeAndDoesNotFabricateAddresses() {
        val summary = parser.parse(TransactionWireFixtures.build(version = 0).bytes)
        assertEquals(TransactionInspectionStatus.PARTIAL, summary.inspectionStatus)
        assertTrue(summary.addressTableLookups!!.isEmpty())
        assertEquals(summary.staticAccountCount, summary.totalAccountCount)
        assertTrue(TransactionInspectionLimitation.VERSIONED_INSPECTION_PARTIAL in summary.limitations)
        assertFalse(TransactionInspectionLimitation.LOOKUP_ADDRESSES_UNRESOLVED in summary.limitations)
    }

    @Test
    fun versionPrefixIsReadAfterSignaturesAndEveryUnknownPrefixStaysUnsupported() {
        val legacy = TransactionWireFixtures.legacy()
        assertEquals(TransactionVersion.LEGACY, parser.parse(legacy.bytes).transactionVersion)
        for (number in 1..127) {
            val vector = TransactionWireFixtures.v0()
            val bytes = vector.replace("message", 0x80 or number)
            val summary = parser.parse(bytes)
            assertEquals(TransactionVersion.VERSIONED_UNSUPPORTED(number), summary.transactionVersion)
            assertEquals(TransactionInspectionStatus.UNSUPPORTED_VERSION, summary.inspectionStatus)
            assertNull(summary.accounts)
            assertNull(summary.feePayer)
            assertNull(summary.header)
            assertNull(summary.recentBlockhash)
            assertNull(summary.instructions)
            assertNull(summary.addressTableLookups)
            assertTrue(TransactionInspectionLimitation.UNSUPPORTED_MESSAGE_VERSION in summary.limitations)
        }
    }

    @Test
    fun everyTruncationPointReturnsStructuredMalformedMetadataAndPreservesOnlyVerifiedVersion() {
        for (vector in listOf(TransactionWireFixtures.legacy(), TransactionWireFixtures.v0())) {
            for (length in 0 until vector.bytes.size) {
                val summary = parser.parse(vector.bytes.copyOf(length))
                assertEquals("length=$length", TransactionInspectionStatus.MALFORMED, summary.inspectionStatus)
                assertNotNull(summary.error)
                assertTrue(summary.error!!.byteOffset in 0..length)
                assertNull(summary.accounts)
                assertNull(summary.instructions)
                assertNull(summary.header)
                assertNull(summary.feePayer)
                assertNull(summary.requiredSignerCount)
                assertNull(summary.recentBlockhash)
                assertNull(summary.addressTableLookups)
                assertEquals(if (length <= vector.offset("message")) TransactionVersion.UNKNOWN
                    else if (vector.bytes[vector.offset("message")].toInt() and 0xff == 0x80)
                        TransactionVersion.V0 else TransactionVersion.LEGACY, summary.transactionVersion)
            }
        }
    }

    @Test
    fun hostileLegacyConditionsHaveStableExplicitFailureCodes() {
        val v = TransactionWireFixtures.legacy()
        val truncatedReferences = TransactionWireFixtures.build(instructions =
            listOf(Instruction(1, listOf(0, 0, 0, 0))))
        val cases = listOf(
            byteArrayOf() to EMPTY_PAYLOAD,
            byteArrayOf(0x80.toByte()) to TRUNCATED_SHORTVEC,
            v.replace("signatureCount", 0) to SIGNATURE_COUNT_OUT_OF_RANGE,
            v.replace("signatureCount", 65) to SIGNATURE_COUNT_OUT_OF_RANGE,
            v.replace("signatureCount", 64) to TRUNCATED_SIGNATURES,
            v.bytes.copyOf(v.offset("message")) to TRUNCATED_MESSAGE,
            v.bytes.copyOf(v.offset("header") + 1) to TRUNCATED_HEADER,
            v.replace("header", 0) to INVALID_HEADER,
            v.bytes.copyOf().also { it[v.offset("header") + 1] = 2 } to INVALID_HEADER,
            v.bytes.copyOf().also { it[v.offset("header") + 2] = 4 } to INVALID_HEADER,
            v.replace("header", 3) to SIGNATURE_COUNT_MISMATCH,
            v.replace("accountCount", 1) to ACCOUNT_COUNT_OUT_OF_RANGE,
            v.replace("accountCount", 0) to ACCOUNT_COUNT_OUT_OF_RANGE,
            v.replaceLength("accountCount", 257) to ACCOUNT_COUNT_OUT_OF_RANGE,
            v.replaceLength("accountCount", 256) to TRUNCATED_ACCOUNTS,
            v.bytes.copyOf(v.offset("accounts") + 159) to TRUNCATED_ACCOUNTS,
            v.bytes.copyOf(v.offset("blockhash") + 31) to TRUNCATED_BLOCKHASH,
            v.replaceLength("instructionCount", 257) to INSTRUCTION_COUNT_OUT_OF_RANGE,
            v.replaceLength("instructionCount", 256) to TRUNCATED_INSTRUCTIONS,
            v.replace("program0", 5) to PROGRAM_INDEX_OUT_OF_RANGE,
            v.replace("program0", 0) to PROGRAM_IS_FEE_PAYER,
            v.replaceLength("referenceCount0", 257) to INSTRUCTION_ACCOUNT_COUNT_OUT_OF_RANGE,
            truncatedReferences.bytes.copyOf(truncatedReferences.offset("references0") + 3) to TRUNCATED_INSTRUCTION_ACCOUNTS,
            v.replace("references0", 5) to ACCOUNT_INDEX_OUT_OF_RANGE,
            v.replaceLength("dataLength0", 1233) to INSTRUCTION_DATA_TOO_LARGE,
            v.bytes.copyOf(v.offset("data0") + 2) to TRUNCATED_INSTRUCTION_DATA,
            (v.bytes + byteArrayOf(0)) to TRAILING_DATA,
            ByteArray(1233) to TRANSACTION_TOO_LARGE,
        )
        cases.forEachIndexed { index, (bytes, reason) ->
            val first = parser.parse(bytes)
            assertEquals("case=$index", TransactionInspectionStatus.MALFORMED, first.inspectionStatus)
            assertEquals("case=$index", reason, first.error!!.reason)
            assertEquals(first, parser.parse(bytes))
            assertEquals(first.hashCode(), parser.parse(bytes).hashCode())
        }
    }

    @Test
    fun nonCanonicalShortvecIsRejectedAtEveryLegacyAndLookupLengthPosition() {
        for ((v, fields) in listOf(
            TransactionWireFixtures.legacy() to listOf("signatureCount", "accountCount", "instructionCount",
                "referenceCount0", "dataLength0"),
            TransactionWireFixtures.v0() to listOf("lookupCount", "lookupWritableCount0", "lookupReadonlyCount0"))) {
            for (field in fields) {
                val at = v.offset(field)
                val bytes = v.bytes.copyOfRange(0, at) +
                    byteArrayOf((v.bytes[at].toInt() or 0x80).toByte(), 0) +
                    v.bytes.copyOfRange(at + 1, v.bytes.size)
                val summary = parser.parse(bytes)
                assertEquals(field, NON_CANONICAL_SHORTVEC, summary.error!!.reason)
            }
        }
    }

    @Test
    fun shortvecEnforcesCanonicalUnsigned16BitEncodingWithoutOverflow() {
        val cases = listOf(
            byteArrayOf(0x81.toByte(), 0) to NON_CANONICAL_SHORTVEC,
            byteArrayOf(0x81.toByte(), 0x80.toByte(), 0) to NON_CANONICAL_SHORTVEC,
            byteArrayOf(0xff.toByte(), 0xff.toByte(), 4) to SHORTVEC_OVERFLOW,
            byteArrayOf(0xff.toByte(), 0xff.toByte(), 0x83.toByte()) to SHORTVEC_OVERFLOW,
            byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0) to SHORTVEC_OVERFLOW,
            byteArrayOf(0xff.toByte(), 0xff.toByte(), 3) to SIGNATURE_COUNT_OUT_OF_RANGE,
        )
        cases.forEach { (bytes, reason) -> assertEquals(reason, parser.parse(bytes).error!!.reason) }
        for (count in listOf(127, 128, 16383, 16384, 65535)) {
            assertEquals(SIGNATURE_COUNT_OUT_OF_RANGE, parser.parse(TransactionWireFixtures.shortVec(count)).error!!.reason)
        }
    }

    @Test
    fun validUpperBoundsForInstructionsReferencesTotalAccountsAndWireLengthAreAccepted() {
        val instructions = parser.parse(TransactionWireFixtures.build(instructions =
            List(256) { Instruction(1, emptyList()) }).bytes)
        assertEquals(TransactionInspectionStatus.PARSED, instructions.inspectionStatus)
        assertEquals(256, instructions.instructionCount)

        val references = parser.parse(TransactionWireFixtures.build(instructions =
            listOf(Instruction(1, List(256) { 0 }))).bytes)
        assertEquals(TransactionInspectionStatus.PARSED, references.inspectionStatus)
        assertEquals(256, references.instructions!!.single().accountReferences.size)

        val v0 = parser.parse(TransactionWireFixtures.build(version = 0,
            instructions = listOf(Instruction(1, listOf(255))),
            lookups = listOf(Lookup(0x66, List(251) { it }, listOf(252, 253, 254)))).bytes)
        assertEquals(TransactionInspectionStatus.PARTIAL, v0.inspectionStatus)
        assertEquals(256, v0.totalAccountCount)
        assertNull(v0.instructions!!.single().accountReferences.single().account)

        val maximum = TransactionWireFixtures.build(instructions =
            listOf(Instruction(1, emptyList(), ByteArray(1062) { 0x5a }))).bytes
        assertEquals(1232, maximum.size)
        assertEquals(TransactionInspectionStatus.PARSED, parser.parse(maximum).inspectionStatus)
        assertEquals(TRANSACTION_TOO_LARGE, parser.parse(maximum + byteArrayOf(0)).error!!.reason)
    }

    @Test
    fun v0MalformedLookupStructuresAndLoadedProgramIndexesNeverBecomeResolvedMetadata() {
        val v = TransactionWireFixtures.v0()
        val malformed = listOf(
            v.replace("program0", 2),
            v.replace("references0", 5),
            v.replaceLength("lookupCount", 257),
            v.replaceLength("lookupWritableCount0", 257),
            v.bytes.copyOf(v.offset("lookup0") + 31),
            v.bytes + byteArrayOf(0),
            TransactionWireFixtures.build(version = 0, lookups =
                listOf(Lookup(0x66, emptyList(), emptyList()))).bytes,
            TransactionWireFixtures.build(version = 0, lookups =
                listOf(Lookup(0x66, List(255) { it }, emptyList()))).bytes,
        )
        malformed.forEach {
            val summary = parser.parse(it)
            assertEquals(TransactionInspectionStatus.MALFORMED, summary.inspectionStatus)
            assertEquals(TransactionVersion.V0, summary.transactionVersion)
            assertNotNull(summary.error)
            assertNull(summary.accounts)
            assertNull(summary.instructions)
            assertNull(summary.addressTableLookups)
        }
        assertEquals(PROGRAM_INDEX_OUT_OF_RANGE, parser.parse(malformed[0]).error!!.reason)
        assertEquals(ACCOUNT_INDEX_OUT_OF_RANGE, parser.parse(malformed[1]).error!!.reason)
        assertEquals(LOOKUP_COUNT_OUT_OF_RANGE, parser.parse(malformed[2]).error!!.reason)
        assertEquals(LOOKUP_INDEX_COUNT_OUT_OF_RANGE, parser.parse(malformed[3]).error!!.reason)
        assertEquals(TOTAL_ACCOUNT_COUNT_OUT_OF_RANGE, parser.parse(malformed.last()).error!!.reason)
    }

    @Test
    fun deterministicMutationAndSeededArbitraryInputsNeverEscapeAsParserExceptions() {
        val vectors = listOf(TransactionWireFixtures.legacy().bytes, TransactionWireFixtures.v0().bytes)
        vectors.forEach { bytes ->
            bytes.indices.forEach { at ->
                listOf(0, 1, 127, 128, 255).forEach { value ->
                    val mutated = bytes.copyOf().also { it[at] = value.toByte() }
                    val first = parser.parse(mutated)
                    assertEquals(first, parser.parse(mutated))
                    assertTrue(first.error == null || first.error.byteOffset in 0..mutated.size)
                }
            }
        }
        val random = Random(446L)
        repeat(2_000) {
            val bytes = ByteArray(random.nextInt(1_400)).also(random::nextBytes)
            val first = parser.parse(bytes)
            assertEquals(first, parser.parse(bytes))
            assertEquals(bytes.size, first.wireLength)
            assertTrue(first.fingerprintSha256.matches(Regex("[0-9a-f]{64}")))
        }
    }
}
