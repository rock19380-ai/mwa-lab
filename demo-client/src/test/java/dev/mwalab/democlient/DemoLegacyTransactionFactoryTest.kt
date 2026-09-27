package dev.mwalab.democlient

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoLegacyTransactionFactoryTest {
    @Test
    fun `memo transaction is canonical legacy shape`() {
        val feePayer = ByteArray(32) { index -> (index + 1).toByte() }
        val blockhash = ByteArray(32) { index -> (index + 33).toByte() }

        val built = DemoLegacyTransactionFactory.memoTransaction(
            feePayer = feePayer,
            recentBlockhash = blockhash,
            memo = "phase2".encodeToByteArray(),
        )

        assertEquals(1, built.transaction[0].toInt() and 0xff)
        assertTrue(built.transaction.copyOfRange(1, 65).all { it == 0.toByte() })
        assertArrayEquals(built.message, built.transaction.copyOfRange(65, built.transaction.size))
        assertArrayEquals(blockhash, built.recentBlockhash)

        assertEquals(1, built.message[0].toInt() and 0xff)
        assertEquals(0, built.message[1].toInt() and 0xff)
        assertEquals(1, built.message[2].toInt() and 0xff)
        assertEquals(2, built.message[3].toInt() and 0xff)
    }

    @Test
    fun `primary signature extraction rejects message mutation`() {
        val feePayer = ByteArray(32) { 7 }
        val blockhash = ByteArray(32) { 9 }
        val built = DemoLegacyTransactionFactory.memoTransaction(feePayer, blockhash)
        val signed = built.transaction.copyOf().also { transaction ->
            repeat(64) { index -> transaction[1 + index] = (index + 1).toByte() }
        }

        val signature = DemoLegacyTransactionFactory.primarySignature(signed, built.message)
        assertEquals(64, signature.size)

        val mutated = signed.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        val failure = runCatching {
            DemoLegacyTransactionFactory.primarySignature(mutated, built.message)
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun `memo program base58 decodes to public key length`() {
        val decoded = DemoLegacyTransactionFactory.decodeBase58(
            "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr",
        )
        assertEquals(32, decoded.size)
    }
}
