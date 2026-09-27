package dev.mwalab.transaction

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LegacyTransactionCodecTest {
    @Test
    fun `patches only requested signer slot and preserves message`() {
        val signerA = ByteArray(32) { 0x11 }
        val signerB = ByteArray(32) { 0x22 }
        val blockhash = ByteArray(32) { 0x33 }
        val originalSignatureA = ByteArray(64) { 0x44 }
        val originalSignatureB = ByteArray(64) { 0x55 }
        val transaction = transaction(
            signers = listOf(signerA, signerB),
            blockhash = blockhash,
            signatures = listOf(originalSignatureA, originalSignatureB),
        )

        val parsed = LegacyTransactionCodec.parseForSigner(transaction, signerB)
        val replacement = ByteArray(64) { 0x66 }
        val signed = parsed.withSignature(replacement)

        assertEquals(1, parsed.signerIndex)
        assertArrayEquals(blockhash, parsed.recentBlockhash)
        assertArrayEquals(parsed.message, signed.copyOfRange(messageOffset(transaction), signed.size))
        assertArrayEquals(originalSignatureA, parsed.primarySignature(signed))
        assertArrayEquals(
            replacement,
            signed.copyOfRange(1 + 64, 1 + 128),
        )
    }

    @Test
    fun `rejects versioned message`() {
        val signer = ByteArray(32) { 0x11 }
        val tx = transaction(signers = listOf(signer)).copyOf().also {
            it[messageOffset(it)] = 0x80.toByte()
        }

        assertRejected(LegacyTransactionCodec.RejectionReason.VERSIONED_UNSUPPORTED) {
            LegacyTransactionCodec.parseForSigner(tx, signer)
        }
    }

    @Test
    fun `rejects signer outside required signer region`() {
        val signer = ByteArray(32) { 0x11 }
        val unsignedAccount = ByteArray(32) { 0x22 }
        val tx = transaction(signers = listOf(signer), unsignedAccounts = listOf(unsignedAccount))

        assertRejected(LegacyTransactionCodec.RejectionReason.SIGNER_NOT_REQUIRED) {
            LegacyTransactionCodec.parseForSigner(tx, unsignedAccount)
        }
    }

    @Test
    fun `rejects signature count mismatch`() {
        val signerA = ByteArray(32) { 0x11 }
        val signerB = ByteArray(32) { 0x22 }
        val canonical = transaction(signers = listOf(signerA, signerB))
        val canonicalMessageOffset = messageOffset(canonical)
        val tx = buildList<Byte> {
            add(1)
            addAll(canonical.copyOfRange(1, 1 + 64).toList())
            addAll(canonical.copyOfRange(canonicalMessageOffset, canonical.size).toList())
        }.toByteArray()

        assertRejected(LegacyTransactionCodec.RejectionReason.SIGNATURE_COUNT_MISMATCH) {
            LegacyTransactionCodec.parseForSigner(tx, signerA)
        }
    }

    @Test
    fun `rejects transaction larger than Solana packet bound`() {
        val signer = ByteArray(32) { 0x11 }
        val base = transaction(signers = listOf(signer))
        val oversized = base + ByteArray(LegacyTransactionCodec.MAX_TRANSACTION_BYTES - base.size + 1)

        assertRejected(LegacyTransactionCodec.RejectionReason.TOO_LARGE) {
            LegacyTransactionCodec.parseForSigner(oversized, signer)
        }
    }

    @Test
    fun `rejects trailing bytes`() {
        val signer = ByteArray(32) { 0x11 }
        val tx = transaction(signers = listOf(signer)) + byteArrayOf(0x01)

        assertRejected(LegacyTransactionCodec.RejectionReason.MALFORMED) {
            LegacyTransactionCodec.parseForSigner(tx, signer)
        }
    }

    @Test
    fun `rejects empty and truncated payloads`() {
        val signer = ByteArray(32) { 0x11 }
        assertRejected(LegacyTransactionCodec.RejectionReason.EMPTY) {
            LegacyTransactionCodec.parseForSigner(ByteArray(0), signer)
        }

        val canonical = transaction(signers = listOf(signer))
        val truncationPoints = listOf(1, 10, 64, canonical.size - 1)
        for (length in truncationPoints) {
            assertRejected(LegacyTransactionCodec.RejectionReason.MALFORMED) {
                LegacyTransactionCodec.parseForSigner(canonical.copyOf(length), signer)
            }
        }
    }

    @Test
    fun `rejects non canonical shortvec and duplicate signer key`() {
        val signer = ByteArray(32) { 0x11 }
        val canonical = transaction(signers = listOf(signer))
        val nonCanonical = byteArrayOf(0x81.toByte(), 0x00) + canonical.copyOfRange(1, canonical.size)
        assertRejected(LegacyTransactionCodec.RejectionReason.MALFORMED) {
            LegacyTransactionCodec.parseForSigner(nonCanonical, signer)
        }

        val duplicateSigner = transaction(signers = listOf(signer, signer))
        assertRejected(LegacyTransactionCodec.RejectionReason.MALFORMED) {
            LegacyTransactionCodec.parseForSigner(duplicateSigner, signer)
        }
    }

    @Test
    fun `parsed transaction is bound to immutable snapshot of approved payload`() {
        val signer = ByteArray(32) { 0x11 }
        val mutableInput = transaction(signers = listOf(signer))
        val parsed = LegacyTransactionCodec.parseForSigner(mutableInput, signer)
        val originalSnapshot = parsed.original.copyOf()
        val messageSnapshot = parsed.message.copyOf()

        mutableInput.fill(0x7f)

        assertArrayEquals(originalSnapshot, parsed.original)
        assertArrayEquals(messageSnapshot, parsed.message)
        val replacement = ByteArray(64) { 0x5a }
        val signed = parsed.withSignature(replacement)
        assertArrayEquals(messageSnapshot, signed.copyOfRange(messageOffset(originalSnapshot), signed.size))
    }

    @Test
    fun `signature patch rejects wrong signature length`() {
        val signer = ByteArray(32) { 0x11 }
        val parsed = LegacyTransactionCodec.parseForSigner(transaction(signers = listOf(signer)), signer)
        try {
            parsed.withSignature(ByteArray(63))
            fail("Expected wrong signature length to fail")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private fun transaction(
        signers: List<ByteArray>,
        unsignedAccounts: List<ByteArray> = emptyList(),
        blockhash: ByteArray = ByteArray(32) { 0x33 },
        signatures: List<ByteArray> = List(signers.size) { ByteArray(64) },
    ): ByteArray {
        require(signatures.size == signers.size)
        val accounts = signers + unsignedAccounts
        val message = buildList<Byte> {
            add(signers.size.toByte())
            add(0)
            add(0)
            addAll(shortVec(accounts.size).toList())
            accounts.forEach { addAll(it.toList()) }
            addAll(blockhash.toList())
            addAll(shortVec(0).toList())
        }.toByteArray()

        return buildList<Byte> {
            addAll(shortVec(signatures.size).toList())
            signatures.forEach { addAll(it.toList()) }
            addAll(message.toList())
        }.toByteArray()
    }

    private fun messageOffset(transaction: ByteArray): Int = 1 + ((transaction[0].toInt() and 0xff) * 64)

    private fun shortVec(value: Int): ByteArray {
        require(value >= 0)
        var remaining = value
        val out = ArrayList<Byte>()
        do {
            var current = remaining and 0x7f
            remaining = remaining ushr 7
            if (remaining != 0) current = current or 0x80
            out += current.toByte()
        } while (remaining != 0)
        return out.toByteArray()
    }

    private fun assertRejected(
        expected: LegacyTransactionCodec.RejectionReason,
        block: () -> Unit,
    ) {
        try {
            block()
            fail("Expected rejection: $expected")
        } catch (rejected: LegacyTransactionCodec.Rejected) {
            assertEquals(expected, rejected.reason)
        }
    }
}
