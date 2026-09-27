package dev.mwalab.transaction

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolanaTransactionMessageDetectorTest {
    @Test
    fun arbitraryTextIsNotTransactionMessage() {
        assertFalse(SolanaTransactionMessageDetector.isTransactionMessage("hello".encodeToByteArray()))
    }

    @Test
    fun minimalStructurallyValidLegacyMessageIsDetected() {
        val message = buildList<Byte> {
            add(1) // required signatures
            add(0) // readonly signed
            add(0) // readonly unsigned
            add(1) // account count shortvec
            repeat(32) { add(0) }
            repeat(32) { add(0) } // recent blockhash
            add(0) // instruction count shortvec
        }.toByteArray()
        assertTrue(SolanaTransactionMessageDetector.isTransactionMessage(message))
    }
}
