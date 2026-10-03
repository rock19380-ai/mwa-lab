package dev.mwalab.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TestWalletStateTest {
    @Test fun lamportFormattingNeverUsesFloatingPointRounding() {
        assertEquals("0", formatLamportsAsSol(0))
        assertEquals("0.000000001", formatLamportsAsSol(1))
        assertEquals("0.5", formatLamportsAsSol(500_000_000))
        assertEquals("1", formatLamportsAsSol(1_000_000_000))
        assertEquals("12.3456789", formatLamportsAsSol(12_345_678_900))
    }

    @Test fun negativeLamportsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { formatLamportsAsSol(-1) }
    }
}
