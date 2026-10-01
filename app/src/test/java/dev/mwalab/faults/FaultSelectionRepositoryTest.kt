package dev.mwalab.faults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaultSelectionRepositoryTest {
    @Test
    fun defaultSwitchResetAndSameSelectionAreSingleActive() {
        val store = MemoryStore()
        val repository = PersistentFaultSelectionRepository(store)
        assertEquals(FaultId.NORMAL, repository.selected.value.id)
        repository.select(FaultId.SIGN_REJECT)
        assertEquals(FaultId.SIGN_REJECT, repository.selected.value.id)
        repository.select(FaultId.RPC_UNAVAILABLE)
        assertEquals(FaultId.RPC_UNAVAILABLE, repository.selected.value.id)
        assertEquals("FAULT_RPC_UNAVAILABLE", store.value)
        repository.select(FaultId.RPC_UNAVAILABLE)
        assertEquals(2, store.writes)
        repository.select(FaultId.NORMAL)
        assertEquals(FaultId.NORMAL, repository.selected.value.id)
        assertEquals("NORMAL", store.value)
    }

    @Test
    fun recreationRecoversOnlySelectedStableId() {
        val store = MemoryStore()
        PersistentFaultSelectionRepository(store).select(FaultId.STALE_BLOCKHASH)
        val recreated = PersistentFaultSelectionRepository(store)
        assertEquals(FaultId.STALE_BLOCKHASH, recreated.selected.value.id)
        assertEquals("FAULT_STALE_BLOCKHASH", store.value)
    }

    @Test
    fun unknownAndCorruptPersistedValuesFailSafe() {
        val unknown = MemoryStore("UNRECOGNIZED")
        val repository = PersistentFaultSelectionRepository(unknown)
        assertEquals(FaultId.NORMAL, repository.selected.value.id)
        repository.select(FaultId.NORMAL)
        assertEquals("NORMAL", unknown.value)
        assertEquals(FaultId.NORMAL, PersistentFaultSelectionRepository(unknown).selected.value.id)

        val corrupt = object : FaultSelectionStore {
            override fun read(): String? = throw ClassCastException("corrupt preference type")
            override fun write(stableId: String): Boolean = true
        }
        assertEquals(FaultId.NORMAL, PersistentFaultSelectionRepository(corrupt).selected.value.id)
    }

    @Test
    fun failedWriteDoesNotChangeAuthority() {
        val store = MemoryStore(acceptWrites = false)
        val repository = PersistentFaultSelectionRepository(store)
        assertTrue(runCatching { repository.select(FaultId.SIGN_REJECT) }.exceptionOrNull() is IllegalStateException)
        assertEquals(FaultId.NORMAL, repository.selected.value.id)
    }

    private class MemoryStore(
        var value: String? = null,
        private val acceptWrites: Boolean = true,
    ) : FaultSelectionStore {
        var writes: Int = 0
        override fun read(): String? = value
        override fun write(stableId: String): Boolean {
            writes++
            if (acceptWrites) value = stableId
            return acceptWrites
        }
    }
}
