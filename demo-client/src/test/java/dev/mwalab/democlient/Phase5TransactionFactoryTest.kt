package dev.mwalab.democlient

import org.junit.Assert.*
import org.junit.Test

class Phase5TransactionFactoryTest {
    @Test fun publicTemplatesMatchIndependentlyCheckedVectors() {
        val baselinePayer = ByteArray(32) { 0x11 }
        val baselineBlockhash = ByteArray(32) { 0x66 }
        for ((kind, name) in listOf(
            Phase5TransactionKind.GOOD_MEMO to "legacy-good-memo-template",
            Phase5TransactionKind.BAD_SYSTEM_OPCODE to "legacy-bad-system-template",
        )) {
            val expected = javaClass.classLoader!!.getResourceAsStream("$name.hex")!!
                .bufferedReader().use { it.readText().trim() }
                .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val built = Phase5TransactionFactory.build(kind, baselinePayer, baselineBlockhash)
            assertArrayEquals(name, expected, built.transaction)
            assertArrayEquals(built.message, built.transaction.copyOfRange(65, built.transaction.size))
            assertTrue(built.transaction.copyOfRange(1, 65).all { it == 0.toByte() })
            assertTrue(built.transaction.size <= 1232)
        }
    }

    @Test fun livePayerAndFreshBlockhashSubstituteOnlyPublicWireLocations() {
        val baselinePayer = ByteArray(32) { 0x11 }
        val baselineBlockhash = ByteArray(32) { 0x66 }
        val payer = ByteArray(32) { 0x77 }
        val fresh = ByteArray(32) { 0x44 }
        for (kind in Phase5TransactionKind.entries) {
            val baseline = Phase5TransactionFactory.build(kind, baselinePayer, baselineBlockhash)
            val live = Phase5TransactionFactory.build(kind, payer, fresh)
            val expected = baseline.transaction.copyOf().also {
                payer.copyInto(it, 69)
                fresh.copyInto(it, 133)
            }
            assertArrayEquals(expected, live.transaction)
            assertArrayEquals(fresh, live.recentBlockhash)
        }
    }

    @Test fun badOpcodeIsStructurallySignableAndScenarioSelectionIsExplicit() {
        val bad = Phase5TransactionFactory.build(Phase5TransactionKind.BAD_SYSTEM_OPCODE,
            ByteArray(32) { 0x11 }, ByteArray(32) { 0x66 })
        assertArrayEquals(byteArrayOf(-1, -1, -1, -1),
            bad.message.copyOfRange(bad.message.size - 4, bad.message.size))
        assertEquals(Phase5AcceptanceScenario.BAD_FAIL_APPROVE,
            Phase5AcceptanceScenario.fromWireName("BAD_FAIL_APPROVE"))
        assertNull(Phase5AcceptanceScenario.fromWireName("RPC_UNAVAILABLE"))
        assertTrue(runCatching { Phase5TransactionFactory.build(Phase5TransactionKind.GOOD_MEMO,
            ByteArray(31), ByteArray(32)) }.isFailure)
        assertTrue(runCatching { Phase5TransactionFactory.build(Phase5TransactionKind.BAD_SYSTEM_OPCODE,
            ByteArray(32), ByteArray(31)) }.isFailure)
    }
}
