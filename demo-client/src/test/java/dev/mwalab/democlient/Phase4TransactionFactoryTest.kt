package dev.mwalab.democlient

import org.junit.Assert.*
import org.junit.Test

class Phase4TransactionFactoryTest {
    @Test fun threeDemoScenariosMatchCheckedInVectorsBeforePublicRuntimeSubstitution() {
        val cases = mapOf(Phase4AcceptanceScenario.SYSTEM_TRANSFER to "legacy-system-transfer",
            Phase4AcceptanceScenario.UNKNOWN_PROGRAM to "legacy-unknown-program",
            Phase4AcceptanceScenario.V0_REJECT to "v0-unresolved-lookup")
        cases.forEach { (scenario, name) ->
            val text = javaClass.classLoader!!.getResourceAsStream("$name.hex")!!.bufferedReader().use { it.readText().trim() }
            val expected = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val built = Phase4TransactionFactory.build(scenario, ByteArray(32) { 0x11 }, ByteArray(32) { 0x66 })
            assertArrayEquals(name, expected, built.transaction)
            assertArrayEquals(built.message, built.transaction.copyOfRange(65, built.transaction.size))
            assertTrue(built.transaction.copyOfRange(1, 65).all { it == 0.toByte() })
        }
    }

    @Test fun livePublicPayerAndBlockhashSubstitutionChangeOnlyTheirWireLocations() {
        val payer = ByteArray(32) { 0x77 }
        val hash = ByteArray(32) { 0x44 }
        for (scenario in Phase4AcceptanceScenario.entries) {
            val baseline = Phase4TransactionFactory.build(scenario, ByteArray(32) { 0x11 }, ByteArray(32) { 0x66 })
            val live = Phase4TransactionFactory.build(scenario, payer, hash)
            val accountOffset = 4 + if (scenario == Phase4AcceptanceScenario.V0_REJECT) 1 else 0
            val blockhashOffset = accountOffset + 96
            val expectedMessage = baseline.message.copyOf().also {
                payer.copyInto(it, accountOffset); hash.copyInto(it, blockhashOffset)
            }
            assertArrayEquals(expectedMessage, live.message)
            assertArrayEquals(hash, live.recentBlockhash)
        }
    }

    @Test fun badPublicInputSizesAreRejectedAndPhase4IntentNamesAreExplicit() {
        assertNull(Phase4AcceptanceScenario.fromWireName("SIGN_AND_SEND_APPROVE"))
        assertEquals(Phase4AcceptanceScenario.SYSTEM_TRANSFER, Phase4AcceptanceScenario.fromWireName("SYSTEM_TRANSFER"))
        assertTrue(runCatching { Phase4TransactionFactory.build(Phase4AcceptanceScenario.SYSTEM_TRANSFER,
            ByteArray(31), ByteArray(32)) }.isFailure)
        assertTrue(runCatching { Phase4TransactionFactory.build(Phase4AcceptanceScenario.SYSTEM_TRANSFER,
            ByteArray(32), ByteArray(33)) }.isFailure)
    }
}
