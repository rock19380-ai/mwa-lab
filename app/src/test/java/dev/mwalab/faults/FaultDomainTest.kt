package dev.mwalab.faults

import com.solana.mobilewalletadapter.common.ProtocolContract
import dev.mwalab.protocol.ProtocolMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaultDomainTest {
    @Test
    fun catalogHasUniqueStableIdsAndExplicitContracts() {
        assertEquals(10, FaultId.entries.size)
        assertEquals(FaultId.entries.size, FaultId.entries.map { it.stableId }.toSet().size)
        assertEquals(FaultId.entries.toSet(), FaultCatalog.profiles.map { it.id }.toSet())
        assertEquals(FaultId.NORMAL, FaultCatalog.get(FaultId.NORMAL).id)
        assertTrue(FaultCatalog.get(FaultId.NORMAL).targetMethods.isEmpty())
        assertNull(FaultCatalog.get(FaultId.NORMAL).hook)
        assertTrue(FaultCatalog.profiles.filter { it.id != FaultId.NORMAL }
            .all { it.hook != null && it.targetMethods.isNotEmpty() })
        for (profile in FaultCatalog.profiles) {
            assertEquals(profile, FaultCatalog.find(profile.id.stableId))
            assertEquals(profile, FaultCatalog.find(profile.id.stableId))
        }
        assertNull(FaultCatalog.find("FAULT_NOT_DEFINED"))
        assertNull(FaultCatalog.find(null))
        assertNull(FaultCatalog.find("fault_sign_reject"))
    }

    @Test
    fun declaredMethodsHooksAndCodesMatchDesignFreeze() {
        val signing = setOf(ProtocolMethod.SIGN_MESSAGES, ProtocolMethod.SIGN_TRANSACTIONS,
            ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS)
        val transactions = signing - ProtocolMethod.SIGN_MESSAGES
        val expected = mapOf(
            FaultId.AUTH_REJECT to Triple(setOf(ProtocolMethod.AUTHORIZE, ProtocolMethod.REAUTHORIZE),
                FaultHook.AUTHORIZATION_DECISION, ProtocolContract.ERROR_AUTHORIZATION_FAILED),
            FaultId.SIGN_REJECT to Triple(signing, FaultHook.SIGNING_PRE_APPROVAL,
                ProtocolContract.ERROR_NOT_SIGNED),
            FaultId.DELAY_5S to Triple(signing, FaultHook.SIGNING_PRE_APPROVAL, null),
            FaultId.UNSUPPORTED_CHAIN to Triple(setOf(ProtocolMethod.AUTHORIZE),
                FaultHook.AUTHORIZATION_DECISION, ProtocolContract.ERROR_CLUSTER_NOT_SUPPORTED),
            FaultId.INVALID_PAYLOAD to Triple(signing, FaultHook.SIGNING_VALIDATION,
                ProtocolContract.ERROR_INVALID_PAYLOADS),
            FaultId.TOO_MANY_PAYLOADS to Triple(signing, FaultHook.SIGNING_VALIDATION,
                ProtocolContract.ERROR_TOO_MANY_PAYLOADS),
            FaultId.RPC_UNAVAILABLE to Triple(setOf(ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS),
                FaultHook.SUBMISSION_PRE_RPC, ProtocolContract.ERROR_NOT_SUBMITTED),
            FaultId.SUBMISSION_FAILURE to Triple(setOf(ProtocolMethod.SIGN_AND_SEND_TRANSACTIONS),
                FaultHook.SUBMISSION_PRE_RPC, ProtocolContract.ERROR_NOT_SUBMITTED),
            FaultId.STALE_BLOCKHASH to Triple(transactions, FaultHook.TRANSACTION_BLOCKHASH_CHECK,
                ProtocolContract.ERROR_INVALID_PAYLOADS),
        )
        assertEquals(9, expected.size)
        expected.forEach { (id, contract) ->
            val profile = FaultCatalog.get(id)
            assertEquals(id.stableId, profile.id.stableId)
            assertEquals(contract.first, profile.targetMethods)
            assertEquals(contract.second, profile.hook)
            assertEquals(contract.third, profile.expectedProtocolCode)
            assertFalse(ProtocolMethod.DEAUTHORIZE in profile.targetMethods)
            assertFalse(ProtocolMethod.GET_CAPABILITIES in profile.targetMethods)
        }
    }

    @Test
    fun engineOnlyDecidesAtDeclaredMethodAndHook() {
        val engine = DeterministicFaultEngine()
        val normal = FaultRequestSnapshot(FaultCatalog.get(FaultId.NORMAL), ProtocolMethod.AUTHORIZE,
            "session-a", 1)
        assertEquals(FaultDecision.Continue, engine.evaluate(normal, FaultHook.AUTHORIZATION_DECISION))

        val reject = FaultRequestSnapshot(FaultCatalog.get(FaultId.SIGN_REJECT),
            ProtocolMethod.SIGN_MESSAGES, "session-a", 1)
        assertEquals(FaultDecision.RejectSigning, engine.evaluate(reject, FaultHook.SIGNING_PRE_APPROVAL))
        assertEquals(FaultDecision.Continue, engine.evaluate(reject, FaultHook.SIGNING_VALIDATION))
        assertEquals(FaultDecision.Continue, engine.evaluate(reject.copy(method = ProtocolMethod.AUTHORIZE),
            FaultHook.SIGNING_PRE_APPROVAL))
        assertEquals(FaultDecision.Delay(5_000), engine.evaluate(
            reject.copy(profile = FaultCatalog.get(FaultId.DELAY_5S)), FaultHook.SIGNING_PRE_APPROVAL))
    }

    @Test
    fun capturedProfileAndSessionStayBoundAfterSelectionChanges() {
        val selection = PersistentFaultSelectionRepository(MemoryStore())
        val engine = DeterministicFaultEngine()
        selection.select(FaultId.SIGN_REJECT)
        val first = engine.capture(selection, ProtocolMethod.SIGN_MESSAGES, "session-a", 2)
        selection.select(FaultId.DELAY_5S)
        val second = engine.capture(selection, ProtocolMethod.SIGN_MESSAGES, "session-b", 3)

        assertEquals(FaultDecision.RejectSigning, engine.evaluate(first, FaultHook.SIGNING_PRE_APPROVAL))
        assertEquals(FaultDecision.Delay(5_000), engine.evaluate(second, FaultHook.SIGNING_PRE_APPROVAL))
        assertTrue(first.belongsTo("session-a", 2))
        assertFalse(first.belongsTo("session-b", 3))
        assertEquals("session-a", first.sessionId)
        assertEquals("session-b", second.sessionId)
    }

    private class MemoryStore : FaultSelectionStore {
        private var value: String? = null
        override fun read(): String? = value
        override fun write(stableId: String): Boolean { value = stableId; return true }
    }
}
