package dev.mwalab.transaction

import com.funkatronics.encoders.Base58
import dev.mwalab.security.DiagnosticSanitizer
import dev.mwalab.transaction.TransactionInspectionFailure.*
import dev.mwalab.transaction.TransactionInspectionLimits.MAX_TRANSACTION_BYTES
import dev.mwalab.transaction.TransactionInspectionLimits.MAX_SIGNATURES
import dev.mwalab.transaction.TransactionInspectionLimits.MAX_ACCOUNTS
import dev.mwalab.transaction.TransactionInspectionLimits.MAX_INSTRUCTIONS
import dev.mwalab.transaction.TransactionInspectionLimits.MAX_ACCOUNTS_PER_INSTRUCTION
import dev.mwalab.transaction.TransactionInspectionLimits.MAX_INSTRUCTION_DATA_BYTES
import dev.mwalab.transaction.TransactionInspectionLimits.MAX_ADDRESS_TABLE_LOOKUPS
import dev.mwalab.transaction.TransactionInspectionLimits.MAX_SHORTVEC_BYTES

/**
 * Bounded structural diagnostics for Solana serialized transactions.
 * Separate from signing validation; no signer identity, signature verification,
 * blockhash freshness, runtime privilege demotion, semantic decoding, or RPC.
 *
 * Wire references:
 * https://github.com/anza-xyz/solana-sdk/blob/master/short-vec/src/lib.rs
 * https://github.com/anza-xyz/solana-sdk/blob/master/message/src/versions/v0/mod.rs
 */
class SolanaWireTransactionParser {
    fun parse(transaction: ByteArray): TransactionSummary {
        // Own accepted input before both hashing and reading. Oversized input is
        // hashed without copying/allocating a transaction-sized diagnostic buffer.
        val bytes = if (transaction.size <= MAX_TRANSACTION_BYTES) transaction.copyOf() else transaction
        val fingerprint = DiagnosticSanitizer.sha256(bytes)
        var version: TransactionVersion = TransactionVersion.UNKNOWN
        var signatureCount: Int? = null
        val reader = Reader(bytes)
        try {
            if (bytes.isEmpty()) reader.fail(EMPTY_PAYLOAD)
            if (bytes.size > MAX_TRANSACTION_BYTES) reader.fail(TRANSACTION_TOO_LARGE)
            val signatures = reader.shortVec()
            if (signatures !in 1..MAX_SIGNATURES) reader.fail(SIGNATURE_COUNT_OUT_OF_RANGE)
            signatureCount = signatures
            reader.skipUnits(signatures, 64, TRUNCATED_SIGNATURES)

            val prefix = reader.u8(TRUNCATED_MESSAGE)
            version = if (prefix and 0x80 == 0) TransactionVersion.LEGACY
                else if (prefix and 0x7f == 0) TransactionVersion.V0
                else TransactionVersion.VERSIONED_UNSUPPORTED(prefix and 0x7f)
            if (version is TransactionVersion.VERSIONED_UNSUPPORTED) {
                return TransactionSummary(fingerprint, bytes.size, version,
                    TransactionInspectionStatus.UNSUPPORTED_VERSION, signatureCount,
                    limitations = baseLimitations + TransactionInspectionLimitation.UNSUPPORTED_MESSAGE_VERSION)
            }

            val required = if (version == TransactionVersion.LEGACY) prefix else reader.u8(TRUNCATED_HEADER)
            val readonlySigned = reader.u8(TRUNCATED_HEADER)
            val readonlyUnsigned = reader.u8(TRUNCATED_HEADER)
            if (required !in 1..MAX_SIGNATURES || readonlySigned >= required) reader.fail(INVALID_HEADER)
            if (required != signatures) reader.fail(SIGNATURE_COUNT_MISMATCH)

            val accountCount = reader.shortVec()
            if (accountCount !in required..MAX_ACCOUNTS) reader.fail(ACCOUNT_COUNT_OUT_OF_RANGE)
            if (readonlyUnsigned > accountCount - required) reader.fail(INVALID_HEADER)
            val header = TransactionMessageHeader(required, readonlySigned, readonlyUnsigned)
            reader.requireUnits(accountCount, 32, TRUNCATED_ACCOUNTS)
            val accounts = List(accountCount) { index ->
                AccountSummary(index, reader.key(TRUNCATED_ACCOUNTS),
                    header.isSigner(index), header.isWritable(index, accountCount), index == 0)
            }
            val blockhash = reader.key(TRUNCATED_BLOCKHASH)

            val instructionCount = reader.shortVec()
            if (instructionCount > MAX_INSTRUCTIONS) reader.fail(INSTRUCTION_COUNT_OUT_OF_RANGE)
            // Every instruction needs at least program-index and two length bytes.
            reader.requireUnits(instructionCount, 3, TRUNCATED_INSTRUCTIONS)
            val drafts = List(instructionCount) { index ->
                val programIndex = reader.u8(TRUNCATED_INSTRUCTIONS)
                // v0 program IDs must be static keys, never ALT-loaded keys.
                if (programIndex >= accountCount) reader.fail(PROGRAM_INDEX_OUT_OF_RANGE)
                if (programIndex == 0) reader.fail(PROGRAM_IS_FEE_PAYER)
                val referenceCount = reader.shortVec()
                if (referenceCount > MAX_ACCOUNTS_PER_INSTRUCTION) reader.fail(INSTRUCTION_ACCOUNT_COUNT_OUT_OF_RANGE)
                reader.requireUnits(referenceCount, 1, TRUNCATED_INSTRUCTION_ACCOUNTS)
                val references = List(referenceCount) { reader.u8(TRUNCATED_INSTRUCTION_ACCOUNTS) }
                if (version == TransactionVersion.LEGACY && references.any { it >= accountCount }) {
                    reader.fail(ACCOUNT_INDEX_OUT_OF_RANGE)
                }
                val dataLength = reader.shortVec()
                if (dataLength > MAX_INSTRUCTION_DATA_BYTES) reader.fail(INSTRUCTION_DATA_TOO_LARGE)
                val dataHash = reader.dataHash(dataLength)
                DraftInstruction(index, programIndex, references, dataLength, dataHash)
            }

            var totalAccounts = accountCount
            val lookups = if (version == TransactionVersion.V0) {
                val count = reader.shortVec()
                if (count > MAX_ADDRESS_TABLE_LOOKUPS) reader.fail(LOOKUP_COUNT_OUT_OF_RANGE)
                // Each valid descriptor: key + two lengths + at least one index.
                reader.requireUnits(count, 35, TRUNCATED_LOOKUPS)
                List(count) { index ->
                    val table = reader.key(TRUNCATED_LOOKUPS)
                    val writable = reader.lookupIndexes()
                    val readonly = reader.lookupIndexes()
                    val loaded = writable.size + readonly.size
                    if (loaded == 0) reader.fail(EMPTY_ADDRESS_LOOKUP)
                    if (loaded > MAX_ACCOUNTS - totalAccounts) reader.fail(TOTAL_ACCOUNT_COUNT_OUT_OF_RANGE)
                    totalAccounts += loaded
                    AddressTableLookupSummary(index, table, writable, readonly)
                }
            } else emptyList()
            if (reader.remaining != 0) reader.fail(TRAILING_DATA)
            if (drafts.any { draft -> draft.references.any { it >= totalAccounts } }) {
                reader.fail(ACCOUNT_INDEX_OUT_OF_RANGE)
            }

            val instructions = drafts.map { draft ->
                InstructionSummary(draft.index, draft.programIndex, accounts[draft.programIndex].publicKey,
                    draft.references.map { InstructionAccountReference(it, accounts.getOrNull(it)) },
                    draft.dataLength, draft.dataHash)
            }
            val limitations = baseLimitations + if (version == TransactionVersion.V0) {
                listOf(TransactionInspectionLimitation.VERSIONED_INSPECTION_PARTIAL) +
                    if (lookups.isEmpty()) emptyList() else listOf(TransactionInspectionLimitation.LOOKUP_ADDRESSES_UNRESOLVED)
            } else emptyList()
            return TransactionSummary(fingerprint, bytes.size, version,
                if (version == TransactionVersion.LEGACY) TransactionInspectionStatus.PARSED
                else TransactionInspectionStatus.PARTIAL,
                signatureCount, header, blockhash, accounts, instructions, lookups, limitations)
        } catch (failure: ParseFailure) {
            // Malformed structures expose only the verified prefix/count and
            // fingerprint/size; no incomplete accounts, privileges, or instructions.
            return TransactionSummary(fingerprint, bytes.size, version, TransactionInspectionStatus.MALFORMED,
                signatureCount, limitations = baseLimitations,
                error = TransactionInspectionError(failure.reason, failure.offset))
        }
    }

    private data class DraftInstruction(
        val index: Int,
        val programIndex: Int,
        val references: List<Int>,
        val dataLength: Int,
        val dataHash: String,
    )

    private class ParseFailure(val reason: TransactionInspectionFailure, val offset: Int) : Exception()

    private class Reader(private val bytes: ByteArray) {
        private var cursor = 0
        val remaining: Int get() = bytes.size - cursor

        fun fail(reason: TransactionInspectionFailure): Nothing = throw ParseFailure(reason, cursor)

        fun requireUnits(count: Int, unitSize: Int, reason: TransactionInspectionFailure) {
            // Check division before multiplication, allocation, or slicing.
            if (count < 0 || unitSize <= 0 || count > remaining / unitSize) fail(reason)
        }

        fun skipUnits(count: Int, unitSize: Int, reason: TransactionInspectionFailure) {
            requireUnits(count, unitSize, reason)
            cursor += count * unitSize
        }

        fun u8(reason: TransactionInspectionFailure): Int {
            requireUnits(1, 1, reason)
            return bytes[cursor++].toInt() and 0xff
        }

        fun shortVec(): Int {
            var value = 0
            repeat(MAX_SHORTVEC_BYTES) { index ->
                val byte = u8(TRUNCATED_SHORTVEC)
                // Solana shortvec is ShortU16, not an unrestricted varint.
                if (index == 2 && byte > 3) fail(SHORTVEC_OVERFLOW)
                value = value or ((byte and 0x7f) shl (7 * index))
                if (byte and 0x80 == 0) {
                    if (index > 0 && byte == 0) fail(NON_CANONICAL_SHORTVEC)
                    return value
                }
            }
            fail(SHORTVEC_OVERFLOW)
        }

        fun key(reason: TransactionInspectionFailure): String {
            requireUnits(1, 32, reason)
            val key = Base58.encodeToString(bytes.copyOfRange(cursor, cursor + 32))
            cursor += 32
            return key
        }

        fun dataHash(length: Int): String {
            requireUnits(length, 1, TRUNCATED_INSTRUCTION_DATA)
            val hash = DiagnosticSanitizer.sha256(bytes.copyOfRange(cursor, cursor + length))
            cursor += length
            return hash
        }

        fun lookupIndexes(): List<Int> {
            val count = shortVec()
            if (count > MAX_ACCOUNTS) fail(LOOKUP_INDEX_COUNT_OUT_OF_RANGE)
            requireUnits(count, 1, TRUNCATED_LOOKUPS)
            return List(count) { u8(TRUNCATED_LOOKUPS) }
        }
    }

    companion object {
        private val baseLimitations = listOf(
            TransactionInspectionLimitation.SIGNATURES_NOT_VERIFIED,
            TransactionInspectionLimitation.BLOCKHASH_NOT_VALIDATED,
            TransactionInspectionLimitation.HEADER_PRIVILEGES_ONLY,
            TransactionInspectionLimitation.SEMANTIC_DECODING_NOT_IMPLEMENTED,
        )
    }
}
